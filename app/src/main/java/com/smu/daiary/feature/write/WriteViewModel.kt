package com.smu.daiary.feature.write

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smu.daiary.data.model.DiaryEntry
import com.smu.daiary.data.repository.DailyDataRepository
import com.smu.daiary.data.repository.DiaryRepository
import com.smu.daiary.data.source.CalendarDataSource
import com.smu.daiary.data.source.PhotoDataSource
import com.smu.daiary.data.source.WeatherDataSource
import com.smu.daiary.data.remote.ClaudeApi
import com.smu.daiary.feature.write.PaymentSelectableItem
import com.smu.daiary.R
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream


private const val TAG = "WriteViewModel"

class WriteViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // SharedPreferences 언어 설정("한국어"/"English")에 맞는 로케일 Context를 반환.
    // Application context는 MainActivity.attachBaseContext의 로케일 재설정 영향을 받지
    // 않으므로, 매번 직접 Configuration을 덮어써야 한다.
    private fun localizedContext(): Context {
        val prefs = context.getSharedPreferences("daiary_settings", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "한국어") ?: "한국어"
        val locale = if (lang == "English") Locale("en") else Locale("ko")
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    // Repositories & DataSources
    private val diaryRepository = DiaryRepository()
    private val dailyDataRepository = DailyDataRepository()
    private val aiRepository = com.smu.daiary.data.repository.AiRepository()
    private val weatherDataSource = WeatherDataSource(context)
    private val photoDataSource = PhotoDataSource(context)
    private val calendarDataSource = CalendarDataSource(context)
    private val claudeApi = ClaudeApi()

    // 블록 목록 (실제 수집 데이터로 채워짐)
    private val _blocks = MutableStateFlow<List<ContentBlock>>(emptyList())
    val blocks: StateFlow<List<ContentBlock>> = _blocks.asStateFlow()

    private val _photos =
        MutableStateFlow<List<PhotoSelectableItem>>(emptyList())

    val photos =
        _photos.asStateFlow()

    private val _payments =
        MutableStateFlow<List<PaymentSelectableItem>>(emptyList())

    val payments =
        _payments.asStateFlow()

    // 데이터 로딩 상태
    private val _isLoadingBlocks = MutableStateFlow(false)
    val isLoadingBlocks: StateFlow<Boolean> = _isLoadingBlocks.asStateFlow()

    // 초안 상태
    private val _draft = MutableStateFlow<DiaryDraft?>(null)
    val draft: StateFlow<DiaryDraft?> = _draft.asStateFlow()

    // 저장 중 상태
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // 날씨·감정 선택
    private val _selectedWeather = MutableStateFlow<String?>(null)
    val selectedWeather: StateFlow<String?> = _selectedWeather.asStateFlow()

    private val _selectedEmotion = MutableStateFlow<String?>(null)
    val selectedEmotion: StateFlow<String?> = _selectedEmotion.asStateFlow()

    // AI 초안 생성 상태
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _generateError = MutableStateFlow<String?>(null)
    val generateError: StateFlow<String?> = _generateError.asStateFlow()

    // 저장 완료 이벤트 (스낵바 표시용)
    private val _saveEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveEvent: SharedFlow<Unit> = _saveEvent.asSharedFlow()

    // 기존 일기 편집 시 원본 ID (null = 신규 작성)
    private val _existingEntryId = MutableStateFlow<String?>(null)
    private var recentDiarySamples: String = ""

    /**
     * 실제 DataSource로부터 오늘 데이터를 수집하고
     * DailyDataRepository에 저장한 뒤 블록 목록을 구성합니다.
     * 각 항목은 병렬로 수집되어 실패해도 다른 항목에 영향을 주지 않습니다.
     */
    fun loadBlocks(userId: String) {
        viewModelScope.launch {
            loadRecentDiaryStyle(userId)

            _isLoadingBlocks.value = true
            _blocks.value = emptyList()
            _photos.value = emptyList()
            _payments.value = emptyList()


            val date = LocalDate.now().toString()
            val blocks = mutableListOf<ContentBlock>()

            Log.d(TAG, "📡 데이터 수집 시작 | userId=$userId, date=$date")

            // --- 날씨, 캘린더, 사진 병렬 수집 ---
            val weatherDeferred = async { runCatching { weatherDataSource.fetchWeather() } }
            val calendarDeferred = async { runCatching { calendarDataSource.fetchTodayEvents() } }
            val photoDeferred = async { runCatching { photoDataSource.fetchTodayPhotos() } }

            // 날씨
            weatherDeferred.await()
                .onSuccess { weather ->
                    Log.d(TAG, "🌤️ 날씨 수집 완료: ${weather.description} ${weather.temperature}°C")
                    dailyDataRepository.updateWeather(userId, date, weather)
                    blocks.add(ContentBlock(
                        id = "weather", type = BlockType.WEATHER,
                        content = localizedContext().getString(
                            R.string.block_weather_content,
                            localizedWeatherDescription(weather.description),
                            weather.temperature.toInt(),
                            weather.humidity
                        )
                    ))
                }
                .onFailure {
                    Log.w(TAG, "⚠️ 날씨 수집 실패 (권한 또는 네트워크 문제)", it)
                    blocks.add(ContentBlock(id = "weather", type = BlockType.WEATHER, content = localizedContext().getString(R.string.block_weather_unavailable)))
                }

            // 캘린더
            calendarDeferred.await()
                .onSuccess { events ->
                    Log.d(TAG, "📅 캘린더 수집 완료: ${events.size}개")
                    dailyDataRepository.updateCalendar(userId, date, events)
                    if (events.isEmpty()) {
                        blocks.add(ContentBlock(id = "calendar_0", type = BlockType.CALENDAR, content = localizedContext().getString(R.string.block_calendar_empty)))
                    } else {
                        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                        events.forEachIndexed { i, event ->
                            val startStr = Instant.ofEpochMilli(event.startTime)
                                .atZone(ZoneId.systemDefault()).format(timeFormatter)
                            val locationPart = if (event.location.isNotBlank()) " · ${event.location}" else ""
                            blocks.add(ContentBlock(
                                id = "calendar_$i", type = BlockType.CALENDAR,
                                content = "${event.title} $startStr$locationPart"
                            ))
                        }
                    }
                }
                .onFailure {
                    Log.w(TAG, "⚠️ 캘린더 수집 실패 (권한 문제)", it)
                    blocks.add(ContentBlock(id = "calendar_0", type = BlockType.CALENDAR, content = localizedContext().getString(R.string.block_calendar_unavailable)))
                }

            // 사진
            photoDeferred.await()
                .onSuccess { photos ->
                    Log.d(TAG, "🖼️ 사진 수집 완료: ${photos.size}장")
                    dailyDataRepository.updatePhotos(userId, date, photos)

                    _photos.value = photos.map { photo ->
                        PhotoSelectableItem(
                            uri = photo.uri,
                            isSelected = true
                        )
                    }

                        blocks.add(
                            ContentBlock(
                                id = "photo",
                                type = BlockType.PHOTO,
                                content = "오늘 찍은 사진 ${photos.size}장 · 선택 ${photos.size}장",
                                isSelected = photos.isNotEmpty()
                                )
                            )

                }
                .onFailure {
                    Log.w(TAG, "⚠️ 사진 수집 실패 (권한 문제)", it)
                    blocks.add(
                        ContentBlock(
                            id = "photo_error",
                            type = BlockType.PHOTO,
                            content = "사진을 불러오지 못했습니다"
                        )
                    )
                }

            // 결제 내역 (NotificationListenerService가 Firestore에 저장해둔 데이터를 읽어옴)
            dailyDataRepository.getDailyData(userId, date)
                .onSuccess { dailyData ->
                    val payments = dailyData?.payments ?: emptyList()

                    Log.d(TAG, "💳 결제 내역 로드 완료: ${payments.size}건")

                    if (payments.isNotEmpty()) {
                        val timeFormatter =
                            DateTimeFormatter.ofPattern("HH:mm")

                        _payments.value =
                            payments.mapIndexed { index, payment ->
                                val timeText =
                                    Instant.ofEpochMilli(payment.paidAt)
                                        .atZone(ZoneId.systemDefault())
                                        .format(timeFormatter)

                                val category =
                                    payment.category.ifBlank {
                                        "기타"
                                    }

                                PaymentSelectableItem(
                                    id = index,
                                    displayText = "$timeText ${categoryEmoji(category)} ${payment.merchant} ${String.format("%,d", payment.amount)}원",
                                    amount = payment.amount,
                                    category = category,
                                    isSelected = true
                                )
                            }

                        blocks.add(
                            ContentBlock(
                                id = "payment_summary",
                                type = BlockType.PAYMENT,
                                content = buildPaymentSummary(_payments.value),
                                isSelected = true
                            )
                        )
                    }
                }
                .onFailure {
                    Log.w(TAG, "⚠️ 결제 내역 로드 실패", it)
                }
                .onFailure { Log.w(TAG, "⚠️ 결제 내역 로드 실패", it) }

            Log.d(TAG, "✅ 전체 수집 완료 | 블록 수=${blocks.size}")
            _blocks.value = blocks
            _isLoadingBlocks.value = false
        }
    }

    private fun localizedWeatherDescription(canonical: String): String = when (canonical) {
        "맑음" -> localizedContext().getString(R.string.weather_sunny)
        "흐림" -> localizedContext().getString(R.string.weather_cloudy)
        "비"   -> localizedContext().getString(R.string.weather_rain)
        "눈"   -> localizedContext().getString(R.string.weather_snow)
        "바람" -> localizedContext().getString(R.string.weather_wind)
        else   -> canonical
    }
    private fun uriToBase64(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)

            context.contentResolver
                .openInputStream(uri)
                ?.use { input ->

                    val bytes =
                        input.readBytes()

                    Base64.encodeToString(
                        bytes,
                        Base64.NO_WRAP
                    )
                }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "이미지 Base64 변환 실패",
                e
            )
            null
        }
    }

    fun toggleBlock(id: String) {
        _blocks.update { list ->
            list.map { if (it.id == id) it.copy(isSelected = !it.isSelected) else it }
        }
    }

    fun updatePaymentSelection(
        selectedPayments: List<String>
    ) {

        _blocks.update { list ->

            list.map { block ->

                if (
                    block.type ==
                    BlockType.PAYMENT
                ) {

                    val lines =
                        block.content
                            .split("\n")

                    val header =
                        lines.first()

                    val filtered =
                        lines.drop(1)
                            .filter {

                                selectedPayments.any {
                                        p ->
                                    it.contains(p)
                                }

                            }

                    block.copy(
                        content =
                            buildString {
                                appendLine(header)
                                filtered.forEach {
                                    appendLine(it)
                                }
                            }
                    )

                }

                else {
                    block
                }

            }

        }

    }

    fun togglePhoto(uri: String) {
        _photos.update { list ->
            list.map { photo ->
                if (photo.uri == uri) {
                    photo.copy(isSelected = !photo.isSelected)
                } else {
                    photo
                }
            }
        }
    }

    fun addSelectablePhoto(uri: String) {
        val alreadyExists =
            _photos.value.any {
                it.uri == uri
            }

        if (alreadyExists) return

        _photos.update { list ->
            list + PhotoSelectableItem(
                uri = uri,
                isSelected = true
            )
        }

        syncPhotoBlockSelection()
    }

    fun togglePayment(
        id: Int
    ) {

        _payments.update { list ->

            list.map { payment ->

                if (
                    payment.id == id
                ) {

                    payment.copy(
                        isSelected =
                            !payment.isSelected
                    )

                }

                else {
                    payment
                }

            }

        }

        syncPaymentBlockSelection()

    }

    private fun syncPaymentBlockSelection() {

        val selected =
            _payments.value
                .filter {
                    it.isSelected
                }

        _blocks.update { list ->

            list.map { block ->

                if (
                    block.type ==
                    BlockType.PAYMENT
                ) {

                    block.copy(

                        content =
                            buildPaymentSummary(
                                _payments.value
                            ),

                        isSelected =
                            selected.isNotEmpty()

                    )

                }

                else {
                    block
                }

            }

        }

    }

    private fun buildPaymentSummary(
        payments: List<PaymentSelectableItem>
    ): String {
        val selected =
            payments.filter { it.isSelected }

        if (selected.isEmpty()) {
            return "선택된 결제 내역이 없습니다"
        }

        val totalAmount =
            selected.sumOf { it.amount }

        val paymentLines =
            selected.joinToString("\n") {
                "- ${it.displayText}"
            }

        return "오늘 결제 ${selected.size}건 · 총 ${String.format("%,d", totalAmount)}원\n$paymentLines"
    }

    private fun categoryEmoji(category: String): String {
        return when (category) {
            "카페" -> "☕"
            "편의점" -> "🛒"
            "교통" -> "🚌"
            "식사" -> "🍔"
            else -> "💳"
        }
    }


    fun setAllPhotosSelected(selected: Boolean) {
        _photos.update { list ->
            list.map { photo ->
                photo.copy(isSelected = selected)
            }
        }
    }

    fun syncPhotoBlockSelection() {
        val totalCount = _photos.value.size
        val selectedCount = _photos.value.count { it.isSelected }
        val hasSelectedPhoto = _photos.value.any { it.isSelected }

        _blocks.update { list ->
            list.map { block ->
                if (block.type == BlockType.PHOTO) {
                    block.copy(
                        content = "오늘 찍은 사진 ${totalCount}장 · 선택 ${selectedCount}장",
                        isSelected = hasSelectedPhoto
                    )
                } else {
                    block
                }
            }
        }
    }



    fun generateDraft() = viewModelScope.launch {
        val selected = _blocks.value.filter { it.isSelected }
        val today = LocalDate.now().toString()

        if (selected.isEmpty()) {
            _draft.value = DiaryDraft(
                date = today,
                aiContent = "오늘 하루를 기록해보세요.",
                editedContent = "오늘 하루를 기록해보세요.",
                photos = emptyList()
            )
            return@launch
        }

        viewModelScope.launch {
            _isGenerating.value = true
            _generateError.value = null

            if (selected.isEmpty()) {
                _draft.value = DiaryDraft(
                    date = today,
                    aiContent = "",
                    editedContent = "",
                    photos = emptyList()
                )
                return@launch
            }

            val prefs = context.getSharedPreferences("daiary_settings", android.content.Context.MODE_PRIVATE)
            val locale = if (prefs.getString("language", "한국어") == "English") "en" else "ko"

            val selectedPhotoBase64 =
                _photos.value
                    .filter { it.isSelected }
                    .mapNotNull { uriToBase64(it.uri) }

            Log.d(TAG, "📸 선택된 사진 수: ${_photos.value.count { it.isSelected }}")
            Log.d(TAG, "📸 base64 변환 성공 수: ${selectedPhotoBase64.size}")

            val photoSummary =
                withContext(Dispatchers.IO) {
                    try {
                        claudeApi.analyzePhotos(selectedPhotoBase64)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 사진 분석 실패", e)
                        "사진 ${selectedPhotoBase64.size}장이 선택됨"
                    }
                }

            Log.d(TAG, "📸 사진 분석 결과: $photoSummary")


            val mbti = getApplication<Application>()
                .getSharedPreferences(
                    "user_settings",
                    android.content.Context.MODE_PRIVATE
                )
                .getString("mbti", "INFP") ?: "INFP"

            val result =
                aiRepository.generateDraft(
                    blocks = selected,
                    locale = locale,
                    mbti = mbti,
                    photoSummary = photoSummary,
                    recentDiarySamples = recentDiarySamples
                )
            val content = result.getOrElse { fallbackTemplate(selected) }

            if (result.isFailure) {
                _generateError.value = if (locale == "en")
                    "AI generation failed. Using default template."
                else
                    "초안 생성에 실패했습니다. 기본 템플릿으로 대체합니다."
            }

            val selectedPhotoUris =
                _photos.value
                    .filter { it.isSelected }
                    .map { it.uri }

            _draft.value = DiaryDraft(
                date = today,
                aiContent = content,
                photos = selectedPhotoUris
            )
            _isGenerating.value = false
        }
    }

    private fun fallbackTemplate(selected: List<ContentBlock>): String = buildString {
        appendLine(localizedContext().getString(R.string.draft_intro))
        appendLine()
        selected.forEach { block ->
            when (block.type) {
                BlockType.PAYMENT  -> appendLine(localizedContext().getString(R.string.draft_block_payment, block.content))
                BlockType.PHOTO    -> appendLine(localizedContext().getString(R.string.draft_block_photo, block.content))
                BlockType.CALENDAR -> appendLine(localizedContext().getString(R.string.draft_block_calendar, block.content))
                BlockType.HEALTH   -> appendLine(localizedContext().getString(R.string.draft_block_health, block.content))
                BlockType.WEATHER  -> appendLine(localizedContext().getString(R.string.draft_block_weather, block.content))
            }
        }
        appendLine()
        append(localizedContext().getString(R.string.draft_outro))
    }

    fun clearGenerateError() { _generateError.value = null }

    fun updateEditedContent(content: String) {
        _draft.update { it?.copy(editedContent = content) }
    }

    fun addPhoto(uri: String) {
        _draft.update { it?.copy(photos = it.photos + uri) }
    }

    fun removePhoto(uri: String) {
        _draft.update { it?.copy(photos = it.photos.filter { p -> p != uri }) }
    }

    fun saveDraft(userId: String, onComplete: (Boolean) -> Unit) {
        val d = _draft.value ?: return
        _isSaving.value = true
        viewModelScope.launch {
            val localDate = runCatching { LocalDate.parse(d.date) }.getOrNull()
            val formattedTitle = if (localDate != null)
                localizedContext().getString(R.string.diary_entry_title, localDate.year, localDate.monthValue, localDate.dayOfMonth)
            else d.date
            val mood = when (_selectedEmotion.value) {
                localizedContext().getString(R.string.emotion_joy),
                localizedContext().getString(R.string.emotion_excited) -> "happy"
                localizedContext().getString(R.string.emotion_sad),
                localizedContext().getString(R.string.emotion_angry) -> "sad"
                else -> "neutral"
            }
            val existingId = _existingEntryId.value

            val selectedPhotoUris = _photos.value
                .filter { it.isSelected }
                .map { it.uri }

            val entry = DiaryEntry(
                id = existingId ?: "",
                title = formattedTitle,
                content = d.editedContent ?: d.aiContent,
                date = d.date,
                mood = mood,
                emotion = _selectedEmotion.value ?: "",
                weather = _selectedWeather.value ?: "",
                photos = selectedPhotoUris
            )
            val result = if (existingId != null) {
                diaryRepository.updateDiary(userId, entry)
            } else {
                diaryRepository.addDiary(userId, entry)
            }
            _isSaving.value = false
            _draft.update { it?.copy(status = if (result.isSuccess) DraftStatus.SAVED else DraftStatus.IDLE) }
            if (result.isSuccess) _saveEvent.tryEmit(Unit)
            onComplete(result.isSuccess)
        }
    }

    fun updateWeatherSelection(weather: String?) { _selectedWeather.value = weather }
    fun updateEmotionSelection(emotion: String?) { _selectedEmotion.value = emotion }

    fun loadExistingEntry(entry: DiaryEntry) {
        _existingEntryId.value = entry.id
        _draft.value = DiaryDraft(
            date = entry.date,
            aiContent = entry.content,
            editedContent = entry.content,
            photos = entry.photos
        )
        _selectedWeather.value = entry.weather.ifEmpty { null }
        _selectedEmotion.value = entry.emotion.ifEmpty { null }
    }

    fun clearDraftOnly() {
        _draft.value = null
    }

    fun loadRecentDiaryStyle(
        userId: String
    ) {

        viewModelScope.launch {

            diaryRepository
                .getDiaries(userId)
                .collect { diaries ->

                    recentDiarySamples =
                        diaries
                            .take(2)
                            .map {
                                it.content
                                    .takeLast(500)
                            }
                            .joinToString(
                                "\n\n---\n\n"
                            )

                }

        }

    }

    fun resetDraft() {
        _draft.value = null
        _blocks.value = emptyList()
        _selectedWeather.value = null
        _selectedEmotion.value = null
        _existingEntryId.value = null
    }
}

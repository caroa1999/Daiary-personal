package com.smu.daiary.data.source

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smu.daiary.data.model.DailyData
import com.smu.daiary.data.model.PaymentData
import com.smu.daiary.data.repository.DailyDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import com.smu.daiary.util.DiaryDateUtil

class PaymentNotificationService : NotificationListenerService() {

    private val repository = DailyDataRepository()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex() // Race condition 방지

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text =
            extras.getCharSequence("android.text")?.toString()
                ?: extras.getCharSequence("android.bigText")?.toString()
                ?: ""

        android.util.Log.d(
            "PAYMENT",
            "package=$packageName title=$title text=$text"
        )

        // 지원하는 앱 패키지명 목록
        val supportedApps = mapOf(
            "viva.republica.toss"   to ::parseToss,
            "com.kakaobank.channel" to ::parseKakaoBank,
            "com.smu.daiary"        to ::parseTestPayment
        )

        val parser = supportedApps.entries
            .firstOrNull { packageName.contains(it.key) }
            ?.value ?: return

        val payment = parser(title, text) ?: return

        // Firestore에 저장 (mutex로 동시 저장 시 race condition 방지)
        scope.launch {
            val userId = getUserId() ?: return@launch
            // 오전 4시 이전 결제는 전날 일기 데이터로 귀속
            val date = DiaryDateUtil.diaryDate().toString()
            mutex.withLock {
                val existing = repository.getDailyData(userId, date).getOrNull()
                if (existing == null) {
                    // 오늘 첫 번째 결제 → 문서 새로 생성
                    repository.saveDailyData(userId, DailyData(date = date, payments = listOf(payment)))
                } else {
                    repository.updatePayments(userId, date, existing.payments + payment)
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        scope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // 토스 알림 파싱
    // 실제 토스 알림 형식:
    //   title: "1,000원 결제"
    //   text:  "비씨체크 | 씨유(CU)화곡시원점(일시불)"
    private fun parseToss(title: String, text: String): PaymentData? {
        if (!title.contains("출금") && !title.contains("결제")) return null

        // title에서 금액 추출
        val amountRegex = Regex("""([\d,]+)원""")
        val amountMatch = amountRegex.find(title) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toIntOrNull() ?: return null

        // text의 "|" 뒤에서 가맹점명 추출
        val merchant = text.substringAfter("|", "").trim()
        if (merchant.isBlank()) return null

        return PaymentData(
            merchant = merchant,
            amount = amount,
            paidAt = System.currentTimeMillis(),
            category = classifyPayment(merchant)
        )
    }

    // 카카오뱅크 알림 파싱 (추후 형식 확인 후 업데이트 예정)
    private fun parseKakaoBank(title: String, text: String): PaymentData? {
        if (!title.contains("출금") && !title.contains("결제")) return null
        return parseAmountAndMerchant(text)
    }

    private fun parseTestPayment(title: String, text: String): PaymentData? {
        if (!title.contains("결제") && !text.contains("원")) return null
        return parseAmountAndMerchant(text)
    }

    // 가맹점명, 금액 추출 공통 로직
    // 예시: "스타벅스 4,500원" → merchant: "스타벅스", amount: 4500
    private fun parseAmountAndMerchant(text: String): PaymentData? {
        val amountRegex = Regex("""([\d,]+)원""")
        val amountMatch = amountRegex.find(text) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toIntOrNull() ?: return null
        val merchant = text.substringBefore(amountMatch.value).trim()
        if (merchant.isBlank()) return null

        return PaymentData(
            merchant = merchant,
            amount = amount,
            paidAt = System.currentTimeMillis(),
            category = classifyPayment(merchant)
        )
    }

    private fun classifyPayment(
        merchant: String
    ): String {

        return when {

                    merchant.contains("스타벅스") ||
                    merchant.contains("투썸") ||
                    merchant.contains("메가커피") ||
                    merchant.contains("컴포즈") ->

                          "카페"

                    merchant.contains("GS25") ||
                    merchant.contains("CU") ||
                    merchant.contains("세븐") ->

                          "편의점"

                    merchant.contains("버스") ||
                    merchant.contains("지하철") ||
                    merchant.contains("카카오T") ->

                          "교통"

                    merchant.contains("맥도날드") ||
                    merchant.contains("버거킹") ||
                    merchant.contains("롯데리아") ->

                          "식사"

            else ->
                "기타"

        }

    }

    // 현재 로그인된 userId 가져오기 (Firebase Auth)
    private fun getUserId(): String? {
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
}

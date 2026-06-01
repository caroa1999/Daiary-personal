package com.smu.daiary.data.source

import com.smu.daiary.BuildConfig
import com.smu.daiary.feature.write.BlockType
import com.smu.daiary.feature.write.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AnthropicDataSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    suspend fun generateDiary(
        blocks: List<ContentBlock>,
        locale: String,
        mbti: String,
        photoSummary: String? = null,
        recentDiarySamples: String = ""
    ): String =
        withContext(Dispatchers.IO) {
            val blocksText = blocks.joinToString("\n") { "- [${it.type.label}] ${it.content}" }

            val photoText = photoSummary
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    if (locale == "en") {
                        "\n\nPhoto analysis result:\n$it"
                    } else {
                        "\n\n사진 분석 결과:\n$it"
                    }
                }
                ?: ""

            val prompt = buildPrompt(
                blocksText = blocksText + photoText,
                locale = locale,
                mbti = mbti,
                recentDiarySamples = recentDiarySamples
            )

            val body = JSONObject().apply {
                put("model", "claude-haiku-4-5-20251001")
                put("max_tokens", 1024)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("빈 응답")

            if (!response.isSuccessful) {
                throw Exception("API 오류 (${response.code}): $responseBody")
            }

            JSONObject(responseBody)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        }

    private fun buildPrompt(
        blocksText: String,
        locale: String,
        mbti: String,
        recentDiarySamples: String = ""
    ): String {
        android.util.Log.d("AnthropicDS", "🔍 buildPrompt locale = $locale")
        return if (locale == "en") {
        """
You are an AI that writes a warm, personal diary entry based on the user's daily data.

Write a 3–5 paragraph diary in first person based on the data below.
- Reflect MBTI personality: $mbti
- Connect the data into a natural narrative, not a bullet list
- Use plain text only, no markdown

${if (recentDiarySamples.isNotBlank()) """
        [User writing style samples]
        The following are recent diary entries written by the user .
        Do not copy events or phrases .
        Only refer to sentence length, emotional tone, reflection style, and wording habits.
        If the samples have different styles, prioritize the most recent one.

        $recentDiarySamples
        """ else ""}

[Data]
$blocksText
        """.trimIndent()
    } else {
        """
당신은 사용자를 대신해 하루 일기를 쓰는 AI입니다.
아래 데이터를 바탕으로 오늘 하루를 돌아보는 1인칭 일기를 작성해 주세요.
사용자의 MBTI는 $mbti 입니다.

MBTI 성향은 20~30% 정도만 반영하세요.
성격을 과장하거나 고정관념처럼 표현하지 마세요.
같은 사람이 다른 기분으로 쓴 일기처럼 자연스럽게 조절하세요.

다음 요소에만 은은하게 반영하세요.
- 사건을 해석하는 방식
- 무엇에 주목하는지
- 감정 표현 방식
- 하루를 정리하는 방식
- 미래를 바라보는 방식

단, MBTI 이름 자체를 직접 언급하지 마세요.

[작성 규칙]
- 문체: 반말 일기체 (예: "~했다", "~이었다", "~인 것 같다")
- 분량: 사용자 평균 분량 우선 (없으면 5~8문장)
- 첫 문장: 오늘의 날씨나 기분으로 하루를 여는 문장으로 시작
- 중간 문단: 하루의 흐름(아침→낮→저녁) 순서로 사건과 그때의 감정, 생각을 연결
- 마지막 문단: 오늘 하루를 돌아보며 느낀 점이나 내일에 대한 짧은 생각으로 마무리
- 단순한 사실 나열 금지 — 그 순간 어떤 감정이었는지 내면을 담을 것
- 마크다운 없이 순수 텍스트로만 작성
- 사용자의 기존 평균 문장 수를 넘기지 말 것
- 최근 일기가 짧으면 생성 결과도 짧게 유지
- 감정 해석이나 교훈을 추가하지 말 것
- 문장을 늘리기 위해 배경 설명을 만들지 말 것
- 하루를 평가하거나 교훈을 내리지 말 것
- 사용자가 쓰지 않은 감정 해석을 추가하지 말 것

[결제 데이터 해석 규칙]
- 결제 정보는 하루 행동을 추론하는 참고 데이터로 사용
- 시간 흐름(아침/점심/저녁)과 함께 자연스럽게 연결
- 카테고리를 행동으로 해석 가능
  · 카페 → 쉬거나 잠시 머문 시간
  · 편의점 → 간단한 구매나 잠깐의 활동
  · 교통 → 이동이 있었음
  · 식사 → 식사 시간이나 휴식
- 단, 결제 사실 이상을 단정하지 말 것
- 소비 자체보다 그 순간의 분위기와 흐름을 중심으로 작성
- 결제 금액을 반복 나열하지 말 것
- 선택되지 않은 결제 데이터는 무시할 것
- 같은 장소/카테고리의 반복 결제는 하나의 행동으로 묶어 해석할 수 있음
- 결제만으로 감정이나 목적을 단정하지 말 것
- 결제 데이터는 하루의 보조 정보이며 사진·날씨·일정보다 우선하지 말 것
- 이동, 식사, 구매는 필요한 경우에만 언급
- 반복된 이동이나 소비를 그대로 나열하지 말 것

${if (recentDiarySamples.isNotBlank()) """
        [사용자 기존 일기 문체 참고]

        아래는 사용자가 최근 작성한 일기입니다.내용이나 사건을 복사하지 말고
                다음 요소만 우선 모방하세요 .

        -문장 길이
                -문단 수
                -감정 표현 강도
        -종결 어미
                -생각 전개 방식

        사용자 평균 문장 길이의
                1.5 배 이상 길어지지 마세요.새로운 비유, 철학적 해석,
        과도한 감정 묘사를 추가하지 마세요.사용자 문체가 짧으면 짧게,
        담백하면 담백하게 유지하세요.최근 일기를 가장 우선 참고하세요.

        $recentDiarySamples
        """ else ""}

[오늘의 데이터]
$blocksText
""".trimIndent()
    }
}
}

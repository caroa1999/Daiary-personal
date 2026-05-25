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
        photoSummary: String? = null
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
                blocksText + photoText,
                locale,
                mbti
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
        mbti: String
    ): String =if (locale == "en") {
        """
You are an AI that writes a warm, personal diary entry based on the user's daily data.

Write a 3–5 paragraph diary in first person based on the data below.
- Reflect MBTI personality: $mbti
- Connect the data into a natural narrative, not a bullet list
- Use plain text only, no markdown

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
- 분량: 3~5 문단
- 첫 문장: 오늘의 날씨나 기분으로 하루를 여는 문장으로 시작
- 중간 문단: 하루의 흐름(아침→낮→저녁) 순서로 사건과 그때의 감정, 생각을 연결
- 마지막 문단: 오늘 하루를 돌아보며 느낀 점이나 내일에 대한 짧은 생각으로 마무리
- 단순한 사실 나열 금지 — 그 순간 어떤 감정이었는지 내면을 담을 것
- 마크다운 없이 순수 텍스트로만 작성

[오늘의 데이터]
$blocksText
""".trimIndent()
    }
}

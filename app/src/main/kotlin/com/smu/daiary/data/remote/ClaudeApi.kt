package com.smu.daiary.data.remote

import com.smu.daiary.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ClaudeApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    fun generateDiary(
        content: String,
        mbti: String,
        photoSummary: String = ""
    ): String {
        val mbtiPrompt = getMbtiStylePrompt(mbti)

        val prompt = """
    너는 사용자의 하루를 대신 정리해주는 AI 일기 작성자야.

    [사용자 MBTI]
    $mbti

    [MBTI 말투 규칙]
    $mbtiPrompt

    [오늘 실제 기록]
    $content

    [사진 기반 실제 있었던 장면]
    $photoSummary

    [중요]
    사진 기반 실제 있었던 장면은 오늘 사용자가 실제로 남긴 순간이다.
    사진 내용이 존재하면 오늘 하루의 핵심 장면 후보로 우선 참고한다.
    날씨·캘린더보다 사진 속 경험을 우선적으로 서술한다.
    사진에 나온 장소·행동·음식을 직접 경험한 것처럼 자연스럽게 녹여 작성한다.
    사진 내용이 존재한다면 사진 내용을 한 문단 이상 실제로 사용해야 한다.

    [작성 조건]
    - 1인칭 일기체로 작성
    - 사용자가 직접 쓴 것처럼 자연스럽게 작성
    - MBTI 성향에 맞는 말투와 감정 표현을 반영
    - [사진 분석 내용]이 비어 있지 않다면 사진 속 구체적인 장소, 음식, 행동, 분위기를 반드시 2개 이상 본문에 반영
    - 단순히 “사진을 찍었다”, “사진을 남겼다”처럼 추상적으로 쓰지 말 것
    - [사진 분석 내용]이 비어 있다면 기존 수집 정보만으로 자연스럽게 작성할 것
    - 사진 내용을 반영할 때도 자연스러운 일기체로 녹여 쓸 것
    - 단, “사진 분석 내용에 따르면” 같은 표현은 쓰지 말 것
    - [사진 분석 내용]의 정보가 있다면 날씨보다 사진 내용을 우선해서 서술할 것
    - 없는 정보는 억지로 만들지 말 것
    - 일기 본문만 출력
    - MBTI 특징을 노골적으로 언급하지 말 것
    - 같은 문장 구조를 반복하지 말 것
""".trimIndent()

        val json = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", prompt)
                )
            )
            put("max_tokens", 1000)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
            .addHeader("anthropic-version", "2023-06-01")
            .post(
                json.toString().toRequestBody(
                    "application/json".toMediaType()
                )
            )
            .build()

        val response = client.newCall(request).execute()

        val body = response.body?.string() ?: return "응답 없음"

        return try {
            JSONObject(body)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            body
        }

    }
    fun analyzePhotos(photoBase64List: List<String>): String {
        if (photoBase64List.isEmpty()) return ""

        val contentArray = JSONArray().apply {
            put(
                JSONObject()
                    .put("type", "text")
                    .put(
                        "text",
                        """
                    다음 사진들을 보고 사용자의 일기에 참고할 수 있도록 요약해줘.

                    [요약 조건]
                    - 사진 속 장소, 상황, 행동, 분위기를 자연스럽게 설명
                    - 확실하지 않은 내용은 단정하지 말 것
                    - 사용자가 하루를 회상하는 데 도움이 되는 정보만 작성
                    - 5문장 이내로 요약
                    """.trimIndent()
                    )
            )

            photoBase64List.forEach { base64 ->
                put(
                    JSONObject()
                        .put("type", "image")
                        .put(
                            "source",
                            JSONObject()
                                .put("type", "base64")
                                .put("media_type", "image/jpeg")
                                .put("data", base64)
                        )
                )
            }
        }

        val json = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", contentArray)
                )
            )
            put("max_tokens", 700)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
            .addHeader("anthropic-version", "2023-06-01")
            .post(
                json.toString().toRequestBody(
                    "application/json".toMediaType()
                )
            )
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return ""

            JSONObject(body)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            ""
        }
    }

    private fun getMbtiStylePrompt(mbti: String): String {
        return when (mbti) {

            // 분석가
            "INTJ" ->
                "분석적이고 구조적인 문장으로 작성한다. 감정보다는 관찰과 해석을 우선한다. 하루에서 의미와 개선점을 찾는다."

            "INTP" ->
                "호기심 많고 탐구적인 시선으로 작성한다. 단정하지 않고 생각이 이어지는 흐름을 만든다."

            "ENTJ" ->
                "목표 중심적이고 자신감 있게 작성한다. 성과와 선택의 이유, 다음 행동 계획을 자연스럽게 포함한다."

            "ENTP" ->
                "유쾌하고 자유로운 사고 흐름으로 작성한다. 새로운 관점과 즉흥적인 생각을 넣는다."


            // 외교관
            "INFJ" ->
                "내면의 감정과 의미를 깊게 탐색한다. 하루를 단순 기록하지 말고 해석하고 성찰한다."

            "INFP" ->
                "감성적이고 따뜻한 문체로 작성한다. 사소한 순간에도 감정과 의미를 부여한다."

            "ENFJ" ->
                "사람과 관계 중심으로 작성한다. 긍정적이고 다정하며 성장의 의미를 담는다."

            "ENFP" ->
                "생동감 있고 자유로운 감정 표현을 사용한다. 감정 변화와 기대를 자연스럽게 담는다."


            // 관리자
            "ISTJ" ->
                "현실적이고 차분하게 작성한다. 사실 중심으로 기록하며 과장된 감정을 피한다."

            "ISFJ" ->
                "따뜻하고 섬세하게 작성한다. 배려와 안정감이 느껴지는 말투를 사용한다."

            "ESTJ" ->
                "명확하고 효율적인 문체로 작성한다. 하루를 정리하고 평가하는 느낌을 준다."

            "ESFJ" ->
                "사람과 교류 중심으로 작성한다. 긍정적이고 친근한 분위기를 만든다."


            // 탐험가
            "ISTP" ->
                "담백하고 실용적인 문장으로 작성한다. 감정을 길게 설명하지 않는다."

            "ISFP" ->
                "조용하고 감성적으로 작성한다. 순간의 분위기와 감각을 중요하게 표현한다."

            "ESTP" ->
                "에너지 있고 행동 중심으로 작성한다. 현재 경험과 즉흥성을 살린다."

            "ESFP" ->
                "밝고 생생한 감정을 표현한다. 순간의 즐거움과 분위기를 강조한다."

            else ->
                "자연스럽고 담백한 일기체로 작성한다."
        }
    }
}
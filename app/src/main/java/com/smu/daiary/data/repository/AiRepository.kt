package com.smu.daiary.data.repository

import com.smu.daiary.data.source.AnthropicDataSource
import com.smu.daiary.feature.write.ContentBlock

class AiRepository(
    private val dataSource: AnthropicDataSource = AnthropicDataSource()
) {
    suspend fun analyzePhotos(photoBase64List: List<String>): String =
        dataSource.analyzePhotos(photoBase64List)

    suspend fun generateFollowUpQuestions(
        blocks: List<ContentBlock>,
        locale: String,
        photoSummary: String? = null
    ): List<String> =
        dataSource.generateFollowUpQuestions(
            blocks = blocks,
            locale = locale,
            photoSummary = photoSummary
        )

    suspend fun generateDraft(
        blocks: List<ContentBlock>,
        locale: String,
        mbti: String,
        photoSummary: String? = null,
        recentDiarySamples: String = "",
        followUpAnswers: Map<Int, String> = emptyMap()
    ): Result<String> =
        runCatching {
            dataSource.generateDiary(
                blocks = blocks,
                locale = locale,
                mbti = mbti,
                photoSummary = photoSummary,
                recentDiarySamples = recentDiarySamples,
                followUpAnswers = followUpAnswers
            )
        }
}

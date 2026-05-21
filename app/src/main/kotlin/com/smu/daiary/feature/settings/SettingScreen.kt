package com.smu.daiary.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext


data class MbtiOption(
    val type: String,
    val keywords: String
)

@Composable
fun SettingsScreen() {

    val context = LocalContext.current

    val prefs =
        context.getSharedPreferences(
            "user_settings",
            Context.MODE_PRIVATE
        )

    val mbtiList = listOf(
        MbtiOption("ISTJ", "현실적 · 책임감 · 계획형"),
        MbtiOption("ISFJ", "섬세함 · 배려형 · 안정감"),
        MbtiOption("INFJ", "통찰력 · 공감형 · 깊은 생각"),
        MbtiOption("INTJ", "독립적 · 분석적 · 전략형"),

        MbtiOption("ISTP", "논리적 · 관찰형 · 실용적"),
        MbtiOption("ISFP", "감성적 · 자유형 · 섬세함"),
        MbtiOption("INFP", "감성적 · 공감형 · 상상력"),
        MbtiOption("INTP", "분석적 · 탐구형 · 아이디어"),

        MbtiOption("ESTP", "활동적 · 현실형 · 즉흥적"),
        MbtiOption("ESFP", "밝음 · 표현형 · 즐거움"),
        MbtiOption("ENFP", "열정적 · 상상력 · 자유형"),
        MbtiOption("ENTP", "창의적 · 토론형 · 도전적"),

        MbtiOption("ESTJ", "체계적 · 실행형 · 책임감"),
        MbtiOption("ESFJ", "사교적 · 배려형 · 조화"),
        MbtiOption("ENFJ", "공감형 · 리더십 · 따뜻함"),
        MbtiOption("ENTJ", "목표지향 · 결단력 · 전략형")
    )

    var selected by remember {
        mutableStateOf(
            prefs.getString(
                "mbti",
                "INFP"
            ) ?: "INFP"
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp,
                    top = 48.dp,
                    end = 24.dp,
                    bottom = 24.dp)
    ) {

        Text(
            text = "MBTI 설정",
            style =
                MaterialTheme.typography.headlineMedium
        )

        Spacer(
            Modifier.height(32.dp)
        )

        LazyVerticalGrid(
            columns =
                GridCells.Fixed(4)
        ) {

            items(mbtiList) { mbti ->

                ElevatedCard(
                    onClick = {
                        selected = mbti.type
                    },
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor =
                                if (selected == mbti.type)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                        )
                ) {
                    Column(
                        modifier =
                            Modifier.padding(12.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = mbti.type,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(
                            Modifier.height(4.dp)
                        )

                        Text(
                            text = mbti.keywords,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(
            Modifier.height(36.dp)
        )

        Button(
            onClick = {

                prefs.edit()
                    .putString(
                        "mbti",
                        selected
                    )
                    .apply()

            },

            modifier =
                Modifier.align(
                    Alignment.CenterHorizontally
                )
        ) {

            Text("저장")
        }
    }
}
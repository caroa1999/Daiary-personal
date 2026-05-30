package com.smu.daiary.feature.settings

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext


data class MbtiOption(
    val type: String,
    val keywords: String
)

@Composable
fun SettingsScreen(onConfirm: () -> Unit = {}) {

    val context = LocalContext.current

    val prefs =
        context.getSharedPreferences(
            "user_settings",
            Context.MODE_PRIVATE
        )

    val mbtiList = listOf(
        MbtiOption("ISTJ", "계획형"),
        MbtiOption("ISFJ", "배려형"),
        MbtiOption("INFJ", "통찰형"),
        MbtiOption("INTJ", "전략형"),

        MbtiOption("ISTP", "실용형"),
        MbtiOption("ISFP", "자유형"),
        MbtiOption("INFP", "공감형"),
        MbtiOption("INTP", "탐구형"),

        MbtiOption("ESTP", "즉흥형"),
        MbtiOption("ESFP", "표현형"),
        MbtiOption("ENFP", "열정형"),
        MbtiOption("ENTP", "토론형"),

        MbtiOption("ESTJ", "실행형"),
        MbtiOption("ESFJ", "조화형"),
        MbtiOption("ENFJ", "리더형"),
        MbtiOption("ENTJ", "결단형")
    )

    var selected by remember {
        mutableStateOf<String?>(
            prefs.getString("mbti", "INFP")
        )
    }
    var showSavedMessage by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, top = 48.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(
                text = "MBTI 설정",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(32.dp))

            LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                items(mbtiList) { mbti ->
                    Card(
                        onClick = {
                            selected = if (selected == mbti.type) null else mbti.type
                        },
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth()
                            .border(
                                width = if (selected == mbti.type) 2.dp else 1.dp,
                                color = if (selected == mbti.type)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFDFAF5)
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        )
                    ){
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = mbti.type,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    color = if (selected == mbti.type)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                if (selected == mbti.type) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.TopStart)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = mbti.keywords,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            if (selected != null) {
                Text(
                    text = "나의 MBTI: $selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    prefs.edit().apply {
                        if (selected == null) remove("mbti") else putString("mbti", selected)
                        apply()
                    }
                    showSavedMessage = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("저장")
            }
        }

        if (showSavedMessage) {
            AlertDialog(
                onDismissRequest = { showSavedMessage = false },
                title = { Text("저장 완료") },
                text = { Text("MBTI가 저장되었습니다 ✓") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSavedMessage = false
                            onConfirm()
                        }
                    ) {
                        Text("확인")
                    }
                }
            )
        }
    }
}

package com.smu.daiary.feature.write

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSelectionScreen(
    viewModel: WriteViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val selectedCount = photos.count { it.isSelected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사진 선택") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    viewModel.syncPhotoBlockSelection()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp)
            ) {
                Text("선택 완료")
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("사진 선택 $selectedCount/${photos.size}")

                    Row {
                        TextButton(
                            onClick = { viewModel.setAllPhotosSelected(true) }
                        ) {
                            Text("전체 선택")
                        }

                        TextButton(
                            onClick = { viewModel.setAllPhotosSelected(false) }
                        ) {
                            Text("전체 해제")
                        }
                    }
                }
            }

            items(photos) { photo ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.togglePhoto(photo.uri)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = Uri.parse(photo.uri),
                            contentDescription = "사진",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = if (photo.isSelected) "선택됨" else "선택 안 됨",
                            modifier = Modifier.weight(1f)
                        )

                        Checkbox(
                            checked = photo.isSelected,
                            onCheckedChange = {
                                viewModel.togglePhoto(photo.uri)
                            }
                        )
                    }
                }
            }
        }
    }
}
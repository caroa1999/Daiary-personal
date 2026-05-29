package com.smu.daiary.data.source

import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.smu.daiary.data.model.PhotoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import android.content.ContentUris
import androidx.core.net.toUri
import com.smu.daiary.util.DiaryDateUtil

class PhotoDataSource(private val context: Context) {

    // 일기 기준 날짜의 사진만 조회. 오전 4시 이전이면 전날 기준으로 조회한다.
    private fun todayRange(): Pair<Long, Long> {
        val today = DiaryDateUtil.diaryDate()
        val zone = ZoneId.systemDefault()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }

    suspend fun fetchTodayPhotos(): List<PhotoMeta> = withContext(Dispatchers.IO) {
        val (start, end) = todayRange()
        val photos = mutableListOf<PhotoMeta>()

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN  // 촬영 시각 (epoch millis)
        )
        val selection = "${MediaStore.Images.Media.DATE_TAKEN} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(start.toString(), end.toString())

        val cursor = context.contentResolver.query(
            uri, projection, selection, selectionArgs,
            "${MediaStore.Images.Media.DATE_TAKEN} ASC"
        )

        cursor?.use {
            val idIdx = it.getColumnIndex(MediaStore.Images.Media._ID)
            val takenIdx = it.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)

            while (it.moveToNext()) {
                val imageId = it.getLong(idIdx)
                val takenAt = it.getLong(takenIdx)

                val contentUri =
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        imageId
                    )

                val (lat, lon) =
                    extractGps(contentUri.toString())

                photos.add(
                    PhotoMeta(
                        uri = contentUri.toString(),
                        takenAt = takenAt,
                        latitude = lat,
                        longitude = lon
                    )
                )
            }
        }

        photos
    }

    // EXIF에서 위도/경도 파싱. GPS 정보 없는 사진은 0.0으로 반환
    private fun extractGps(uri: String): Pair<Double, Double> {
        return try {
            val input =
                context.contentResolver.openInputStream(
                    uri.toUri()
                ) ?: return 0.0 to 0.0

            val exif = ExifInterface(input)

            val latLon = FloatArray(2)

            if (exif.getLatLong(latLon)) {
                latLon[0].toDouble() to latLon[1].toDouble()
            } else {
                0.0 to 0.0
            }

        } catch (e: Exception) {
            0.0 to 0.0
        }
    }
}

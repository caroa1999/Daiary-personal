package com.smu.daiary.data.source

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.smu.daiary.data.model.HealthData
import com.smu.daiary.util.DiaryDateUtil
import java.time.ZoneId

private const val TAG = "HealthDataSource"

/**
 * Health Connect API로 사용자의 건강 데이터를 수집합니다.
 *
 * 수집 항목:
 * - 걸음 수 (StepsRecord)
 * - 수면 시간 (SleepSessionRecord)
 *
 * 전제 조건:
 * - Android 14 이상 권장 (Health Connect 기본 탑재)
 * - 사용자가 Health Connect에 데이터 제공 앱(삼성 헬스, 갤럭시 워치 등) 동기화 설정
 * - 우리 앱에 READ_STEPS, READ_SLEEP 권한 허용
 *
 * 권한 미허용 / Health Connect 미설치 시 빈 HealthData 반환합니다.
 */
class HealthDataSource(private val context: Context) {

    companion object {
        /** 우리 앱이 요청하는 Health Connect 권한 목록 */
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }

    /** Health Connect 클라이언트. SDK 사용 불가 시 null 반환 */
    private fun client(): HealthConnectClient? {
        return if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            Log.w(TAG, "⚠️ Health Connect SDK 사용 불가 (미설치 또는 미지원 기기)")
            null
        }
    }

    /** 우리 앱이 필요한 모든 권한을 갖고 있는지 확인 */
    suspend fun hasAllPermissions(): Boolean {
        val client = client() ?: return false
        return client.permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)
    }

    /**
     * 일기 기준 날짜(오전 4시 이전이면 전날)의 걸음 수 + 수면 시간을 조회합니다.
     * 권한 없거나 데이터 없을 시 0으로 채운 HealthData 반환.
     */
    suspend fun fetchTodayHealth(): HealthData {
        val client = client() ?: return HealthData()
        if (!hasAllPermissions()) {
            Log.w(TAG, "⚠️ Health Connect 권한 미허용")
            return HealthData()
        }

        val date = DiaryDateUtil.diaryDate()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val filter = TimeRangeFilter.between(start, end)

        val steps = readSteps(client, filter)
        val sleepMinutes = readSleep(client, filter)

        Log.d(TAG, "🏃 건강 수집 완료 | steps=$steps | sleep=${sleepMinutes}분")
        return HealthData(steps = steps, sleepDurationMinutes = sleepMinutes)
    }

    /** 시간 범위 내 StepsRecord 모두 더해 총 걸음 수 반환 */
    private suspend fun readSteps(client: HealthConnectClient, filter: TimeRangeFilter): Int {
        val response = client.readRecords(
            ReadRecordsRequest(StepsRecord::class, filter)
        )
        return response.records.sumOf { it.count }.toInt()
    }

    /** 시간 범위 내 SleepSessionRecord 길이를 모두 더해 총 수면 분 반환 */
    private suspend fun readSleep(client: HealthConnectClient, filter: TimeRangeFilter): Int {
        val response = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, filter)
        )
        val totalMinutes = response.records.sumOf {
            (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60_000L
        }
        return totalMinutes.toInt()
    }
}

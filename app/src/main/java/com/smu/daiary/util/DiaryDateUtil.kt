package com.smu.daiary.util

import java.time.LocalDate
import java.time.LocalTime

/**
 * 일기 작성 기준 날짜 유틸리티.
 *
 * 일반적으로 자정(00:00)이 지나면 다음 날로 전환되지만,
 * 사용자는 주로 밤 늦게 일기를 작성하므로 오전 4시 전까지는
 * 전날 일기를 쓰는 것으로 간주한다.
 *
 *  00:00 ~ 03:59  →  어제 날짜 (전날 일기)
 *  04:00 ~ 23:59  →  오늘 날짜
 */
object DiaryDateUtil {

    /** 일기 작성 기준이 되는 날짜의 경계 시각 (오전 4시) */
    private val DAY_BOUNDARY = LocalTime.of(4, 0)

    /**
     * 일기 기준 날짜를 반환.
     * 오전 4시 이전이면 어제, 이후면 오늘.
     */
    fun diaryDate(): LocalDate {
        return if (isLateNight()) {
            LocalDate.now().minusDays(1)
        } else {
            LocalDate.now()
        }
    }

    /**
     * 현재 시각이 자정~오전 4시 사이인지 여부.
     * true이면 UI에서 "어제 일기 작성 중" 배너를 표시해야 한다.
     */
    fun isLateNight(): Boolean {
        val now = LocalTime.now()
        return now.isBefore(DAY_BOUNDARY)
    }
}

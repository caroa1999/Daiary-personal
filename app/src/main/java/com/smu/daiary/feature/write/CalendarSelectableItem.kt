package com.smu.daiary.feature.write

data class CalendarSelectableItem(
    val id: Int,
    val displayText: String,
    val startTime: Long,
    val isSelected: Boolean = true
)

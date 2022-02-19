package io.github.wulkanowy.ui.modules.timetable

import io.github.wulkanowy.data.db.entities.Timetable
import java.time.Duration

sealed class TimetableItem(val type: TimetableItemType) {

    data class Small(
        val lesson: Timetable,
        val onClick: (Timetable) -> Unit,
    ) : TimetableItem(TimetableItemType.SMALL)

    data class Normal(
        val lesson: Timetable,
        val showGroupsInPlan: Boolean,
        val timeLeft: TimeLeft?,
        val onClick: (Timetable) -> Unit,
    ) : TimetableItem(TimetableItemType.NORMAL)
}

data class TimeLeft(
    val isShowTimeUntil: Boolean,
    val until: Duration,
    val left: Duration?,
    val isJustFinished: Boolean,
)

enum class TimetableItemType {
    SMALL,
    NORMAL,
}

package io.github.wulkanowy.ui.modules.timetable

import io.github.wulkanowy.data.db.entities.Timetable

sealed class TimetableItem(val type: TimetableItemType) {

    data class Small(
        val lesson: Timetable,
        val onClick: (Timetable) -> Unit,
    ) : TimetableItem(TimetableItemType.SMALL)

    data class Normal(
        val lesson: Timetable,
        val onClick: (Timetable) -> Unit,
    ) : TimetableItem(TimetableItemType.NORMAL)
}

enum class TimetableItemType {
    SMALL,
    NORMAL,
}

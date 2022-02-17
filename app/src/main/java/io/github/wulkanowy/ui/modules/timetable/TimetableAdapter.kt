package io.github.wulkanowy.ui.modules.timetable

import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.wulkanowy.R
import io.github.wulkanowy.data.db.entities.Timetable
import io.github.wulkanowy.databinding.ItemTimetableBinding
import io.github.wulkanowy.databinding.ItemTimetableSmallBinding
import io.github.wulkanowy.utils.*
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

class TimetableAdapter @Inject constructor() :
    ListAdapter<TimetableItem, RecyclerView.ViewHolder>(differ) {

    override fun getItemViewType(position: Int): Int = getItem(position).type.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (TimetableItemType.values()[viewType]) {
            TimetableItemType.SMALL -> SmallViewHolder(
                ItemTimetableSmallBinding.inflate(inflater, parent, false)
            )
            TimetableItemType.NORMAL -> NormalViewHolder(
                ItemTimetableBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SmallViewHolder -> holder.bind(getItem(position) as TimetableItem.Small)
            is NormalViewHolder -> holder.bind(getItem(position) as TimetableItem.Normal)
        }
    }

    private inner class SmallViewHolder(
        private val binding: ItemTimetableSmallBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TimetableItem.Small) {
            val lesson = item.lesson
            with(binding) {
                timetableSmallItemNumber.text = lesson.number.toString()
                timetableSmallItemSubject.text = lesson.subject
                timetableSmallItemTimeStart.text = lesson.start.toFormattedString("HH:mm")
                timetableSmallItemRoom.text = lesson.room
                timetableSmallItemTeacher.text = lesson.teacher

                bindSubjectStyle(timetableSmallItemSubject, lesson)
                bindSmallDescription(lesson)
                bindSmallColors(lesson)

                root.setOnClickListener { item.onClick(lesson) }
            }
        }

        private fun bindSmallDescription(lesson: Timetable) {
            with(binding) {
                if (lesson.info.isNotBlank() && !lesson.changes) {
                    timetableSmallItemDescription.visibility = VISIBLE
                    timetableSmallItemDescription.text = lesson.info

                    timetableSmallItemRoom.visibility = GONE
                    timetableSmallItemTeacher.visibility = GONE

                    timetableSmallItemDescription.setTextColor(
                        root.context.getThemeAttrColor(
                            if (lesson.canceled) R.attr.colorPrimary
                            else R.attr.colorTimetableChange
                        )
                    )
                } else {
                    timetableSmallItemDescription.visibility = GONE
                    timetableSmallItemRoom.visibility = VISIBLE
                    timetableSmallItemTeacher.visibility = VISIBLE
                }
            }
        }

        private fun bindSmallColors(lesson: Timetable) {
            with(binding) {
                if (lesson.canceled) {
                    updateNumberAndSubjectCanceledColor(
                        timetableSmallItemNumber,
                        timetableSmallItemSubject
                    )
                } else {
                    updateNumberColor(timetableSmallItemNumber, lesson)
                    updateSubjectColor(timetableSmallItemSubject, lesson)
                    updateRoomColor(timetableSmallItemRoom, lesson)
                    updateTeacherColor(timetableSmallItemTeacher, lesson)
                }
            }
        }
    }

    private inner class NormalViewHolder(
        private val binding: ItemTimetableBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TimetableItem.Normal) {
            val lesson = item.lesson
            with(binding) {
                timetableItemNumber.text = lesson.number.toString()
                timetableItemSubject.text = lesson.subject
                timetableItemGroup.text = lesson.group
                timetableItemRoom.text = lesson.room
                timetableItemTeacher.text = lesson.teacher
                timetableItemTimeStart.text = lesson.start.toFormattedString("HH:mm")
                timetableItemTimeFinish.text = lesson.end.toFormattedString("HH:mm")

                bindSubjectStyle(timetableItemSubject, lesson)
                bindNormalDescription(lesson)
                bindNormalColors(lesson)

                root.setOnClickListener { item.onClick(lesson) }
            }
        }

        private fun bindNormalDescription(lesson: Timetable) {
            with(binding) {
                if (lesson.info.isNotBlank() && !lesson.changes) {
                    timetableItemDescription.visibility = VISIBLE
                    timetableItemDescription.text = lesson.info

                    timetableItemRoom.visibility = GONE
                    timetableItemGroup.visibility = GONE
                    timetableItemTeacher.visibility = GONE

                    timetableItemDescription.setTextColor(
                        root.context.getThemeAttrColor(
                            if (lesson.canceled) R.attr.colorPrimary
                            else R.attr.colorTimetableChange
                        )
                    )
                } else {
                    timetableItemDescription.visibility = GONE
                    timetableItemRoom.visibility = VISIBLE
                    timetableItemGroup.visibility = if (
//                        showGroupsInPlan && // todo
                        lesson.group.isNotBlank()
                    ) VISIBLE else GONE
                    timetableItemTeacher.visibility = VISIBLE
                }
            }
        }

        private fun bindNormalColors(lesson: Timetable) {
            with(binding) {
                if (lesson.canceled) {
                    updateNumberAndSubjectCanceledColor(timetableItemNumber, timetableItemSubject)
                } else {
                    updateNumberColor(timetableItemNumber, lesson)
                    updateSubjectColor(timetableItemSubject, lesson)
                    updateRoomColor(timetableItemRoom, lesson)
                    updateTeacherColor(timetableItemTeacher, lesson)
                }
            }
        }
    }

    private fun getPreviousLesson(position: Int): Instant? {
        val items = currentList.filterIsInstance<TimetableItem.Normal>().map { it.lesson }
        return items
            .getOrNull(position - 1 - items.filterIndexed { i, item -> i < position && !item.isStudentPlan }.size)
            ?.let {
                if (!it.canceled && it.isStudentPlan) it.end
                else null
            }
    }

    // todo: move this logic to presenter
    private fun updateTimeLeft(binding: ItemTimetableBinding, lesson: Timetable, position: Int) {
        val isShowTimeUntil = lesson.isShowTimeUntil(getPreviousLesson(position))
        val until = lesson.until.plusMinutes(1)
        val left = lesson.left?.plusMinutes(1)
        val isJustFinished = lesson.isJustFinished

        with(binding) {
            when {
                // before lesson
                isShowTimeUntil -> {
                    Timber.d("Show time until lesson: $position")
                    timetableItemTimeLeft.visibility = GONE
                    with(timetableItemTimeUntil) {
                        visibility = VISIBLE
                        text = context.getString(
                            R.string.timetable_time_until,
                            context.getString(
                                R.string.timetable_minutes,
                                until.toMinutes().toString(10)
                            )
                        )
                    }
                }
                // after lesson start
                left != null -> {
                    Timber.d("Show time left lesson: $position")
                    timetableItemTimeUntil.visibility = GONE
                    with(timetableItemTimeLeft) {
                        visibility = VISIBLE
                        text = context.getString(
                            R.string.timetable_time_left,
                            context.getString(
                                R.string.timetable_minutes,
                                left.toMinutes().toString()
                            )
                        )
                    }
                }
                // right after lesson finish
                isJustFinished -> {
                    Timber.d("Show just finished lesson: $position")
                    timetableItemTimeUntil.visibility = GONE
                    timetableItemTimeLeft.visibility = VISIBLE
                    timetableItemTimeLeft.text = root.context.getString(R.string.timetable_finished)
                }
                else -> {
                    timetableItemTimeUntil.visibility = GONE
                    timetableItemTimeLeft.visibility = GONE
                }
            }
        }
    }

    private fun bindSubjectStyle(subjectView: TextView, lesson: Timetable) {
        subjectView.paint.isStrikeThruText = lesson.canceled
    }

    private fun updateNumberAndSubjectCanceledColor(numberView: TextView, subjectView: TextView) {
        numberView.setTextColor(numberView.context.getThemeAttrColor(R.attr.colorPrimary))
        subjectView.setTextColor(subjectView.context.getThemeAttrColor(R.attr.colorPrimary))
    }

    private fun updateNumberColor(numberView: TextView, lesson: Timetable) {
        numberView.setTextColor(
            numberView.context.getThemeAttrColor(
                if (lesson.changes || lesson.info.isNotBlank()) R.attr.colorTimetableChange
                else android.R.attr.textColorPrimary
            )
        )
    }

    private fun updateSubjectColor(subjectView: TextView, lesson: Timetable) {
        subjectView.setTextColor(
            subjectView.context.getThemeAttrColor(
                if (lesson.subjectOld.isNotBlank() && lesson.subjectOld != lesson.subject) R.attr.colorTimetableChange
                else android.R.attr.textColorPrimary
            )
        )
    }

    private fun updateRoomColor(roomView: TextView, lesson: Timetable) {
        roomView.setTextColor(
            roomView.context.getThemeAttrColor(
                if (lesson.roomOld.isNotBlank() && lesson.roomOld != lesson.room) R.attr.colorTimetableChange
                else android.R.attr.textColorSecondary
            )
        )
    }

    private fun updateTeacherColor(teacherTextView: TextView, lesson: Timetable) {
        teacherTextView.setTextColor(
            teacherTextView.context.getThemeAttrColor(
                if (lesson.teacherOld.isNotBlank()) R.attr.colorTimetableChange
                else android.R.attr.textColorSecondary
            )
        )
    }

    companion object {
        private val differ = object : DiffUtil.ItemCallback<TimetableItem>() {
            override fun areItemsTheSame(oldItem: TimetableItem, newItem: TimetableItem): Boolean =
                when {
                    oldItem is TimetableItem.Small && newItem is TimetableItem.Small -> {
                        oldItem.lesson.id == newItem.lesson.id
                    }
                    oldItem is TimetableItem.Normal && newItem is TimetableItem.Normal -> {
                        oldItem.lesson.id == newItem.lesson.id
                    }
                    else -> oldItem == newItem
                }

            override fun areContentsTheSame(oldItem: TimetableItem, newItem: TimetableItem) =
                oldItem == newItem
        }
    }
}

package io.github.wulkanowy.ui.modules

import androidx.fragment.app.Fragment
import io.github.wulkanowy.data.serializers.LocalDateSerializer
import io.github.wulkanowy.ui.modules.attendance.AttendanceFragment
import io.github.wulkanowy.ui.modules.conference.ConferenceFragment
import io.github.wulkanowy.ui.modules.dashboard.DashboardFragment
import io.github.wulkanowy.ui.modules.exam.ExamFragment
import io.github.wulkanowy.ui.modules.grade.GradeFragment
import io.github.wulkanowy.ui.modules.homework.HomeworkFragment
import io.github.wulkanowy.ui.modules.luckynumber.LuckyNumberFragment
import io.github.wulkanowy.ui.modules.message.MessageFragment
import io.github.wulkanowy.ui.modules.more.MoreFragment
import io.github.wulkanowy.ui.modules.note.NoteFragment
import io.github.wulkanowy.ui.modules.schoolandteachers.school.SchoolFragment
import io.github.wulkanowy.ui.modules.schoolannouncement.SchoolAnnouncementFragment
import io.github.wulkanowy.ui.modules.timetable.TimetableFragment
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
sealed class Destination private constructor() : java.io.Serializable {

    /*
    Type in children classes have to be as getter to avoid null in enums
    https://stackoverflow.com/questions/68866453/kotlin-enum-val-is-returning-null-despite-being-set-at-compile-time
    */
    abstract val type: Type

    abstract val fragment: Fragment

    enum class Type(val defaultDestination: Destination) {
        DASHBOARD(Dashboard),
        GRADE(Grade),
        ATTENDANCE(Attendance),
        EXAM(Exam),
        TIMETABLE(Timetable()),
        HOMEWORK(Homework),
        NOTE(Note),
        CONFERENCE(Conference),
        SCHOOL_ANNOUNCEMENT(SchoolAnnouncement),
        SCHOOL(School),
        LUCKY_NUMBER(More),
        MORE(More),
        MESSAGE(Message);
    }

    @Serializable
    object Dashboard : Destination() {
        override val type get() = Type.DASHBOARD
        override val fragment get() = DashboardFragment.newInstance()
    }

    @Serializable
    object Grade : Destination() {
        override val type get() = Type.GRADE
        override val fragment get() = GradeFragment.newInstance()
    }

    @Serializable
    object Attendance : Destination() {
        override val type get() = Type.ATTENDANCE
        override val fragment get() = AttendanceFragment.newInstance()
    }

    @Serializable
    object Exam : Destination() {
        override val type get() = Type.EXAM
        override val fragment get() = ExamFragment.newInstance()
    }

    @Serializable
    data class Timetable(
        @Serializable(with = LocalDateSerializer::class)
        private val date: LocalDate? = null
    ) : Destination() {
        override val type get() = Type.TIMETABLE
        override val fragment get() = TimetableFragment.newInstance(date)
    }

    @Serializable
    object Homework : Destination() {
        override val type get() = Type.HOMEWORK
        override val fragment get() = HomeworkFragment.newInstance()
    }

    @Serializable
    object Note : Destination() {
        override val type get() = Type.NOTE
        override val fragment get() = NoteFragment.newInstance()
    }

    @Serializable
    object Conference : Destination() {
        override val type get() = Type.CONFERENCE
        override val fragment get() = ConferenceFragment.newInstance()
    }

    @Serializable
    object SchoolAnnouncement : Destination() {
        override val type get() = Type.SCHOOL_ANNOUNCEMENT
        override val fragment get() = SchoolAnnouncementFragment.newInstance()
    }

    @Serializable
    object School : Destination() {
        override val type get() = Type.SCHOOL
        override val fragment get() = SchoolFragment.newInstance()
    }

    @Serializable
    object LuckyNumber : Destination() {
        override val type get() = Type.LUCKY_NUMBER
        override val fragment get() = LuckyNumberFragment.newInstance()
    }

    @Serializable
    object More : Destination() {
        override val type get() = Type.MORE
        override val fragment get() = MoreFragment.newInstance()
    }

    @Serializable
    object Message : Destination() {
        override val type get() = Type.MESSAGE
        override val fragment get() = MessageFragment.newInstance()
    }
}
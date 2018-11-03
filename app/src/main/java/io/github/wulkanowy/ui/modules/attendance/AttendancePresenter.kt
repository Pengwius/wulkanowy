package io.github.wulkanowy.ui.modules.attendance

import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import io.github.wulkanowy.data.ErrorHandler
import io.github.wulkanowy.data.repositories.AttendanceRepository
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.SessionRepository
import io.github.wulkanowy.ui.base.BasePresenter
import io.github.wulkanowy.utils.SchedulersProvider
import io.github.wulkanowy.utils.isHolidays
import io.github.wulkanowy.utils.logEvent
import io.github.wulkanowy.utils.nextSchoolDay
import io.github.wulkanowy.utils.previousOrSameSchoolDay
import io.github.wulkanowy.utils.previousSchoolDay
import io.github.wulkanowy.utils.toFormattedString
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDate.now
import org.threeten.bp.LocalDate.ofEpochDay
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.inject.Inject

class AttendancePresenter @Inject constructor(
    private val errorHandler: ErrorHandler,
    private val schedulers: SchedulersProvider,
    private val attendanceRepository: AttendanceRepository,
    private val sessionRepository: SessionRepository,
    private val prefRepository: PreferencesRepository
) : BasePresenter<AttendanceView>(errorHandler) {

    lateinit var currentDate: LocalDate
        private set

    fun onAttachView(view: AttendanceView, date: Long?) {
        super.onAttachView(view)
        view.initView()
        loadData(ofEpochDay(date ?: now().previousOrSameSchoolDay.toEpochDay()))
        reloadView()
    }

    fun onPreviousDay() {
        loadData(currentDate.previousSchoolDay)
        reloadView()
        logEvent("Attendance day changed", mapOf("button" to "prev", "date" to currentDate.toFormattedString()))
    }

    fun onNextDay() {
        loadData(currentDate.nextSchoolDay)
        reloadView()
        logEvent("Attendance day changed", mapOf("button" to "next", "date" to currentDate.toFormattedString()))
    }

    fun onSwipeRefresh() {
        loadData(currentDate, true)
    }

    fun onViewReselected() {
        loadData(now().previousOrSameSchoolDay)
        reloadView()
    }

    fun onAttendanceItemSelected(item: AbstractFlexibleItem<*>?) {
        if (item is AttendanceItem) view?.showAttendanceDialog(item.attendance)
    }

    private fun loadData(date: LocalDate, forceRefresh: Boolean = false) {
        currentDate = date
        disposable.apply {
            clear()
            add(sessionRepository.getSemesters()
                .delay(200, MILLISECONDS)
                .map { it.single { semester -> semester.current } }
                .flatMap { attendanceRepository.getAttendance(it, date, date, forceRefresh) }
                .map { list ->
                    if (prefRepository.showPresent) list
                    else list.filter { !it.presence }
                }
                .map { items -> items.map { AttendanceItem(it) } }
                .map { items -> items.sortedBy { it.attendance.number } }
                .subscribeOn(schedulers.backgroundThread)
                .observeOn(schedulers.mainThread)
                .doFinally {
                    view?.run {
                        hideRefresh()
                        showProgress(false)
                    }
                }
                .subscribe({
                    view?.apply {
                        updateData(it)
                        showEmpty(it.isEmpty())
                        showContent(it.isNotEmpty())
                    }
                    logEvent("Attendance load", mapOf("items" to it.size, "forceRefresh" to forceRefresh, "date" to currentDate.toFormattedString()))
                }) {
                    view?.run { showEmpty(isViewEmpty) }
                    errorHandler.proceed(it)
                }
            )
        }
    }

    private fun reloadView() {
        view?.apply {
            showProgress(true)
            showContent(false)
            showEmpty(false)
            clearData()
            showNextButton(!currentDate.plusDays(1).isHolidays)
            showPreButton(!currentDate.minusDays(1).isHolidays)
            updateNavigationDay(currentDate.toFormattedString("EEEE \n dd.MM.YYYY").capitalize())
        }
    }
}

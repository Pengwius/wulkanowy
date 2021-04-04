package io.github.wulkanowy.ui.modules.luckynumberwidget

import io.github.wulkanowy.data.Status
import io.github.wulkanowy.data.db.SharedPrefProvider
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.ui.base.BasePresenter
import io.github.wulkanowy.ui.base.ErrorHandler
import io.github.wulkanowy.ui.modules.luckynumberwidget.LuckyNumberWidgetProvider.Companion.getStudentWidgetKey
import io.github.wulkanowy.ui.modules.luckynumberwidget.LuckyNumberWidgetProvider.Companion.getThemeWidgetKey
import io.github.wulkanowy.utils.flowWithResource
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

class LuckyNumberWidgetConfigurePresenter @Inject constructor(
    errorHandler: ErrorHandler,
    studentRepository: StudentRepository,
    private val sharedPref: SharedPrefProvider
) : BasePresenter<LuckyNumberWidgetConfigureView>(errorHandler, studentRepository) {

    private var appWidgetId: Int? = null

    private var selectedStudent: Student? = null

    fun onAttachView(view: LuckyNumberWidgetConfigureView, appWidgetId: Int?) {
        super.onAttachView(view)
        this.appWidgetId = appWidgetId
        view.initView()
        loadData()
    }

    fun onItemSelect(student: Student) {
        selectedStudent = student
        view?.showThemeDialog()
    }

    fun onThemeSelect(index: Int) {
        appWidgetId?.let {
            sharedPref.putLong(getThemeWidgetKey(it), index.toLong())
        }
        registerStudent(selectedStudent)
    }

    fun onDismissThemeView() {
        view?.finishView()
    }

    private fun loadData() {
        flowWithResource { studentRepository.getSavedStudents(false) }.onEach {
            when (it.status) {
                Status.LOADING -> Timber.d("Lucky number widget configure students data load")
                Status.SUCCESS -> {
                    val selectedStudentId = appWidgetId?.let { id ->
                        sharedPref.getLong(getStudentWidgetKey(id), 0)
                    } ?: -1

                    when {
                        it.data!!.isEmpty() -> view?.openLoginView()
                        it.data.size == 1 -> {
                            selectedStudent = it.data.single().student
                            view?.showThemeDialog()
                        }
                        else -> view?.updateData(it.data, selectedStudentId)
                    }
                }
                Status.ERROR -> errorHandler.dispatch(it.error!!)
            }
        }.launch()
    }

    private fun registerStudent(student: Student?) {
        requireNotNull(student)

        appWidgetId?.let { id ->
            sharedPref.putLong(getStudentWidgetKey(id), student.id)
            view?.run {
                updateLuckyNumberWidget(id)
                setSuccessResult(id)
            }
        }
        view?.finishView()
    }
}

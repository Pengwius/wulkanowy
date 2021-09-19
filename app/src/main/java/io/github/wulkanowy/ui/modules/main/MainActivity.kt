package io.github.wulkanowy.ui.modules.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Build.VERSION_CODES.P
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.elevation.ElevationOverlayProvider
import dagger.hilt.android.AndroidEntryPoint
import io.github.wulkanowy.MainNavGraphDirections
import io.github.wulkanowy.R
import io.github.wulkanowy.data.db.entities.Student
import io.github.wulkanowy.data.db.entities.StudentWithSemesters
import io.github.wulkanowy.data.db.entities.StudentsWrapper
import io.github.wulkanowy.databinding.ActivityMainBinding
import io.github.wulkanowy.ui.base.BaseActivity
import io.github.wulkanowy.ui.modules.conference.ConferenceFragment
import io.github.wulkanowy.ui.modules.exam.ExamFragment
import io.github.wulkanowy.ui.modules.homework.HomeworkFragment
import io.github.wulkanowy.ui.modules.luckynumber.LuckyNumberFragment
import io.github.wulkanowy.ui.modules.message.MessageFragment
import io.github.wulkanowy.ui.modules.note.NoteFragment
import io.github.wulkanowy.ui.modules.schoolannouncement.SchoolAnnouncementFragment
import io.github.wulkanowy.utils.AnalyticsHelper
import io.github.wulkanowy.utils.AppInfo
import io.github.wulkanowy.utils.InAppReviewHelper
import io.github.wulkanowy.utils.UpdateHelper
import io.github.wulkanowy.utils.createNameInitialsDrawable
import io.github.wulkanowy.utils.dpToPx
import io.github.wulkanowy.utils.getThemeAttrColor
import io.github.wulkanowy.utils.navigateToConnectedAction
import io.github.wulkanowy.utils.nickOrName
import timber.log.Timber
import java.io.Serializable
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<MainPresenter, ActivityMainBinding>(), MainView,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    @Inject
    override lateinit var presenter: MainPresenter

    @Inject
    lateinit var analytics: AnalyticsHelper

    @Inject
    lateinit var updateHelper: UpdateHelper

    @Inject
    lateinit var inAppReviewHelper: InAppReviewHelper

    @Inject
    lateinit var appInfo: AppInfo

    private var accountMenu: MenuItem? = null

    private val overlayProvider by lazy { ElevationOverlayProvider(this) }

    private lateinit var navController: NavController

    companion object {
        const val EXTRA_START_MENU = "extraStartMenu"

        fun getStartIntent(
            context: Context,
            startMenu: MainView.Section? = null,
            clear: Boolean = false
        ) = Intent(context, MainActivity::class.java).apply {
            if (clear) flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
            startMenu?.let { putExtra(EXTRA_START_MENU, it.id) }
        }
    }

    override val isRootView get() = false//navController.isRootFragment

    override val currentStackSize get() = 0//navController.currentStack?.size

    override val currentViewTitle = ""
//        get() = (navController.currentFrag as? MainView.TitledView)?.titleStringId?.let {
//            getString(it)
//        }

    override val currentViewSubtitle get() = "" //(navController.currentFrag as? MainView.TitledView)?.subtitleString

    override var startMenuIndex = 0

    override var startMenuMoreIndex = -1

    private val moreMenuFragments = mapOf<Int, Fragment>(
        MainView.Section.MESSAGE.id to MessageFragment.newInstance(),
        MainView.Section.EXAM.id to ExamFragment.newInstance(),
        MainView.Section.HOMEWORK.id to HomeworkFragment.newInstance(),
        MainView.Section.NOTE.id to NoteFragment.newInstance(),
        MainView.Section.CONFERENCE.id to ConferenceFragment.newInstance(),
        MainView.Section.SCHOOL_ANNOUNCEMENT.id to SchoolAnnouncementFragment.newInstance(),
        MainView.Section.LUCKY_NUMBER.id to LuckyNumberFragment.newInstance(),
    )

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityMainBinding.inflate(layoutInflater).apply { binding = this }.root)
        setSupportActionBar(binding.mainToolbar)
        messageContainer = binding.mainMessageContainer
        updateHelper.messageContainer = binding.mainFragmentContainer

        val section = MainView.Section.values()
            .singleOrNull { it.id == intent.getIntExtra(EXTRA_START_MENU, -1) }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
        navController = (navHostFragment as NavHostFragment).navController
        binding.mainBottomNav.setupWithNavController(navController)

        presenter.onAttachView(this, section)

        if (appInfo.systemVersion >= Build.VERSION_CODES.N_MR1) {
            initShortcuts()
        }

        updateHelper.checkAndInstallUpdates(this)
    }

    override fun onResume() {
        super.onResume()
        updateHelper.onResume(this)
    }

    //https://developer.android.com/guide/playcore/in-app-updates#status_callback
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateHelper.onActivityResult(requestCode, resultCode)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun initShortcuts() {
        val shortcutsList = mutableListOf<ShortcutInfo>()

        listOf(
            Triple(
                getString(R.string.grade_title),
                R.drawable.ic_shortcut_grade,
                MainView.Section.GRADE
            ),
            Triple(
                getString(R.string.attendance_title),
                R.drawable.ic_shortcut_attendance,
                MainView.Section.ATTENDANCE
            ),
            Triple(
                getString(R.string.exam_title),
                R.drawable.ic_shortcut_exam,
                MainView.Section.EXAM
            ),
            Triple(
                getString(R.string.timetable_title),
                R.drawable.ic_shortcut_timetable,
                MainView.Section.TIMETABLE
            )
        ).forEach { (title, icon, enum) ->
            shortcutsList.add(
                ShortcutInfo.Builder(applicationContext, title)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(Icon.createWithResource(applicationContext, icon))
                    .setIntents(
                        arrayOf(
                            Intent(applicationContext, MainActivity::class.java)
                                .setAction(Intent.ACTION_VIEW),
                            Intent(applicationContext, MainActivity::class.java)
                                .putExtra(EXTRA_START_MENU, enum.id)
                                .setAction(Intent.ACTION_VIEW)
                                .addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
                        )
                    )
                    .build()
            )
        }

        getSystemService<ShortcutManager>()?.dynamicShortcuts = shortcutsList
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_menu_main, menu)
        accountMenu = menu?.findItem(R.id.mainMenuAccount)

        presenter.onActionMenuCreated()
        return true
    }

    @SuppressLint("NewApi")
    override fun initView() {
        with(binding.mainToolbar) {
            stateListAnimator = null
            setBackgroundColor(
                overlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(dpToPx(4f))
            )
        }

        with(binding.mainBottomNav) {
            with(menu) {
                add(Menu.NONE, R.id.dashboardFragment, 0, R.string.dashboard_title)
                    .setIcon(R.drawable.ic_main_dashboard)
                add(Menu.NONE, R.id.gradeFragment, 1, R.string.grade_title)
                    .setIcon(R.drawable.ic_main_grade)
                add(Menu.NONE, R.id.attendanceFragment, 2, R.string.attendance_title)
                    .setIcon(R.drawable.ic_main_attendance)
                add(Menu.NONE, R.id.timetableFragment, 3, R.string.timetable_title)
                    .setIcon(R.drawable.ic_main_timetable)
                add(Menu.NONE, 4, 4, R.string.more_title)
                    .setIcon(R.drawable.ic_main_more)
            }
            selectedItemId = startMenuIndex
            setOnItemSelectedListener {
                it.navigateToConnectedAction(navController)
                presenter.onTabSelected(it.itemId, false)
            }
            setOnItemReselectedListener { presenter.onTabSelected(it.itemId, true) }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.accountFragment || destination.id == R.id.studentInfoFragment) {
                binding.mainBottomNav.isVisible = false

                if (appInfo.systemVersion >= P) {
                    window.navigationBarColor = getThemeAttrColor(R.attr.colorSurface)
                }
            } else {
                binding.mainBottomNav.isVisible = true

                if (appInfo.systemVersion >= P) {
                    window.navigationBarColor = getThemeAttrColor(android.R.attr.navigationBarColor)
                }
            }

//            analytics.setCurrentScreen(this@MainActivity, name)
//            presenter.onViewChange(section)
//            rootFragments = listOf(
//                DashboardFragment.newInstance(),
//                GradeFragment.newInstance(),
//                AttendanceFragment.newInstance(),
//                TimetableFragment.newInstance(),
//                MoreFragment.newInstance()
//            )
        }
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragment =
            supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)
        pushView(fragment)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.mainMenuAccount) presenter.onAccountManagerSelected()
        else false
    }

    override fun onSupportNavigateUp(): Boolean {
        return presenter.onUpNavigate()
    }

    override fun switchMenuView(position: Int) {
        if (supportFragmentManager.isStateSaved) return

//        analytics.popCurrentScreen(navController.currentFrag!!::class.simpleName)
//        navController.switchTab(position)
    }

    override fun setViewTitle(title: String) {
        supportActionBar?.title = title
    }

    override fun setViewSubTitle(subtitle: String?) {
        supportActionBar?.subtitle = subtitle
    }

    override fun showHomeArrow(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    override fun showAccountPicker(studentWithSemesters: List<StudentWithSemesters>) {
        navigateToDestination(
            MainNavGraphDirections.actionToSwitchDialog(StudentsWrapper(studentWithSemesters))
        )
    }

    override fun showActionBarElevation(show: Boolean) {
        ViewCompat.setElevation(binding.mainToolbar, if (show) dpToPx(4f) else 0f)
    }

    override fun notifyMenuViewReselected() {
//        (navController.currentStack?.getOrNull(0) as? MainView.MainChildView)?.onFragmentReselected()
    }

    override fun notifyMenuViewChanged() {
        Timber.d("Menu view changed")
//        (navController.currentStack?.getOrNull(0) as? MainView.MainChildView)?.onFragmentChanged()
    }

    @Suppress("DEPRECATION")
    fun showDialogFragment(dialog: DialogFragment) {
        if (supportFragmentManager.isStateSaved) return

        //Deprecated method is used here to avoid fragnav bug
//        if (navController.currentDialogFrag?.fragmentManager == null) {
//            FragNavController::class.java.getDeclaredField("mCurrentDialogFrag").apply {
//                isAccessible = true
//                set(navController, null)
//            }
//        }

//        navController.showDialogFragment(dialog)
    }

    fun navigateToDestination(command: NavDirections) {
        navController.navigate(command)
    }

    fun navigateToDestination(id: Int) {
        navController.navigate(id)
    }

    @Deprecated("use navigateToDestination")
    fun pushView(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return

//        analytics.popCurrentScreen(navController.currentFrag!!::class.simpleName)
//        navController.pushFragment(fragment)
    }

    override fun popView(depth: Int) {
        if (supportFragmentManager.isStateSaved) return

//        analytics.popCurrentScreen(navController.currentFrag!!::class.simpleName)
//        navController.safelyPopFragments(depth)
        navController.navigateUp()
    }

    override fun onBackPressed() {
        presenter.onBackPressed { super.onBackPressed() }
    }

    override fun showStudentAvatar(student: Student) {
        accountMenu?.run {
            icon = createNameInitialsDrawable(student.nickOrName, student.avatarColor, 0.44f)
            title = getString(R.string.main_account_picker)
        }
    }

    override fun showInAppReview() {
        inAppReviewHelper.showInAppReview(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        intent.removeExtra(EXTRA_START_MENU)
    }
}

package io.github.wulkanowy.services.sync

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.BigTextStyle
import androidx.core.app.NotificationCompat.PRIORITY_DEFAULT
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.wulkanowy.R
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.SemesterRepository
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.sdk.exception.FeatureNotAvailableException
import io.github.wulkanowy.sdk.scrapper.exception.FeatureDisabledException
import io.github.wulkanowy.services.sync.channels.DebugChannel
import io.github.wulkanowy.services.sync.works.Work
import io.github.wulkanowy.utils.getCompatColor
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import kotlin.random.Random

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted private val workerParameters: WorkerParameters,
    private val studentRepository: StudentRepository,
    private val semesterRepository: SemesterRepository,
    private val works: Set<@JvmSuppressWildcards Work>,
    private val preferencesRepository: PreferencesRepository,
    private val notificationManager: NotificationManagerCompat
) : CoroutineWorker(appContext, workerParameters) {

    companion object {
        const val ONE_TIME_KEY = "one_time"
        const val WORKS_TO_RUN = "works_to_run"
    }

    override suspend fun doWork() = coroutineScope {
        Timber.i("SyncWorker is starting")

        if (!studentRepository.isCurrentStudentSet()) return@coroutineScope Result.failure()

        val worksRequest = inputData.getStringArray(WORKS_TO_RUN)

        val student = studentRepository.getCurrentStudent()
        val semester = semesterRepository.getCurrentSemester(student, worksRequest.isNullOrEmpty())

        val worksToRun = if (worksRequest.isNullOrEmpty()) works else works.filter {
            it::class.java.toString() in worksRequest
        }
        val exceptions = worksToRun.mapNotNull { work ->
            try {
                Timber.i("${work.name} is starting")
                work.doWork(student, semester, workerParameters)
                Timber.i("${work.name} result: Success")
                null
            } catch (e: Throwable) {
                Timber.w("${work.name} result: An exception ${e.message} occurred")
                work.onFailure(workerParameters, e)
                if (e is FeatureDisabledException || e is FeatureNotAvailableException) null
                else {
                    Timber.e(e)
                    e
                }
            }
        }
        val result = when {
            exceptions.isNotEmpty() && inputData.getBoolean(ONE_TIME_KEY, false) -> {
                Result.failure(
                    Data.Builder()
                        .putString("error", exceptions.map { it.stackTraceToString() }.toString())
                        .build()
                )
            }
            exceptions.isNotEmpty() -> Result.retry()
            else -> Result.success()
        }

        if (preferencesRepository.isDebugNotificationEnable) notify(result)
        Timber.i("SyncWorker result: $result")

        result
    }

    private fun notify(result: Result) {
        notificationManager.notify(
            Random.nextInt(Int.MAX_VALUE),
            NotificationCompat.Builder(applicationContext, DebugChannel.CHANNEL_ID)
                .setContentTitle("Debug notification")
                .setSmallIcon(R.drawable.ic_stat_push)
                .setAutoCancel(true)
                .setColor(applicationContext.getCompatColor(R.color.colorPrimary))
                .setStyle(BigTextStyle().bigText("${SyncWorker::class.java.simpleName} result: $result"))
                .setPriority(PRIORITY_DEFAULT)
                .build()
        )
    }
}

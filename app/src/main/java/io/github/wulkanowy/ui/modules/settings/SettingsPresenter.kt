package io.github.wulkanowy.ui.modules.settings

import io.github.wulkanowy.data.ErrorHandler
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.services.job.ServiceHelper
import io.github.wulkanowy.ui.base.BasePresenter
import io.github.wulkanowy.utils.isHolidays
import io.github.wulkanowy.utils.logEvent
import org.threeten.bp.LocalDate.now
import javax.inject.Inject

class SettingsPresenter @Inject constructor(
    errorHandler: ErrorHandler,
    private val preferencesRepository: PreferencesRepository,
    private val serviceHelper: ServiceHelper
) : BasePresenter<SettingsView>(errorHandler) {

    override fun onAttachView(view: SettingsView) {
        super.onAttachView(view)

        view.run {
            setServicesSuspended(preferencesRepository.serviceEnablesKey, now().isHolidays)
        }
    }

    fun onSharedPreferenceChanged(key: String) {
        when (key) {
            preferencesRepository.serviceEnablesKey -> {
                if (preferencesRepository.serviceEnabled) serviceHelper.startFullSyncService()
                else serviceHelper.stopFullSyncService()
            }
            preferencesRepository.servicesIntervalKey,
            preferencesRepository.servicesOnlyWifiKey -> {
                serviceHelper.reloadFullSyncService()
            }
            preferencesRepository.currentThemeKey -> {
                view?.setTheme(preferencesRepository.currentTheme)
            }
        }

        logEvent("Setting changed", mapOf("name" to key))
    }
}

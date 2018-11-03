package io.github.wulkanowy.ui.modules.splash

import io.github.wulkanowy.data.ErrorHandler
import io.github.wulkanowy.data.repositories.PreferencesRepository
import io.github.wulkanowy.data.repositories.SessionRepository
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class SplashPresenterTest {

    @Mock
    lateinit var splashView: SplashView

    @Mock
    lateinit var sessionRepository: SessionRepository

    @Mock
    lateinit var errorHandler: ErrorHandler

    @Mock
    lateinit var preferencesRepository: PreferencesRepository

    private lateinit var presenter: SplashPresenter

    @Before
    fun initPresenter() {
        MockitoAnnotations.initMocks(this)
        presenter = SplashPresenter(sessionRepository, preferencesRepository, errorHandler)
    }

    @Test
    fun testOpenLoginView() {
        doReturn(false).`when`(sessionRepository).isSessionSaved
        presenter.onAttachView(splashView)
        verify(splashView).openLoginView()
    }

    @Test
    fun testMainMainView() {
        doReturn(true).`when`(sessionRepository).isSessionSaved
        presenter.onAttachView(splashView)
        verify(splashView).openMainView()
    }
}

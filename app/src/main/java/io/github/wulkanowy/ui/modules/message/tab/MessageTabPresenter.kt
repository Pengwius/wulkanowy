package io.github.wulkanowy.ui.modules.message.tab

import io.github.wulkanowy.data.Status
import io.github.wulkanowy.data.db.entities.Message
import io.github.wulkanowy.data.enums.MessageFolder
import io.github.wulkanowy.data.repositories.MessageRepository
import io.github.wulkanowy.data.repositories.SemesterRepository
import io.github.wulkanowy.data.repositories.StudentRepository
import io.github.wulkanowy.ui.base.BasePresenter
import io.github.wulkanowy.ui.base.ErrorHandler
import io.github.wulkanowy.utils.AnalyticsHelper
import io.github.wulkanowy.utils.afterLoading
import io.github.wulkanowy.utils.flowWithResourceIn
import io.github.wulkanowy.utils.toFormattedString
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.xdrop.fuzzywuzzy.FuzzySearch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.pow

class MessageTabPresenter @Inject constructor(
    errorHandler: ErrorHandler,
    studentRepository: StudentRepository,
    private val messageRepository: MessageRepository,
    private val semesterRepository: SemesterRepository,
    private val analytics: AnalyticsHelper
) : BasePresenter<MessageTabView>(errorHandler, studentRepository) {

    lateinit var folder: MessageFolder

    private lateinit var lastError: Throwable

    private var lastSearchQuery = ""

    private var messages = emptyList<Message>()

    private val searchChannel = Channel<String>()

    private val messagesToDelete = mutableListOf<Message>()

    private var onlyUnread: Boolean? = false

    private var onlyWithAttachments = false

    private var isActionMode = false

    fun onAttachView(view: MessageTabView, folder: MessageFolder) {
        super.onAttachView(view)
        view.initView()
        initializeSearchStream()
        errorHandler.showErrorMessage = ::showErrorViewOnError
        this.folder = folder
    }

    fun onSwipeRefresh() {
        Timber.i("Force refreshing the $folder message")
        view?.run { loadData(true, onlyUnread == true, onlyWithAttachments) }
    }

    fun onRetry() {
        view?.run {
            showErrorView(false)
            showProgress(true)
            loadData(true, onlyUnread == true, onlyWithAttachments)
        }
    }

    fun onDetailsClick() {
        view?.showErrorDetailsDialog(lastError)
    }

    fun onParentViewLoadData(forceRefresh: Boolean) {
        loadData(forceRefresh, onlyUnread == true, onlyWithAttachments)
    }

    fun onParentFinishActionMode() {
        view?.showActionMode(false)
    }

    fun onDestroyActionMode() {
        isActionMode = false
        messagesToDelete.clear()
        updateDataInView(messages)

        view?.run {
            enableSwipe(true)
            notifyParentShowNewMessage(true)
        }
    }

    fun onPrepareActionMode(): Boolean {
        isActionMode = true
        messagesToDelete.clear()
        updateDataInView(messages)

        view?.apply {
            enableSwipe(false)
            notifyParentShowNewMessage(false)
        }
        return true
    }

    fun onActionModeSelectDelete() {
        Timber.i("Delete ${messagesToDelete.size} messages)")
        val messageList = messagesToDelete.toList()

        presenterScope.launch {
            view?.run {
                showProgress(true)
                showContent(false)
                showActionMode(false)
            }

            runCatching {
                val student = studentRepository.getCurrentStudent(true)
                messageRepository.deleteMessages(student, messageList)
            }
                .onFailure(errorHandler::dispatch)
                .onSuccess { view?.showMessage("Usnięto") }

            view?.run {
                showProgress(false)
                showContent(true)
            }
        }
    }

    fun onActionModeSelectCheckAll() {
        val isAllSelected = messagesToDelete.containsAll(messages)

        if (isAllSelected) {
            messagesToDelete.clear()
            view?.showActionMode(false)
        } else {
            messagesToDelete.addAll(messages)
            updateDataInView(messages)
        }

        view?.updateSelectAllMenu(!isAllSelected)
    }

    fun onMessageItemLongSelected(messageItem: MessageTabDataItem.MessageItem) {
        if (!isActionMode) {
            view?.showActionMode(true)

            messagesToDelete.add(messageItem.message)

            view?.updateActionModeTitle(messagesToDelete.size)
            updateDataInView(messages)
        }
    }

    fun onMessageItemSelected(messageItem: MessageTabDataItem.MessageItem, position: Int) {
        Timber.i("Select message ${messageItem.message.id} item (position: $position)")

        if (!isActionMode) {
            view?.run {
                showActionMode(false)
                openMessage(messageItem.message)
            }
        } else {
            if (!messageItem.isSelected) {
                messagesToDelete.add(messageItem.message)
            } else {
                messagesToDelete.remove(messageItem.message)
            }

            if (messagesToDelete.isEmpty()) {
                view?.showActionMode(false)
            }

            view?.run {
                updateActionModeTitle(messagesToDelete.size)
                updateSelectAllMenu(messagesToDelete.containsAll(messages))
            }
            updateDataInView(messages)
        }
    }

    fun onUnreadFilterSelected(isChecked: Boolean) {
        view?.run {
            onlyUnread = isChecked
            loadData(false, onlyUnread == true, onlyWithAttachments)
        }
    }

    fun onAttachmentsFilterSelected(isChecked: Boolean) {
        view?.run {
            onlyWithAttachments = isChecked
            loadData(false, onlyUnread == true, onlyWithAttachments)
        }
    }

    private fun loadData(
        forceRefresh: Boolean,
        onlyUnread: Boolean,
        onlyWithAttachments: Boolean
    ) {
        Timber.i("Loading $folder message data started")

        flowWithResourceIn {
            val student = studentRepository.getCurrentStudent()
            val semester = semesterRepository.getCurrentSemester(student)
            messageRepository.getMessages(student, semester, folder, forceRefresh)
        }.onEach {
            when (it.status) {
                Status.LOADING -> {
                    if (!it.data.isNullOrEmpty()) {
                        view?.run {
                            enableSwipe(true)
                            showErrorView(false)
                            showRefresh(true)
                            showProgress(false)
                            showContent(true)
                        }

                        messages = it.data

                        val filteredData = getFilteredData(
                            query = lastSearchQuery,
                            onlyUnread = onlyUnread,
                            onlyWithAttachments = onlyWithAttachments
                        )
                        updateDataInView(filteredData)

                        view?.notifyParentDataLoaded()
                    }
                }
                Status.SUCCESS -> {
                    Timber.i("Loading $folder message result: Success")
                    messages = it.data!!

                    view?.run {
                        showEmpty(it.data.isEmpty())
                        showContent(true)
                        showErrorView(false)
                    }

                    updateDataInView(
                        getFilteredData(
                            lastSearchQuery,
                            onlyUnread,
                            onlyWithAttachments
                        )
                    )

                    analytics.logEvent(
                        "load_data",
                        "type" to "messages",
                        "items" to it.data.size,
                        "folder" to folder.name
                    )
                }
                Status.ERROR -> {
                    Timber.i("Loading $folder message result: An exception occurred")
                    errorHandler.dispatch(it.error!!)
                }
            }
        }.afterLoading {
            view?.run {
                showRefresh(false)
                showProgress(false)
                enableSwipe(true)
                notifyParentDataLoaded()
            }
        }.catch {
            errorHandler.dispatch(it)
            view?.notifyParentDataLoaded()
        }.launch()
    }

    private fun showErrorViewOnError(message: String, error: Throwable) {
        view?.run {
            if (isViewEmpty) {
                lastError = error
                setErrorDetails(message)
                showErrorView(true)
                showEmpty(false)
                showProgress(false)
            } else showError(message, error)
        }
    }

    fun onSearchQueryTextChange(query: String) {
        presenterScope.launch {
            searchChannel.send(query)
        }
    }

    @OptIn(FlowPreview::class)
    private fun initializeSearchStream() {
        presenterScope.launch {
            searchChannel.consumeAsFlow()
                .debounce(250)
                .map { query ->
                    lastSearchQuery = query

                    val isOnlyUnread = onlyUnread == true
                    val isOnlyWithAttachments = onlyWithAttachments

                    getFilteredData(query, isOnlyUnread, isOnlyWithAttachments)
                }
                .catch { Timber.e(it) }
                .collect {
                    Timber.d("Applying filter. Full list: ${messages.size}, filtered: ${it.size}")

                    view?.run {
                        showEmpty(it.isEmpty())
                        showContent(true)
                        showErrorView(false)
                    }

                    updateDataInView(it)
                    view?.resetListPosition()
                }
        }
    }

    private fun getFilteredData(
        query: String,
        onlyUnread: Boolean = false,
        onlyWithAttachments: Boolean = false
    ): List<Message> {
        if (query.trim().isEmpty()) {
            val sortedMessages = messages.sortedByDescending { it.date }
            return when {
                onlyUnread && onlyWithAttachments -> sortedMessages.filter { it.unread == onlyUnread && it.hasAttachments == onlyWithAttachments }
                onlyUnread -> sortedMessages.filter { it.unread == onlyUnread }
                onlyWithAttachments -> sortedMessages.filter { it.hasAttachments == onlyWithAttachments }
                else -> sortedMessages
            }
        } else {
            val sortedMessages = messages
                .map { it to calculateMatchRatio(it, query) }
                .sortedWith(compareBy<Pair<Message, Int>> { -it.second }.thenByDescending { it.first.date })
                .filter { it.second > 6000 }
                .map { it.first }
            return when {
                onlyUnread && onlyWithAttachments -> sortedMessages.filter { it.unread == onlyUnread && it.hasAttachments == onlyWithAttachments }
                onlyUnread -> sortedMessages.filter { it.unread == onlyUnread }
                onlyWithAttachments -> sortedMessages.filter { it.hasAttachments == onlyWithAttachments }
                else -> sortedMessages
            }
        }
    }

    private fun updateDataInView(data: List<Message>) {
        val list = buildList {
            if (!isActionMode) {
                add(
                    MessageTabDataItem.FilterHeader(
                        onlyUnread = onlyUnread.takeIf { folder != MessageFolder.SENT },
                        onlyWithAttachments = onlyWithAttachments
                    )
                )
            }

            addAll(data.map { message ->
                MessageTabDataItem.MessageItem(
                    message = message,
                    isSelected = messagesToDelete.any { it.id == message.id },
                    isActionMode = isActionMode
                )
            })
        }

        view?.updateData(list)
    }

    private fun calculateMatchRatio(message: Message, query: String): Int {
        val subjectRatio = FuzzySearch.tokenSortPartialRatio(query.lowercase(), message.subject)

        val senderOrRecipientRatio = FuzzySearch.tokenSortPartialRatio(
            query.lowercase(),
            if (message.sender.isNotEmpty()) message.sender.lowercase()
            else message.recipient.lowercase()
        )

        val dateRatio = listOf(
            FuzzySearch.ratio(
                query.lowercase(),
                message.date.toFormattedString("dd.MM").lowercase()
            ),
            FuzzySearch.ratio(
                query.lowercase(),
                message.date.toFormattedString("dd.MM.yyyy").lowercase()
            )
        ).maxOrNull() ?: 0


        return (subjectRatio.toDouble().pow(2)
                + senderOrRecipientRatio.toDouble().pow(2)
                + dateRatio.toDouble().pow(2) * 2
                ).toInt()
    }
}

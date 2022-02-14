package io.github.wulkanowy.ui.modules.message.tab

import io.github.wulkanowy.data.db.entities.Message
import io.github.wulkanowy.ui.base.BaseView

interface MessageTabView : BaseView {

    val isViewEmpty: Boolean

    fun initView()

    fun resetListPosition()

    fun updateData(data: List<MessageTabDataItem>)

    fun updateActionModeTitle(selectedMessagesSize: Int)

    fun showProgress(show: Boolean)

    fun enableSwipe(enable: Boolean)

    fun showContent(show: Boolean)

    fun showEmpty(show: Boolean)

    fun showErrorView(show: Boolean)

    fun notifyParentShowNewMessage(show: Boolean)

    fun setErrorDetails(message: String)

    fun showRefresh(show: Boolean)

    fun openMessage(message: Message)

    fun notifyParentDataLoaded()

    fun showActionMode(show: Boolean)

    fun showSelectCheckboxes(show: Boolean)
}

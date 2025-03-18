package com.trimble.ttm.formlibrary.ui.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.logger.TRASH_LIST
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.commons.utils.newConcurrentHashSet
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.TrashListAdapter
import com.trimble.ttm.formlibrary.customViews.STATE
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.FragmentTrashBinding
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.END_REACHED
import com.trimble.ttm.formlibrary.utils.MESSAGE
import com.trimble.ttm.formlibrary.utils.SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH
import com.trimble.ttm.formlibrary.utils.TRASH_INDEX
import com.trimble.ttm.formlibrary.utils.TRASH_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.findNavControllerSafely
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.navigateTo
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import com.trimble.ttm.formlibrary.viewmodel.TrashViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class TrashFragment : Fragment(), KoinComponent {
    private lateinit var trashListAdapter: TrashListAdapter
    private val trashViewModel: TrashViewModel by viewModel()
    private var isScrolling = false
    private var fragmentTrashBinding: FragmentTrashBinding? = null
    private var multipleInvocationLock = false
    private val logTag = "TrashFragment"
    private var isInternetActive = false
    private var deleteMessageDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        fragmentTrashBinding = FragmentTrashBinding.inflate(inflater, container, false)
        return fragmentTrashBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")
        fragmentTrashBinding?.progressErrViewTrash?.setProgressState(getString(R.string.loading_text))
        setupMessageList()
        observeForMessages()
        observeForNewMessage()
        observeForErrorData()
        observeForLastItemReachedState()
        setMessageDeletionOnClickListener()
        setSelectAllCheckBoxListener()
    }

    private fun observeForMessages() {
        trashListAdapter.multiSelect = trashViewModel.getSelectedItems().isNotEmpty()
        trashViewModel.messages.observe(viewLifecycleOwner) { messageSet ->
            multipleInvocationLock = false
            trashViewModel.deletedMessages.forEach { deletedMessage ->
                messageSet.removeIf { it == deletedMessage }
            }
            if (messageSet.isNotEmpty()) {
                fragmentTrashBinding?.run {
                    if (cboxSelectAll.isVisible.not()) cboxSelectAll.visibility = View.VISIBLE
                    if (paginationProgressBar.isVisible) paginationProgressBar.hide()
                    trashListAdapter.updateMessageSet(messageSet)
                    if(progressErrViewTrash.currentState == STATE.PROGRESS) progressErrViewTrash.setNoState()
                    else progressErrViewTrash.visibility = View.GONE
                    trashMessageListRecyclerView.visibility = View.VISIBLE
                }
            } else {
                updateUIForEmptyMessages()
            }
        }

        trashViewModel.isPaginationMessageReceived.observe(viewLifecycleOwner) {
            if (it && trashViewModel.selectAllCheckedTimeStamp > 0L && trashViewModel.getSelectedItems().isNotEmpty()) {
                trashListAdapter.selectAllItems()
                trashListAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun observeForNewMessage() {
        fragmentTrashBinding?.run {
            trashViewModel.isNewTrashMessageReceived.observe(viewLifecycleOwner) { isNewTrashMessageReceived ->
                if (isNewTrashMessageReceived && cboxSelectAll.isChecked == true) {
                    resetSelectAllCheckBox()
                }
            }
        }
    }

    private fun observeForErrorData() {
        trashViewModel.errorData.observe(viewLifecycleOwner) {
            fragmentTrashBinding?.run {
                if (paginationProgressBar.isVisible) paginationProgressBar.hide()
                if (it != END_REACHED) {
                    progressErrViewTrash.setErrorState(it)
                }
            }
        }
    }

    private fun setMessageDeletionOnClickListener() {
        fragmentTrashBinding?.run {
            imgbtnDeleteTrash.setOnClickListener {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag delete button clicked")
                if (cboxSelectAll.isChecked && !isInternetActive) {
                    context?.let {context ->
                        ToastCompat.makeText(
                            context,
                            context.getString(R.string.delete_all_disabled_msg),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    handleMultipleMessagesSelectDelete()
                }
            }
        }
    }

    private fun handleMultipleMessagesSelectDelete() {
        fragmentTrashBinding?.run {
            context?.let { context ->
                deleteMessageDialog = UiUtils.showAlertDialog(
                    AlertDialogData(
                        context = context,
                        message = Utils.getPermanentDeletionMessageBasedOnSelection(
                            context,
                            cboxSelectAll.isChecked,
                            trashViewModel.getSelectedItems().size
                        ),
                        title = getString(R.string.delete_message_title),
                        positiveActionText = getString(R.string.delete),
                        negativeActionText = getString(R.string.cancel).uppercase(),
                        isCancelable = false,
                        positiveAction = {
                            Log.logUiInteractionInInfoLevel(
                                logTag,
                                "$logTag delete dialog positive button clicked"
                            )
                            progressErrViewTrash.show()
                            progressErrViewTrash.setStateText(getString(R.string.deleting_text))
                            if (cboxSelectAll.isChecked) {
                                if(isInternetActive) handleSelectAllMessagesDelete()
                                else {
                                    Log.d(
                                        logTag,
                                        "Internet not available - Delete Failed"
                                    )
                                    progressErrViewTrash.visibility = View.GONE
                                    progressErrViewTrash.setNoState()
                                    ToastCompat.makeText(
                                        context, getString(R.string.delete_failure_message), Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            else handleSelectAllCheckBoxUnCheck()
                        },
                        negativeAction = {
                            Log.logUiInteractionInInfoLevel(
                                logTag,
                                "$logTag delete dialog negative button clicked"
                            )
                            deleteMessageDialog?.dismiss()
                            deleteMessageDialog = null
                        })
                ).also { alertDialog ->
                    alertDialog.setOnKeyListener { _, keyCode, _ ->
                        if (keyCode == KeyEvent.KEYCODE_BACK && alertDialog.isShowing) {
                            alertDialog.dismiss()
                        }
                        true
                    }
                }
            }
        }
    }

    private fun setSelectAllCheckBoxListener() {
        fragmentTrashBinding?.run {
            cboxSelectAll.setOnCheckedChangeListener { compoundButton, isChecked ->
                if (compoundButton.isPressed.not()) {
                    trashViewModel.selectAllCheckedTimeStamp = 0L
                    return@setOnCheckedChangeListener
                }

                if (isChecked) {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag select all checkbox checked")
                    trashListAdapter.selectAllItems()
                    trashViewModel.selectAllCheckedTimeStamp = System.currentTimeMillis()
                    trashListAdapter.notifyDataSetChanged()
                } else {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag select all checkbox unchecked")
                    trashListAdapter.clearAllSelectedItems()
                    trashViewModel.selectAllCheckedTimeStamp = 0L
                    trashListAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun handleSelectAllMessagesDelete() {
        trashViewModel.trashDeleteAllMessagesResponse.observe(viewLifecycleOwner) {
            if (it != null && it.isDeleteSuccess) {
                deletionProcessCleanUp(true)
                context?.let { context ->
                    ToastCompat.makeText(
                        context, getString(R.string.delete_success_message), Toast.LENGTH_LONG
                    ).show()
                }
                trashListAdapter.updateMessageSet(newConcurrentHashSet())
            } else {
                context?.let { context ->
                    ToastCompat.makeText(
                        context, getString(R.string.delete_failure_message), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        trashViewModel.deleteAllMessages()
    }

    private fun handleSelectAllCheckBoxUnCheck() {
        lifecycleScope.launch(trashViewModel.dispatcherProvider.io() + CoroutineName(logTag)) {
            val messageSet = mutableSetOf<Message>()
            trashViewModel.deletedMessages.addAll(trashViewModel.getSelectedItems())
            messageSet.addAll(trashViewModel.getTotalMessages())
            messageSet.removeAll(trashViewModel.deletedMessages)
            withContext(trashViewModel.dispatcherProvider.mainImmediate()) {
                Log.d(TRASH_LIST, "selectAll ${messageSet.map { it.asn }}")
                trashListAdapter.updateMessageSet(messageSet)
                deletionProcessCleanUp(false)
                context?.let { context ->
                    ToastCompat.makeText(
                        context,
                        getString(R.string.delete_success_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (trashViewModel.getTotalMessages().isEmpty()) {
                    fragmentTrashBinding?.progressErrViewTrash?.setErrorState(
                        getString(R.string.no_messages_available)
                    )
                } else {
                    fragmentTrashBinding?.progressErrViewTrash?.setNoState()
                }
            }
            trashViewModel.deleteSelectedMessages()
        }
    }

    private fun observeForNetworkConnectivityChange() {
        lifecycleScope.launch {
            (activity as? MessagingActivity)?.run {
                messagingViewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
                    isInternetActive = isAvailable
                    if (isAvailable && trashViewModel.messages.value.isNullOrEmpty() && multipleInvocationLock.not()) fetchMessages(
                        true
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        trashViewModel.logScreenViewEvent(TRASH_SCREEN_TIME)
        (activity as? MessagingActivity)?.run {
            initMessagingSectionSlider(TRASH_INDEX)
        }
        //makes this toolbar visible
        activity?.supportFragmentManager?.findFragmentById(R.id.toolbar)?.view?.visibility =
            View.VISIBLE
        if (trashViewModel.isDeleteImageButtonVisible) fragmentTrashBinding?.imgbtnDeleteTrash?.show()
        if (trashViewModel.messages.value.isNullOrEmpty()) {
            multipleInvocationLock = true
            fetchMessages(true)
        }
        observeForNetworkConnectivityChange()
    }

    private fun observeForLastItemReachedState() {
        trashViewModel.isLastItemReceived.observe(viewLifecycleOwner) {
            if (it && fragmentTrashBinding?.paginationProgressBar!!.isVisible) {
                fragmentTrashBinding?.paginationProgressBar?.hide()
            }
        }
    }

    private fun fetchMessages(isFirstTimeFetch: Boolean) =
        trashViewModel.getMessages(
            isFirstTimeFetch
        )

    private fun setupMessageList() {
        trashListAdapter = TrashListAdapter (trashViewModel, {
            onMessageClicked(it)
        }, { selectionItemSize ->
            onMultipleMessageSelectedForDeletion(selectionItemSize)
        }, {
            unCheckSelectAllOnItemClick()
        })
        context?.let { context ->
            val itemDecorator = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
                .also { itemDecoration ->
                    ContextCompat.getDrawable(context, R.drawable.divider)?.let { drawable ->
                        itemDecoration.setDrawable(drawable)
                    }
                }
            fragmentTrashBinding?.run {
                trashMessageListRecyclerView.addItemDecoration(itemDecorator)
                trashMessageListRecyclerView.layoutManager = LinearLayoutManager(context)
                trashMessageListRecyclerView.adapter = trashListAdapter
            }
        }

        initRecyclerViewOnScrollListener()
    }

    private fun initRecyclerViewOnScrollListener() {
        fragmentTrashBinding?.trashMessageListRecyclerView?.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                    isScrolling = true
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                (fragmentTrashBinding?.trashMessageListRecyclerView?.layoutManager as LinearLayoutManager).let {
                    val firstVisibleProductPosition = it.findFirstVisibleItemPosition()
                    val visibleProductCount = it.childCount
                    val totalProductCount = it.itemCount

                    if (isScrolling && (firstVisibleProductPosition + visibleProductCount == totalProductCount)) {
                        isScrolling = false
                        lifecycleScope.launch(trashViewModel.dispatcherProvider.io() + CoroutineName(logTag)) {
                            withContext(trashViewModel.dispatcherProvider.main()) {
                                fragmentTrashBinding?.paginationProgressBar?.show()
                            }
                            fetchMessages(false)
                        }
                    }
                }
            }
        })
    }

    private fun onMessageClicked(message: Message) {
        Log.logUiInteractionInInfoLevel(logTag, "$logTag onMessageClicked messageAsn: ${message.asn}")
        findNavControllerSafely()?.navigateTo(
            R.id.messageViewPagerContainerFragment,
            R.id.action_messageViewPagerContainerFragment_to_messageDetailFragment,
            bundleOf(MESSAGE to message, SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH to true)
        )
    }

    private fun onMultipleMessageSelectedForDeletion(selectedItemSize: Int) {
        fragmentTrashBinding?.run {
            if (selectedItemSize.isLessThanAndEqualTo(ZERO)) {
                cboxSelectAll.isChecked = false
                imgbtnDeleteTrash.visibility = View.INVISIBLE
                trashViewModel.isDeleteImageButtonVisible = false
            } else {
                imgbtnDeleteTrash.visibility = View.VISIBLE
                trashViewModel.isDeleteImageButtonVisible = true
            }
        }
    }

    private fun unCheckSelectAllOnItemClick() {
        if (fragmentTrashBinding?.cboxSelectAll?.isChecked == true) {
            fragmentTrashBinding?.cboxSelectAll?.isChecked = false
        }
    }

    private fun updateUIForEmptyMessages() {
        fragmentTrashBinding?.run {
            if (cboxSelectAll.isVisible) cboxSelectAll.visibility = View.GONE
            trashMessageListRecyclerView.visibility = View.GONE
            progressErrViewTrash.show()
            progressErrViewTrash.setErrorState(getString(R.string.no_messages_available))
        }
    }

    private fun deletionProcessCleanUp(clearAllMessages: Boolean) {
        trashViewModel.removeDeletedItems(trashViewModel.getSelectedItems(), trashViewModel.getTotalMessages(), clearAllMessages)
        trashViewModel.setSelectedItems(newConcurrentHashSet())
        fragmentTrashBinding?.run {
            cboxSelectAll.isChecked = false
            imgbtnDeleteTrash.visibility = View.INVISIBLE
        }
        trashViewModel.isDeleteImageButtonVisible = false
        deleteMessageDialog?.dismiss()
        deleteMessageDialog = null
    }

    private fun resetSelectAllCheckBox() {
        trashViewModel.setSelectedItems(newConcurrentHashSet())
        trashViewModel.selectAllCheckedTimeStamp = 0L
        onMultipleMessageSelectedForDeletion(selectedItemSize = ZERO)
        trashListAdapter.notifyDataSetChanged()
        context?.let { context ->
            ToastCompat.makeText(
                context,
                context.getString(R.string.unselect_all_messages_on_new_message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        fragmentTrashBinding?.trashMessageListRecyclerView?.adapter = null
        fragmentTrashBinding?.trashMessageListRecyclerView?.clearOnScrollListeners()
        fragmentTrashBinding = null
        deleteMessageDialog?.dismiss()
        super.onDestroyView()
    }
}
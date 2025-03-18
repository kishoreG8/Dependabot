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
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.DISPATCH_FORM_SAVE_PATH
import com.trimble.ttm.commons.utils.UNCOMPLETED_DISPATCH_FORM_PATH
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.MessageResponseListAdapter
import com.trimble.ttm.formlibrary.customViews.STATE
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.FragmentSentBinding
import com.trimble.ttm.formlibrary.manager.workmanager.scheduleOneTimeImageDelete
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.END_REACHED
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.FORM_UI_RESPONSE
import com.trimble.ttm.formlibrary.utils.HAS_PRE_DEFINED_RECIPIENTS
import com.trimble.ttm.formlibrary.utils.IS_FROM_SENT_FORM
import com.trimble.ttm.formlibrary.utils.MESSAGE
import com.trimble.ttm.formlibrary.utils.SENT_INDEX
import com.trimble.ttm.formlibrary.utils.SENT_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.SENT_USERS
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.findNavControllerSafely
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.navigateTo
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import com.trimble.ttm.formlibrary.viewmodel.SentViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class SentFragment : Fragment(), KoinComponent {
    private lateinit var messageResponseListAdapter: MessageResponseListAdapter
    private val sentViewModel: SentViewModel by viewModel()
    private var deleteMessageDialog: AlertDialog? = null
    private var isScrolling = false
    private var fragmentSentBinding: FragmentSentBinding? = null
    private var internetAvailable = false
    private var multipleInvocationLock = false
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val logTag = "SentFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        fragmentSentBinding = FragmentSentBinding.inflate(inflater, container, false)
        return fragmentSentBinding!!.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::messageResponseListAdapter.isInitialized) {
            sentViewModel.userSelectedItemsOnMessageListAdapter =
                messageResponseListAdapter.selectedItems
            sentViewModel.messageListOnMessageListAdapter = messageResponseListAdapter.messageSet
            sentViewModel.multiSelectOnMessageListAdapter = messageResponseListAdapter.multiSelect
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")
        fragmentSentBinding?.run {
            progressErrViewSent.setProgressState(getString(R.string.loading_text))
            setupMessageList()
            observeForMessages()
            observeLastItemReachedData()
            observeForErrorData()
            setMessageDeletionClickListener()
            setSelectAllCheckBoxListener()
        }
    }

    private fun FragmentSentBinding.observeForMessages() {
        sentViewModel.messages.observe(viewLifecycleOwner) { messageSet ->
            if (messageSet.isNotEmpty()) {
                if (paginationProgressBar.isVisible) paginationProgressBar.hide()
                messageResponseListAdapter.messageSet = mutableSetOf()
                messageResponseListAdapter.messageSet = messageSet
                sentViewModel.originalMessageListCopy = messageSet
                messageResponseListAdapter.notifyDataSetChanged()
                if(progressErrViewSent.currentState == STATE.PROGRESS) progressErrViewSent.setNoState()
                else progressErrViewSent.visibility = View.GONE
                fragmentSentBinding?.messageListRecyclerViewSent?.visibility = View.VISIBLE
            }
            else {
                progressErrViewSent.visibility = View.VISIBLE
                progressErrViewSent.setErrorState(getString(R.string.no_messages_available))
                fragmentSentBinding?.messageListRecyclerViewSent?.visibility = View.GONE
            }
        }
    }

    private fun FragmentSentBinding.observeForErrorData() {
        sentViewModel.errorData.observe(viewLifecycleOwner) {
            if (paginationProgressBar.isVisible) paginationProgressBar.hide()
            if (it != END_REACHED) {
                progressErrViewSent.setErrorState(it)
            }
        }
    }

    private fun FragmentSentBinding.setMessageDeletionClickListener() {
        imgbtnDeleteSent.setOnClickListener {
            Log.logUiInteractionInInfoLevel(logTag, "$logTag Delete button clicked")
            if (cboxSelectAllSent.isChecked && !internetAvailable) {
                context?.let { context ->
                    ToastCompat.makeText(
                        context,
                        context.getString(R.string.delete_all_disabled_msg),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                context?.let { context ->
                    deleteMessageDialog = UiUtils.showAlertDialog(
                        AlertDialogData(
                            context = context,
                            message = Utils.getPermanentDeletionMessageBasedOnSelection(
                                context,
                                cboxSelectAllSent.isChecked,
                                messageResponseListAdapter.selectedItems.size
                            ),
                            title = getString(R.string.delete_message_title),
                            positiveActionText = getString(R.string.delete),
                            negativeActionText = getString(R.string.cancel).uppercase(),
                            isCancelable = false,
                            positiveAction = {
                                Log.logUiInteractionInInfoLevel(logTag, "$logTag Delete positive button clicked in dialog")
                                progressErrViewSent.show()
                                progressErrViewSent.setStateText(getString(R.string.deleting_text))
                                if (cboxSelectAllSent.isChecked) handleSelectAllCheckBoxCheck()
                                else handleSelectAllCheckBoxUnCheck()
                            },
                            negativeAction = {
                                Log.logUiInteractionInInfoLevel(logTag, "$logTag Delete negative button clicked in dialog")
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
    }

    private fun handleSelectAllCheckBoxCheck() {
        lifecycleScope.launch(CoroutineName(logTag)) {
            withContext(this.coroutineContext + sentViewModel.dispatchers.io()) {
                sentViewModel.deleteAllMessage(
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber()
                )
            }
        }
    }

    private var imageNames = mutableListOf<String>()
    private fun handleSelectAllCheckBoxUnCheck() {
        lifecycleScope.launch(sentViewModel.dispatchers.io() + CoroutineName(logTag)) {
            try {
                messageResponseListAdapter.selectedItems.forEach {
                    sentViewModel.deleteMessage(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber(),
                        it.createdUnixTime
                    )
                    imageNames.addAll(Utils.getImageIdentifiers(it.formData.fieldData).toMutableList())
                }.also {
                    withContext(sentViewModel.dispatchers.main()) {
                        deletionProcessCleanUp()
                        context?.let { context ->
                            ToastCompat.makeText(
                                context,
                                getString(R.string.delete_success_message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Exception in handleSelectAllCheckBoxUnCheck", e)
            } finally {
                if (imageNames.size >= 1) {
                    requireActivity().applicationContext.scheduleOneTimeImageDelete(
                        imageNames,
                        shouldDeleteFromStorage = false
                    )
                    imageNames.clear()
                }
            }
        }
    }



    private fun FragmentSentBinding.setSelectAllCheckBoxListener() {
        cboxSelectAllSent.setOnCheckedChangeListener { compoundButton, isChecked ->
            if (compoundButton.isPressed.not()) return@setOnCheckedChangeListener
            if (isChecked) {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag Select All checkbox checked")
                messageResponseListAdapter.selectAllItems()
                messageResponseListAdapter.notifyDataSetChanged()
            } else {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag Select All checkbox unchecked")
                messageResponseListAdapter.clearAllSelectedItems()
                messageResponseListAdapter.messageSet = sentViewModel.originalMessageListCopy
                messageResponseListAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun deletionProcessCleanUp() {
        //Resets selectedList items after deletion
        messageResponseListAdapter.selectedItems.clear()
        fragmentSentBinding?.run {
            cboxSelectAllSent.isChecked = false
            imgbtnDeleteSent.visibility = View.INVISIBLE
        }
        sentViewModel.isDeleteImageButtonVisible = false
        deleteMessageDialog?.dismiss()
        deleteMessageDialog = null
    }

    private fun observeForNetworkConnectivityChange() {
        lifecycleScope.launch {
            (activity as? MessagingActivity)?.run {
                messagingViewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
                    internetAvailable = isAvailable
                    if (isAvailable && sentViewModel.messages.value.isNullOrEmpty() && multipleInvocationLock.not()) {
                        fetchMessages(true)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        sentViewModel.logScreenViewEvent(SENT_SCREEN_TIME)
        (activity as? MessagingActivity)?.run {
            initMessagingSectionSlider(SENT_INDEX)
        }
        //makes this toolbar visible
        activity?.supportFragmentManager?.findFragmentById(R.id.toolbar)?.view?.visibility =
            View.VISIBLE
        if (sentViewModel.isDeleteImageButtonVisible) fragmentSentBinding?.imgbtnDeleteSent?.show()
        if (sentViewModel.messages.value.isNullOrEmpty()) {
            multipleInvocationLock = true
            fetchMessages(true)
        }
        setDeleteObserver()
        observeForNetworkConnectivityChange()
    }

    private fun setDeleteObserver(
        mainDispatcher: CoroutineDispatcher = sentViewModel.dispatchers.main()
    ) {
        lifecycleScope.launch(mainDispatcher + CoroutineName(logTag)) {
            sentViewModel.collectionDeleteResponse.safeCollect(logTag) {
                it?.let {
                    if (it.isDeleteSuccess) {
                        deletionProcessCleanUp()
                        context?.let { context ->
                            ToastCompat.makeText(
                                context,
                                getString(R.string.delete_success_message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        messageResponseListAdapter.messageSet = mutableSetOf()
                        messageResponseListAdapter.notifyDataSetChanged()
                    } else {
                        context?.let { context ->
                            ToastCompat.makeText(
                                context,
                                getString(R.string.delete_failure_message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun observeLastItemReachedData() {
        sentViewModel.isLastItemReceived.observe(viewLifecycleOwner) {
            if (it && fragmentSentBinding?.paginationProgressBar!!.isVisible) {
                fragmentSentBinding?.paginationProgressBar?.hide()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        fragmentSentBinding?.messageListRecyclerViewSent?.adapter = null
        fragmentSentBinding?.messageListRecyclerViewSent?.clearOnScrollListeners()
        fragmentSentBinding = null
        deleteMessageDialog?.dismiss()
        super.onDestroyView()
    }

    private fun fetchMessages(isFirstTimeFetch: Boolean) =
        sentViewModel.getMessages(isFirstTimeFetch)

    private fun setupMessageList() {
        messageResponseListAdapter = MessageResponseListAdapter({
            onMessageClicked(it)
        }, { selectionItemSize ->
            onMultipleMessageSelectedForDeletion(selectionItemSize)
        })
        context?.let { context ->
            val itemDecorator = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
                .also { itemDecoration ->
                    ContextCompat.getDrawable(context, R.drawable.divider)?.let { drawable ->
                        itemDecoration.setDrawable(drawable)
                    }
                }
            fragmentSentBinding?.run {
                with(messageListRecyclerViewSent) {
                    setItemViewCacheSize(20)
                    addItemDecoration(itemDecorator)
                    layoutManager = LinearLayoutManager(context)
                    adapter = messageResponseListAdapter
                }
            }
        }
        //restores values on orientation change
        with(messageResponseListAdapter) {
            selectedItems = sentViewModel.userSelectedItemsOnMessageListAdapter
            messageSet = sentViewModel.messageListOnMessageListAdapter
            multiSelect = sentViewModel.multiSelectOnMessageListAdapter
        }
        initRecyclerViewOnScrollListener()
    }

    private fun initRecyclerViewOnScrollListener() {
        fragmentSentBinding?.messageListRecyclerViewSent?.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                    isScrolling = true
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                (fragmentSentBinding?.messageListRecyclerViewSent?.layoutManager as LinearLayoutManager).let {
                    val firstVisibleProductPosition = it.findFirstVisibleItemPosition()
                    val visibleProductCount = it.childCount
                    val totalProductCount = it.itemCount

                    if (isScrolling && (firstVisibleProductPosition + visibleProductCount == totalProductCount)) {
                        isScrolling = false
                        lifecycleScope.launch(sentViewModel.dispatchers.io() + CoroutineName(logTag)) {
                            withContext(sentViewModel.dispatchers.main()) {
                                fragmentSentBinding?.paginationProgressBar?.show()
                            }
                            fetchMessages(false)
                        }
                    }
                }
            }
        })
    }

    private fun onMessageClicked(messageFormResponse: MessageFormResponse) {
        // put the navigation action in the coroutine scope was introducing a navigation bug in the
        // back button. To solve this, the implementation changes to use the aplicationScope instead
        // the lifecyclescope, and in this way left the navigation action outside of the scope
        Log.logUiInteractionInInfoLevel(logTag, "$logTag Message clicked. FormId: ${messageFormResponse.formId}")
        findNavControllerSafely()?.navigateTo(
            R.id.messageViewPagerContainerFragment,
            R.id.action_messageViewPagerContainerFragment_to_messageReplyFragment,
            bundleOf(
                MESSAGE to Message(
                    replyFormId = messageFormResponse.formId,
                    replyFormClass = messageFormResponse.formClass,
                    formName = messageFormResponse.formName,
                    dateTime = messageFormResponse.timestamp,
                    asn = messageFormResponse.formData.baseSerialNumber.toString()
                ), SENT_USERS to messageFormResponse.recipientUsers,
                IS_FROM_SENT_FORM to true, CREATED_AT to messageFormResponse.createdUnixTime,
                FORM_RESPONSE_TYPE to messageFormResponse.formResponseType,
                HAS_PRE_DEFINED_RECIPIENTS to messageFormResponse.hasPredefinedRecipients,
                FORM_UI_RESPONSE to messageFormResponse.formData,
                DISPATCH_FORM_SAVE_PATH to messageFormResponse.dispatchFormSavePath,
                UNCOMPLETED_DISPATCH_FORM_PATH to messageFormResponse.uncompletedDispatchFormPath
            )
        )
    }

    private fun onMultipleMessageSelectedForDeletion(selectedItemsSize: Int) {
        fragmentSentBinding?.run {
            if (selectedItemsSize.isLessThanAndEqualTo(ZERO)) {
                cboxSelectAllSent.isChecked = false
                imgbtnDeleteSent.visibility = View.INVISIBLE
                sentViewModel.isDeleteImageButtonVisible = false
            } else {
                imgbtnDeleteSent.show()
                sentViewModel.isDeleteImageButtonVisible = true
            }
        }
    }
}
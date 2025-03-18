package com.trimble.ttm.formlibrary.ui.fragments

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
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
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.MessageResponseListAdapter
import com.trimble.ttm.formlibrary.customViews.STATE
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.FragmentDraftBinding
import com.trimble.ttm.formlibrary.manager.workmanager.scheduleOneTimeImageDelete
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.model.MessageFormResponse
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.CREATED_AT
import com.trimble.ttm.formlibrary.utils.DRAFTED_USERS
import com.trimble.ttm.formlibrary.utils.DRAFT_INDEX
import com.trimble.ttm.formlibrary.utils.DRAFT_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.DelayProvider
import com.trimble.ttm.formlibrary.utils.END_REACHED
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.FORM_UI_RESPONSE
import com.trimble.ttm.formlibrary.utils.HAS_PRE_DEFINED_RECIPIENTS
import com.trimble.ttm.formlibrary.utils.IS_FROM_DRAFTED_FORM
import com.trimble.ttm.formlibrary.utils.MESSAGE
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.findNavControllerSafely
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.navigateBack
import com.trimble.ttm.formlibrary.utils.ext.navigateTo
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import com.trimble.ttm.formlibrary.viewmodel.DELAY_TO_AVOID_FREEZING_ANIMATION
import com.trimble.ttm.formlibrary.viewmodel.DraftViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class DraftFragment : Fragment(), KoinComponent {
    private lateinit var messageResponseListAdapter: MessageResponseListAdapter
    private val draftViewModel: DraftViewModel by viewModel()
    private val messagingViewModel: MessagingViewModel by sharedViewModel()
    private var deleteMessageDialog: AlertDialog? = null
    private var isScrolling = false
    private var internetAvailable = false
    private var fragmentDraftBinding: FragmentDraftBinding? = null
    private var multipleInvocationLock = false
    private val appModuleCommunicator: AppModuleCommunicator by inject()
    private val logTag = "DraftFragment"


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        fragmentDraftBinding = FragmentDraftBinding.inflate(inflater, container, false)
        getSelectedDispatchId()
        return fragmentDraftBinding!!.root
    }

    private fun getSelectedDispatchId(){
        lifecycleScope.launch {
            val selectedDispatchId = appModuleCommunicator.getSelectedDispatchId("DraftFragment-onMessageClicked")
            Log.d("DraftFragment", "selectedDispatchId in Draft Fragment onCretae: $selectedDispatchId")
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::messageResponseListAdapter.isInitialized) {
            draftViewModel.userSelectedItemsOnMessageListAdapter =
                messageResponseListAdapter.selectedItems
            draftViewModel.messageListOnMessageListAdapter = messageResponseListAdapter.messageSet
            draftViewModel.multiSelectOnMessageListAdapter = messageResponseListAdapter.multiSelect
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")
        // To close this fragment when it's opened and a HPN arrives
        messagingViewModel.shouldFinishFragment.observe(viewLifecycleOwner){
            if (it) {
                navigateBack()
            }
        }
        fragmentDraftBinding?.progressErrViewDraft?.setProgressState(getString(R.string.loading_text))
        setupMessageList()
        observeForMessages()
        observeForErrorData()
        setMessageDeletionClickListener()
        setSelectAllCheckBoxListener()
        observeForLastDraftReachedFlow()
    }

    private fun observeForLastDraftReachedFlow() {
        draftViewModel.isLastDraftReceived.observe(viewLifecycleOwner) {
            if (it && fragmentDraftBinding?.paginationProgressBar!!.isVisible) {
                fragmentDraftBinding?.paginationProgressBar?.hide()
            }
        }
    }

    private fun observeForErrorData() {
        fragmentDraftBinding?.run {
            draftViewModel.errorData.observe(viewLifecycleOwner) {
                if (paginationProgressBar.isVisible) paginationProgressBar.hide()
                if (it != END_REACHED) {
                    progressErrViewDraft.setErrorState(it)
                }
            }
        }
    }

    private fun observeForMessages() {
        fragmentDraftBinding?.run {
            draftViewModel.messages.observe(viewLifecycleOwner) { messageSet ->
                if (messageSet.isNotEmpty()) {
                    if (paginationProgressBar.isVisible) paginationProgressBar.hide()
                    messageResponseListAdapter.messageSet = mutableSetOf()
                    messageResponseListAdapter.messageSet = messageSet
                    draftViewModel.originalMessageListCopy = messageSet
                    messageResponseListAdapter.notifyDataSetChanged()
                    if(progressErrViewDraft.currentState == STATE.PROGRESS) progressErrViewDraft.setNoState()
                    else progressErrViewDraft.visibility = View.GONE
                    fragmentDraftBinding?.messageListRecyclerViewDraft?.visibility = View.VISIBLE
                } else{
                    progressErrViewDraft.visibility = View.VISIBLE
                    progressErrViewDraft.setErrorState(getString(R.string.no_messages_available))
                    fragmentDraftBinding?.messageListRecyclerViewDraft?.visibility = View.GONE
                }
            }
        }
    }

    private fun setSelectAllCheckBoxListener() {
        fragmentDraftBinding?.run {
            cboxSelectAllDraft.setOnCheckedChangeListener { compoundButton, isChecked ->
                if (compoundButton.isPressed.not()) return@setOnCheckedChangeListener
                if (isChecked) {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag select all checkbox checked")
                    messageResponseListAdapter.selectAllItems()
                    messageResponseListAdapter.notifyDataSetChanged()
                } else {
                    Log.logUiInteractionInInfoLevel(logTag, "$logTag select all checkbox unchecked")
                    messageResponseListAdapter.clearAllSelectedItems()
                    messageResponseListAdapter.messageSet = draftViewModel.originalMessageListCopy
                    messageResponseListAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setMessageDeletionClickListener() {
        fragmentDraftBinding?.run {
            imgbtnDeleteDraft.setOnClickListener {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag delete button clicked")
                if (cboxSelectAllDraft.isChecked && !internetAvailable) {
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
                                    cboxSelectAllDraft.isChecked,
                                    messageResponseListAdapter.selectedItems.size
                                ),
                                title = getString(R.string.delete_message_title),
                                positiveActionText = getString(R.string.delete),
                                negativeActionText = getString(R.string.cancel).uppercase(),
                                isCancelable = false,
                                positiveAction = {
                                    Log.logUiInteractionInInfoLevel(logTag, "$logTag delete dialog positive button clicked")
                                    handleAlertDialogPositiveAction()
                                },
                                negativeAction = {
                                    Log.logUiInteractionInInfoLevel(logTag, "$logTag delete dialog negative button clicked")
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
    }

    private fun handleAlertDialogPositiveAction() {
        fragmentDraftBinding?.run {
            progressErrViewDraft.show()
            progressErrViewDraft.setStateText(getString(R.string.deleting_text))
            if (cboxSelectAllDraft.isChecked) handleSelectAllCheckBoxCheck()
            else handleSelectAllCheckBoxUnCheck()
        }
    }

    private fun handleSelectAllCheckBoxCheck() {
        lifecycleScope.launch(CoroutineName(logTag)) {
            withContext(this.coroutineContext + draftViewModel.dispatcherProvider.io()) {
                draftViewModel.deleteAllMessagesAsync(
                    appModuleCommunicator.doGetCid(),
                    appModuleCommunicator.doGetTruckNumber()
                ).await()
            }.also {
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

    private var imageNames = mutableListOf<String>()
    private fun handleSelectAllCheckBoxUnCheck() {
        lifecycleScope.launch(draftViewModel.dispatcherProvider.io() + CoroutineName(logTag)) {
            try {
                messageResponseListAdapter.selectedItems.forEach {
                    draftViewModel.deleteMessage(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetTruckNumber(),
                        it.createdUnixTime
                    )
                    imageNames.addAll(Utils.getImageIdentifiers(it.formData.fieldData).toMutableList())
                }.also {
                    withContext(draftViewModel.dispatcherProvider.main()) {
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
                if (imageNames.size > 0) {
                    requireActivity().applicationContext.scheduleOneTimeImageDelete(
                        imageNames,
                        shouldDeleteFromStorage = true
                    )
                    imageNames.clear()
                }
            }
        }
    }

    private fun deletionProcessCleanUp() {
        //Resets selectedList items after deletion
        messageResponseListAdapter.selectedItems.clear()

        fragmentDraftBinding?.run {
            cboxSelectAllDraft.isChecked = false
            imgbtnDeleteDraft.visibility = View.INVISIBLE
        }

        draftViewModel.isDeleteImageButtonVisible = false
        deleteMessageDialog?.dismiss()
        deleteMessageDialog = null
    }

    private fun observeForNetworkConnectivityChange() {
        lifecycleScope.launch {
            (activity as? MessagingActivity)?.run {
                messagingViewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
                    internetAvailable = isAvailable
                    if (isAvailable && draftViewModel.messages.value.isNullOrEmpty() && multipleInvocationLock.not()) fetchMessages(
                        true
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        draftViewModel.logScreenViewEvent(DRAFT_SCREEN_TIME)
        (activity as? MessagingActivity)?.run { initMessagingSectionSlider(DRAFT_INDEX) }
        // Makes this toolbar visible
        activity?.findViewById<ComposeView>(R.id.toolbar)?.visibility = View.VISIBLE
        if (draftViewModel.isDeleteImageButtonVisible) fragmentDraftBinding?.imgbtnDeleteDraft?.show()
        if (draftViewModel.messages.value.isNullOrEmpty()) {
            multipleInvocationLock = true
            fetchMessages(true)
        }
        observeForNetworkConnectivityChange()
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        fragmentDraftBinding?.messageListRecyclerViewDraft?.adapter = null
        fragmentDraftBinding?.messageListRecyclerViewDraft?.clearOnScrollListeners()
        deleteMessageDialog?.dismiss()
        fragmentDraftBinding = null
        super.onDestroyView()
    }

    private fun fetchMessages(isFirstTimeFetch: Boolean) =
        lifecycleScope.launch(
            CoroutineName(logTag)
        ) {
            Log.d("DraftFragment", "fetchMessages($isFirstTimeFetch)")
            draftViewModel.getMessages(isFirstTimeFetch)
        }

    private fun setupMessageList() {
        messageResponseListAdapter = MessageResponseListAdapter({
            onMessageClicked(it)
        }, { selectionItemSize ->
            onMultipleMessageSelectedForDeletion(selectionItemSize)
        })
        val itemDecorator = context?.let { context ->
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
                .also { itemDecoration ->
                    ContextCompat.getDrawable(context, R.drawable.divider)
                        ?.let { drawable ->
                            itemDecoration.setDrawable(drawable)
                        }
                }
        }
        fragmentDraftBinding?.run {
            if (itemDecorator != null) {
                messageListRecyclerViewDraft.addItemDecoration(itemDecorator)
            }
            messageListRecyclerViewDraft.layoutManager = context?.let { context ->
                LinearLayoutManager(context)
            }
            messageListRecyclerViewDraft.adapter = messageResponseListAdapter
            messageListRecyclerViewDraft.setItemViewCacheSize(20)
        }
        //restores values on orientation change
        with(messageResponseListAdapter) {
            selectedItems = draftViewModel.userSelectedItemsOnMessageListAdapter
            messageSet = draftViewModel.messageListOnMessageListAdapter
            multiSelect = draftViewModel.multiSelectOnMessageListAdapter
        }
        initRecyclerViewOnScrollListener()
    }

    private fun initRecyclerViewOnScrollListener() {
        fragmentDraftBinding?.messageListRecyclerViewDraft?.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                    isScrolling = true
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                (fragmentDraftBinding?.messageListRecyclerViewDraft?.layoutManager as LinearLayoutManager).let {
                    val firstVisibleProductPosition = it.findFirstVisibleItemPosition()
                    val visibleProductCount = it.childCount
                    val totalProductCount = it.itemCount

                    if (isScrolling && (firstVisibleProductPosition + visibleProductCount == totalProductCount)) {
                        isScrolling = false
                        lifecycleScope.launch(draftViewModel.dispatcherProvider.io() + CoroutineName(logTag)) {
                            withContext(draftViewModel.dispatcherProvider.main()) {
                                fragmentDraftBinding?.paginationProgressBar?.show()
                            }
                            fetchMessages(false)

                        }
                    }
                }
            }
        })
    }

    private fun goToMessage(messageFormResponse: MessageFormResponse) {
        // put the navigation action in the coroutine scope was introducing a navigation bug in the
        // back button. To solve this, the implementation changes to use the aplicationScope instead
        // the lifecyclescope, and in this way left the navigation action outside of the scope
        Log.logUiInteractionInInfoLevel(logTag, "$logTag message clicked. MsgFormId: ${messageFormResponse.formId} MsgFormName: ${messageFormResponse.formName}")
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
                ), DRAFTED_USERS to messageFormResponse.recipientUsers,
                IS_FROM_DRAFTED_FORM to true, CREATED_AT to messageFormResponse.createdUnixTime,
                FORM_RESPONSE_TYPE to messageFormResponse.formResponseType,
                HAS_PRE_DEFINED_RECIPIENTS to messageFormResponse.hasPredefinedRecipients,
                UNCOMPLETED_DISPATCH_FORM_PATH to messageFormResponse.uncompletedDispatchFormPath,
                DISPATCH_FORM_SAVE_PATH to messageFormResponse.dispatchFormSavePath,
                FORM_UI_RESPONSE to messageFormResponse.formData
            )
        )
    }

    private fun onMessageClicked(messageFormResponse: MessageFormResponse) {
        if(draftViewModel.shouldSendDraftDataBackToDispatchStopForm(messageFormResponse)) {
            redirectToActiveDispatchIfActiveDispatchNotEmpty(messageFormResponse)
        } else {
            goToMessage(messageFormResponse)
        }
    }

    private fun redirectToActiveDispatchIfActiveDispatchNotEmpty(messageFormResponse: MessageFormResponse) {
        lifecycleScope.launch {
            val selectedDispatchId =
                appModuleCommunicator.getSelectedDispatchId("DraftFragment-onMessageClicked")
            Log.d(
                "DraftFragment",
                "selectedDispatchId in Draft Fragment onMessageClicked: $selectedDispatchId"
            )
            if (appModuleCommunicator.getCurrentWorkFlowId("MessagingViewModel.restoreSelectedDispatch")
                    .isNotEmpty()
            ) {
                DelayProvider().callDelay(DELAY_TO_AVOID_FREEZING_ANIMATION)
                lifecycleScope.launch(draftViewModel.dispatcherProvider.main()) {
                    val cid = appModuleCommunicator.doGetCid()
                    if (cid.isEmpty()) return@launch
                    if (draftViewModel.isDraftedFormOfTheActiveDispatch(messageFormResponse.dispatchFormSavePath)
                            .await().not()
                    ) {
                        goToMessage(messageFormResponse)
                        return@launch
                    }
                    val intent = draftViewModel.provideFormActivityIntent(messageFormResponse, cid.toInt()).apply {
                        `package` = context?.packageName
                    }
                    startActivity(intent)
                    activity?.finish()
                }
            } else {
                lifecycleScope.launch {
                    goToMessage(messageFormResponse)
                }
            }
        }
    }

    private fun onMultipleMessageSelectedForDeletion(selectedItemsSize: Int) {

        fragmentDraftBinding?.run {
            if (selectedItemsSize.isLessThanAndEqualTo(ZERO)) {
                cboxSelectAllDraft.isChecked = false
                imgbtnDeleteDraft.visibility = View.INVISIBLE
                draftViewModel.isDeleteImageButtonVisible = false
            } else {
                imgbtnDeleteDraft.show()
                draftViewModel.isDeleteImageButtonVisible = true
            }
        }
    }

    private fun navigateBack() {
        findNavControllerSafely()?.navigateBack(R.id.draftFragment)
    }
}
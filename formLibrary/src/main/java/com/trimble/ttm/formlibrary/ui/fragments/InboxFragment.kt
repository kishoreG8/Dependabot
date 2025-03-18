package com.trimble.ttm.formlibrary.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trimble.ttm.commons.composable.commonComposables.ComposableLifecycleOnStop
import com.trimble.ttm.commons.composable.fab.FabState
import com.trimble.ttm.commons.composable.fab.MultiFloatingActionButton
import com.trimble.ttm.commons.logger.ACTIVITY
import com.trimble.ttm.commons.logger.DEVICE_AUTH
import com.trimble.ttm.commons.logger.INBOX_LIST
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AlertDialogData
import com.trimble.ttm.commons.model.AuthenticationState
import com.trimble.ttm.commons.model.FabItem
import com.trimble.ttm.commons.utils.AUTH_DEVICE_ERROR
import com.trimble.ttm.commons.utils.UiUtils
import com.trimble.ttm.commons.utils.newConcurrentHashSet
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.InboxListAdapter
import com.trimble.ttm.formlibrary.customViews.STATE
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.FragmentInboxBinding
import com.trimble.ttm.formlibrary.model.Message
import com.trimble.ttm.formlibrary.ui.activities.FormLibraryActivity
import com.trimble.ttm.formlibrary.ui.activities.MessagingActivity
import com.trimble.ttm.formlibrary.utils.END_REACHED
import com.trimble.ttm.formlibrary.utils.FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.INBOX_FORM_RESPONSE_TYPE
import com.trimble.ttm.formlibrary.utils.INBOX_INDEX
import com.trimble.ttm.formlibrary.utils.INBOX_SCREEN_TIME
import com.trimble.ttm.formlibrary.utils.IS_REPLY_WITH_SAME
import com.trimble.ttm.formlibrary.utils.MESSAGE
import com.trimble.ttm.formlibrary.utils.NEW_FORM_VIEW_COUNT
import com.trimble.ttm.formlibrary.utils.SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ZERO
import com.trimble.ttm.formlibrary.utils.ext.findNavControllerSafely
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.navigateTo
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.isLessThanAndEqualTo
import com.trimble.ttm.formlibrary.viewmodel.InboxViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessageFormViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import com.trimble.ttm.formlibrary.widget.DriverTtsWidget
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drakeet.support.toast.ToastCompat
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class InboxFragment : Fragment(), KoinComponent {
    private val logTag = "InboxFragment"
    private lateinit var inboxListAdapter: InboxListAdapter
    private val inboxViewModel: InboxViewModel by viewModel()
    private val messageFormViewModel: MessageFormViewModel by viewModel()
    private val messagingViewModel: MessagingViewModel by sharedViewModel()
    private var deleteMessageDialog: AlertDialog? = null
    private var isScrolling = false
    private var isFABAlreadyPressed = false
    private var fragmentInboxBinding: FragmentInboxBinding? = null
    private var isInternetActive = false
    private var didMessageFetchInvokedAlready = false
    private var isGetMessagesCallInitiated = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.logLifecycle(logTag, "$logTag onCreateView")
        fragmentInboxBinding = FragmentInboxBinding.inflate(inflater, container, false)
        return fragmentInboxBinding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.logLifecycle(logTag, "$logTag onViewCreated")
        // To guarantee that it won't close the fragments after arrive this screen via HPN
        messagingViewModel.setShouldFinishFragment(false)

        handleAuthenticationProcess()

        lifecycleScope.launch(messageFormViewModel.coroutineDispatcherProvider.mainImmediate()) {
            messageFormViewModel.observeDeletedInboxMessagesASN().collect {
                if (isAdded && it.isNotEmpty()) {
                    inboxViewModel.removeDeletedMessageFromTotalMessages(
                        it,
                        inboxViewModel.getTotalMessages()
                    )
                    notifyToMsgWidget()
                }
            }
        }
        inboxViewModel.isNewInboxMessageReceived.observe(viewLifecycleOwner) {
            if (it) {
                resetAllSelectionsOnNewMessage()
            }
        }
        composeFabSetup()
        observeForNetworkConnectivityChange()
    }

    private fun composeFabSetup() {
        fragmentInboxBinding?.fabComposeView?.apply {
            setContent {
                // Integrate the FAB with FabItems into your layout
                val fabState = remember { mutableStateOf(FabState.COLLAPSED) }

                MessageFloatingActionButton(fabState)
                if (fabState.value == FabState.EXPANDED) {
                    fragmentInboxBinding?.transparentLayout?.visibility = View.VISIBLE
                } else {
                    fragmentInboxBinding?.transparentLayout?.visibility = View.INVISIBLE
                }

                fragmentInboxBinding?.transparentLayout?.setOnClickListener {
                    if (fabState.value == FabState.EXPANDED)
                        fabState.value = FabState.COLLAPSED
                }

                BackHandler(fabState.value == FabState.EXPANDED) {
                    fabState.value = FabState.COLLAPSED
                }

                ComposableLifecycleOnStop {
                    if (fabState.value == FabState.EXPANDED) {
                        fabState.value = FabState.COLLAPSED
                    }
                }
            }
        }
    }

    private fun handleAuthenticationProcess() {
        messagingViewModel.handleAuthenticationProcess(
            caller = logTag,
            onAuthenticationComplete = {
                Log.d("$DEVICE_AUTH$ACTIVITY", "Authentication Complete")
                messagingViewModel.changeAuthenticationCompletedStatus(true)
                setUpScreen()
                inboxViewModel.getAppModuleCommunicator().startForegroundService()
            },
            doAuthentication = {
                Log.d("$DEVICE_AUTH$ACTIVITY", "Authentication is not completed. Calling doAuthentication.")
                authenticate()
            },
            onAuthenticationFailed = {
                Log.e("$DEVICE_AUTH$ACTIVITY", getString(R.string.firestore_authentication_failure))
                fragmentInboxBinding?.progressView?.setErrorState(getString(R.string.firestore_authentication_failure))
            },
            onNoInternet = {
                Log.e("$DEVICE_AUTH$ACTIVITY", getString(R.string.no_internet_authentication_failed))
                fragmentInboxBinding?.progressView?.setErrorState(getString(R.string.no_internet_authentication_failed))
            }
        )
    }

    private fun authenticate() {
        messagingViewModel.authenticationState
            .observe(viewLifecycleOwner) { authenticationState ->
                when (authenticationState) {
                    is AuthenticationState.Loading -> fragmentInboxBinding?.progressView?.setProgressState(
                        getString(R.string.authenticate_progress_text)
                    )

                    is AuthenticationState.Error -> {
                        authenticationState.errorMsgStringRes.let { errorStr ->
                            fragmentInboxBinding?.progressView?.setErrorState(
                                if (errorStr == AUTH_DEVICE_ERROR) getString(
                                    R.string.device_authentication_failure
                                ) else getString(R.string.firestore_authentication_failure)
                            )
                        }
                    }

                    is AuthenticationState.FirestoreAuthenticationSuccess -> {
                        messagingViewModel.changeAuthenticationCompletedStatus(true)
                        messagingViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()
                        lifecycleScope.launch(CoroutineName(logTag)) {
                            //Used separate async job to get their work done
                            val fetchAndRegisterFcmDeviceSpecificTokenJob = async {
                                messagingViewModel.fetchAndRegisterFcmDeviceSpecificToken()
                            }


                            val getFeatureFlagUpdatesJob = async {
                                messagingViewModel.getFeatureFlagUpdates()
                            }
                            //Once all the the jobs are done, then next set of code will be executed
                            listOf(fetchAndRegisterFcmDeviceSpecificTokenJob, getFeatureFlagUpdatesJob).awaitAll()
                            messagingViewModel.startForegroundService()
                            fragmentInboxBinding?.progressView?.setProgressState(getString(R.string.loading_text))
                            setUpScreen()
                        }
                    }
                }
            }
        messagingViewModel.doAuthentication("Inbox")
    }

    private fun setUpScreen() {
        fetchMessages(true)
        fragmentInboxBinding?.progressView?.setProgressState(getString(R.string.loading_text))
        messagingViewModel.setEnableTabs()
        fragmentInboxBinding?.run {
            setupMessageList()
            observeForMessages()
            observeForLastMessageReachedState()
            observeForErrorData()
            setMessageDeletionClickListener()
            setSelectAllCheckBoxListener()
        }
    }

    private fun FragmentInboxBinding.observeForMessages() {
        inboxListAdapter.multiSelect = inboxViewModel.getSelectedItems().isNotEmpty()
        inboxViewModel.messages.observe(viewLifecycleOwner) { messageSet ->
            isGetMessagesCallInitiated = false
            inboxViewModel.deletedMessages.forEach { deletedMessage ->
                messageSet.removeIf { it == deletedMessage }
            }
            if (messageSet.isNotEmpty()) {
                if (cboxSelectAll.isVisible.not()) cboxSelectAll.visibility = View.VISIBLE
                if (paginationProgressBar.isVisible) paginationProgressBar.hide()
                Log.d(INBOX_LIST, "observeMessages")
                inboxListAdapter.updateMessageSet(messageSet)
                if (progressView.currentState == STATE.PROGRESS) progressView.setNoState()
                else progressView.visibility = View.GONE
                fragmentInboxBinding?.messageListRecyclerView?.visibility = View.VISIBLE
            } else {
                updateUIForEmptyMessages()
            }
        }
    }

    private fun resetAllSelectionsOnNewMessage() {
        inboxViewModel.setSelectedItems(newConcurrentHashSet())
        inboxViewModel.selectAllCheckedTimeStamp = 0L
        onMultipleMessageSelectedForDeletion(selectedItemsSize = ZERO)
        inboxListAdapter.notifyDataSetChanged()
        context?.let { context ->
            ToastCompat.makeText(
                context,
                context.getString(R.string.unselect_all_messages_on_new_message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateUIForEmptyMessages() {
        fragmentInboxBinding?.run {
            Log.d(INBOX_LIST, "emptyMessages")
            if (cboxSelectAll.isVisible) cboxSelectAll.visibility = View.GONE
            messageListRecyclerView.visibility = View.GONE
            progressView.show()
            progressView.setErrorState(getString(R.string.no_messages_available))
        }
    }

    private fun FragmentInboxBinding.observeForErrorData() {
        inboxViewModel.errorData.observe(viewLifecycleOwner) {
            if (paginationProgressBar.isVisible) paginationProgressBar.hide()
            if (it != END_REACHED) {
                progressView.setErrorState(it)
            }
        }
    }

    private fun FragmentInboxBinding.setMessageDeletionClickListener() {
        imgbtnDeleteInbox.setOnClickListener {
            Log.logUiInteractionInInfoLevel(logTag, "$logTag delete button clicked")
            if (cboxSelectAll.isChecked && !isInternetActive) {
                context?.let { context ->
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

    private fun FragmentInboxBinding.handleMultipleMessagesSelectDelete() {
        context?.let { context ->
            deleteMessageDialog = UiUtils.showAlertDialog(
                AlertDialogData(
                    context = context,
                    message = Utils.getInboxDeletionMessageBasedOnSelection(
                        context,
                        cboxSelectAll.isChecked,
                        inboxViewModel.getSelectedItems().size
                    ),
                    title = getString(R.string.delete_message_title),
                    positiveActionText = getString(R.string.move_to_trash),
                    negativeActionText = getString(R.string.cancel).uppercase(),
                    isCancelable = false,
                    positiveAction = {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag delete dialog positive button clicked")
                        progressView.show()
                        progressView.setStateText(getString(R.string.deleting_text))
                        if (cboxSelectAll.isChecked) handleSelectAllMessagesDelete()
                        else handleSelectAllCheckBoxUnCheck()
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

    private fun unCheckSelectAllOnItemClick() {
        if(fragmentInboxBinding?.cboxSelectAll?.isChecked == true) {
            fragmentInboxBinding?.cboxSelectAll?.isChecked = false
        }
    }


    private fun handleSelectAllMessagesDelete() {
        messageFormViewModel.inboxDeleteAllMessagesResponse.observe(viewLifecycleOwner) {
            if (it != null && it.isDeleteSuccess) {
                deletionProcessCleanUp(true)
                context?.let { context ->
                    ToastCompat.makeText(
                        context, getString(R.string.delete_success_message), Toast.LENGTH_LONG
                    ).show()
                }
                Log.d(INBOX_LIST, "EmptyingMessageSet")
                inboxListAdapter.updateMessageSet(newConcurrentHashSet())
            } else {
                context?.let { context ->
                    ToastCompat.makeText(
                        context, getString(R.string.delete_failure_message), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        messageFormViewModel.markAllTheMessagesAsDeleted()
    }

    private fun handleSelectAllCheckBoxUnCheck() {
        lifecycleScope.launch(inboxViewModel.dispatchers.io()+ CoroutineName(logTag)) {
            val messageSet = newConcurrentHashSet<Message>()
            // Storing the selectedItems which will be used for updating the firestore document
            // Storing Deleted Messages for handling inside the livedata's observer
            inboxViewModel.deletedMessages.addAll(inboxViewModel.getSelectedItems())
            messageSet.addAll(inboxViewModel.getTotalMessages())
            messageSet.removeAll(inboxViewModel.deletedMessages)
            withContext(inboxViewModel.dispatchers.mainImmediate()) {
                Log.d(INBOX_LIST,"selectAll ${messageSet.map { it.asn }}")
                inboxListAdapter.updateMessageSet(messageSet)
                deletionProcessCleanUp(false)
                context?.let { context ->
                    ToastCompat.makeText(
                        context,
                        getString(R.string.delete_success_message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (inboxViewModel.getTotalMessages().isEmpty())
                    fragmentInboxBinding?.progressView?.setErrorState(
                        getString(R.string.no_messages_available)
                    )
                else{
                    fragmentInboxBinding?.progressView?.setNoState()
                }
            }
            markMessageAsDeleted()
        }
    }

    private fun FragmentInboxBinding.setSelectAllCheckBoxListener() {
        cboxSelectAll.setOnCheckedChangeListener { compoundButton, isChecked ->
            if (compoundButton.isPressed.not()) {
                inboxViewModel.selectAllCheckedTimeStamp = 0L
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag select all checkbox checked")
                inboxListAdapter.selectAllItems()
                inboxViewModel.selectAllCheckedTimeStamp = System.currentTimeMillis()
                inboxListAdapter.notifyDataSetChanged()
            } else {
                Log.logUiInteractionInInfoLevel(logTag, "$logTag select all checkbox unchecked")
                inboxListAdapter.clearAllSelectedItems()
                inboxViewModel.selectAllCheckedTimeStamp = 0L
                Log.d(
                    INBOX_LIST,
                    "clearSelected ${inboxViewModel.getTotalMessages().map { it.asn }}"
                )
                inboxListAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun markMessageAsDeleted() {
        inboxViewModel.deletedMessages.reversed().forEach {
            messageFormViewModel.markMessageAsDeleted(
                it.asn,
                INBOX_LIST
            )
        }
    }

    private fun FragmentInboxBinding.observeForLastMessageReachedState() {
        inboxViewModel.isLastMessageReceived.observe(viewLifecycleOwner) {
            if (it && paginationProgressBar.isVisible) {
                paginationProgressBar.hide()
            }
        }
    }

    private fun deletionProcessCleanUp(clearAllMessages: Boolean) {
        //Resets selectedList items after deletion
        inboxViewModel.removeDeletedItems(inboxViewModel.getSelectedItems(),inboxViewModel.getTotalMessages(),clearAllMessages)
        inboxViewModel.setSelectedItems(newConcurrentHashSet())
        fragmentInboxBinding?.run {
            cboxSelectAll.isChecked = false
            imgbtnDeleteInbox.visibility = View.INVISIBLE
        }
        inboxViewModel.isDeleteImageButtonVisible = false
        deleteMessageDialog?.dismiss()
        deleteMessageDialog = null
        notifyToMsgWidget()
    }

    private fun notifyToMsgWidget() {
        inboxViewModel.getAppModuleCommunicator().getAppModuleApplicationScope().launch(
            inboxViewModel.dispatchers.default() +
                    CoroutineName(
                        logTag
                    )
        ) {
            context?.let {
                it.sendBroadcast(
                    Intent(
                        it,
                        DriverTtsWidget::class.java
                    ).apply {
                        action = DriverTtsWidget.DELETE_ACTION
                    }
                )
            }
        }

    }

    private fun observeForNetworkConnectivityChange() {
        (activity as? MessagingActivity)?.run {
            messagingViewModel.isNetworkAvailable.observe(viewLifecycleOwner) { isAvailable ->
                isInternetActive = isAvailable
                lifecycleScope.launch(CoroutineName(logTag)) {
                    when {
                        isAvailable -> doOnActiveInternetConnectivity()
                        else -> {
                            if (messagingViewModel.getAuthenticationProcessResult().isFirestoreAuthenticated.not())
                                fragmentInboxBinding?.progressView?.setErrorState(
                                    getString(
                                        R.string.no_internet_authentication_failed
                                    )
                                )
                        }
                    }
                }
            }
        }
    }

    private suspend fun doOnActiveInternetConnectivity() {
        messagingViewModel.getAuthenticationProcessResult().let { authenticationProcessResult ->
            if(authenticationProcessResult.isAuthenticationComplete()) return@let
            if (!authenticationProcessResult.isFirestoreAuthenticated) {
                authenticate()
                return
            } else messagingViewModel.fetchAndRegisterFcmDeviceSpecificToken()
        }
        if (inboxViewModel.messages.value.isNullOrEmpty() && didMessageFetchInvokedAlready.not())
            fetchMessages(true)
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(logTag, "$logTag onResume")
        inboxViewModel.logScreenViewEvent(INBOX_SCREEN_TIME)
        // Dismiss floating menu if it's open - mainly to HPN behavior
        // To go the top of the list if we're accessing via HPN
        if (messagingViewModel.shouldGoToListStart.value == true) {
            fragmentInboxBinding?.messageListRecyclerView?.layoutManager?.scrollToPosition(0)
        }
        messagingViewModel.setShouldGoToListStart(false)

        (activity as? MessagingActivity)?.run {
            initMessagingSectionSlider(INBOX_INDEX)
        }
        isFABAlreadyPressed = false
        //makes this toolbar visible
        activity?.supportFragmentManager?.findFragmentById(R.id.toolbar)?.view?.visibility =
            View.VISIBLE
        if (inboxViewModel.isDeleteImageButtonVisible) fragmentInboxBinding?.imgbtnDeleteInbox?.show()
        if (inboxViewModel.messages.value.isNullOrEmpty()) {
            didMessageFetchInvokedAlready = true
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(logTag, "$logTag onStop")
    }

    override fun onDestroyView() {
        Log.logLifecycle(logTag, "$logTag onDestroyView")
        fragmentInboxBinding?.messageListRecyclerView?.adapter = null
        fragmentInboxBinding?.messageListRecyclerView?.clearOnScrollListeners()
        fragmentInboxBinding = null
        deleteMessageDialog?.dismiss()
        super.onDestroyView()
    }

    private fun fetchMessages(isFirstTimeFetch: Boolean) {
        if (isGetMessagesCallInitiated.not()) {
            isGetMessagesCallInitiated = true
            inboxViewModel.getMessages(isFirstTimeFetch)
        }
    }


    private fun setupMessageList() {
        inboxListAdapter = InboxListAdapter(inboxViewModel,{
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
            fragmentInboxBinding?.run {
                messageListRecyclerView.addItemDecoration(itemDecorator)
                messageListRecyclerView.layoutManager = LinearLayoutManager(context)
                messageListRecyclerView.adapter = inboxListAdapter
            }
        }
        initRecyclerViewOnScrollListener()
    }

    private fun initRecyclerViewOnScrollListener() {
        fragmentInboxBinding?.messageListRecyclerView?.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                    isScrolling = true
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                (fragmentInboxBinding?.messageListRecyclerView?.layoutManager as LinearLayoutManager).let {
                    val firstVisibleMessagePosition = it.findFirstVisibleItemPosition()
                    val visibleMessageCount = it.childCount
                    val totalMessageCount = it.itemCount

                    if (isScrolling && (firstVisibleMessagePosition + visibleMessageCount == totalMessageCount)) {
                        isScrolling = false
                        lifecycleScope.launch(inboxViewModel.dispatchers.io() + CoroutineName(logTag)) {
                            withContext(inboxViewModel.dispatchers.main()) {
                                fragmentInboxBinding?.paginationProgressBar?.show()
                            }
                            fetchMessages(false)
                        }
                    }
                }
            }
        })
    }

    private fun onMessageClicked(message: Message) {
        Log.logUiInteractionInInfoLevel(logTag, "$logTag message clicked. ASN: ${message.asn}")
        if(inboxViewModel.showReply(message.replyFormClass, message.formId, message.replyFormId)){
            //reply with same
            findNavControllerSafely()?.navigateTo(
                R.id.messageViewPagerContainerFragment,
                R.id.action_messageViewPagerContainerFragment_to_messageReplyFragment,
                bundleOf(
                    MESSAGE to message,
                    FORM_RESPONSE_TYPE to INBOX_FORM_RESPONSE_TYPE,
                    SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH to false,
                    IS_REPLY_WITH_SAME to true,
                )
            )
        }else{
            //reply with new
            findNavControllerSafely()?.navigateTo(
                R.id.messageViewPagerContainerFragment,
                R.id.action_messageViewPagerContainerFragment_to_messageDetailFragment,
                bundleOf(MESSAGE to message, SHOULD_OPEN_MESSAGE_DETAIL_FOR_TRASH to false)
            )
        }

    }

    private fun onMultipleMessageSelectedForDeletion(selectedItemsSize: Int) {
        fragmentInboxBinding?.run {
            if (selectedItemsSize.isLessThanAndEqualTo(ZERO)) {
                cboxSelectAll.isChecked = false
                imgbtnDeleteInbox.visibility = View.INVISIBLE
                inboxViewModel.isDeleteImageButtonVisible = false
            } else {
                imgbtnDeleteInbox.show()
                inboxViewModel.isDeleteImageButtonVisible = true
            }
        }

    }

    @Composable
    fun MessageFloatingActionButton(
        floatingActionButtonState: MutableState<FabState>
    ) {
            MultiFloatingActionButton(
                listOf(
                    FabItem(
                        stringResource(id = R.string.freeform_message)
                    ),
                    FabItem(
                        stringResource(id = R.string.email_with_form)
                    )
                ), floatingActionButtonState.value, {
                    floatingActionButtonState.value = it
                }
            ) {
                when (it.label) {
                    getString(R.string.email_with_form) -> {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag new form opened from fab")
                        inboxViewModel.logNewEventWithDefaultParameters(NEW_FORM_VIEW_COUNT)
                        startActivity(Intent(requireContext(), FormLibraryActivity::class.java))
                    }
                    getString(R.string.freeform_message) -> {
                        Log.logUiInteractionInInfoLevel(logTag, "$logTag new message opened from fab")
                        findNavControllerSafely()?.navigateTo(
                            R.id.messageViewPagerContainerFragment,
                            R.id.action_messageViewPagerContainerFragment_to_vanillaMessagingFragment
                        )
                    }
                }
            }
    }
}

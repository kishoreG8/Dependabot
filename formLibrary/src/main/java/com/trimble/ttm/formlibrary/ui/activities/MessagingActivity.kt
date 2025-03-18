package com.trimble.ttm.formlibrary.ui.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.tabs.TabLayout
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.ui.BaseToolbarInteractionActivity
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.HamburgerMenuAdapter
import com.trimble.ttm.formlibrary.databinding.ActivityMessagingBinding
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.manager.FormManager
import com.trimble.ttm.formlibrary.model.HamburgerMenuItem
import com.trimble.ttm.formlibrary.ui.fragments.MessageViewPagerContainerFragment
import com.trimble.ttm.formlibrary.utils.FormUtils
import com.trimble.ttm.formlibrary.utils.INBOX_INDEX
import com.trimble.ttm.formlibrary.utils.INBOX_SHORTCUT_USE_COUNT
import com.trimble.ttm.formlibrary.utils.INVALID_INDEX
import com.trimble.ttm.formlibrary.utils.MESSAGES_MENU_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.TTS_UTTERANCE_ID
import com.trimble.ttm.formlibrary.utils.TTS_VOLUME_LEVEL
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.Utils.getIntentSendErrorString
import com.trimble.ttm.formlibrary.utils.Utils.goToFormLibraryActivity
import com.trimble.ttm.formlibrary.utils.dispatchDetailImplicitIntentAction
import com.trimble.ttm.formlibrary.utils.dispatchListImplicitIntentAction
import com.trimble.ttm.formlibrary.utils.ext.openActivityIfItsAlreadyInBackStack
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.utils.getHamburgerMenu
import com.trimble.ttm.formlibrary.utils.isNotEqualTo
import com.trimble.ttm.formlibrary.viewmodel.DraftingViewModel
import com.trimble.ttm.formlibrary.viewmodel.MessagingViewModel
import com.trimble.ttm.toolbar.ui.IconType
import com.trimble.ttm.toolbar.ui.OnFragmentInteractionListener
import com.trimble.ttm.toolbar.ui.compose.InstinctAppBar
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

@ExperimentalCoroutinesApi
class MessagingActivity :
    BaseToolbarInteractionActivity(),
    OnFragmentInteractionListener,
    KoinComponent {

    private val tag = "MessagingActivity"
    private var tts: TextToSpeech? = null
    private lateinit var hamburgerMenuAdapter: HamburgerMenuAdapter
    internal var viewPagerTabPositionToBeShownAfterScreenOpen = INVALID_INDEX
    private var lastViewPagerTabPositionShownToTheDriver = INVALID_INDEX
    private lateinit var activityMessagingBinding: ActivityMessagingBinding
    internal val messagingViewModel: MessagingViewModel by viewModel()
    private val draftingViewModel: DraftingViewModel by viewModel()
    private val formDataStoreManager: FormDataStoreManager by inject()

    private val formManager: FormManager by lazy {
        FormManager()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        intent?.let {
            if (messagingViewModel.isMessagingSectionSelectedFromDispatchScreenNavMenu.not()) {
                viewPagerTabPositionToBeShownAfterScreenOpen =
                    intent.getIntExtra(MESSAGES_MENU_TAB_INDEX, INVALID_INDEX)
                lastViewPagerTabPositionShownToTheDriver = viewPagerTabPositionToBeShownAfterScreenOpen
                messagingViewModel.isMessagingSectionSelectedFromDispatchScreenNavMenu = true
            }
        }
        activityMessagingBinding = ActivityMessagingBinding.inflate(layoutInflater)
        setContentView(activityMessagingBinding.root)
        activityMessagingBinding.toolbar.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                InstinctAppBar(toolbarViewModel)
            }
        }
        toolbarViewModel.title.postValue(getString(R.string.messaging).uppercase())
        toolbarViewModel.iconType.postValue(IconType.HAMBURGER)
        setupHamburgerMenu()
        messagingViewModel.isAuthenticationCompleted.observe(this) {  authCompleted ->
            if (authCompleted) {
                updateHotKeysMenu()
            }
        }
        updateInspectionMenu()
        messagingViewModel.isEDVIREnabled.observe(this) {
            updateInspectionMenu()
        }
        messagingViewModel.recordShortCutIconClickEvent(INBOX_SHORTCUT_USE_COUNT, intent)
        try {
            startForResult.launch(
                Intent().apply {
                    action = TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
                }
            )
        } catch (e: Exception) {
            Log.e(
                tag,
                getIntentSendErrorString(
                    this,
                    "No TTS service enable"
                ),
                e
            )
        }
        observeForNetworkConnectivityChange()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        messagingViewModel.recordShortCutIconClickEvent(INBOX_SHORTCUT_USE_COUNT, intent)
        intent.run {
            if (this.extras == null) return
            val newViewPagerTabPositionToOpen = getIntExtra(MESSAGES_MENU_TAB_INDEX, INVALID_INDEX)
            if (newViewPagerTabPositionToOpen.isNotEqualTo(INVALID_INDEX)) {
                viewPagerTabPositionToBeShownAfterScreenOpen = newViewPagerTabPositionToOpen
            } else {
                Log.e(
                    tag,
                    Utils.getIntentDataErrorString(
                        this@MessagingActivity,
                        "newViewPagerTabPositionToOpen",
                        "Int",
                        " a not valid value. value equals to $INVALID_INDEX",
                        intent.action ?: "onNewIntent"
                    )
                )
            }
            try {
                messagingViewModel.setCurrentTabPosition(viewPagerTabPositionToBeShownAfterScreenOpen)
                lastViewPagerTabPositionShownToTheDriver = viewPagerTabPositionToBeShownAfterScreenOpen

                val messageFragment = supportFragmentManager.findFragmentById(R.id.message_nav_host_fragment)

                // This code allow RM to correctly redirects when a HPN message arrives
                messageFragment?.childFragmentManager?.fragments?.let {
                    // We must assure that we are only finishing the messages fragments (draft/reply/detail)
                    if (it.first() !is MessageViewPagerContainerFragment) {
                        messagingViewModel.setShouldFinishFragment(true)

                        // This will hide the virtual keyboard of draft messages and reply
                        this@MessagingActivity.currentFocus?.let { view ->
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.hideSoftInputFromWindow(view.windowToken, 0)
                        }
                    } else {
                        // It'll prevent the inbox to close since the observers are still on and can trigger onBackPressed
                        messagingViewModel.setShouldFinishFragment(false)
                    }

                    // Will redirect the inbox to the list start
                    messagingViewModel.setShouldGoToListStart(true)
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception in onNewIntent", e)
            }
        }
    }

    private fun updateInspectionMenu() {
        hamburgerMenuAdapter.showInspectionInHamburgerMenu(lifecycleScope)
    }

    override fun onStart() {
        openActivityIfItsAlreadyInBackStack(PreviewImageActivity::class.java.name)
        openActivityIfItsAlreadyInBackStack(ImportImageUriActivity::class.java.name)
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
        activityMessagingBinding.toolbar.visibility = View.VISIBLE
        onBackPressedDispatcher.addCallback(this, onBackButtonPressCallback)
        lifecycleScope.launch(
            messagingViewModel.defaultDispatcherProvider.main() + CoroutineName(tag)
        ) {
            formDataStoreManager.setValue(FormDataStoreManager.IS_DRAFT_VIEW, false)
            draftingViewModel.draftProcessFinished.collect {
                Log.i(tag, "draftProcessFinished: has to draft $it")
                // show the toast when the draft process is finished
                if (it && draftingViewModel.showDraftMessage) {
                    this@MessagingActivity.showToast(
                        getString(R.string.draft_saved)
                    )
                    draftingViewModel.showDraftMessage = false
                }
            }
        }
        supportActionBar?.hide()
        // Dismiss drawer when opening from a HPN
        activityMessagingBinding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun observeForNetworkConnectivityChange() {
        lifecycleScope.launch(CoroutineName(tag)) {
            messagingViewModel.listenToNetworkConnectivityChange().collectLatest { isAvailable ->
                    messagingViewModel.changeNetworkAvailabilityStatus(isAvailable)
            }
        }
    }

    private fun setupHamburgerMenu() {
        activityMessagingBinding.hamburgerMenuLayout.tvHeaderTitle.apply {
            activityMessagingBinding.hamburgerMenuLayout.tvHeaderTitle.setText(R.string.base_app_name)
        }
        hamburgerMenuAdapter = HamburgerMenuAdapter(this)
        hamburgerMenuAdapter.hamburgerMenuList = getHamburgerMenu()
        activityMessagingBinding.hamburgerMenuLayout.hamburgerMenuList.setOnGroupClickListener { _, _, groupPosition, _ ->
            val group = hamburgerMenuAdapter.getGroup(groupPosition) as? HamburgerMenuItem
            Log.logUiInteractionInInfoLevel(
                tag,
                "$tag ${group?.menuItemStringRes?.let { strRes -> this.resources.getString(strRes) }} menu item clicked from hamburger menu"
            )
            when (group?.menuItemStringRes) {
                R.string.menu_messaging -> {
                    findViewById<TabLayout>(R.id.messageTabLayout).getTabAt(
                        INBOX_INDEX
                    )?.select()
                }
                R.string.menu_form_library, R.string.menu_hot_keys -> goToFormLibraryActivity(context = this, selectedMenuGroupIndex = group.menuItemStringRes, hotKeysMenuGroupIndex = R.string.menu_hot_keys)
                R.string.menu_inspections -> Intent(
                    this,
                    EDVIRInspectionsActivity::class.java
                ).also {
                    startActivity(it)
                }
                R.string.menu_trip_list -> {
                    with(Intent()) {
                        action = dispatchListImplicitIntentAction
                        `package` = applicationContext.packageName
                        startActivity(this)
                        finish()
                    }
                }
                R.string.menu_stop_list -> {
                    goToStopList()
                }
            }
            activityMessagingBinding.drawerLayout.closeDrawer(GravityCompat.START)
            false
        }
        activityMessagingBinding.hamburgerMenuLayout.hamburgerMenuList.setAdapter(
            hamburgerMenuAdapter
        )
        updateMenuItems()
    }

    private fun goToStopList() {
        messagingViewModel.restoreSelectedDispatch(executeActionInDispatch = {
            with(Intent()) {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                `package` = applicationContext.packageName
                action = dispatchDetailImplicitIntentAction
                startActivity(this)
            }
            finish()
        }, executeActionWithoutDispatch = {
            lifecycleScope.launch(
                CoroutineName(tag) + messagingViewModel.defaultDispatcherProvider.main()
            ) {
                showToast(R.string.err_loading_messages)
            }
        })
    }

    private fun updateMenuItems() {
        lifecycleScope.launch(
            CoroutineName(tag) + messagingViewModel.defaultDispatcherProvider.main()
        ) {
            addOrRemoveStopsButton()
            addOrRemoveTripsButtom()
        }
    }

    private suspend fun addOrRemoveTripsButtom() {
        messagingViewModel.hasOnlyOneTripOnList().collect { hasOnlyOne ->
            if (hasOnlyOne && messagingViewModel.hasActiveDispatch()) {
                hamburgerMenuAdapter.removeGroupItem(
                    R.string.menu_trip_list
                )
            } else {
                hamburgerMenuAdapter.addGroupItem(
                    R.string.menu_trip_list,
                    hamburgerMenuAdapter.groupCount
                )
            }
        }
    }

    private suspend fun addOrRemoveStopsButton() {
        if (messagingViewModel.hasActiveDispatch()) {
            hamburgerMenuAdapter.addGroupItem(
                R.string.menu_stop_list,
                hamburgerMenuAdapter.groupCount
            )
        } else {
            hamburgerMenuAdapter.removeGroupItem(
                R.string.menu_stop_list
            )
        }
    }

    private fun updateHotKeysMenu() {
        messagingViewModel.isHotKeysAvailable.observe(this) {
            hamburgerMenuAdapter.showHotKeysHamburgerMenu(it)
        }
        messagingViewModel.canShowHotKeysMenu()
    }

    fun readTheList(speakList: ArrayList<String>) {
        // Sets specified volume level for TTS in AudioManager
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).let {
            it.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (TTS_VOLUME_LEVEL * it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) / 100,
                0
            )
        }
        // To remove old TTS data
        tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, TTS_UTTERANCE_ID)
        speakList.forEach {
            tts?.speak(it, TextToSpeech.QUEUE_ADD, null, TTS_UTTERANCE_ID)
        }
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                tts = null
                tts = TextToSpeech(applicationContext) {
                    lifecycleScope.launch(CoroutineName(tag)) {
                        Mutex().withLock {
                            if (it == TextToSpeech.SUCCESS) {
                                tts?.setLanguage(Locale.getDefault()).let { languageSelectStatus ->
                                    if (languageSelectStatus == TextToSpeech.LANG_MISSING_DATA) {
                                        showToast(R.string.tts_language_data_missing)
                                    }
                                }
                            } else {
                                showToast(R.string.tts_init_failed)
                            }
                        }
                    }
                }
            } else {
                try {
                    startActivity(
                        Intent().apply {
                            action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                        }
                    )
                } catch (e: Exception) {
                    Log.e(
                        tag,
                        getIntentSendErrorString(
                            this,
                            "No TTS service enable"
                        ),
                        e
                    )
                }
            }
        }

    override fun onPause() {
        super.onPause()
        onBackButtonPressCallback.remove()
        try {
            messagingViewModel.setCurrentTabPosition(findViewById<TabLayout>(R.id.messageTabLayout).selectedTabPosition)
        } catch (e: Exception) {
            // Ignored
        }
    }

    override suspend fun onAllPermissionsGranted() {
        // Ignore
    }

    internal fun initMessagingSectionSlider(indexToPoint: Int) {
        viewPagerTabPositionToBeShownAfterScreenOpen = indexToPoint
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    formManager.focusNextEditableFormField(
                        this,
                        messagingViewModel.mapFieldIdsToViews
                    )
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        super.dispatchTouchEvent(ev)
        val v: View? = currentFocus
        /**
         * this method hides the keyboard when the current focus is on EditText.
         * if another edit text is clicked the focus shifts to that EditText and pops up the keyboard.
         * if the focus is not on EditText the keyboard does not popup.
         */
        FormUtils.checkIfEditTextIsFocusedAndHideKeyboard(this, v, ev)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(
            findNavController(R.id.message_nav_host_fragment),
            AppBarConfiguration.Builder(findNavController(R.id.message_nav_host_fragment).graph)
                .build()
        )
    }

    override fun onToolbarNavigationIconClicked(iconType: IconType) {
        with(activityMessagingBinding) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                Log.logUiInteractionInInfoLevel(tag, "$tag Hamburger icon closed")
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                Log.logUiInteractionInInfoLevel(tag, "$tag Hamburger icon opened")
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    private val onBackButtonPressCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Log.logUiInteractionInInfoLevel(tag, "$tag onBackPressed")
            if (::activityMessagingBinding.isInitialized.not() || activityMessagingBinding.drawerLayout.isDrawerOpen(GravityCompat.START).not()) {
                if (supportFragmentManager.isStateSaved.not()) finish()
                return
            }
            activityMessagingBinding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private fun destroyTTS() {
        tts?.apply {
            stop()
            shutdown()
            tts = null
        }
    }

    fun stopTTS() {
        tts?.stop()
    }

    fun lockDrawer() {
        activityMessagingBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    fun unlockDrawer() {
        activityMessagingBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            toolbarViewModel.configurationChanged()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
    }

    override fun onDestroy() {
        Log.logLifecycle(tag, "$tag onDestroy")
        destroyTTS()
        super.onDestroy()
    }
}

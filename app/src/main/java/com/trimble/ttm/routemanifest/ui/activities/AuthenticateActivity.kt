package com.trimble.ttm.routemanifest.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.ACTIVITY
import com.trimble.ttm.commons.logger.DEVICE_AUTH
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.model.AuthenticationState
import com.trimble.ttm.commons.ui.BaseToolbarInteractionActivity
import com.trimble.ttm.commons.utils.AUTH_DEVICE_ERROR
import com.trimble.ttm.commons.utils.WORKFLOW_SHORTCUT_USE_COUNT
import com.trimble.ttm.formlibrary.customViews.STATE
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.viewmodel.AuthenticationViewModel
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.routemanifest.databinding.ActivityAuthenticateBinding
import com.trimble.ttm.routemanifest.service.RouteManifestForegroundService
import com.trimble.ttm.routemanifest.usecases.TripPanelUseCase
import com.trimble.ttm.routemanifest.utils.ext.startForegroundServiceIfNotStartedPreviously
import com.trimble.ttm.toolbar.ui.OnFragmentInteractionListener
import com.trimble.ttm.toolbar.ui.compose.InstinctAppBar
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AuthenticateActivity : BaseToolbarInteractionActivity(),
    OnFragmentInteractionListener {

    private val tag = "AuthenticateActivity"
    private val authenticationViewModel: AuthenticationViewModel by viewModel()
    // Inject this in VM if there is a requirement of VM in app module for this activity
    private val tripPanelUseCase: TripPanelUseCase by inject()
    private lateinit var activityAuthenticateBinding: ActivityAuthenticateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        activityAuthenticateBinding = ActivityAuthenticateBinding.inflate(layoutInflater)
        setContentView(activityAuthenticateBinding.root)
        activityAuthenticateBinding.toolbar.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                InstinctAppBar(toolbarViewModel)
            }
        }
        toolbarViewModel.title.postValue(getString(R.string.app_name).uppercase())
        observeForNetworkConnectivityChange()
        tripPanelUseCase.dismissTripPanelOnLaunch()
        authenticationViewModel.authenticationState.observe(this) { authenticationState ->
            when (authenticationState) {
                is AuthenticationState.Loading -> activityAuthenticateBinding.progressErrorView.setProgressState(
                    getString(R.string.authenticate_progress_text)
                )

                is AuthenticationState.Error -> {
                    authenticationState.errorMsgStringRes.let { errorStr ->
                        activityAuthenticateBinding.progressErrorView.setErrorState(
                            if (errorStr == AUTH_DEVICE_ERROR) getString(
                                R.string.device_authentication_failure
                            ) else getString(R.string.firestore_authentication_failure)
                        )
                        Log.e(
                            "$DEVICE_AUTH$ACTIVITY", errorStr, null,
                            "intent action" to intent.action
                        )
                    }
                }

                is AuthenticationState.FirestoreAuthenticationSuccess -> {
                    lifecycleScope.launch(CoroutineName(tag)) {
                        authenticationViewModel.fetchAuthenticationPreRequisites().await()
                        this@AuthenticateActivity.startForegroundServiceIfNotStartedPreviously(RouteManifestForegroundService::class.java)
                        redirectOnAuthenticationSuccess()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
    }

    override fun onStop() {
        super.onStop()
        Log.logLifecycle(tag, "$tag onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }

    private fun observeForNetworkConnectivityChange() {
        lifecycleScope.launch(authenticationViewModel.defaultDispatcherProvider.io()) {
            authenticationViewModel.listenToNetworkConnectivityChange().collect { isInternetAvailable ->
                if (isInternetAvailable && activityAuthenticateBinding.progressErrorView.currentState == STATE.ERROR)
                    authenticationViewModel.doAuthentication("AuthenticationActivity_ObserveForNetworkConnectivityChange")
            }
        }
    }

    override suspend fun onAllPermissionsGranted() {
        doAfterAllRuntimePermissionsHandled()
    }

    private suspend fun doAfterAllRuntimePermissionsHandled() {
        authenticationViewModel.handleAuthenticationProcess(
            caller = tag,
            onAuthenticationComplete = {
                Log.d("$DEVICE_AUTH$ACTIVITY", "Authentication Complete")
                lifecycleScope.launch {
                    authenticationViewModel.checkEDVIRAvailabilityAndUpdateHamburgerMenuVisibility()
                    redirectOnAuthenticationSuccess()
                }
            },
            doAuthentication = {
                Log.d("$DEVICE_AUTH$ACTIVITY", "Authentication is not completed. Calling doAuthentication.")
                authenticationViewModel.doAuthentication("AuthenticationActivity_DoAfterAllPermissionsGranted")
            },
            onAuthenticationFailed = {
                Log.e("$DEVICE_AUTH$ACTIVITY", getString(com.trimble.ttm.formlibrary.R.string.firestore_authentication_failure))
                activityAuthenticateBinding.progressErrorView.setErrorState(getString(R.string.firestore_authentication_failure))
            },
            onNoInternet = {
                Log.e("$DEVICE_AUTH$ACTIVITY", getString(com.trimble.ttm.formlibrary.R.string.no_internet_authentication_failed))
                activityAuthenticateBinding.progressErrorView.setErrorState(getString(R.string.no_internet_authentication_failed))
            }
        )
    }

    private suspend fun redirectOnAuthenticationSuccess() =
        withContext(lifecycleScope.coroutineContext + authenticationViewModel.defaultDispatcherProvider.main()) {
            activityAuthenticateBinding.progressErrorView.setNoState()
            authenticationViewModel.recordShortcutClickEvent(eventName = WORKFLOW_SHORTCUT_USE_COUNT, referrer = referrer.toString(), intent = intent)
            launchDispatchListScreen()
        }

    private fun launchDispatchListScreen() {
        tripPanelUseCase.dismissTripPanelOnLaunch()
        Intent(
            this@AuthenticateActivity,
            DispatchListActivity::class.java
        ).let {
            activityAuthenticateBinding.progressErrorView.setNoState()
            startActivity(it)
            finish()
        }
    }
}
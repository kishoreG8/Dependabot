package com.trimble.ttm.routemanifest.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.DefaultDispatcherProvider
import com.trimble.ttm.commons.utils.DispatcherProvider
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.CLOSE_FIRST
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.IS_MANDATORY_INSPECTION
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager.Companion.SHOW_INSPECTION_ALERT_DIALOG
import com.trimble.ttm.formlibrary.repo.DraftingRepo
import com.trimble.ttm.formlibrary.utils.EMPTY_STRING
import com.trimble.ttm.formlibrary.utils.INBOX_INDEX
import com.trimble.ttm.formlibrary.utils.INTENT_CLASS_NAME
import com.trimble.ttm.formlibrary.utils.IS_FOR_NEW_MESSAGE
import com.trimble.ttm.formlibrary.utils.IS_FOR_NEW_TRIP
import com.trimble.ttm.formlibrary.utils.LAUNCH_MODE
import com.trimble.ttm.formlibrary.utils.MESSAGES_MENU_TAB_INDEX
import com.trimble.ttm.formlibrary.utils.NOTIFICATION_DISPATCH_DATA
import com.trimble.ttm.formlibrary.utils.SCREEN
import com.trimble.ttm.routemanifest.R
import com.trimble.ttm.commons.preferenceManager.DataStoreManager
import com.trimble.ttm.routemanifest.model.Dispatch
import com.trimble.ttm.routemanifest.utils.DISPATCH_ID_TO_RENDER
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class NotificationRedirectionActivity : AppCompatActivity(), KoinComponent {

    private val tag = "NotificationRedirectionActivity"

    private val formDataStoreManager: FormDataStoreManager by inject()
    private val dataStoreManager: DataStoreManager by inject()
    private val draftingRepo: DraftingRepo by inject()
    private val coroutineDispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_redirection)
    }

    override fun onStart() {
        super.onStart()
        Log.logLifecycle(tag, "$tag onStart")
        lifecycleScope.launch(CoroutineName(tag) + coroutineDispatcherProvider.io()) {
            val isMandatoryInspection =
                formDataStoreManager.getValue(IS_MANDATORY_INSPECTION, false)

            // If a message app notification is received isForNewMessage is set to true
            var isForNewMessage = false
            intent?.let {
                isForNewMessage = intent.getBooleanExtra(IS_FOR_NEW_MESSAGE, false)
            }

            // If we are not on a mandatory inspection, we retrieve the information from the previous intent
            // When a New Message HPN is received user is redirected to Messaging Screen
            if (!isMandatoryInspection) {
                // This is to assure that the memory will be released before we send the other intent
                delay(100)
                intent?.let {
                    val dispatch = getDispatch()
                    val screen = intent.getIntExtra(SCREEN, -1)
                    val destinationClass = intent.getStringExtra(INTENT_CLASS_NAME)
                    val launchMode = intent.getIntExtra(LAUNCH_MODE, -1)

                    // If we don't receive a destination class, this will prevent the app from crashing
                    destinationClass?.let {
                        var cls: Class<*>? = null
                        cls = Class.forName(it)
                        // we store a flag to draft the next form
                        // We create the new intent and redirect the user properly
                        val activeDispatchId = dataStoreManager.getValue(DataStoreManager.ACTIVE_DISPATCH_KEY, EMPTY_STRING)
                        val newIntent = createIntent(
                            dispatch,
                            screen,
                            isForNewMessage,
                            launchMode,
                            cls,
                            activeDispatchId
                        )
                        //check if we need to draft if we are in a draft view or not
                        if (
                            formDataStoreManager.getValue(
                                FormDataStoreManager.IS_DRAFT_VIEW, false
                            )
                        ) {
                            //we need to draft first. Send a close action to start the draft process
                            formDataStoreManager.setValue(CLOSE_FIRST, true)
                            //start a listener to know when to close this activity and redirect the intent
                            draftingRepo.draftProcessFinished.safeCollect(javaClass.name) {
                                //make a delay to let the current activities or fragments got close
                                delay(200)
                                application.startActivity(newIntent)
                                finish()
                            }
                        } else {
                            //we don't need to draft at all, then send the redirection intent normally
                            application.startActivity(newIntent)
                            finish()
                        }
                    } ?: finish()
                }
            } else {
                // If we are on a mandatory inspection, we show the dialog that says that the user can't leave the inspection
                formDataStoreManager.setValue(SHOW_INSPECTION_ALERT_DIALOG, true)
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }

    private fun createIntent(
        dispatch: Dispatch?, screen: Int?,
        isForNewMessage: Boolean, launchMode: Int, destinationClass: Class<*>, activeDispatchId : String
    ): Intent {
        Intent(application, destinationClass).apply {
            flags = launchMode
            putExtra(DISPATCH_ID_TO_RENDER, activeDispatchId)
            dispatch?.let {
                putExtra(NOTIFICATION_DISPATCH_DATA, it)
                putExtra(IS_FOR_NEW_TRIP, true)
            }
            if (isForNewMessage) {
                putExtra(MESSAGES_MENU_TAB_INDEX, INBOX_INDEX)
                putExtra(SCREEN, screen)
            }
        }.also {
            return it
        }
    }

    private fun getDispatch():  Dispatch? {
        val dispatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NOTIFICATION_DISPATCH_DATA, Dispatch::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NOTIFICATION_DISPATCH_DATA)
        }
        return dispatch
    }

}
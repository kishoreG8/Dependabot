package com.trimble.ttm.formlibrary.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.adapter.UserListAdapter
import com.trimble.ttm.formlibrary.customViews.setErrorState
import com.trimble.ttm.formlibrary.customViews.setNoState
import com.trimble.ttm.formlibrary.customViews.setProgressState
import com.trimble.ttm.formlibrary.databinding.ActivityContactListBinding
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.utils.IS_HOME_PRESSED
import com.trimble.ttm.formlibrary.utils.SELECTED_USERS_KEY
import com.trimble.ttm.formlibrary.utils.Utils
import com.trimble.ttm.formlibrary.utils.ext.hide
import com.trimble.ttm.formlibrary.utils.ext.setDebounceClickListener
import com.trimble.ttm.formlibrary.utils.ext.show
import com.trimble.ttm.formlibrary.utils.ext.showToast
import com.trimble.ttm.formlibrary.utils.isNull
import com.trimble.ttm.formlibrary.viewmodel.ContactListViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class ContactListActivity : AppCompatActivity(), KoinComponent {

    private val tag = "ContactListActivity"
    private val contactListViewModel: ContactListViewModel by viewModel()
    private lateinit var userListAdapter: UserListAdapter
    private lateinit var activityContactListBinding: ActivityContactListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.logLifecycle(tag, "$tag onCreate")
        activityContactListBinding = ActivityContactListBinding.inflate(layoutInflater)
        setContentView(activityContactListBinding.root)
        setupToolbar()
        setupContactList()
        if (savedInstanceState.isNull()) cacheSelectedUsers()
        observeUsers()
        observeForUserFetchError()
        fetchContacts()
    }

    private fun observeUsers() {
        activityContactListBinding.uiStateView.setProgressState(getString(R.string.loading_text))
        contactListViewModel.users.observe(this) { userSet ->
            if (userSet.isNotEmpty()) {
                activityContactListBinding.btnSave.show()
                userListAdapter.userSet = userSet
                userListAdapter.notifyDataSetChanged()
            }
            activityContactListBinding.uiStateView.setNoState()
        }
    }

    private fun observeForUserFetchError() {
        contactListViewModel.errorData.observe(this) {
            lifecycleScope.safeLaunch(CoroutineName("$tag User fetching error process")) {
                if (userListAdapter.userSet.isEmpty()) {
                    activityContactListBinding.uiStateView.setErrorState(it)
                    activityContactListBinding.btnSave.hide()
                } else {
                    if (userListAdapter.userSet.isEmpty())
                        activityContactListBinding.uiStateView.setErrorState(getString(R.string.no_contacts_available))
                }
                activityContactListBinding.btnSave.show()
            }
        }
    }

    private fun fetchContacts() {
        lifecycleScope.safeLaunch(
            CoroutineName("$tag Fetch contacts") +
                    Dispatchers.IO +
                    SupervisorJob()
        ) {
            contactListViewModel.getContacts()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::userListAdapter.isInitialized && userListAdapter.getUsersList().first
                .isNotEmpty()
        ) contactListViewModel.cacheSelectedUsers(userListAdapter.getUsersList().first as ArrayList<User>)
    }

    private fun cacheSelectedUsers() {
        intent?.extras?.let { bundle ->
            bundle.getParcelableArrayList<User>(SELECTED_USERS_KEY)?.let { selectedUsers ->
                if (selectedUsers.isNotEmpty())
                    contactListViewModel.cacheSelectedUsers(selectedUsers)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(activityContactListBinding.toolbar)
        activityContactListBinding.tvToolbarTitle.text = getString(R.string.select_contacts)
        activityContactListBinding.tvToolbarTitle.setTextColor(
            ContextCompat.getColor(
                applicationContext,
                R.color.textColor
            )
        )
        with(activityContactListBinding) {
            btnSave.hide()
            ivCancel.setDebounceClickListener { finish() }
            btnSave.setOnClickListener {
                saveContacts(true)
            }
        }

    }

    private fun saveContacts(isDoneClicked: Boolean = false) {
        if (::userListAdapter.isInitialized) {
            Log.logUiInteractionInNoticeLevel(tag, "$tag done or cancel button clicked. isDoneClicked: $isDoneClicked")
            if (userListAdapter.getUsersList().first.isNotEmpty() || (userListAdapter.getUsersList().first.isEmpty() && !isDoneClicked)) {
                contactListViewModel.updateSelectedUsersList(userListAdapter.getUsersList().third, userListAdapter.getUsersList().second)
                try{
                    setResult(Activity.RESULT_OK, Intent().apply {
                        putExtras(Bundle().apply {
                            this.putParcelableArrayList(
                                SELECTED_USERS_KEY,
                                ArrayList(contactListViewModel.selectedUsers.toList()) as ArrayList<out Parcelable>
                            )
                        })
                        putExtra(IS_HOME_PRESSED, !isDoneClicked)
                    })
                }catch (e:Exception){
                    Log.e(
                        ContactListActivity::class.java.simpleName,
                        Utils.getIntentSendErrorString(
                            this,
                            "No contacts avialable"
                        ),
                        e
                    )
                }
                finish()
            } else {
                if (isDoneClicked)
                    showToast(getString(R.string.select_a_recipient))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.logLifecycle(tag, "$tag onResume")
    }

    override fun onStop() {
        Log.logLifecycle(tag, "$tag onStop")
        saveContacts()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.logLifecycle(tag, "$tag onDestroy")
    }

    private fun setupContactList() {
        userListAdapter = UserListAdapter()
        val itemDecorator = DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
            .also { itemDecoration ->
                ContextCompat.getDrawable(this, R.drawable.divider)?.let { drawable ->
                    itemDecoration.setDrawable(drawable)
                }
            }
        activityContactListBinding.userListView.addItemDecoration(itemDecorator)
        activityContactListBinding.userListView.layoutManager = LinearLayoutManager(this)
        activityContactListBinding.userListView.adapter = userListAdapter
    }
}
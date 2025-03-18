package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.commons.utils.ext.safeLaunch
import com.trimble.ttm.formlibrary.R
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.usecases.ContactsUseCase
import com.trimble.ttm.formlibrary.utils.toSafeLong
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.catch

class ContactListViewModel(
    private val application: Application,
    private val contactsUseCase: ContactsUseCase,
    private val appModuleCommunicator: AppModuleCommunicator
) : NetworkConnectivityListenerViewModel(appModuleCommunicator) {
    private val tag = "ContactListVM"
    private val _users = MutableLiveData<MutableSet<User>>()
    val users: LiveData<MutableSet<User>> = _users
    private val _errorData = MutableLiveData<String>()
    val errorData: LiveData<String> = _errorData
    var selectedUsers = mutableSetOf<User>()
    var isDataFetchInProgress = false

    init {
        appModuleCommunicator.getAppModuleApplicationScope().safeLaunch(CoroutineName("$tag Initialization")) {
            contactsUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(
                appModuleCommunicator.doGetCid(),
                appModuleCommunicator.doGetObcId(),
                this,
                tag
            )
        }
    }

    override fun onNetworkConnectivityChange(status: Boolean) {
        if (status.not()) setDataFetchProgressStatus(false)
    }

    suspend fun getContacts() {
        if (appModuleCommunicator.doGetCid().isNotEmpty() && appModuleCommunicator.doGetObcId().isNotEmpty()) {
            viewModelScope.safeLaunch(CoroutineName("$tag Get contacts")) {
                contactsUseCase.getContactListFlow()
                    .catch {
                        setDataFetchProgressStatus(false)
                        _errorData.postValue(application.getString(R.string.unable_to_fetch_contacts))
                    }
                    .safeCollect("$tag Get contacts") { userSet ->
                        setDataFetchProgressStatus(false)
                        if (userSet.isNotEmpty()) {
                            if (selectedUsers.isNotEmpty()) {
                                userSet.forEach { user ->
                                    selectedUsers.find { selectedUser -> selectedUser.uID == user.uID }
                                        ?.let {
                                            user.isSelected = true
                                        }
                                }
                            }
                            _users.postValue(contactsUseCase.sortUsersAlphabetically(userSet))
                        } else
                            _errorData.postValue(application.getString(R.string.no_contacts_available))
                    }
            }
            viewModelScope.safeLaunch(CoroutineName("$tag Get contacts")) {
                if (isDataFetchInProgress.not()) {
                    setDataFetchProgressStatus(true)
                    contactsUseCase.getContacts(
                        appModuleCommunicator.doGetCid(),
                        appModuleCommunicator.doGetObcId().toSafeLong()
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        setDataFetchProgressStatus(false)
        contactsUseCase.resetValueInContactRepo()
    }

    private fun setDataFetchProgressStatus(status: Boolean) {
        isDataFetchInProgress = status
    }

    fun cacheSelectedUsers(selectedUsers: ArrayList<User>) {
        this.selectedUsers.clear()
        this.selectedUsers.addAll(selectedUsers)
    }

    fun updateSelectedUsersList(unCheckedUsers: List<User>, checkedUsers: MutableSet<User>): MutableSet<User> {
        selectedUsers.removeIf { alreadyAvailableUser ->
            unCheckedUsers.any { unCheckedUser ->
                unCheckedUser.uID == alreadyAvailableUser.uID
            }
        }
        selectedUsers.addAll(checkedUsers)
        selectedUsers = selectedUsers.distinctBy { it.uID }.toMutableSet()
        return selectedUsers
    }
}
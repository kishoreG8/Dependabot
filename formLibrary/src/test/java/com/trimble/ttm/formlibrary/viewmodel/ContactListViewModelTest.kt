package com.trimble.ttm.formlibrary.viewmodel

import android.app.Application
import android.content.Context
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.formlibrary.CoroutineTestRuleWithMainUnconfinedDispatcher
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.usecases.ContactsUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContactListViewModelTest {

    @RelaxedMockK
    private lateinit var application: Application
    private lateinit var formDataStoreManager: FormDataStoreManager
    private lateinit var contactListViewModel: ContactListViewModel
    @RelaxedMockK
    private lateinit var context: Context
    @MockK
    private lateinit var contactsUseCase: ContactsUseCase

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @get:Rule
    var coroutinesTestRule = CoroutineTestRuleWithMainUnconfinedDispatcher()

    @MockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private val testScope = TestScope()


    @Before
    fun setup() {
        MockKAnnotations.init(this)
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns testScope
        contactListViewModel = ContactListViewModel(application, contactsUseCase, appModuleCommunicator = appModuleCommunicator)
        formDataStoreManager = spyk(FormDataStoreManager(context))
        every { context.filesDir } returns temporaryFolder.newFolder()
        coEvery { appModuleCommunicator.doGetCid() } returns "10119"
        coEvery { appModuleCommunicator.doGetObcId() } returns "123442"
    }

    @Test
    fun `verify get contacts list is not called if CID is empty`() = runTest {    //NOSONAR

        coEvery { appModuleCommunicator.doGetCid() } returns ""
        contactListViewModel.getContacts()

        coVerify(exactly = 0) {
            contactsUseCase.getContactListFlow()
        }
    }

    @Test
    fun `verify get contacts list is not called if OBC ID is empty`() = runTest {    //NOSONAR

        coEvery { appModuleCommunicator.doGetObcId() } returns ""
        contactListViewModel.getContacts()

        coVerify(exactly = 0) {
            contactsUseCase.getContactListFlow()
        }
    }

    @Test
    fun `verify get contacts list is called but empty set`() = runTest {
        coEvery { contactsUseCase.sortUsersAlphabetically(any()) } returns mutableSetOf()
        coEvery { contactsUseCase.getContactListFlow() } returns flow { emit(mutableSetOf()) }
        contactListViewModel.getContacts()

        coVerify(exactly = 0) {
            contactsUseCase.sortUsersAlphabetically(any())
        }
    }

    @Test
    fun `verify get contacts list is called`() = runTest {
        val users = mutableSetOf<User>()
        with(mutableSetOf<User>()) {
            add(User(uID = 1, "user1", "user1@trimble.com"))
            add(User(uID = 2, "user2", "user2@trimble.com"))
            add(User(uID = 3, "user3", "user3@trimble.com"))
            add(User(uID = 4, "user4", "user4@trimble.com"))
            users.addAll(this)
        }

        coEvery { contactsUseCase.getContacts(any(), any()) } just runs
        coEvery { contactsUseCase.sortUsersAlphabetically(any()) } returns users
        coEvery { contactsUseCase.getContactListFlow() } returns flow { emit(users) }
        contactListViewModel.getContacts()

        coVerify(exactly = 1) {
            contactsUseCase.sortUsersAlphabetically(any())
            contactsUseCase.getContacts(any(), any())
        }
    }

    @Test
    fun `verify network connectivity change`() {    //NOSONAR
        contactListViewModel.onNetworkConnectivityChange(false)
        assertFalse(contactListViewModel.isDataFetchInProgress)
    }

    @Test
    fun `verify cache selected users`() {    //NOSONAR
        with(arrayListOf<User>()) {
            add(User(uID = 0, "user1", "user1@trimble.com"))
            add(User(uID = 1, "user2", "user2@trimble.com"))
            contactListViewModel.cacheSelectedUsers(this)
            assertEquals(this.size, contactListViewModel.selectedUsers.size)
        }
    }

    @Test
    fun `verify users list is getting updated`() {    //NOSONAR
        with(mutableSetOf<User>()) {
            add(User(uID = 1, "user1", "user1@trimble.com"))
            add(User(uID = 2, "user2", "user2@trimble.com"))
            add(User(uID = 3, "user3", "user3@trimble.com"))
            add(User(uID = 4, "user4", "user4@trimble.com"))
            contactListViewModel.selectedUsers.addAll(this)
        }
        val unCheckedUsers = mutableSetOf<User>().also {
            it.add(User(uID = 1, "user1", "user1@trimble.com"))
            it.add(User(uID = 2, "user2", "user2@trimble.com"))
        }
        val newlyCheckedUsers = mutableSetOf<User>().also {
            it.add(User(uID = 5, "user5", "user5@trimble.com"))
            it.add(User(uID = 6, "user6", "user6@trimble.com"))
        }
        val updatedListOfUsers = mutableSetOf<User>().also {
            it.add(User(uID = 3, "user3", "user3@trimble.com"))
            it.add(User(uID = 4, "user4", "user4@trimble.com"))
            it.add(User(uID = 5, "user5", "user5@trimble.com"))
            it.add(User(uID = 6, "user6", "user6@trimble.com"))
        }
        assertEquals(
            updatedListOfUsers,
            contactListViewModel.updateSelectedUsersList(unCheckedUsers.toList(), newlyCheckedUsers)
        )
    }

    @After
    fun after() {
        unmockkAll()
    }

}
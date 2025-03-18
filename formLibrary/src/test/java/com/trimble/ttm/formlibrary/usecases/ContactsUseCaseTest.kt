package com.trimble.ttm.formlibrary.usecases

import com.trimble.ttm.commons.utils.ext.safeCollect
import com.trimble.ttm.formlibrary.model.User
import com.trimble.ttm.formlibrary.repo.ContactsRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class ContactsUseCaseTest {

    @RelaxedMockK
    private lateinit var contactsUseCase: ContactsUseCase

    @RelaxedMockK
    private lateinit var cacheGroupsUseCase: CacheGroupsUseCase

    @RelaxedMockK
    private lateinit var contactsRepository: ContactsRepository

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        contactsUseCase = ContactsUseCase(contactsRepository, cacheGroupsUseCase)
    }

    @Test
    fun `verify getContactListFlow call`() = runTest {    //NOSONAR
        val users = mutableSetOf<User>()
        users.add(User(0, "user1", "user1@gmail.com", 1))
        users.add(User(1, "user2", "user2@gmail.com", 0))
        coEvery { contactsUseCase.getContactListFlow() } returns flow { emit(users) }
        contactsUseCase.getContactListFlow().safeCollect(this.javaClass.name) {
            assertEquals(users, it)
        }

        coVerify {
            contactsRepository.getContactsListFlow()
        }
    }

    @Test
    fun `verify getContacts call`() = runTest {    //NOSONAR
        coEvery { contactsRepository.getContacts(any(), any(), any()) } just runs

        contactsUseCase.getContacts("123", 234, true)
        coVerify(exactly = 1) {
            contactsRepository.getContacts(any(), any(), any())
        }
    }

    @Test
    fun `verify resetPagination call`() {    //NOSONAR
        verify(exactly = 0) {
            contactsUseCase.resetValueInContactRepo()
        }
    }

    @Test
    fun `verify contacts server sync in online`() = testScope.runTest {
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns true
        coEvery { contactsRepository.getContacts(any(), any(), any()) } just runs
        contactsUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(
            "10119",
            "11000751",
            testScope,
            "testTag"
        )
        coVerify(exactly = 1) {
            contactsRepository.getContacts(any(), any(), any())
        }
    }

    @Test
    fun `verify contacts server sync in offline`() = testScope.runTest {
        coEvery {
            cacheGroupsUseCase.checkAndUpdateCacheForGroupsFromServer(
                any(),
                any(),
                any(),
                any()
            )
        } returns false

        contactsUseCase.cacheGroupIdsFormIdsAndUserIdsFromServer(
            "10119",
            "11000751",
            testScope,
            "testTag"
        )

        coVerify(exactly = 0) {
            contactsRepository.getContacts(any(), any(), any())
        }
    }

    @Test
    fun `verify whether users are sorted alphabetically`() {
        contactsUseCase = ContactsUseCase(mockk(), mockk())
        val users = mutableSetOf<User>()
        users.add(User(username = "C"))
        users.add(User(username = "d"))
        users.add(User(username = "A"))
        users.add(User(username = "Aa"))
        users.add(User(username = "ac"))
        users.add(User(username = "E"))
        users.add(User(username = "b"))

        val sortedUsers = contactsUseCase.sortUsersAlphabetically(users)
        assertEquals(User(username = "Aa"), sortedUsers.elementAt(1))
        assertEquals(User(username = "C"), sortedUsers.elementAt(4))
        assertNotEquals(User(username = "d"), sortedUsers.elementAt(2))
    }

    @Test
    fun `verify whether user names with alphanumeric characters are sorted in order`() {
        contactsUseCase = ContactsUseCase(mockk(), mockk())
        val users = mutableSetOf<User>()
        users.add(User(username = "C2"))
        users.add(User(username = "d2"))
        users.add(User(username = "A"))
        users.add(User(username = "3E2"))
        users.add(User(username = "b1"))
        val sortedUsers = contactsUseCase.sortUsersAlphabetically(users)
        assertEquals(User(username = "3E2"), sortedUsers.elementAt(0))
        assertNotEquals(User(username = "A"), sortedUsers.elementAt(0))
    }

    @Test
    fun `verify whether user names with special characters are sorted in order`() {
        contactsUseCase = ContactsUseCase(mockk(), mockk())
        val users = mutableSetOf<User>()
        users.add(User(username = "!C2"))
        users.add(User(username = "#d2"))
        users.add(User(username = "@A"))
        users.add(User(username = "&E2"))
        users.add(User(username = "b1"))
        val sortedUsers = contactsUseCase.sortUsersAlphabetically(users)
        assertEquals(User(username = "!C2"), sortedUsers.elementAt(0))
        assertEquals(User(username = "&E2"), sortedUsers.elementAt(2))
        assertNotEquals(User(username = "#d2"), sortedUsers.elementAt(2))
    }

    @After
    fun after() {
        unmockkAll()
    }
}
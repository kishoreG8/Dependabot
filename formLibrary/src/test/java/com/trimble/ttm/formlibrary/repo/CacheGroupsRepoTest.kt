package com.trimble.ttm.formlibrary.repo

import android.content.Context
import com.trimble.ttm.commons.moduleCommunicator.AppModuleCommunicator
import com.trimble.ttm.commons.preferenceManager.FormDataStoreManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheGroupsRepoTest {

    private lateinit var formDataStoreManager: FormDataStoreManager
    private lateinit var context: Context
    @get:Rule
    val temporaryFolder = TemporaryFolder()
    @RelaxedMockK
    private lateinit var appModuleCommunicator: AppModuleCommunicator
    private lateinit var cacheGroupsRepo: CacheGroupsRepoImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk()
        formDataStoreManager = spyk(FormDataStoreManager(context))
        appModuleCommunicator = mockk()
        every { context.filesDir } returns temporaryFolder.newFolder()
        every { appModuleCommunicator.getAppModuleApplicationScope() } returns CoroutineScope(Job())
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.GROUP_UNITS_LAST_MODIFIED_TIME_KEY, 0L) } returns 0L
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.GROUP_FORMS_LAST_MODIFIED_TIME_KEY, 0L) } returns 0L
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.GROUP_USERS_LAST_MODIFIED_TIME_KEY, 0L) } returns 0L
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY, true) } returns false
        coEvery { formDataStoreManager.getValue(FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY, true) } returns false
        cacheGroupsRepo = CacheGroupsRepoImpl(mockk(), formDataStoreManager,appModuleCommunicator)
    }

    @Test
    fun `verify for blu enabled driver originated form to display in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["inUse"] = false
            it["inUseBits"] = 4L
            it["driverOriginate"] = 1L
        }
        validFieldMap.forEach {
            assertTrue(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for blu enabled dispatch originated form which cannot be displayed in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["inUse"] = false
            it["inUseBits"] = 4L
            it["dispatchOriginate"] = 1L
        }
        validFieldMap.forEach {
            assertFalse(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for blu enabled driver and dispatch originated form to display in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["inUse"] = false
            it["inUseBits"] = 4L
            it["dispatchOriginate"] = 1L
            it["driverOriginate"] = 1L
        }
        validFieldMap.forEach {
            assertTrue(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for blu enabled form which cannot be displayed in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["inUse"] = false
            it["inUseBits"] = 4L
            it["dispatchOriginate"] = 0L
            it["driverOriginate"] = 0L
        }
        validFieldMap.forEach {
            assertFalse(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for blu enabled form which cannot be displayed in form library and dispatch and driver originate is absent`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["inUse"] = false
            it["inUseBits"] = 4L
        }
        validFieldMap.forEach {
            assertFalse(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for blu disabled form which cannot be displayed in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["inUse"] = false
            it["inUseBits"] = 2L
            it["dispatchOriginate"] = 0L
            it["driverOriginate"] = 0L
        }
        validFieldMap.forEach {
            assertFalse(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for invalid form data which cannot be displayed in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["inUse"] = false
        }
        validFieldMap.forEach {
            assertFalse(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for free form which can be displayed in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["formClass"] = 1L
            it["inUseBits"] = 4L
            it["driverOriginate"] = 1L
        }
        validFieldMap.forEach {
            assertTrue(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for normal form which can be displayed in form library`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["forcedReply"] = false
            it["formid"] = 123L
            it["groupAccess"] = 1L
            it["formClass"] = 0L
            it["inUseBits"] = 5L
            it["driverOriginate"] = 1L
        }
        validFieldMap.forEach {
            assertTrue(cacheGroupsRepo.parseFormId(it, mutableMapOf(), 0L, "tag"))
        }
    }

    @Test
    fun `verify for address book enabled user to display in contacts`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["addrBook"] = true
            it["uid"] = 123L
        }
        assertTrue(cacheGroupsRepo.parseUserId(validFieldMap["123"] ?: emptyMap<String, Any>(), mutableSetOf(), 0L, "tag"))
    }

    @Test
    fun `verify for address book disabled user to not to display in contacts`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["addrBook"] = false
            it["uid"] = 123L
        }
        assertFalse(cacheGroupsRepo.parseUserId(validFieldMap["123"] ?: emptyMap<String, Any>(), mutableSetOf(), 0L, "tag"))
    }

    @Test
    fun `verify for invalid user data to not to display in contacts`() {
        val validFieldMap = mutableMapOf<String, Any>()
        validFieldMap["123"] = mutableMapOf<String, Any>().also {
            it["uid"] = 123L
        }
        assertFalse(cacheGroupsRepo.parseUserId(validFieldMap["123"] ?: emptyMap<String, Any>(), mutableSetOf(), 0L, "tag"))
    }

    @Test
    fun `verify local caching on absence of last modified field in all group collections of firestore`() =
        runTest {
            assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(0, 0, 0))
        }

    @Test
    fun `verify local caching on absence of last modified field in any two of the group collections in firestore`() =
        runTest {
            assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(193949490, 0, 0))
        }

    @Test
    fun `verify local caching on absence of last modified field in any one of the group collection in firestore`() =
        runTest {
            assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(0, 19393930, 13838380))
        }

    @Test
    fun `verify caching on presence of last modified field in all of the groups collection in firestore on fresh app install`() =
        runTest {
            assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(1939390, 19393930, 13838380))
        }

    @Test
    fun `verify local caching on out dated local time for any of the groups`() = runTest {
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_UNITS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 11221210L
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_FORMS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 0L
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_USERS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 0L
        assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(11221220L, 0, 0))
    }

    @Test
    fun `verify up-to-date local cache`() = runTest {
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_UNITS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 18484840L
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_FORMS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 13838380L
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_USERS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 183737760L
        assertFalse(cacheGroupsRepo.isCacheOutDatedOrEmpty(18484840L, 13838380L, 183737760L))
    }

    @Test
    fun `verify if empty formlibrary firestore snapshot enables caching`() = runTest {
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
                true)
        } returns true
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY,
                true)
        } returns false
        assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(0L, 0L, 0L))
    }

    @Test
    fun `verify if empty contacts firestore snapshot enables caching`() = runTest {
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
                true)
        } returns false
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY,
                true)
        } returns true
        assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(0L, 0L, 0L))
    }

    @Test
    fun `verify if empty formlibrary and contacts firestore snapshot enables caching`() = runTest {
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
                true)
        } returns true
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY,
                true)
        } returns true
        assertTrue(cacheGroupsRepo.isCacheOutDatedOrEmpty(0L, 0L, 0L))
    }

    @Test
    fun `verify no caching on correct formlibrary and contacts firestore snapshot`() = runTest {
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_FORM_LIBRARY_SNAPSHOT_EMPTY,
                true)
        } returns false
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.IS_CONTACTS_SNAPSHOT_EMPTY,
                true)
        } returns false
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_UNITS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 18484840L
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_FORMS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 13838380L
        coEvery {
            formDataStoreManager.getValue(
                FormDataStoreManager.GROUP_USERS_LAST_MODIFIED_TIME_KEY,
                0L)
        } returns 183737760L
        assertFalse(cacheGroupsRepo.isCacheOutDatedOrEmpty(18484840L, 13838380L, 183737760L))
    }

    @After
    fun after() {
        unmockkAll()
    }

}
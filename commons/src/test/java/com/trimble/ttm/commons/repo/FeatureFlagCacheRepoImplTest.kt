package com.trimble.ttm.commons.repo

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.core.ListenerRegistrationImpl
import com.trimble.ttm.commons.logger.Log
import com.trimble.ttm.commons.utils.FEATURE_FLAGS
import com.trimble.ttm.commons.utils.FeatureFlagDocument
import com.trimble.ttm.commons.utils.FeatureGatekeeper
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagCacheRepoImplTest {

    @RelaxedMockK
    private lateinit var firebaseFirestore: FirebaseFirestore

    private lateinit var expectedFeatureFlagMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>
    private lateinit var objectUnderTest: FeatureFlagCacheRepoImpl
    private lateinit var documentSnapshotList: List<QueryDocumentSnapshot>
    private lateinit var listenerSlotList: List<CapturingSlot<EventListener<DocumentSnapshot>>>

    fun createDocumentSnapshotMockList(): List<QueryDocumentSnapshot> {
        listenerSlotList = listOf(slot(), slot())
        return listOf(
            createDocumentSnapshotMock(
                "save_to_drafts",
                createDocumentReferenceMock(
                    "save_to_drafts",
                    listenerSlotList[0]
                )
            ),
            createDocumentSnapshotMock(
                "form_compose",
                createDocumentReferenceMock(
                    "form_compose",
                    listenerSlotList[1]
                )
            )
        )
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(Log)
        documentSnapshotList = createDocumentSnapshotMockList()
        createQuerySnapshotTaskMock(firebaseFirestore, FEATURE_FLAGS, documentSnapshotList)
        objectUnderTest = FeatureFlagCacheRepoImpl(firebaseFirestore)
        expectedFeatureFlagMap = hashMapOf(
            FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG to
                FeatureFlagDocument(featureFlagName = FeatureGatekeeper.KnownFeatureFlags.SAVE_TO_DRAFTS_FLAG.id),
            FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG to
                FeatureFlagDocument(featureFlagName = FeatureGatekeeper.KnownFeatureFlags.FORM_COMPOSE_FLAG.id)
        )
    }

    @Test
    fun `validate that feature flags will populate the map when updateFeatureFlagCacheMap is called`() = runTest {
        var actualFeatureFlagsMap: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument> = hashMapOf()
        val featureFlagsCallback:
            (Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>) -> Unit = { actualFeatureFlagsMap = it }

        // Act
        val result = objectUnderTest.listenAndUpdateFeatureFlagCacheMap(featureFlagsCallback)
        objectUnderTest.getFeatureFlagListeners().keys.forEachIndexed { index, featureFlag ->
            if (listenerSlotList[index].isCaptured) {
                listenerSlotList[index].captured.onEvent(documentSnapshotList.get(index), null)
            }
        }

        // Assert
        assertThat("2 listeners created", objectUnderTest.getFeatureFlagListeners().size == 2)
        assertThat("2 feature flags created", objectUnderTest.getFeatureFlagDocumentMap().size == 2)
        assertMapEqualsExpected(result, expectedFeatureFlagMap, "for result")
        assertMapEqualsExpected(actualFeatureFlagsMap, expectedFeatureFlagMap, "for callback")
    }

    @Test
    fun `validate that feature flags cache is cleared when clearCacheAndListeners is called`() = runTest {
        // Act
        val result = objectUnderTest.listenAndUpdateFeatureFlagCacheMap { }
        objectUnderTest.getFeatureFlagListeners().keys.forEachIndexed { index, featureFlag ->
            if (listenerSlotList[index].isCaptured) {
                listenerSlotList[index].captured.onEvent(documentSnapshotList.get(index), null)
            }
        }

        // Assert
        assertThat("2 listeners created", objectUnderTest.getFeatureFlagListeners().size == 2)
        assertThat("2 feature flags created", objectUnderTest.getFeatureFlagDocumentMap().size == 2)

        assert(!objectUnderTest.getCacheLock()) { "Cache lock should be open" }
        objectUnderTest.clearCacheAndListeners(true)
        assertThat("Cleared cache", objectUnderTest.getFeatureFlagDocumentMap().size == 0)
        assertThat("Cleared listeners", objectUnderTest.getFeatureFlagListeners().size == 0)
    }

    @Test
    fun `validate clearCacheAndListeners cache lock flag opens and closes`() = runTest {
        assert(!objectUnderTest.getCacheLock()) { "Cache lock should be open" }

        objectUnderTest.clearCacheAndListeners(false)
        assert(objectUnderTest.getCacheLock()) { "Cache lock should be closed" }

        objectUnderTest.clearCacheAndListeners(true)
        assert(!objectUnderTest.getCacheLock()) { "Cache lock should be open" }
    }

    private fun assertMapEqualsExpected(
        actual: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>,
        expected: Map<FeatureGatekeeper.KnownFeatureFlags, FeatureFlagDocument>,
        appendMsg: String
    ) {
        expected.forEach { entry ->
            print("${entry.key} : ${entry.value} $appendMsg")
            print("actual=${actual.size} $appendMsg")
            assertTrue(actual.containsKey(entry.key), "Actual should contains key= ${entry.key} $appendMsg")
            assertEquals(entry.value, actual[entry.key], appendMsg)
        }
    }

    fun createDocumentReferenceMock(id: String, eventListenerSlot: CapturingSlot<EventListener<DocumentSnapshot>>): DocumentReference {
        val mockedListenerRegistration = mockk<ListenerRegistrationImpl>()
        every { mockedListenerRegistration.remove() } returns Unit

        val mockedDocumentReference = mockk<DocumentReference>()
        every { mockedDocumentReference.id } returns id
        every { mockedDocumentReference.addSnapshotListener(any<MetadataChanges>(), capture(eventListenerSlot)) } returns mockedListenerRegistration
        return mockedDocumentReference
    }

    fun createDocumentSnapshotMock(id: String, reference: DocumentReference): QueryDocumentSnapshot {
        val mockedDocumentSnapshot = mockk<QueryDocumentSnapshot>()
        every { mockedDocumentSnapshot.id } returns id
        every { mockedDocumentSnapshot.reference } returns reference
        every { mockedDocumentSnapshot.get(any<FieldPath>()) } returns null
        every { mockedDocumentSnapshot.get(any<String>()) } returns null
        return mockedDocumentSnapshot
    }

    fun createQuerySnapshotTaskMock(
        firebaseFirestore: FirebaseFirestore,
        collection: String,
        documentSnapshotList: List<QueryDocumentSnapshot>
    ): Task<QuerySnapshot> {
        val mockedQuery = mockk<Query>()
        val mockedQuerySnapshot = mockk<QuerySnapshot>()
        every { mockedQuerySnapshot.isEmpty } returns documentSnapshotList.isEmpty()
        every { mockedQuerySnapshot.documents } returns documentSnapshotList
        every { mockedQuerySnapshot.query } returns mockedQuery
        every { mockedQuerySnapshot.iterator() } returns documentSnapshotList.listIterator() as MutableIterator<QueryDocumentSnapshot>

        val mockedQuerySnapshotTask = mockk<Task<QuerySnapshot>>()
        every { mockedQuerySnapshotTask.isComplete } returns true
        every { mockedQuerySnapshotTask.isSuccessful } returns true
        every { mockedQuerySnapshotTask.exception } returns null
        every { mockedQuerySnapshotTask.isCanceled } returns false
        every { mockedQuerySnapshotTask.result } returns mockedQuerySnapshot

        coEvery { firebaseFirestore.collection(collection).get() } returns mockedQuerySnapshotTask

        return mockedQuerySnapshotTask
    }
}

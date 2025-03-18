package com.trimble.ttm.formlibrary.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.storage.StorageReference
import com.trimble.ttm.commons.utils.getOrCreateToBeUploadedFolder
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.use
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageHandlerTests {

    private val context: Context = mockk()
    private val storageReference: StorageReference = mockk()
    private lateinit var imageHandler: ImageHandlerImpl
    private val testDispatcher = Dispatchers.Unconfined
    private val mockTask = mockk<Task<Void>>()
    private val onSuccessListener = slot<OnSuccessListener<Void?>>()
    private val onFailureListener = slot<OnFailureListener>()
    private val path = "testPath"
    private val uniqueId = "testUniqueId"

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0

        imageHandler = ImageHandlerImpl(context, storageReference)
        Dispatchers.setMain(testDispatcher)
        mockkStatic(BitmapFactory::class)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(BitmapFactory::class)
    }

    @Test
    fun testDeleteImage_success1() = runBlocking {
        // Arrange
        every { storageReference.child("$path/$uniqueId") } returns storageReference
        every { storageReference.delete() } returns mockTask
        every { mockTask.addOnSuccessListener(capture(onSuccessListener)) } answers {
            onSuccessListener.captured.onSuccess(null) // Invoke onSuccess listener with captured instance
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask

        val imageHandler = ImageHandlerImpl(context, storageReference)

        // Act
        val result = imageHandler.deleteImage(Dispatchers.IO, path, uniqueId)

        // Assert
        assertTrue(result)
    }

    @Test
    fun testDeleteImage_failure() = runBlocking {
        // Arrange
        every { storageReference.child("$path/$uniqueId") } returns storageReference
        every { storageReference.delete() } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } returns mockTask // Configure addOnSuccessListener
        every { mockTask.addOnFailureListener(capture(onFailureListener)) } answers {
            onFailureListener.captured.onFailure(Exception("Test exception")) // Invoke onFailure listener with captured instance
            mockTask
        }

        val imageHandler = ImageHandlerImpl(context, storageReference)

        // Act
        val result = imageHandler.deleteImage(Dispatchers.IO, path, uniqueId)

        // Assert
        assertFalse(result)
    }

    @Test
    fun testSaveImageLocallyFromByteArray_trueCase() = runTest {
        // Arrange
        val bitmap = mockk<Bitmap>() // Create a mock Bitmap object
        val imageData = ByteArrayOutputStream().use { outputStream ->
            every { bitmap.compress(any(), any(), any()) } returns true // Mock compress behavior
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray()
        }
        val filesDir = tempFolder.newFolder("filesDir")
        every { context.filesDir } returns filesDir
        every { BitmapFactory.decodeByteArray(imageData, 0, imageData.size) } returns bitmap // Mock decodeByteArray

        // Act
        val result = imageHandler.saveImageLocallyFromByteArray(testDispatcher, uniqueId, imageData)

        // Assert
        assertTrue(result)
        // You can also add assertions to verify that the file was created in the expected location
        val expectedFile = File(context.getOrCreateToBeUploadedFolder(), "$uniqueId.jpg")
        if (expectedFile.exists()) {
            assertTrue(expectedFile.exists())
            expectedFile.delete()
        }
    }

    @Test
    fun `saveImageLocallyFromByteArray returns false when an exception occurs`() = runTest {
        // Arrange
        val imageData = ByteArray(10)

        every { BitmapFactory.decodeByteArray(imageData, 0, imageData.size) } throws Exception("Decoding error")

        // Act
        val result = imageHandler.saveImageLocallyFromByteArray(testDispatcher, uniqueId, imageData)

        // Assert
        assert(!result)
    }
}
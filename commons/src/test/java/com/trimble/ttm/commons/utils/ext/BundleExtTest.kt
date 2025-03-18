package com.trimble.ttm.commons.utils.ext

import android.content.Context
import android.os.Bundle
import com.trimble.ttm.commons.model.DispatchFormPath
import com.trimble.ttm.commons.model.FormDef
import com.trimble.ttm.commons.utils.DISPATCH_FORM_PATH_SAVED
import com.trimble.ttm.commons.utils.DRIVER_FORM_ID
import com.trimble.ttm.commons.utils.IMESSAGE_REPLY_FORM_DEF
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic

import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals



class BundleExtTest {
    private lateinit var context: Context
    private lateinit var objectUnderTest: Bundle

    lateinit var formDefBeforeTiramisu: FormDef
    lateinit var formDefTiramisu: FormDef

    lateinit var formPathBeforeTiramisu: DispatchFormPath
    lateinit var formPathTiramisu: DispatchFormPath


    @Before
    fun setUp() {
        context = mockk()
        objectUnderTest = mockk()
        mockkStatic(Bundle::isTiramisuOrLatestVersion)
        every {
            objectUnderTest.isTiramisuOrLatestVersion()
        } returns true

        formDefTiramisu = FormDef(cid=33, formid=33, name="OnOrAfterTiramisu")
        every {
            objectUnderTest.getParcelable(IMESSAGE_REPLY_FORM_DEF, FormDef::class.java)
        } returns formDefTiramisu

        formDefBeforeTiramisu = FormDef(cid=9, formid=9, name="BeforeTiramisu")
        every {
            objectUnderTest.getParcelable(IMESSAGE_REPLY_FORM_DEF) as? FormDef
        } returns formDefBeforeTiramisu

        formPathTiramisu = DispatchFormPath(formId = 33, stopName = "OnOrAfterTiramisu")
        every {
            objectUnderTest.getParcelable(DISPATCH_FORM_PATH_SAVED, DispatchFormPath::class.java)
        } returns formPathTiramisu
        every {
            objectUnderTest.getParcelable(DRIVER_FORM_ID, DispatchFormPath::class.java)
        } returns formPathTiramisu
        formPathBeforeTiramisu = DispatchFormPath(formId = 9, stopName = "BeforeTiramisu")
        every {
            objectUnderTest.getParcelable(DISPATCH_FORM_PATH_SAVED) as? DispatchFormPath
        } returns formPathBeforeTiramisu
        every {
            objectUnderTest.getParcelable(DRIVER_FORM_ID) as? DispatchFormPath
        } returns formPathBeforeTiramisu
    }

    @Test
    fun `calls correct getSerializable for getReplyFormDef method based on android version before tiramisu`()  {
        every {
            objectUnderTest.isTiramisuOrLatestVersion()
        } returns false

        val result = objectUnderTest.getReplyFormDef()

        assertEquals(formDefBeforeTiramisu, result, "Older version of method called")
    }

    @Test
    fun `calls correct getSerializable for getReplyFormDef method based on android version on or after tiramisu`()  {
        every {
            objectUnderTest.isTiramisuOrLatestVersion()
        } returns true

        val result = objectUnderTest.getReplyFormDef()

        assertEquals(formDefTiramisu, result, "Tiramisu version of method called")
    }

    @Test
    fun `calls correct getSerializable for getDispatchFormPathSaved method based on android version before tiramisu`()  {
        every {
            objectUnderTest.isTiramisuOrLatestVersion()
        } returns false

        val result = objectUnderTest.getDispatchFormPathSaved()

        assertEquals(formPathBeforeTiramisu, result, "Older version of method called")
    }

    @Test
    fun `calls correct getSerializable for getDispatchFormPathSaved method based on android version on or after tiramisu`()  {
        every {
            objectUnderTest.isTiramisuOrLatestVersion()
        } returns true

        val result = objectUnderTest.getDispatchFormPathSaved()

        assertEquals(formPathTiramisu, result, "Tiramisu version of method called")
    }

    @Test
    fun `calls correct getSerializable for getDriverFormId method based on android version before tiramisu`()  {
        every {
            objectUnderTest.isTiramisuOrLatestVersion()
        } returns false

        val result = objectUnderTest.getDriverFormId()

        assertEquals(formPathBeforeTiramisu, result, "Older version of method called")
    }

    @Test
    fun `calls correct getSerializable for getDriverFormId method based on android version on or after tiramisu`()  {
        every {
            objectUnderTest.isTiramisuOrLatestVersion()
        } returns true

        val result = objectUnderTest.getDriverFormId()

        assertEquals(formPathTiramisu, result, "Tiramisu version of method called")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}

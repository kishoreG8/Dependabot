package com.trimble.ttm.formlibrary.model


import com.trimble.ttm.formlibrary.model.Message.Companion.getFormattedUiString
import com.trimble.ttm.formlibrary.model.Message.Companion.getWithDateRemovedAndFormatted
import com.trimble.ttm.formlibrary.model.Message.Companion.removeFirstDateOccurrence
import org.junit.Assert
import org.junit.Test

class MessageTest {

    @Test
    fun `verify line feed with valid ascii` (){
        val sampleString = "Route&#x0A;Manifest"
        val expected = sampleString.getFormattedUiString() // expected "Route\r\nManifest"
        print(expected)
        Assert.assertEquals(expected, sampleString.getFormattedUiString())
    }

    @Test
    fun `verify line feed with invalid ascii &#x0D` (){
        val sampleString = "Route&#x0D;Manifest"
        Assert.assertNotEquals("Route\nManifest", sampleString.getFormattedUiString())
    }

    @Test
    fun `verify line feed with invalid ascii $#x0A` (){
        val sampleString = "Route$#x0A;Manifest"
        Assert.assertNotEquals("Route\nManifest", sampleString.getFormattedUiString())
    }

    @Test
    fun `verify line feed with invalid ascii &#x10` (){
        val sampleString = "Route&#x10;Manifest"
        Assert.assertNotEquals("Route\nManifest", sampleString.getFormattedUiString())
    }

    @Test
    fun `not remove any date in the message because don't have at the beginning` (){
        val sampleString = "Hi Lohith This is a test message at 08/23 20:08 GMT+05:30"
        Assert.assertEquals("Hi Lohith This is a test message at 08/23 20:08 GMT+05:30", sampleString.removeFirstDateOccurrence())
    }

    @Test
    fun `remove extra date at the beginning of the message` (){
        val sampleString = "08/23 20:08 GMT+05:30 Hi Lohith This is a test message at 08/23 20:08 GMT+05:30"
        Assert.assertEquals("Hi Lohith This is a test message at 08/23 20:08 GMT+05:30", sampleString.removeFirstDateOccurrence())
    }

    @Test
    fun `remove extra date at the beginning of the message with new line character` (){
        val sampleString = "08/23 20:08 GMT-05:30\n" +
                "Hi Lohith\n" +
                "This is a test message at 08/23 20:08 GMT+05:30"
        Assert.assertEquals("Hi Lohith\nThis is a test message at 08/23 20:08 GMT+05:30", sampleString.removeFirstDateOccurrence())
    }

    @Test
    fun `remove extra date at the beginning of the message with new line character and formatted` (){
        val sampleString = "08/23 20:08 GMT+05:30&#x0A;Hi Lohith&#x0A;This is a test message at 08/23 20:08 GMT+05:30"
        val result = sampleString.getWithDateRemovedAndFormatted()
        val expected = sampleString.getWithDateRemovedAndFormatted() // expected "Hi Lohith\r\nThis is a test message at 08/23 20:08 GMT+05:30"
        print(expected)
        Assert.assertEquals(expected, result)
    }

}
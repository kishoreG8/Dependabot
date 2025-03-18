package com.trimble.ttm.routemanifest.utils

import com.trimble.ttm.commons.composable.customViews.setCurrencySymbolPrefix
import com.trimble.ttm.commons.composable.utils.formfieldutils.*
import com.trimble.ttm.commons.model.FormField
import com.trimble.ttm.commons.utils.*
import com.trimble.ttm.formlibrary.utils.COMMA
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormNumericUtilsTest {

    @Test
    fun `check if the string is numeric`() {
        assertTrue(isNumeric(value = "123.45"))
        assertFalse(isNumeric(value = "-.354"))
        assertTrue(isNumeric(value = "234.0"))
        assertTrue(isNumeric(value = "123.32"))
        assertFalse(isNumeric(value = "abcd"))
    }

    @Test
    fun `check if string is converted to ThousandSeparatedFormat`() {
        assertContains(convertToThousandSeparatedString(value = "123456"), COMMA)
        assertEquals(convertToThousandSeparatedString(value = EMPTY_STRING), EMPTY_STRING)
    }

    @Test
    fun `check if the value is valid and within range`() {
        assertEquals(
            checkValueIsValid(
                value = "1234",
                decimalDigitsAllowed = 0,
                minRange = BigDecimal(1),
                maxRange = BigDecimal(2000)
            ), EMPTY_STRING
        )
        assertEquals(
            checkValueIsValid(
                value = "123.56",
                decimalDigitsAllowed = 3,
                minRange = BigDecimal(1),
                maxRange = BigDecimal(2000)
            ), EMPTY_STRING
        )
        assertEquals(
            checkValueIsValid(
                value = "123443",
                decimalDigitsAllowed = 0,
                minRange = BigDecimal(0),
                maxRange = BigDecimal(2000)
            ), "$VALUE_IS_NOT_IN_RANGE 0 , 2000"
        )
        assertEquals(
            checkValueIsValid(
                value = "1245",
                decimalDigitsAllowed = 0,
                minRange = BigDecimal(0),
                maxRange = BigDecimal(2000)
            ), EMPTY_STRING
        )
        assertEquals(
            checkValueIsValid(
                value = "123.54567",
                decimalDigitsAllowed = 3,
                minRange = BigDecimal(0),
                maxRange = BigDecimal(2000)
            ), "$DECIMAL_RANGE_EXCEEDS 3"
        )
        assertEquals(
            checkValueIsValid(
                value = "1234543.5667",
                decimalDigitsAllowed = 0,
                minRange = BigDecimal(0),
                maxRange = BigDecimal(2000)
            ), "$VALUE_IS_NOT_IN_RANGE 0 , 2000"
        )
    }

    @Test
    fun `check if the string is converted to currency format`() {
        setCurrencySymbolPrefix(currencyPrefix = "$")
        assertContains(convertToCurrency(value = "3456"), "$")
    }

    @Test
    fun `check if special characters are removed`() {
        assertEquals(removeSpecialCharacters(value = "&54)@#"), "54")
        assertEquals(removeSpecialCharacters(value = "45"), "45")
    }

    @Test
    fun `check if the given string has decimal digits in range`() {
        assertEquals(
            checkIfDecimalDigitInRange(decimalDigitsAllowed = 3, value = "123.56"),
            EMPTY_STRING
        )
        assertEquals(
            checkIfDecimalDigitInRange(decimalDigitsAllowed = 0, value = "123.45"),
            DECIMAL_DIGITS_ARE_NOT_ALLOWED
        )
        assertEquals(
            checkIfDecimalDigitInRange(decimalDigitsAllowed = 2, value = "123.345"),
            "$DECIMAL_RANGE_EXCEEDS 2"
        )
    }

    @Test
    fun `check the count of special characters works as expected`(){
        assertEquals(countSeparatorAndCurrencySymbol("$1,123"),2)
        assertEquals(countSeparatorAndCurrencySymbol("1234"),0)
    }

    @Test
    fun `check the required field is empty or not`(){
        assertEquals(checkTheRequiredFieldsFilledOrNot(formField = FormField(required = 0)), EMPTY_STRING)
        val formField = FormField(required = 1)
        assertEquals(checkTheRequiredFieldsFilledOrNot(formField = formField),"*$FIELD_CAN_NOT_BE_EMPTY")
        formField.uiData = "Hello"
        assertEquals(checkTheRequiredFieldsFilledOrNot(formField = formField), EMPTY_STRING)
    }



}
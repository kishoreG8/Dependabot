package com.trimble.ttm.commons.model

import com.trimble.ttm.commons.utils.EMPTY_STRING

data class FormFieldAttribute(
    val uniqueTag: String = EMPTY_STRING,
    val fieldType: String = EMPTY_STRING,
    val formFieldData: String = EMPTY_STRING
)
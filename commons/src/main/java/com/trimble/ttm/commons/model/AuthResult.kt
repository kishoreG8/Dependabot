package com.trimble.ttm.commons.model

/**
 * This class holds firebase authentication success and error state
 * */
data class AuthResult(val status: Boolean, val exception: Exception?)
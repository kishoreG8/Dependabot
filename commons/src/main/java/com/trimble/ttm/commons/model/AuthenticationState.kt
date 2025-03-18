package com.trimble.ttm.commons.model

sealed class AuthenticationState {
    object Loading : AuthenticationState()
    object FirestoreAuthenticationSuccess : AuthenticationState()
    data class Error(val errorMsgStringRes: String) : AuthenticationState()
}


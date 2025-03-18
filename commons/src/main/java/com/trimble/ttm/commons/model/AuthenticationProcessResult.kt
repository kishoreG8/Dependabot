package com.trimble.ttm.commons.model

data class AuthenticationProcessResult(
    val isFirestoreAuthenticated: Boolean,
    val isFCMTokenSavedInFireStore: Boolean
) {

    fun isAuthenticationComplete(): Boolean {
        return this.isFirestoreAuthenticated && this.isFCMTokenSavedInFireStore
    }
}

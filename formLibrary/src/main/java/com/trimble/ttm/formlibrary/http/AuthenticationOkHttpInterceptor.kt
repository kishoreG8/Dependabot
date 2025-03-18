package com.trimble.ttm.formlibrary.http

import com.google.common.net.HttpHeaders.AUTHORIZATION
import com.trimble.ttm.formlibrary.utils.APP_CHECK_AUTHORIZATION
import com.trimble.ttm.formlibrary.utils.BEARER
import com.trimble.ttm.formlibrary.utils.CONTENT_TYPE
import com.trimble.ttm.formlibrary.utils.CONTENT_TYPE_JSON
import okhttp3.Interceptor
import okhttp3.Response


class AuthenticationOkHttpInterceptor(private val authToken: String?, private val appCheckToken: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.run {
        proceed(
            request()
                .newBuilder()
                .addHeader(CONTENT_TYPE, CONTENT_TYPE_JSON)
                .header(AUTHORIZATION, BEARER.plus(" ").plus(authToken))
                .header(APP_CHECK_AUTHORIZATION,appCheckToken)
                .build()
        )

    }
}
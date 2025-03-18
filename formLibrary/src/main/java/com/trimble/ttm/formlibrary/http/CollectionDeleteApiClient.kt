package com.trimble.ttm.formlibrary.http

import com.google.gson.GsonBuilder
import com.trimble.ttm.formlibrary.utils.ext.getBaseUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


enum class BuildEnvironment {
    Dev,
    Qa,
    Stg,
    Prod
}
object CollectionDeleteApiClient {
    private val clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
    fun createRetrofit(buildEnvironment: BuildEnvironment,authToken:String, appCheckToken : String): Retrofit {
        clientBuilder.addInterceptor(AuthenticationOkHttpInterceptor(authToken, appCheckToken))
        return Retrofit.Builder()
            .baseUrl(buildEnvironment.getBaseUrl())
            .client(clientBuilder.build())
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder().setLenient().create()
                )
            )
            .build()
    }

    inline fun <reified T> createApi(buildEnvironment: BuildEnvironment,authToken: String, appCheckToken : String): T =
        createRetrofit(buildEnvironment,authToken, appCheckToken).create(T::class.java)
}
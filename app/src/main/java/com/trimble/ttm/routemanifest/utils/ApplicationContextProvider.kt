package com.trimble.ttm.routemanifest.utils

import android.app.Application
import android.content.Context

object ApplicationContextProvider {
    private lateinit var application: Application

    fun init(application: Application) {
        this.application = application
    }

    fun getApplicationContext() : Context {
        return this.application.applicationContext
    }
}
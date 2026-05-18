package com.example.myapplication

import android.app.Application
import com.example.myapplication.core.network.Session

class NungilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }
}

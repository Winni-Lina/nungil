package com.example.myapplication

import android.app.Application
import com.example.myapplication.core.network.Session

class NungilApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }
}

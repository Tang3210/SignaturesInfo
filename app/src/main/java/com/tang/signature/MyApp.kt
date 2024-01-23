package com.tang.signature

import android.app.Application


/**
 * @author: Tang
 * @date: 2024/1/22
 * @description:
 */
class MyApp: Application() {

    override fun onCreate() {
        super.onCreate()
        myApp = this
    }


    companion object {
        private lateinit var myApp: MyApp
        fun getMyApp() = myApp
    }
}
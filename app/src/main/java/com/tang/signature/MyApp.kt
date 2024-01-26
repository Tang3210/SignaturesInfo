package com.tang.signature

import android.app.Application
import com.tencent.mmkv.MMKV


/**
 * @author: Tang
 * @date: 2024/1/22
 * @description:
 */
class MyApp: Application() {

    override fun onCreate() {
        super.onCreate()
        myApp = this
        MMKV.initialize(this)
    }


    companion object {
        private lateinit var myApp: MyApp
        fun getMyApp() = myApp
    }
}
package com.tang.signature

import android.util.Log
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.SPUtils
import com.tencent.mmkv.MMKV


/**
 * @author: Tang
 * @date: 2024/1/25
 * @description:
 */
object Cache {

    private val key = "Cache2"

    fun save(string: String) {
        val set = get().apply {
            remove(string)
            add(string)
        }

        MMKV.defaultMMKV().encode(key, GsonUtils.toJson(set))

    }

    fun get(): MutableList<String> {
        return GsonUtils.fromJson(MMKV.defaultMMKV().decodeString(key),
            GsonUtils.getListType(String().javaClass)) ?: mutableListOf()
    }
}
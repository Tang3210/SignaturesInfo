package com.tang.signature

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.AppUtils.AppInfo


/**
 * @author: Tang
 * @date: 2023/3/9
 * @description:
 */
class MainViewModel: ViewModel() {

    var md5 = ""
    var sha1 = ""
    var sha256 = ""
    var search = mutableStateOf("")
    var signatures = mutableStateOf("")
    private val allData = AppUtils.getAppsInfo()
    var data = mutableStateOf(allData)
    var selected = mutableStateOf<AppInfo?>(null)

    fun setSearchText(s: String){
        search.value = s
        val list = mutableListOf<AppInfo>()
        if (s.isEmpty()) {
            data.value = allData
            return
        }
        for (appInfo in allData) {
            if (appInfo.name.contains(s)){
                list.add(appInfo)
            }
        }
        data.value = list
    }

    fun getSignatures() {
        selected.value?.let {
            md5 = handlerData(AppUtils.getAppSignaturesMD5(it.packageName))
            sha1 = handlerData(AppUtils.getAppSignaturesSHA1(it.packageName))
            sha256 = handlerData(AppUtils.getAppSignaturesSHA256(it.packageName))
            signatures.value = "签名信息：\nmd5: \n${md5} \n\nsha1: \n${sha1} \n\nsha256:\n${sha256}\n"
        }
    }

    private fun handlerData(signatures: List<String>) = signatures.toString()
        .replace(":", "")
        .replace("[", "")
        .replace("]", "")
        .lowercase()
}
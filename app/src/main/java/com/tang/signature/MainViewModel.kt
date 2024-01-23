package com.tang.signature

import android.content.Intent
import android.content.pm.Signature
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.AppUtils.AppInfo
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.UriUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.security.AccessController.getContext
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey


/**
 * @author: Tang
 * @date: 2023/3/9
 * @description:
 */
class MainViewModel: ViewModel() {

    var md5 = ""
    var sha1 = ""
    var sha256 = ""
    var publicKey = mutableStateOf("")
    var search = mutableStateOf("")
    var signatures = mutableStateOf("")
    var showDialog  = mutableStateOf(false)
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
            try {
                // 获取第一个证书（默认情况下只有一个）
                val appSignatures = AppUtils.getAppSignatures(it.packageName)

                val publicKey1 = getPublicKey(appSignatures!![0]) as RSAPublicKey

                publicKey.value = publicKey1.modulus!!.toString()
            } catch (e: Exception) {
                e.message
            }

            md5 = handlerData(AppUtils.getAppSignaturesMD5(it.packageName))
            sha1 = handlerData(AppUtils.getAppSignaturesSHA1(it.packageName))
            sha256 = handlerData(AppUtils.getAppSignaturesSHA256(it.packageName))
            signatures.value = """
                已选：${it.name},   版本：${it.versionName}  ${it.versionCode},
                
                包名：${it.packageName}
                
                md5: 
                ${md5}
                
                公钥: 
                ${publicKey.value}
               
            """.trimIndent()
        }
    }

    @Throws(CertificateException::class)
    fun getPublicKey(signature: Signature): PublicKey? {
        val certFactory = CertificateFactory.getInstance("X.509")
        val bais = ByteArrayInputStream(signature.toByteArray())
        val cert = certFactory.generateCertificate(bais)
        return cert.publicKey
    }

    private fun handlerData(signatures: List<String>) = signatures.toString()
        .replace(":", "")
        .replace("[", "")
        .replace("]", "")
        .lowercase()

    fun writeFile() {
        val path = PathUtils.getInternalAppDataPath() + "/signatures_info.txt"
        Log.i("===", "writeFile: $path")
        FileUtils.createFileByDeleteOldFile(path)
        FileIOUtils.writeFileFromString(path, signatures.value)
        val intent = Intent()
        intent.setAction(Intent.ACTION_SEND)
        intent.setType("application/vnd.android.package-archive")
        val uri = UriUtils.file2Uri(File(path))
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityUtils.getTopActivity().startActivity(intent)
    }

}
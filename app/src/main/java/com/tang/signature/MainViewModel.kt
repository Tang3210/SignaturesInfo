package com.tang.signature

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.UriUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey

private const val TAG = "MainViewModel"
private const val SEARCH_DEBOUNCE_MS = 120L

data class AppEntry(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long
) {
    val versionLabel: String
        get() = "${versionName.ifBlank { "-" }} ($versionCode)"
}

data class SignatureInfo(
    val app: AppEntry,
    val md5: String,
    val sha1: String,
    val sha256: String,
    val publicKey: String
) {
    fun asText(): String = """
        版本：${app.versionLabel}

        包名：${app.packageName}

        md5:
        $md5

        公钥:
        $publicKey
    """.trimIndent()
}

class MainViewModel : ViewModel() {

    private val appContext: Context
        get() = MyApp.getMyApp()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _data = MutableStateFlow<List<AppEntry>>(emptyList())
    val data: StateFlow<List<AppEntry>> = _data.asStateFlow()

    private val _selected = MutableStateFlow<AppEntry?>(null)
    val selected: StateFlow<AppEntry?> = _selected.asStateFlow()

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _isSignatureLoading = MutableStateFlow(false)
    val isSignatureLoading: StateFlow<Boolean> = _isSignatureLoading.asStateFlow()

    private val _signatureInfo = MutableStateFlow<SignatureInfo?>(null)
    val signatureInfo: StateFlow<SignatureInfo?> = _signatureInfo.asStateFlow()

    private val _signatureError = MutableStateFlow<String?>(null)
    val signatureError: StateFlow<String?> = _signatureError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var allApps: List<AppEntry> = emptyList()
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var signatureJob: Job? = null
    private var shareJob: Job? = null

    init {
        loadApps()
    }

    fun loadApps() {
        if (loadJob?.isActive == true) return

        _isLoading.value = true
        _loadError.value = null
        loadJob = viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    readInstalledApps()
                }
                allApps = apps
                applyFilter(immediate = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load installed apps", e)
                _loadError.value = e.message ?: "读取应用列表失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchText(value: String) {
        if (_search.value == value) return

        _search.value = value
        applyFilter(immediate = false)
    }

    fun selectApp(app: AppEntry) {
        signatureJob?.cancel()
        _selected.value = app
        _showDialog.value = true
        _isSignatureLoading.value = true
        _signatureInfo.value = null
        _signatureError.value = null

        signatureJob = viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    createSignatureInfo(app)
                }
                if (_selected.value?.packageName != app.packageName) return@launch

                _signatureInfo.value = info
                promoteApp(app.packageName)

                withContext(Dispatchers.IO) {
                    runCatching { Cache.save(app.packageName) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read signature for ${app.packageName}", e)
                if (_selected.value?.packageName == app.packageName) {
                    _signatureError.value = e.message ?: "读取签名失败"
                }
            } finally {
                if (_selected.value?.packageName == app.packageName) {
                    _isSignatureLoading.value = false
                }
            }
        }
    }

    fun dismissDialog() {
        signatureJob?.cancel()
        _showDialog.value = false
        _isSignatureLoading.value = false
    }

    fun shareSignatureInfo() {
        val info = _signatureInfo.value ?: return
        if (shareJob?.isActive == true) return

        shareJob = viewModelScope.launch {
            try {
                val intent = withContext(Dispatchers.IO) {
                    createShareIntent(info)
                }
                ActivityUtils.getTopActivity()?.startActivity(intent)
                    ?: error("无法打开分享面板")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share signature info", e)
                _message.value = e.message ?: "分享失败"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun applyFilter(immediate: Boolean) {
        filterJob?.cancel()
        val query = _search.value

        if (query.isBlank()) {
            _data.value = allApps
            return
        }

        filterJob = viewModelScope.launch {
            if (!immediate) {
                delay(SEARCH_DEBOUNCE_MS)
            }
            val filtered = withContext(Dispatchers.Default) {
                allApps.filter { app ->
                    app.name.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                }
            }
            if (_search.value == query) {
                _data.value = filtered
            }
        }
    }

    private fun promoteApp(packageName: String) {
        val index = allApps.indexOfFirst { it.packageName == packageName }
        if (index <= 0) return

        allApps = buildList(allApps.size) {
            add(allApps[index])
            addAll(allApps.subList(0, index))
            addAll(allApps.subList(index + 1, allApps.size))
        }
        applyFilter(immediate = true)
    }

    private fun readInstalledApps(): List<AppEntry> {
        val packageManager = appContext.packageManager
        val cacheOrder = Cache.get()
            .withIndex()
            .associate { (index, packageName) -> packageName to index }
        val installedPackages = packageManager.getInstalledPackages(0)

        return installedPackages
            .mapIndexedNotNull { originalIndex, packageInfo ->
                packageInfo.toAppEntry(packageManager, originalIndex)
            }
            .sortedWith(
                compareByDescending<IndexedApp> {
                    cacheOrder[it.app.packageName] ?: -1
                }.thenBy { it.originalIndex }
            )
            .map { it.app }
    }

    private fun PackageInfo.toAppEntry(
        packageManager: PackageManager,
        originalIndex: Int
    ): IndexedApp? {
        val applicationInfo = applicationInfo ?: return null
        val label = runCatching {
            applicationInfo.loadLabel(packageManager).toString()
        }.getOrDefault("")

        return IndexedApp(
            originalIndex = originalIndex,
            app = AppEntry(
                packageName = packageName,
                name = label.ifBlank { packageName },
                versionName = versionName.orEmpty(),
                versionCode = versionCodeCompat()
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            versionCode.toLong()
        }

    private fun createSignatureInfo(app: AppEntry): SignatureInfo {
        val signatures = getSignatures(app.packageName)
        check(signatures.isNotEmpty()) { "未找到该应用的签名信息" }

        return SignatureInfo(
            app = app,
            md5 = digest(signatures, "MD5"),
            sha1 = digest(signatures, "SHA-1"),
            sha256 = digest(signatures, "SHA-256"),
            publicKey = publicKeyText(signatures.first())
        )
    }

    @SuppressLint("PackageManagerGetSignatures")
    private fun getSignatures(packageName: String): Array<Signature> {
        val packageManager = appContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo ?: return emptyArray()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners ?: emptyArray()
            } else {
                signingInfo.signingCertificateHistory ?: emptyArray()
            }
        } else {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
            packageInfo.signatures ?: emptyArray()
        }
    }

    private fun digest(signatures: Array<Signature>, algorithm: String): String =
        signatures.joinToString("\n") { signature ->
            MessageDigest.getInstance(algorithm)
                .digest(signature.toByteArray())
                .toHexString()
        }

    private fun publicKeyText(signature: Signature): String = runCatching {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(signature.toByteArray()))
        val publicKey = certificate.publicKey
        if (publicKey is RSAPublicKey) {
            publicKey.modulus.toString()
        } else {
            publicKey.encoded.toHexString()
        }
    }.getOrDefault("")

    private fun ByteArray.toHexString(): String {
        val hex = CharArray(size * 2)
        for (index in indices) {
            val value = this[index].toInt() and 0xFF
            hex[index * 2] = HEX_CHARS[value ushr 4]
            hex[index * 2 + 1] = HEX_CHARS[value and 0x0F]
        }
        return String(hex)
    }

    private fun createShareIntent(info: SignatureInfo): Intent {
        val path = "${PathUtils.getInternalAppDataPath()}/signatures_info.txt"
        check(FileUtils.createFileByDeleteOldFile(path)) { "创建分享文件失败" }
        check(FileIOUtils.writeFileFromString(path, info.asText())) {
            "写入分享文件失败"
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, UriUtils.file2Uri(File(path)))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private data class IndexedApp(
        val originalIndex: Int,
        val app: AppEntry
    )

    private companion object {
        val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}

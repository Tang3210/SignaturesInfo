package com.tang.signature

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.tang.signature.ui.theme.SignatureTheme
import com.tang.signature.ui.theme.grey
import com.tang.signature.ui.theme.white
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignatureTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting()
                }
            }
        }
    }
}

@Composable
fun Greeting(viewModel: MainViewModel = viewModel()) {
    val search by viewModel.search.collectAsState()
    val apps by viewModel.data.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val showDialog by viewModel.showDialog.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            ToastUtils.showShort(it)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        TextField(
            value = search,
            placeholder = {
                Text("输入app名称或包名")
            },
            singleLine = true,
            onValueChange = viewModel::setSearchText,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = white,
                focusedIndicatorColor = MaterialTheme.colors.primary,
                unfocusedIndicatorColor = grey
            )
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                isLoading && apps.isEmpty() -> {
                    LoadingContent("正在读取应用")
                }

                loadError != null && apps.isEmpty() -> {
                    ErrorContent(
                        message = loadError.orEmpty(),
                        onRetry = viewModel::loadApps
                    )
                }

                apps.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (search.isBlank()) "没有找到应用" else "没有匹配的应用",
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            items = apps,
                            key = { it.packageName }
                        ) { app ->
                            AppRow(
                                app = app,
                                onClick = { viewModel.selectApp(app) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        SignatureDialog(viewModel)
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        AppIcon(app)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.name,
            style = MaterialTheme.typography.body1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AppIcon(app: AppEntry) {
    val icon by produceState<Drawable?>(initialValue = null, key1 = app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                MyApp.getMyApp().packageManager.getApplicationIcon(app.packageName)
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (icon == null) {
            Text(
                text = app.name.take(1).uppercase(),
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f)
            )
        } else {
            Image(
                painter = rememberDrawablePainter(drawable = icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SignatureDialog(viewModel: MainViewModel) {
    val selected by viewModel.selected.collectAsState()
    val info by viewModel.signatureInfo.collectAsState()
    val isLoading by viewModel.isSignatureLoading.collectAsState()
    val error by viewModel.signatureError.collectAsState()

    Dialog(onDismissRequest = viewModel::dismissDialog) {
        SignatureDialogContent(
            selected = selected,
            info = info,
            isLoading = isLoading,
            error = error,
            onCopyPackage = {
                ClipboardUtils.copyText(selected?.packageName)
                ToastUtils.showShort("复制成功")
            },
            onCopyMd5 = {
                ClipboardUtils.copyText(info?.md5)
                ToastUtils.showShort("复制成功")
            },
            onCopyPublicKey = {
                ClipboardUtils.copyText(info?.publicKey)
                ToastUtils.showShort("复制成功")
            },
            onShare = viewModel::shareSignatureInfo
        )
    }
}

@Composable
private fun SignatureDialogContent(
    selected: AppEntry?,
    info: SignatureInfo?,
    isLoading: Boolean,
    error: String?,
    onCopyPackage: () -> Unit,
    onCopyMd5: () -> Unit,
    onCopyPublicKey: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.surface,
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = selected?.name.orEmpty(),
                style = MaterialTheme.typography.h6,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在读取签名")
                    }
                }

                error != null -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }

                info != null -> {
                    SignatureInfoContent(info)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DialogActionButton(
                    text = "复制包名",
                    enabled = selected != null,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyPackage
                )
                DialogActionButton(
                    text = "复制MD5",
                    enabled = info != null,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyMd5
                )
                DialogActionButton(
                    text = "复制公钥",
                    enabled = info?.publicKey?.isNotBlank() == true,
                    modifier = Modifier.weight(1f),
                    onClick = onCopyPublicKey
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onShare,
                enabled = info != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "生成文本并分享",
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SignatureInfoContent(info: SignatureInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SignatureField(
            label = "版本",
            value = info.app.versionLabel
        )
        SignatureField(
            label = "包名",
            value = info.app.packageName
        )
        SignatureField(
            label = "MD5",
            value = info.md5
        )
        SignatureField(
            label = "公钥",
            value = info.publicKey
        )
    }
}

@Composable
private fun SignatureField(
    label: String,
    value: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = buildAnnotatedString {
                append(label)
                append(": ")
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)){
                    append(value)
                }
            },
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(
    name = "签名信息弹窗",
    showBackground = true,
    backgroundColor = 0xFF202020,
    widthDp = 360,
    heightDp = 720
)
@Composable
private fun SignatureDialogPreview() {
    val app = AppEntry(
        packageName = "com.vivo.carlauncher",
        name = "车载launcher",
        versionName = "7.0.9",
        versionCode = 70009
    )
    val info = SignatureInfo(
        app = app,
        md5 = "cb3817a94474ee58ab37d0825bd2559167ed5f1f30e8b0216826f0463ed4b1d",
        sha1 = "",
        sha256 = "",
        publicKey = "2147955189449766239281460959521584"
    )

    SignatureTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            SignatureDialogContent(
                selected = app,
                info = info,
                isLoading = false,
                error = null,
                onCopyPackage = {},
                onCopyMd5 = {},
                onCopyPublicKey = {},
                onShare = {}
            )
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = grey
        )
    ) {
        Text(
            text = text,
            maxLines = 1,
            color = if (enabled) {
                MaterialTheme.colors.onSurface
            } else {
                MaterialTheme.colors.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}

@Composable
private fun LoadingContent(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colors.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

package com.tang.signature

import android.graphics.drawable.BitmapDrawable
import android.media.Image
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.tang.signature.ui.theme.SignatureTheme
import com.tang.signature.ui.theme.grey
import com.tang.signature.ui.theme.white

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignatureTheme {
                // A surface container using the 'background' color from the theme
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

@Preview(showBackground = true)
@Composable
fun Greeting() {
    val viewModel: MainViewModel = viewModel()
    Column {
        TextField(
            value = viewModel.search.value,
            placeholder = {
                Text("输入app名称")
            },
            singleLine = true,
            onValueChange = {
                viewModel.setSearchText(it)
            },
            modifier = Modifier
                .fillMaxWidth(),
            colors = TextFieldDefaults.textFieldColors(backgroundColor = white)
        )
        Text(text = "已选：${viewModel.selected.value?.name},   \n包名：${viewModel.selected.value?.packageName}",
            modifier = Modifier.padding(10.dp, 0.dp, 0.dp, 0.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = viewModel.signatures.value, modifier = Modifier.padding(10.dp, 0.dp, 0.dp, 0.dp))
        Row {
//            Spacer(modifier = Modifier.width(15.dp))
            Text(text = "复制MD5", textAlign = TextAlign.Center, modifier = Modifier
                .weight(1f)
                .clickable {
                    ClipboardUtils.copyText(viewModel.md5)
                    ToastUtils.showShort("复制成功")
                }
                .background(grey)
                .padding(15.dp, 15.dp))

            Spacer(modifier = Modifier.width(15.dp))
            Text(text = "复制SHA1", textAlign = TextAlign.Center, modifier = Modifier
                .weight(1f)
                .clickable {
                    ClipboardUtils.copyText(viewModel.sha1)
                    ToastUtils.showShort("复制成功")
                }
                .background(grey)
                .padding(15.dp, 15.dp))

            Spacer(modifier = Modifier.width(15.dp))
            Text(text = "复制SHA256", textAlign = TextAlign.Center, modifier = Modifier
                .weight(1f)
                .clickable {
                    ClipboardUtils.copyText(viewModel.sha256)
                    ToastUtils.showShort("复制成功")
                }
                .background(grey)
                .padding(0.dp, 15.dp))
        }
        LazyColumn(){
            items(viewModel.data.value){
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            viewModel.selected.value = it
                            viewModel.getSignatures()
                        }
                        .padding(15.dp, 15.dp)
                        .fillMaxWidth()
                ) {
                    Image(painter = rememberDrawablePainter(drawable = it.icon), contentDescription = "",
                        modifier = Modifier
                            .width(50.dp)
                            .height(50.dp))
                    Text(text = it.name, modifier = Modifier.padding(15.dp, 0.dp))
                }
            }
        }
    }
}
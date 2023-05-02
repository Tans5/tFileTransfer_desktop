package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.colorLightGrayBg
import com.tans.tfiletranserdesktop.ui.resources.colorTeal700
import com.tans.tfiletranserdesktop.ui.resources.colorTextBlack
import com.tans.tfiletranserdesktop.ui.resources.colorWhite
import com.tans.tfiletransporter.transferproto.fileexplore.requestMsgSuspend
import kotlinx.coroutines.launch


class MessageContent(
    val fileTransferScreen: FileTransferScreen
) : BaseScreen<Unit>(Unit) {

    override fun initData() {}

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        Surface(modifier = Modifier.fillMaxSize(), color = colorLightGrayBg) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val messagesState = fileTransferScreen.bindState().map { it.messages }.distinctUntilChanged().subscribeAsState(emptyList())
                        val messages = messagesState.value
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomEnd)
                        ) {
                            items(
                                count = messages.size
                            ) { index ->
                                val message = messages[index]
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                    )
                                ) {
                                    if (message.fromRemote) {
                                        Row(Modifier.fillMaxWidth()) {
                                            Card(
                                                shape = RoundedCornerShape(10.dp),
                                                backgroundColor = colorTeal700
                                            ) {
                                                SelectionContainer {
                                                    Text(
                                                        text = message.msg,
                                                        style = TextStyle(
                                                            color = colorWhite,
                                                            fontSize = 14.sp
                                                        ),
                                                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            Spacer(modifier = Modifier.width(70.dp))
                                        }
                                    } else {
                                        Row(Modifier.fillMaxWidth()) {
                                            Spacer(modifier = Modifier.width(70.dp))
                                            Spacer(modifier = Modifier.weight(1f))
                                            Card(
                                                shape = RoundedCornerShape(10.dp),
                                                backgroundColor = colorWhite
                                            ) {
                                                SelectionContainer {
                                                    Text(
                                                        text = message.msg,
                                                        style = TextStyle(
                                                            color = colorTextBlack,
                                                            fontSize = 14.sp
                                                        ),
                                                        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Divider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    Row(modifier = Modifier.fillMaxWidth().background(color = colorWhite)
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)) {
                        val input = remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = input.value,
                            onValueChange = { newValue -> input.value = newValue },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text("Input Message.")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                        Spacer(Modifier.width(10.dp))
                        IconButton(
                            onClick = {
                                launch {
                                    val inputLocal = input.value
                                    if (inputLocal.isNotBlank()) {
                                        runCatching {
                                            fileTransferScreen.fileExplore
                                                .requestMsgSuspend(inputLocal)
                                        }.onSuccess {
                                            fileTransferScreen.updateNewMessage(
                                                Message(
                                                    time = System.currentTimeMillis(),
                                                    msg = inputLocal,
                                                    fromRemote = false
                                                )
                                            )
                                            input.value = ""
                                        }.onFailure {
                                            JvmLog.e(TAG, "Send msg error: ${it.message}", it)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Image(painter = painterResource("images/send.xml"), contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    fun back(): Boolean {
        return false
    }

    fun refresh() {

    }

    companion object {
        private const val TAG = "MessageContent"
    }

}
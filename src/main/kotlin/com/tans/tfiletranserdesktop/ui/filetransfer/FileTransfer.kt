package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.colorTeal200
import com.tans.tfiletranserdesktop.ui.resources.colorTextGray
import com.tans.tfiletranserdesktop.ui.resources.colorWhite
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

enum class FileTransferTab(val tabTag: String) {
    MyFolder("MY FOLDER"),
    RemoteFolder("REMOTE FOLDER"),
    Message("MESSAGE")
}

data class FileTransferState(
    val selectedTab: FileTransferTab = FileTransferTab.MyFolder
)

class FileTransfer : BaseScreen<FileTransferState>(FileTransferState()) {

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        val selectedTab = bindState().map { it.selectedTab }.distinctUntilChanged().subscribeAsState(FileTransferTab.MyFolder)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("My Linux")
                            Text(text = "192.168.1.176", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W400))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { screenRoute.back() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "ArrowBack")
                        }
                    }
                )
            },
            bottomBar = {
                BottomAppBar(backgroundColor = colorWhite, contentColor = colorWhite) {
                    TabRow(
                        selectedTabIndex = 0,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        indicator = {},
                        backgroundColor = colorWhite,
                        contentColor = colorWhite
                    ) {
                        Tab(
                            selected = selectedTab.value == FileTransferTab.MyFolder,
                            selectedContentColor = colorTeal200,
                            unselectedContentColor = colorTextGray,
                            onClick = {
                                launch {
                                    updateState { oldState ->
                                        oldState.copy(selectedTab = FileTransferTab.MyFolder)
                                    }.await()
                                }
                            }
                        ) {
                            Text(FileTransferTab.MyFolder.tabTag)
                        }

                        Tab(
                            selected = selectedTab.value == FileTransferTab.RemoteFolder,
                            selectedContentColor = colorTeal200,
                            unselectedContentColor = colorTextGray,
                            onClick = {
                                launch {
                                    updateState { oldState ->
                                        oldState.copy(selectedTab = FileTransferTab.RemoteFolder)
                                    }.await()
                                }
                            }
                        ) {
                            Text(FileTransferTab.RemoteFolder.tabTag)
                        }

                        Tab(
                            selected = selectedTab.value == FileTransferTab.Message,
                            selectedContentColor = colorTeal200,
                            unselectedContentColor = colorTextGray,
                            onClick = {
                                launch {
                                    updateState { oldState ->
                                        oldState.copy(selectedTab = FileTransferTab.Message)
                                    }.await()
                                }
                            }
                        ) {
                            Text(FileTransferTab.Message.tabTag)
                        }
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 56.dp), contentAlignment = Alignment.Center) {
                when (selectedTab.value) {
                    FileTransferTab.MyFolder -> MyFolderContent()
                    FileTransferTab.RemoteFolder -> RemoteFolderContent()
                    FileTransferTab.Message -> MessageContent()
                    else -> {}
                }
            }
        }
    }

    @Composable
    fun MyFolderContent() {
        Text("My Folder")
    }

    @Composable
    fun RemoteFolderContent() {
        Text("Remote Folder")
    }

    @Composable
    fun MessageContent() {
        Text("Message")
    }

}
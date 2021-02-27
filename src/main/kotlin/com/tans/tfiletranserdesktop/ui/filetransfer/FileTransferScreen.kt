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
import com.squareup.moshi.Types
import com.tans.tfiletranserdesktop.net.RemoteDevice
import com.tans.tfiletranserdesktop.net.filetransporter.FileTransporter
import com.tans.tfiletranserdesktop.net.filetransporter.FilesShareWriterHandle
import com.tans.tfiletranserdesktop.net.filetransporter.launchFileTransport
import com.tans.tfiletranserdesktop.net.model.File
import com.tans.tfiletranserdesktop.net.model.ResponseFolderModel
import com.tans.tfiletranserdesktop.net.model.moshi
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.colorTeal200
import com.tans.tfiletranserdesktop.ui.resources.colorTextGray
import com.tans.tfiletranserdesktop.ui.resources.colorWhite
import com.tans.tfiletranserdesktop.utils.readString
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.net.InetAddress
import java.net.InetSocketAddress

enum class FileTransferTab(val tabTag: String) {
    MyFolder("MY FOLDER"),
    RemoteFolder("REMOTE FOLDER"),
    Message("MESSAGE")
}

enum class ConnectStatus {
    Connected,
    Connecting,
    Error
}

data class FileTransferState(
    val selectedTab: FileTransferTab = FileTransferTab.MyFolder,
    val connectStatus: ConnectStatus = ConnectStatus.Connecting
)

class FileTransferScreen(
    val localAddress: InetAddress,
    val remoteDevice: RemoteDevice,
    val asServer: Boolean
    ) : BaseScreen<FileTransferState>(FileTransferState()) {
    val remoteAddress: InetAddress = (remoteDevice.first as InetSocketAddress).address
    val remoteDeviceInfo = remoteDevice.second
    val fileTransporter = FileTransporter(
        localAddress = localAddress,
        remoteAddress = remoteAddress
    )
    lateinit var remoteFileSeparator: String

    val myFolderContent = MyFolderContent(fileTransferScreen = this)
    val remoteFolderContent = RemoteFolderContent(fileTransferScreen = this)
    val messageContent = MessageContent(fileTransferScreen = this)

    val remoteMessageEvent: Subject<String> = PublishSubject.create<String>().toSerialized()
    val remoteFolderModelEvent: Subject<ResponseFolderModel> = PublishSubject.create<ResponseFolderModel>().toSerialized()

    override fun initData() {
        launch(Dispatchers.IO) {
            val result = runCatching {
                fileTransporter.launchFileTransport(isServer = asServer) {

                    requestFolderChildrenShareChain { _, inputStream, limit, _ ->
                        val string = inputStream.readString(limit)
                        fileTransporter.writerHandleChannel.send(newFolderChildrenShareWriterHandle(string))
                    }

                    folderChildrenShareChain { _, inputStream, limit, _ ->
                        val string = inputStream.readString(limit)
                        val folderModel = moshi.adapter(ResponseFolderModel::class.java).fromJson(string)
                        if (folderModel != null) {
                            remoteFolderModelEvent.onNext(folderModel)
                        }
                    }

                    requestFilesShareChain { _, inputStream, limit, _ ->
                        val string = inputStream.readString(limit)
                        val moshiType = Types.newParameterizedType(List::class.java, File::class.java)
                        val files = moshi.adapter<List<File>>(moshiType).fromJson(string)
                        if (files != null) {
                            sendingFiles(files)
                        }
                    }

                    filesShareDownloader { files, remoteAddress ->
                        val result = runCatching {
                            // TODO: Downloading Files.
                        }
                        true
                    }

                    sendMessageChain { _, inputStream, limit, _ ->
                        val message = inputStream.readString(limit)
                        remoteMessageEvent.onNext(message)
                    }
                }
            }
            result.exceptionOrNull()?.printStackTrace()
            updateState { oldState ->
                oldState.copy(connectStatus = ConnectStatus.Error)
            }.await()
        }

        launch {
            remoteFileSeparator = fileTransporter.whenConnectReady()
            updateState { oldState ->
                oldState.copy(connectStatus = ConnectStatus.Connected)
            }.await()
            myFolderContent.initData()
            remoteFolderContent.initData()
            messageContent.initData()
        }
    }

    fun sendingFiles(files: List<File>) {
        launch {
            fileTransporter.writerHandleChannel.send(FilesShareWriterHandle(files = files) { files, localAddress ->
                // TODO: Sending Files.
            })
        }
    }

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        val selectedTab = bindState().map { it.selectedTab }.distinctUntilChanged().subscribeAsState(FileTransferTab.MyFolder)
        val connectStatus = bindState().map { it.connectStatus }.distinctUntilChanged().subscribeAsState(ConnectStatus.Connecting)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(remoteDeviceInfo)
                            Spacer(Modifier.height(2.dp))
                            Text(text = remoteAddress.hostAddress, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W400))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            launch {
                                when (bindState().firstOrError().map { it.selectedTab }.await()) {
                                    FileTransferTab.MyFolder -> {
                                        if (!myFolderContent.back()) {
                                            screenRoute.back()
                                        }
                                    }
                                    FileTransferTab.Message -> {
                                        if (!messageContent.back()) {
                                            screenRoute.back()
                                        }
                                    }
                                    FileTransferTab.RemoteFolder -> {
                                        if (!remoteFolderContent.back()) {
                                            screenRoute.back()
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "ArrowBack")
                        }
                    }
                )
            },
            bottomBar = {
                if (connectStatus.value == ConnectStatus.Connected) {
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
                                            if (oldState.selectedTab == FileTransferTab.MyFolder) {
                                                myFolderContent.refresh()
                                            }
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
                                            if (oldState.selectedTab == FileTransferTab.RemoteFolder) {
                                                remoteFolderContent.refresh()
                                            }
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
                                            if (oldState.selectedTab == FileTransferTab.Message) {
                                                messageContent.refresh()
                                            }
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
            }
        ) {
            when (connectStatus.value) {
                ConnectStatus.Connected -> {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 56.dp), contentAlignment = Alignment.Center) {
                        when (selectedTab.value) {
                            FileTransferTab.MyFolder -> myFolderContent.start(screenRoute)
                            FileTransferTab.RemoteFolder -> remoteFolderContent.start(screenRoute)
                            FileTransferTab.Message -> messageContent.start(screenRoute)
                            else -> {}
                        }
                    }
                }
                ConnectStatus.Connecting -> Loading()
                else -> screenRoute.back()
            }
        }
    }

    @Composable
    fun Loading() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    override fun stop(screenRoute: ScreenRoute) {
        super.stop(screenRoute)
        myFolderContent.stop(screenRoute)
        remoteFolderContent.stop(screenRoute)
        messageContent.stop(screenRoute)
    }

}
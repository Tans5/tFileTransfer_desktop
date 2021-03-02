package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.moshi.Types
import com.tans.tfiletranserdesktop.net.RemoteDevice
import com.tans.tfiletranserdesktop.net.filetransporter.*
import com.tans.tfiletranserdesktop.net.model.File
import com.tans.tfiletranserdesktop.net.model.ResponseFolderModel
import com.tans.tfiletranserdesktop.net.model.ResponseFolderModelJsonAdapter
import com.tans.tfiletranserdesktop.net.model.moshi
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.*
import com.tans.tfiletranserdesktop.utils.getSizeString
import com.tans.tfiletranserdesktop.utils.readString
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

sealed class FileTransferDialog {
    data class SendingFiles(
        val transferServer: MultiConnectionsFileServer? = null,
        val fileCount: Int,
        val index: Int,
        val fileName: String,
        val fileSize: Long,
        val sendSize: Long
    ) : FileTransferDialog()

    data class DownloadFiles(
        val transferClient: MultiConnectionsFileTransferClient? = null,
        val fileCount: Int,
        val index: Int,
        val fileName: String,
        val fileSize: Long,
        val downloadedSize: Long
    ) : FileTransferDialog()

    object None : FileTransferDialog()
}

data class FileTransferState(
    val selectedTab: FileTransferTab = FileTransferTab.MyFolder,
    val connectStatus: ConnectStatus = ConnectStatus.Connecting,
    val showDialog: FileTransferDialog = FileTransferDialog.None
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
                        val folderModel = ResponseFolderModelJsonAdapter(moshi).fromJson(string)
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
                        launch(Dispatchers.IO) {
                            this@FileTransferScreen.updateState { oldState ->
                                oldState.copy(
                                    showDialog = FileTransferDialog.DownloadFiles(
                                        fileCount = files.size,
                                        index = 0,
                                        fileName = files[0].file.name,
                                        fileSize = files[0].file.size,
                                        downloadedSize = 0L
                                    )
                                )
                            }.await()
                            val result = runCatching {
                                for ((i, f) in files.withIndex()) {
                                    this@FileTransferScreen.updateState { oldState ->
                                        val dialogType = oldState.showDialog
                                        if (dialogType is FileTransferDialog.DownloadFiles) {
                                            oldState.copy(
                                                showDialog = dialogType.copy(
                                                    index = i,
                                                    fileName = f.file.name,
                                                    fileSize = f.file.size,
                                                    downloadedSize = 0L)
                                            )
                                        } else {
                                            oldState
                                        }
                                    }.await()

                                    delay(200)
                                    startMultiConnectionsFileClient(
                                        fileMd5 = f,
                                        serverAddress = remoteAddress,
                                        clientInstance = { client ->
                                            this@FileTransferScreen.updateState { oldState ->
                                                val dialogType = oldState.showDialog
                                                if (dialogType is FileTransferDialog.DownloadFiles) {
                                                    oldState.copy(
                                                        showDialog = dialogType.copy(transferClient = client)
                                                    )
                                                } else {
                                                    oldState
                                                }
                                            }.await()
                                        }) { hasDownload, _ ->
                                        this@FileTransferScreen.updateState { oldState ->
                                            val dialogType = oldState.showDialog
                                            if (dialogType is FileTransferDialog.DownloadFiles) {
                                                oldState.copy(showDialog = dialogType.copy(downloadedSize = hasDownload))
                                            } else {
                                                oldState
                                            }
                                        }.await()
                                    }
                                }
                            }
                            if (result.isFailure) { result.exceptionOrNull()?.printStackTrace() }
                            this@FileTransferScreen.updateState { it.copy(showDialog = FileTransferDialog.None) }.await()
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
        launch(Dispatchers.IO) {
            fileTransporter.writerHandleChannel.send(FilesShareWriterHandle(files = files) { files, localAddress ->
                this@FileTransferScreen.updateState { oldState ->
                    oldState.copy(showDialog = FileTransferDialog.SendingFiles(
                        fileCount = files.size,
                        index = 0,
                        fileName = files[0].file.name,
                        fileSize = files[0].file.size,
                        sendSize = 0L
                    ))
                }.await()
                val result = runCatching {
                    for ((i, file) in files.withIndex()) {
                        this@FileTransferScreen.updateState { oldState ->
                            val dialogType = oldState.showDialog
                            if (dialogType is FileTransferDialog.SendingFiles) {
                                oldState.copy(
                                    showDialog = dialogType.copy(
                                        index = i,
                                        fileName = file.file.name,
                                        fileSize = file.file.size,
                                        sendSize = 0L
                                    )
                                )
                            } else {
                                oldState
                            }
                        }.await()
                        startMultiConnectionsFileServer(
                            fileMd5 = file,
                            localAddress = localAddress,
                            serverInstance = { server ->
                                this@FileTransferScreen.updateState { oldState ->
                                    val dialogType = oldState.showDialog
                                    if (dialogType is FileTransferDialog.SendingFiles) {
                                        oldState.copy(showDialog = dialogType.copy(transferServer = server))
                                    } else {
                                        oldState
                                    }
                                }.await()
                            }
                        ) { hasSend, _ ->
                            this@FileTransferScreen.updateState { oldState ->
                                val dialogType = oldState.showDialog
                                if (dialogType is FileTransferDialog.SendingFiles) {
                                    oldState.copy(showDialog = dialogType.copy(sendSize = hasSend))
                                } else {
                                    oldState
                                }
                            }.await()
                        }
                    }
                }
                if (result.isFailure) {
                    result.exceptionOrNull()?.printStackTrace()
                }
                this@FileTransferScreen.updateState { oldState ->
                    oldState.copy(showDialog = FileTransferDialog.None)
                }.await()
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

                        val dialogType = bindState().map { it.showDialog }.distinctUntilChanged().subscribeAsState(FileTransferDialog.None)
                        when (val dialog = dialogType.value) {
                            is FileTransferDialog.SendingFiles -> FileTransferDialog(dialog)
                            is FileTransferDialog.DownloadFiles -> FileTransferDialog(dialog)
                            FileTransferDialog.None -> { }
                        }
                    }
                }
                ConnectStatus.Connecting -> Loading()
                else -> screenRoute.back()
            }
        }
    }

    @Composable
    fun FileTransferDialog(dialog: FileTransferDialog) {
        if (dialog is FileTransferDialog.DownloadFiles || dialog is FileTransferDialog.SendingFiles) {
            Box(modifier = Modifier.fillMaxSize().clickable { }, contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.width(350.dp),
                    backgroundColor = colorWhite,
                    shape = RoundedCornerShape(4.dp),
                    elevation = 8.dp
                ) {

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 17.dp, end = 15.dp, bottom = 5.dp)
                    ) {
                        Text(
                            text = if (dialog is FileTransferDialog.SendingFiles) "Sending Files (${dialog.index + 1}/${dialog.fileCount})"
                            else "Downloading Files (${(dialog as FileTransferDialog.DownloadFiles).index + 1}/${dialog.fileCount})",
                            style = styleDialogTitle
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (dialog is FileTransferDialog.SendingFiles) dialog.fileName else (dialog as FileTransferDialog.DownloadFiles).fileName,
                            style = styleDialogBody
                        )
                        Spacer(Modifier.height(15.dp))
                        val process = if (dialog is FileTransferDialog.SendingFiles) {
                            (dialog.sendSize.toDouble() / dialog.fileSize.toDouble()).toFloat()
                        } else {
                            ((dialog as FileTransferDialog.DownloadFiles).downloadedSize.toDouble() / dialog.fileSize.toDouble()).toFloat()
                        }
                        LinearProgressIndicator(
                            progress = process,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        val processText = if (dialog is FileTransferDialog.SendingFiles) {
                            "${getSizeString(dialog.sendSize)}/${getSizeString(dialog.fileSize)}"
                        } else {
                            "${getSizeString((dialog as FileTransferDialog.DownloadFiles).downloadedSize)}/${getSizeString(dialog.fileSize)}"
                        }
                        Text(
                            text = processText,
                            modifier = Modifier.align(BiasAlignment.Horizontal(1f)),
                            style = TextStyle(color = colorTextGray, fontSize = 12.sp)
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            modifier = Modifier.align(BiasAlignment.Horizontal(1f)),
                            onClick = {
                                launch {
                                    val dialogType = bindState().map { it.showDialog }.firstOrError().await()
                                    if (dialogType is FileTransferDialog.SendingFiles) {
                                        dialogType.transferServer?.cancel()
                                    }
                                    if (dialogType is FileTransferDialog.DownloadFiles) {
                                        dialogType.transferClient?.cancel()
                                    }
                                }
                            }
                        ) {
                            Text(stringBroadcastSenderDialogCancel)
                        }
                    }
                }
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
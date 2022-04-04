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
import com.tans.tfiletranserdesktop.file.CommonFileLeaf
import com.tans.tfiletranserdesktop.file.FileConstants
import com.tans.tfiletranserdesktop.file.YoungLeaf
import com.tans.tfiletranserdesktop.net.RemoteDevice
import com.tans.tfiletranserdesktop.net.downloadDir
import com.tans.tfiletranserdesktop.net.model.*
import com.tans.tfiletranserdesktop.net.netty.fileexplore.FileExploreConnection
import com.tans.tfiletranserdesktop.net.netty.fileexplore.connectToFileExploreServer
import com.tans.tfiletranserdesktop.net.netty.fileexplore.startFileExploreServer
import com.tans.tfiletranserdesktop.net.netty.filetransfer.downloadFileObservable
import com.tans.tfiletranserdesktop.net.netty.filetransfer.sendFileObservable
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.*
import com.tans.tfiletranserdesktop.utils.getSizeString
import com.tans.tfiletranserdesktop.utils.ioExecutor
import com.tans.tfiletranserdesktop.utils.newChildFile
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.*
import kotlinx.coroutines.rx2.await
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.streams.toList

enum class FileTransferTab(val tabTag: String) {
    MyFolder("MY FOLDER"),
    RemoteFolder("REMOTE FOLDER"),
    Message("MESSAGE")
}

sealed class ConnectStatus {
    data class Connected(val handshakeModel: FileExploreHandshakeModel, val fileExploreConnection: FileExploreConnection) : ConnectStatus()
    object Connecting : ConnectStatus()
    object Error : ConnectStatus()
}

sealed class FileTransferDialog {
    data class SendingFiles(
        val fileCount: Int,
        val index: Int,
        val fileName: String,
        val fileSize: Long,
        val sendSize: Long,
        val task: Deferred<Unit>
    ) : FileTransferDialog()

    data class DownloadFiles(
        val fileCount: Int,
        val index: Int,
        val fileName: String,
        val fileSize: Long,
        val downloadedSize: Long,
        val task: Deferred<Unit>
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
    private val remoteAddress: InetAddress = (remoteDevice.first as InetSocketAddress).address
    private val remoteDeviceInfo = remoteDevice.second

    private val myFolderContent = MyFolderContent(fileTransferScreen = this)
    private val remoteFolderContent = RemoteFolderContent(fileTransferScreen = this)
    private val messageContent = MessageContent(fileTransferScreen = this)

    val remoteMessageEvent: Subject<String> = PublishSubject.create<String>().toSerialized()
    val remoteFolderModelEvent: Subject<ResponseFolderModel> = PublishSubject.create<ResponseFolderModel>().toSerialized()

    override fun initData() {
        launch(Dispatchers.IO) {
            val fileConnection = if (asServer) {
                startFileExploreServer(localAddress)
            } else {
                connectToFileExploreServer(remoteAddress)
            }
            val handshakeModel = fileConnection.observeConnected().await()

            updateState { oldState ->
                oldState.copy(connectStatus = ConnectStatus.Connected(handshakeModel, fileConnection))
            }.await()

            fileConnection.observeRemoteFileExploreContent()
                .doOnNext {
                    when (it) {

                        is RequestFolderModel -> {
                            val parentPath = it.requestPath
                            val path = Paths.get(FileConstants.USER_HOME + parentPath)
                            val children = if (Files.isReadable(path)) {
                                Files.list(path)
                                    .filter { f -> Files.isReadable(f) }
                                    .map { p ->
                                        val name = p.fileName.toString()
                                        val lastModify = OffsetDateTime.ofInstant(
                                            Instant.ofEpochMilli(
                                                Files.getLastModifiedTime(p).toMillis()
                                            ), ZoneId.systemDefault()
                                        )
                                        val pathString = if (parentPath.endsWith(FileConstants.FILE_SEPARATOR)) {
                                            parentPath + name
                                        } else {
                                            parentPath + FileConstants.FILE_SEPARATOR + name
                                        }
                                        if (Files.isDirectory(p)) {
                                            Folder(
                                                name = name,
                                                path = pathString,
                                                childCount = p.let {
                                                    val s = Files.list(it)
                                                    val size = s.count()
                                                    s.close()
                                                    size
                                                },
                                                lastModify = lastModify
                                            )
                                        } else {
                                            File(
                                                name = name,
                                                path = pathString,
                                                size = Files.size(p),
                                                lastModify = lastModify
                                            )
                                        }
                                    }.toArray().toList()

                            } else {
                                emptyList()
                            }
                            fileConnection.sendFileExploreContentToRemote(
                                fileExploreContent = ShareFolderModel(
                                    path = parentPath,
                                    childrenFolders = children.filterIsInstance<Folder>(),
                                    childrenFiles = children.filterIsInstance<File>()
                                )
                            )
                        }

                        is ShareFolderModel -> {
                            remoteFolderModelEvent.onNext(
                                ResponseFolderModel(
                                    path = it.path,
                                    childrenFolders = it.childrenFolders,
                                    childrenFiles = it.childrenFiles
                                )
                            )
                        }

                        is RequestFilesModel -> {
                            fileConnection.sendFileExploreContentToRemote(
                                fileExploreContent = ShareFilesModel(shareFiles = it.requestFiles),
                                waitReplay = true
                            )
                            sendingFiles(it.requestFiles)
                        }

                        is ShareFilesModel -> {

                            launch(Dispatchers.IO) {
                                val files = it.shareFiles
                                val result = runCatching {
                                    for ((i, f) in files.withIndex()) {

                                        delay(300)

                                        val task = async {
                                            downloadFileObservable(
                                                fileMd5 = f,
                                                serverAddress = remoteAddress,
                                                saveFile = downloadDir.newChildFile(f.file.name)
                                            )
                                                .flatMapSingle { hasDownload ->
                                                    this@FileTransferScreen.updateState { oldState ->
                                                        val dialogType = oldState.showDialog
                                                        if (dialogType is FileTransferDialog.DownloadFiles) {
                                                            oldState.copy(showDialog = dialogType.copy(downloadedSize = hasDownload))
                                                        } else {
                                                            oldState
                                                        }
                                                    }
                                                }
                                                .ignoreElements()
                                                .toSingleDefault(Unit)
                                                .await()
                                        }

                                        this@FileTransferScreen.updateState { oldState ->
                                            val d = FileTransferDialog.DownloadFiles(
                                                fileCount = files.size,
                                                index = i,
                                                fileSize = f.file.size,
                                                fileName = f.file.name,
                                                downloadedSize = 0L,
                                                task = task
                                            )
                                            oldState.copy(showDialog = d)
                                        }.await()
                                        try {
                                            task.await()
                                        } catch (t: Throwable) {
                                            break
                                        }
                                    }
                                }
                                if (result.isFailure) { result.exceptionOrNull()?.printStackTrace() }
                                this@FileTransferScreen.updateState { s -> s.copy(showDialog = FileTransferDialog.None) }.await()
                            }
                        }

                        is MessageModel -> {
                            remoteMessageEvent.onNext(it.message)
                        }
                        else -> {}
                    }
                }
                .ignoreElements()
                .await()

            updateState { oldState ->
                oldState.copy(connectStatus = ConnectStatus.Error)
            }.await()
        }

        launch {
            bindState().map { it.connectStatus }.ofType<ConnectStatus.Connected>().firstOrError().await()
            myFolderContent.initData()
            remoteFolderContent.initData()
            messageContent.initData()
        }
    }

    fun sendingFiles(files: List<FileMd5>) {
        launch(Dispatchers.IO) {
            val result = runCatching {
                for ((i, file) in files.withIndex()) {

                    val task: Deferred<Unit> = async {
                        sendFileObservable(
                            fileMd5 = file,
                            localAddress = localAddress)
                            .flatMapSingle { hasSend ->
                                this@FileTransferScreen.updateState { oldState ->
                                    val dialogType = oldState.showDialog
                                    if (dialogType is FileTransferDialog.SendingFiles) {
                                        oldState.copy(showDialog = dialogType.copy(sendSize = hasSend))
                                    } else {
                                        oldState
                                    }
                                }
                            }
                            .ignoreElements()
                            .toSingleDefault(Unit)
                            .await()
                    }

                    this@FileTransferScreen.updateState { oldState ->
                        val d = FileTransferDialog.SendingFiles(
                            fileCount = files.size,
                            index = i,
                            fileName = file.file.name,
                            fileSize = file.file.size,
                            sendSize = 0L,
                            task = task
                        )
                        oldState.copy(showDialog = d)
                    }.await()
                    try {
                        task.await()
                    } catch (t: Throwable) {
                        break
                    }
                }
            }
            if (result.isFailure) {
                result.exceptionOrNull()?.printStackTrace()
            }
            this@FileTransferScreen.updateState { oldState ->
                oldState.copy(showDialog = FileTransferDialog.None)
            }.await()
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
                if (connectStatus.value is ConnectStatus.Connected) {
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
                is ConnectStatus.Connected -> {
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
                                        dialogType.task.cancel()
                                    }
                                    if (dialogType is FileTransferDialog.DownloadFiles) {
                                        dialogType.task.cancel()
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

    fun getFileExploreConnection(): Single<FileExploreConnection> = bindState()
        .map { it.connectStatus }
        .ofType<ConnectStatus.Connected>()
        .map { it.fileExploreConnection }
        .firstOrError()

    fun getFileExploreHandshakeModel(): Single<FileExploreHandshakeModel> = bindState()
        .map { it.connectStatus }
        .ofType<ConnectStatus.Connected>()
        .map { it.handshakeModel }
        .firstOrError()

    override fun stop(screenRoute: ScreenRoute) {
        super.stop(screenRoute)
        myFolderContent.stop(screenRoute)
        remoteFolderContent.stop(screenRoute)
        messageContent.stop(screenRoute)
        ioExecutor.execute {
            getFileExploreConnection().blockingGet().close(true)
        }
    }

}

fun CommonFileLeaf.toFile(): File {
    return File(
        name = name,
        path = path,
        size = size,
        lastModify = OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault())
    )
}

fun File.toFileLeaf(): CommonFileLeaf {
    return CommonFileLeaf(
        name = name,
        path = path,
        size = size,
        lastModified = lastModify.toInstant().toEpochMilli()
    )
}
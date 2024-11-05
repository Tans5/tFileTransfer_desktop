package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.file.*
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.resources.Res
import com.tans.tfiletranserdesktop.resources.broadcast_sender_dialog_cancel
import com.tans.tfiletranserdesktop.resources.connection_error_title
import com.tans.tfiletranserdesktop.resources.drop_files_to_send
import com.tans.tfiletranserdesktop.resources.handshake_error_title
import com.tans.tfiletranserdesktop.resources.tab_message
import com.tans.tfiletranserdesktop.resources.tab_my_folder
import com.tans.tfiletranserdesktop.resources.tab_remote_folder
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.*
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import com.tans.tfiletransporter.transferproto.fileexplore.*
import com.tans.tfiletransporter.transferproto.fileexplore.model.*
import com.tans.tfiletransporter.transferproto.filetransfer.*
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import kotlinx.coroutines.*
import kotlinx.coroutines.rx3.await
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File
import java.net.InetAddress
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

enum class FileTransferTab {
    MyFolder,
    RemoteFolder,
    Message
}

sealed class ConnectStatus {
    data class Connected(val handshake: Handshake) : ConnectStatus()
    data object Connecting : ConnectStatus()

    data object Closed : ConnectStatus()

    data class Error(val title: StringResource, val msg: String) : ConnectStatus()
}

sealed class FileTransferDialog {
    data class SendingFiles(
        val fileCount: Int,
        val index: Int,
        val fileName: String,
        val fileSize: Long,
        val sendSize: Long,
        val speed: String,
        val task: FileSender,
    ) : FileTransferDialog()

    data class DownloadFiles(
        val fileCount: Int,
        val index: Int,
        val fileName: String,
        val fileSize: Long,
        val downloadedSize: Long,
        val speed: String,
        val task: FileDownloader
    ) : FileTransferDialog()

    data class Error(val msg: String) : FileTransferDialog()

    data object DragFiles : FileTransferDialog()
    data object None : FileTransferDialog()
}

data class Message(
    val time: Long,
    val msg: String,
    val fromRemote: Boolean
)

data class FileTransferScreenState(
    val selectedTab: FileTransferTab = FileTransferTab.MyFolder,
    val connectStatus: ConnectStatus = ConnectStatus.Connecting,
    val showDialog: FileTransferDialog = FileTransferDialog.None,
    val messages: List<Message> = emptyList()
)

@Suppress("FunctionName")
class FileTransferScreen(
    private val localAddress: InetAddress,
    remoteDevice: RemoteDevice,
    private val asServer: Boolean
    ) : BaseScreen<FileTransferScreenState>(FileTransferScreenState()) {
    private val remoteAddress: InetAddress = remoteDevice.remoteAddress.address
    private val remoteDeviceInfo =  remoteDevice.deviceName

    private val myFolderContent = MyFolderContent(fileTransferScreen = this)
    private val isInitMyFolderContent = AtomicBoolean(false)
    private val remoteFolderContent = RemoteFolderContent(fileTransferScreen = this)
    private val isInitRemoteFolderContent = AtomicBoolean(false)
    private val messageContent = MessageContent(fileTransferScreen = this)
    private val isInitMessageContent = AtomicBoolean(false)

    private val scanDirRequest: FileExploreRequestHandler<ScanDirReq, ScanDirResp> by lazy {
        object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
            override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp {
                return request.scanChildren()
            }
        }
    }

    private val sendFilesRequest: FileExploreRequestHandler<SendFilesReq, SendFilesResp> by lazy {
        object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
            override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp {
                if (isNew) {
                    downloadFiles(files = request.sendFiles, maxConnection = request.maxConnection)
                }
                return SendFilesResp(bufferSize = FILE_TRANSFER_BUFFER_SIZE.toInt())
            }
        }
    }

    private val downloadFilesRequest: FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> by lazy {
        object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
            override fun onRequest(isNew: Boolean, request: DownloadFilesReq): DownloadFilesResp {
                if (isNew) {
                    sendFilesWithoutRequest(files = request.downloadFiles)
                }
                return DownloadFilesResp(maxConnection = FILE_TRANSFER_MAX_CONNECTION)
            }
        }
    }

    val fileExplore: FileExplore by lazy {
        FileExplore(
            log = JvmLog,
            scanDirRequest = scanDirRequest,
            sendFilesRequest = sendFilesRequest,
            downloadFileRequest = downloadFilesRequest
        )
    }

    private val speedCalculator: SpeedCalculator by lazy {
        val result = SpeedCalculator()
        result.addObserver(object : SpeedCalculator.Companion.SpeedObserver {
            override fun onSpeedUpdated(speedInBytes: Long, speedInString: String) {
                ioExecutor.execute {
                    launch {
                        updateState { oldState ->
                            when (val d = oldState.showDialog) {
                                is FileTransferDialog.SendingFiles -> oldState.copy(showDialog = d.copy(speed = speedInString))
                                is FileTransferDialog.DownloadFiles -> oldState.copy(showDialog = d.copy(speed = speedInString))
                                else -> oldState
                            }
                        }.await()
                    }
                }
            }
        })
        result
    }

    private val ioExecutor: Executor by lazy {
        Dispatchers.IO.asExecutor()
    }

    override fun initData() {
        launch(Dispatchers.IO) {
            val connectResult = if (asServer) {
                JvmLog.d(TAG, "Start bind address: $localAddress")
                runCatching {
                    withTimeout(5000L) {
                        fileExplore.bindSuspend(address = localAddress)
                    }
                }
            } else {
                JvmLog.d(TAG, "Start connect address: $remoteAddress")
                var connectTimes = 3
                var connectResult: Result<Unit>
                do {
                    delay(200)
                    connectResult = runCatching {
                        fileExplore.connectSuspend(remoteAddress)
                    }
                    if (connectResult.isSuccess) { break }
                } while (--connectTimes > 0)
                connectResult
            }
            if (connectResult.isSuccess) {
                JvmLog.d(TAG, "Create connection success!!")
                JvmLog.d(TAG, "Start handshake.")
                val handshakeResult = if (asServer) {
                    runCatching {
                        withTimeout(3000L) {
                            fileExplore.waitHandshake()
                        }
                    }
                } else {
                    runCatching {
                        fileExplore.handshakeSuspend()
                    }
                }
                if (handshakeResult.isSuccess) {
                    JvmLog.d(TAG, "Handshake success!!")
                    updateState { it.copy(connectStatus = ConnectStatus.Connected(handshakeResult.getOrThrow())) }.await()
                    fileExplore.addObserver(object : FileExploreObserver {
                        override fun onNewState(state: FileExploreState) {}
                        override fun onNewMsg(msg: SendMsgReq) {
                            launch {
                                updateNewMessage(
                                    Message(
                                        time = msg.sendTime,
                                        msg = msg.msg,
                                        fromRemote = true
                                    )
                                )
                            }
                        }
                    })
                    fileExplore.waitClose()
                    updateState { it.copy(connectStatus = ConnectStatus.Closed) }.await()
                } else {
                    JvmLog.e(TAG, "Handshake fail: $handshakeResult", handshakeResult.exceptionOrNull())
                    updateState { it.copy(connectStatus = ConnectStatus.Error(title = Res.string.handshake_error_title, msg = handshakeResult.exceptionOrNull()?.message ?: "")) }.await()
                }
            } else {
                JvmLog.e(TAG, "Create connection fail: $connectResult", connectResult.exceptionOrNull())
                updateState { it.copy(connectStatus = ConnectStatus.Error(title = Res.string.connection_error_title, msg = connectResult.exceptionOrNull()?.message ?: "")) }.await()
            }
        }
    }


    fun sendJvmFilesWithRequest(files: List<File>) {

        launch(Dispatchers.IO) {
            val exploreFiles = files.jvmFilesToExploreFiles().filter { it.size > 0L }
            if (exploreFiles.isNotEmpty()) {
                sendFilesWithRequest(exploreFiles)
            } else {
                updateState {
                    it.copy(showDialog = FileTransferDialog.None)
                }.await()
            }
        }
    }

    fun sendLeafFilesWithRequest(files: List<FileLeaf.CommonFileLeaf>) {
        sendFilesWithRequest(files.leafToExploreFiles().filter { it.size > 0L })
    }

    private fun sendFilesWithRequest(files: List<FileExploreFile>) {
        launch(Dispatchers.IO) {
            if (files.isNotEmpty()) {
                runCatching {
                    fileExplore
                        .requestSendFilesSuspend(files)
                }.onSuccess {
                    sendFilesWithoutRequest(files)
                }.onFailure {
                    JvmLog.e(TAG, "Request send msg error: ${it.message}", it)
                }
            }
        }
    }

    private fun sendFilesWithoutRequest(files: List<FileExploreFile>) {
        launch(Dispatchers.IO) {
            val fixedFiles = files.filter { it.size > 0 }
            val senderFiles = fixedFiles.map { SenderFile( File(it.path), it) }
            if (senderFiles.isEmpty()) { return@launch }
            val sender = FileSender(
                files = senderFiles,
                bindAddress = localAddress,
                log = JvmLog
            )
            updateState {
                it.copy(showDialog = FileTransferDialog.SendingFiles(
                    fileCount = files.size,
                    index = 1,
                    fileName = files[0].name,
                    fileSize = files[0].size,
                    sendSize = 0L,
                    speed = "",
                    task = sender
                ))
            }.await()
            fun updateSenderDialog(updateDialog: (FileTransferDialog.SendingFiles) -> FileTransferDialog) {
                ioExecutor.execute {
                    updateState { s->
                        val dialog = s.showDialog
                        if (dialog is FileTransferDialog.SendingFiles) {
                            s.copy(showDialog = updateDialog(dialog))
                        } else {
                            s
                        }
                    }.blockingGet()
                }
            }
            sender.addObserver(object : FileTransferObserver {
                override fun onNewState(s: FileTransferState) {
                    when (s) {
                        FileTransferState.NotExecute -> {}
                        FileTransferState.Started -> {
                            speedCalculator.start()
                        }
                        FileTransferState.Canceled, FileTransferState.Finished -> {
                            speedCalculator.stop()
                            updateSenderDialog { FileTransferDialog.None }
                        }
                        is FileTransferState.Error, is FileTransferState.RemoteError -> {
                            speedCalculator.stop()
                            updateSenderDialog {
                                val msg = if (s is FileTransferState.Error) {
                                    s.msg
                                } else {
                                    (s as FileTransferState.RemoteError).msg
                                }
                                FileTransferDialog.Error(msg = msg)
                            }
                        }
                    }
                }

                override fun onStartFile(file: FileExploreFile) {
                    speedCalculator.reset()
                    updateSenderDialog {
                        it.copy(index = fixedFiles.indexOf(file), fileName = file.name, fileSize = file.size)
                    }
                }

                override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                    speedCalculator.updateCurrentSize(progress)
                    updateSenderDialog {
                        it.copy(sendSize = progress)
                    }
                }

                override fun onEndFile(file: FileExploreFile) {}

            })
            sender.start()
        }
    }

    fun downloadFiles(files: List<FileExploreFile>, maxConnection: Int) {
        launch(Dispatchers.IO) {
            delay(200L)
            val fixedFiles = files.filter { it.size > 0 }
            if (fixedFiles.isEmpty()) return@launch
            val downloader = FileDownloader(
                downloadDir = downloadDir,
                files = fixedFiles,
                connectAddress = remoteAddress,
                maxConnectionSize = maxConnection.toLong(),
                log = JvmLog
            )
            updateState {
                it.copy(showDialog = FileTransferDialog.DownloadFiles(
                    fileCount = files.size,
                    index = 1,
                    fileName = files[0].name,
                    fileSize = files[0].size,
                    downloadedSize = 0L,
                    speed = "",
                    task = downloader
                ))
            }.await()
            fun updateDownloaderDialog(updateDialog: (FileTransferDialog.DownloadFiles) -> FileTransferDialog) {
                ioExecutor.execute {
                    updateState { s->
                        val dialog = s.showDialog
                        if (dialog is FileTransferDialog.DownloadFiles) {
                            s.copy(showDialog = updateDialog(dialog))
                        } else {
                            s
                        }
                    }.blockingGet()
                }
            }

            downloader.addObserver(object : FileTransferObserver {
                override fun onNewState(s: FileTransferState) {
                    when (s) {
                        FileTransferState.NotExecute -> {}
                        FileTransferState.Started -> {
                            speedCalculator.start()
                        }
                        FileTransferState.Canceled, FileTransferState.Finished -> {
                            speedCalculator.stop()
                            updateDownloaderDialog { FileTransferDialog.None }
                        }
                        is FileTransferState.Error, is FileTransferState.RemoteError -> {
                            speedCalculator.stop()
                            updateDownloaderDialog {
                                val msg = if (s is FileTransferState.Error) {
                                    s.msg
                                } else {
                                    (s as FileTransferState.RemoteError).msg
                                }
                                FileTransferDialog.Error(msg = msg)
                            }
                        }
                    }
                }

                override fun onStartFile(file: FileExploreFile) {
                    speedCalculator.reset()
                    updateDownloaderDialog {
                        it.copy(index = fixedFiles.indexOf(file), fileName = file.name, fileSize = file.size)
                    }
                }

                override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                    speedCalculator.updateCurrentSize(progress)
                    updateDownloaderDialog {
                        it.copy(downloadedSize = progress)
                    }
                }

                override fun onEndFile(file: FileExploreFile) {}

            })

            downloader.start()
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
    @Composable
    override fun start(screenRoute: ScreenRoute) {
        val selectedTab = bindState().map { it.selectedTab }.distinctUntilChanged().subscribeAsState(FileTransferTab.MyFolder)
        val connectStatus = bindState().map { it.connectStatus }.distinctUntilChanged().subscribeAsState(ConnectStatus.Connecting)
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { true },
                    target = object : DragAndDropTarget {
                        override fun onEntered(event: DragAndDropEvent) {
                            JvmLog.d(TAG, "Drag enter: $event")
                            launch(Dispatchers.IO) {
                                val state = stateStore.firstOrError().await()
                                if (state.connectStatus is ConnectStatus.Connected) {
                                    val showingDialog = state.showDialog
                                    if (showingDialog == FileTransferDialog.None) {
                                        updateState {
                                            it.copy(showDialog = FileTransferDialog.DragFiles)
                                        }.await()
                                    }
                                }
                            }
                        }

                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            JvmLog.d(TAG, "Do drop: $event")
                            val files = event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            for (f in files) {
                                JvmLog.d(TAG, "Get drop file: ${f.absoluteFile.canonicalPath}")
                            }
                            if (files.isNotEmpty()) {
                                launch(Dispatchers.IO) {
                                    val state = stateStore.firstOrError().await()
                                    if (state.connectStatus is ConnectStatus.Connected) {
                                        val showingDialog = state.showDialog
                                        if (showingDialog == FileTransferDialog.DragFiles) {
                                            sendJvmFilesWithRequest(files)
                                        }
                                    }
                                }
                            }
                            return true
                        }

                        override fun onExited(event: DragAndDropEvent) {
                            JvmLog.d(TAG, "Drag exit: $event")
                            launch(Dispatchers.IO) {
                                val state = stateStore.firstOrError().await()
                                if (state.showDialog == FileTransferDialog.DragFiles) {
                                    updateState { it.copy(showDialog = FileTransferDialog.None) }.await()
                                }
                            }
                        }
                    }
                ),
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
                                Text(stringResource(Res.string.tab_my_folder))
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
                                Text(stringResource(Res.string.tab_remote_folder))
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
                                Text(stringResource(Res.string.tab_message))
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
                            FileTransferTab.MyFolder -> {
                                if (isInitMyFolderContent.compareAndSet(false, true)) {
                                    myFolderContent.initData()
                                }
                                myFolderContent.start(screenRoute)
                            }
                            FileTransferTab.RemoteFolder -> {
                                if (isInitRemoteFolderContent.compareAndSet(false, true)) {
                                    remoteFolderContent.initData()
                                }
                                remoteFolderContent.start(screenRoute)
                            }
                            FileTransferTab.Message -> {
                                if (isInitMessageContent.compareAndSet(false, true)) {
                                    messageContent.initData()
                                }
                                messageContent.start(screenRoute)
                            }
                        }

                        val dialogType = bindState().map { it.showDialog }.distinctUntilChanged().subscribeAsState(FileTransferDialog.None)
                        when (val dialog = dialogType.value) {
                            is FileTransferDialog.SendingFiles -> FileTransferDialog(dialog)
                            is FileTransferDialog.DownloadFiles -> FileTransferDialog(dialog)
                            FileTransferDialog.DragFiles -> DragFilesDialog()
                            is FileTransferDialog.Error -> {}
                            FileTransferDialog.None -> {}
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
            Box(modifier = Modifier.fillMaxSize().background(color = Color(0.2f, 0.2f, 0.2f, 0.2f)).clickable { }, contentAlignment = Alignment.Center) {
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
                            "${dialog.sendSize.toSizeString()}/${dialog.fileSize.toSizeString()}"
                        } else {
                            "${(dialog as FileTransferDialog.DownloadFiles).downloadedSize.toSizeString()}/${dialog.fileSize.toSizeString()}"
                        }
                        Row {
                            Text(
                                text = processText,
                                style = TextStyle(color = colorTextGray, fontSize = 12.sp)
                            )
                            Spacer(Modifier.weight(1f))
                            val speed = if (dialog is FileTransferDialog.SendingFiles) {
                                dialog.speed
                            } else {
                                (dialog as FileTransferDialog.DownloadFiles).speed
                            }
                            Text(
                                text = speed,
                                style = TextStyle(color = colorTextGray, fontSize = 12.sp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            modifier = Modifier.align(BiasAlignment.Horizontal(1f)),
                            onClick = {
                                launch(Dispatchers.IO) {
                                    val dialogType = bindState().map { it.showDialog }.firstOrError().await()
                                    updateState { it.copy(showDialog = FileTransferDialog.None) }.await()
                                    if (dialogType is FileTransferDialog.SendingFiles) {
                                        dialogType.task.cancel()
                                    }
                                    if (dialogType is FileTransferDialog.DownloadFiles) {
                                        dialogType.task.cancel()
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(Res.string.broadcast_sender_dialog_cancel))
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

    @Composable
    fun DragFilesDialog() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color(0.2f, 0.2f, 0.2f, 0.2f))
                .clickable { },
            contentAlignment = Alignment.Center) {
            Text(text = stringResource(Res.string.drop_files_to_send), style = TextStyle(fontSize = 22.sp, color = Color.DarkGray))
        }
    }

    suspend fun updateNewMessage(msg: Message) {
        updateState { it.copy(messages = it.messages + msg) }.await()
    }

    override fun stop(screenRoute: ScreenRoute) {
        super.stop(screenRoute)
        screenRoute.frameWindowScope.window.dropTarget = null
        myFolderContent.stop(screenRoute)
        remoteFolderContent.stop(screenRoute)
        messageContent.stop(screenRoute)
        ioExecutor.execute {
            fileExplore.closeConnectionIfActive()
        }
    }

    companion object {
        const val TAG = "FileTransferScreen"
    }

}
package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.file.*
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletransporter.transferproto.fileexplore.requestDownloadFilesSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.requestScanDirSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

data class RemoteFolderContentState(
    val fileTree: Optional<FileTree> = Optional.empty(),
    val selectedFiles: Set<FileLeaf.CommonFileLeaf> = emptySet(),
    val sortType: FileSortType = FileSortType.SortByName
)

class RemoteFolderContent(val fileTransferScreen: FileTransferScreen) :
    BaseScreen<RemoteFolderContentState>(defaultState = RemoteFolderContentState()) {

    override fun initData() {
        launch(Dispatchers.IO) {
            loadRemoteRootDir()
        }
    }

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        Box(modifier = Modifier.fillMaxSize()) {
            val state = bindState().map { Triple(it.fileTree, it.selectedFiles, it.sortType) }.distinctUntilChanged()
                .subscribeAsState(Triple(Optional.empty(), emptySet(), FileSortType.SortByName))
            val fileTree = state.value.first.getOrNull()
            val selectedFiles = state.value.second
            val sortType = state.value.third
            if (fileTree != null) {
                FileList(
                    fileTree = fileTree,
                    selectedFiles = selectedFiles,
                    sortType = sortType
                ) { fileOrDir: FileLeaf ->
                    launch(Dispatchers.IO) {
                        when (fileOrDir) {
                            is FileLeaf.CommonFileLeaf -> {
                                updateState { oldState ->
                                    val oldSelectedFiles = oldState.selectedFiles
                                    val newSelectedFiles = if (oldSelectedFiles.contains(fileOrDir)) {
                                        oldSelectedFiles - fileOrDir
                                    } else {
                                        oldSelectedFiles + fileOrDir
                                    }
                                    oldState.copy(selectedFiles = newSelectedFiles)
                                }.await()
                            }
                            is FileLeaf.DirectoryFileLeaf -> {
                                runCatching {
                                    fileTransferScreen.fileExplore
                                        .requestScanDirSuspend(fileOrDir.path)
                                }.onSuccess {
                                    updateState { s ->
                                        s.copy(fileTree = Optional.of(fileTree.newRemoteSubTree(it)), selectedFiles = emptySet())
                                    }.await()
                                }.onFailure {
                                    JvmLog.e(TAG, "Scan remote dir error: ${it.message}", it)
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                FloatingActionButton(onClick = {
                    launch(Dispatchers.IO) {
                        val selectFiles = bindState().map { it.selectedFiles }.firstOrError().await().filter { it.size > 0 }
                        val exploreFiles = selectFiles.toExploreFiles()
                        if (exploreFiles.isNotEmpty()) {
                            runCatching {
                                fileTransferScreen.fileExplore.requestDownloadFilesSuspend(
                                    downloadFiles = exploreFiles
                                )
                            }.onSuccess {
                                fileTransferScreen.downloadFiles(exploreFiles, it.maxConnection)
                                updateState { oldState ->
                                    oldState.copy(selectedFiles = emptySet())
                                }.await()
                            }.onFailure {
                                JvmLog.e(TAG, "Request download file error: ${it.message}", it)
                            }
                        }
                    }
                }) {
                    Image(painter = painterResource("images/download_outline.xml"), contentDescription = null)
                }
            }
        }
    }

    fun back(): Boolean {
        return if (bindState().firstOrError().blockingGet().fileTree.getOrNull()?.isRootFileTree() == false) {
            false
        } else {
            launch {
                updateState { state ->
                    val pt = state.fileTree.getOrNull()?.parentTree
                    if (pt != null) {
                        state.copy(fileTree = Optional.of(pt), selectedFiles = emptySet())
                    } else {
                        state
                    }
                }.await()
            }
            true
        }
    }

    fun refresh() {
        launch(Dispatchers.IO) {
            val fileTree = bindState().firstOrError().map { it.fileTree }.await().getOrNull()
            if (fileTree == null || fileTree.isRootFileTree()) {
                loadRemoteRootDir()
            } else {
                val parentTree = fileTree.parentTree!!
                runCatching {
                    fileTransferScreen.fileExplore
                        .requestScanDirSuspend(parentTree.path)
                }.onSuccess {
                    updateState { s ->
                        s.copy(fileTree = Optional.of(parentTree.newRemoteSubTree(it)), selectedFiles = emptySet())
                    }.await()
                }.onFailure {
                    JvmLog.e(TAG, "Scan remote dir error: ${it.message}", it)
                }
            }
        }
    }

    private suspend fun loadRemoteRootDir() {
        val connectStatus = fileTransferScreen.bindState()
            .map { it.connectStatus }
            .firstOrError()
            .await()
        if (connectStatus is ConnectStatus.Connected) {
            runCatching {
                fileTransferScreen.fileExplore.requestScanDirSuspend(connectStatus.handshake.remoteFileSeparator)
            }.onSuccess {
                JvmLog.d(TAG, "Request scan root dir success")
                updateState { s ->
                    s.copy(fileTree = Optional.of(createRemoteRootTree(it)), selectedFiles = emptySet())
                }.await()
            }.onFailure {
                JvmLog.e(TAG, "Request scan root dir fail: $it", it)
            }
        } else {
            JvmLog.e(TAG, "Wrong connect status: $connectStatus")
        }
    }

    companion object {
        private const val TAG = "RemoteFolderContent"
    }

}
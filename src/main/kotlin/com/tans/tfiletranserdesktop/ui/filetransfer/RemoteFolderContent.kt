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
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.dialogs.LoadingDialog
import io.reactivex.Single
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle

data class RemoteFolderContentState(
    val fileTree: FileTree = newRootFileTree(),
    val selectedFiles: Set<CommonFileLeaf> = emptySet(),
    val sortType: FileSortType = FileSortType.SortByName,
    val loadingDir: Boolean = false
)

class RemoteFolderContent(val fileTransferScreen: FileTransferScreen)
    : BaseScreen<RemoteFolderContentState>(defaultState = RemoteFolderContentState()) {

    override fun initData() {
        launch {
            updateState { it.copy(fileTree = newRootFileTree(path = fileTransferScreen.remoteFileSeparator)) }.await()
            bindState()
                .map { it.fileTree }
                .distinctUntilChanged()
                .flatMapSingle { oldTree ->
                    if (!oldTree.notNeedRefresh) {
                        rxSingle {
                            updateState { it.copy(loadingDir = true) }.await()
                            fileTransferScreen.fileTransporter.writerHandleChannel.send(
                                newRequestFolderChildrenShareWriterHandle(oldTree.path)
                            )
                            fileTransferScreen.remoteFolderModelEvent.firstOrError()
                                .flatMap { remoteFolder ->
                                    if (remoteFolder.path == oldTree.path) {
                                        updateState { oldState ->
                                            val children: List<YoungLeaf> = remoteFolder.childrenFolders
                                                .map {
                                                    DirectoryYoungLeaf(
                                                        name = it.name,
                                                        childrenCount = it.childCount,
                                                        lastModified = it.lastModify.toInstant()
                                                            .toEpochMilli()
                                                    )
                                                } + remoteFolder.childrenFiles
                                                .map {
                                                    FileYoungLeaf(
                                                        name = it.name,
                                                        size = it.size,
                                                        lastModified = it.lastModify.toInstant()
                                                            .toEpochMilli()
                                                    )
                                                }
                                            oldState.copy(
                                                fileTree = children.refreshFileTree(
                                                    parentTree = oldTree,
                                                    dirSeparator = fileTransferScreen.remoteFileSeparator
                                                ), selectedFiles = emptySet()
                                            )
                                        }.map {

                                        }.onErrorResumeNext {
                                            it.printStackTrace()
                                            Single.just(Unit)
                                        }
                                    } else {
                                        Single.just(Unit)
                                    }
                                }.await()
                            updateState { it.copy(loadingDir = false) }.await()
                        }
                    } else {
                        Single.just(Unit)
                    }
                }.ignoreElements().await()
        }
    }

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        Box(modifier = Modifier.fillMaxSize()) {
            val state = bindState().map { Triple(it.fileTree, it.selectedFiles, it.sortType) }.distinctUntilChanged().subscribeAsState(Triple(newRootFileTree(), emptySet(), FileSortType.SortByName))
            state.value.apply {
                FileList(
                    fileTree = first,
                    selectedFiles = second,
                    sortType = third
                ) { fileOrDir: FileLeaf ->
                    launch {
                        updateState { oldState ->
                            when (fileOrDir) {
                                is CommonFileLeaf -> {
                                    val oldSelectedFiles = oldState.selectedFiles
                                    val newSelectedFiles = if (oldSelectedFiles.contains(fileOrDir)) {
                                        oldSelectedFiles - fileOrDir
                                    } else {
                                        oldSelectedFiles + fileOrDir
                                    }
                                    oldState.copy(selectedFiles = newSelectedFiles)
                                }

                                is DirectoryFileLeaf -> {
                                    oldState.copy(fileTree = fileOrDir.newSubTree(oldState.fileTree), selectedFiles = emptySet())
                                }
                            }
                        }.await()
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                FloatingActionButton(onClick = {
                    launch {
                        val selectFiles = bindState().map { it.selectedFiles }.firstOrError().await()
                        if (selectFiles.isNotEmpty()) {
                            fileTransferScreen.fileTransporter.startWriterHandleWhenFinish(
                                newRequestFilesShareWriterHandle(selectFiles.map { it.toFile() })
                            )
                            updateState { oldState ->
                                oldState.copy(selectedFiles = emptySet())
                            }.await()
                        }
                    }
                }) {
                    Image(painter = painterResource("images/download_outline.xml"), contentDescription = null)
                }
            }

            val showLoading = bindState().map { it.loadingDir }.distinctUntilChanged().subscribeAsState(false)
            if (showLoading.value) {
                LoadingDialog()
            }
        }
    }

    fun back(): Boolean {
        return if (bindState().firstOrError().blockingGet().fileTree.isRootFileTree()) {
            false
        } else {
            updateState { state ->
                if (state.fileTree.parentTree == null) state else RemoteFolderContentState(
                    fileTree = state.fileTree.parentTree, selectedFiles = emptySet())
            }.subscribe()
            true
        }
    }

    fun refresh() {
        launch {
            updateState { oldState ->
                val newTree = oldState.fileTree.copy(notNeedRefresh = false)
                oldState.copy(fileTree = newTree, selectedFiles = emptySet())
            }.await()
        }
    }

}
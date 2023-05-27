package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.file.*
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.colorTextBlack
import com.tans.tfiletranserdesktop.ui.resources.colorTextGray
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

enum class FileSortType {
    SortByDate,
    SortByName
}

fun List<FileLeaf.CommonFileLeaf>.sortFile(sortType: FileSortType): List<FileLeaf.CommonFileLeaf> = when (sortType) {
    FileSortType.SortByDate -> {
        sortedByDescending { it.lastModified }
    }
    FileSortType.SortByName -> {
        sortedBy { it.name }
    }
}

fun List<FileLeaf.DirectoryFileLeaf>.sortDir(sortType: FileSortType): List<FileLeaf.DirectoryFileLeaf> = when (sortType) {
    FileSortType.SortByDate -> {
        sortedByDescending { it.lastModified }
    }
    FileSortType.SortByName -> {
        sortedBy { it.name }
    }
}

data class MyFolderContentState(
    val fileTree: Optional<FileTree> = Optional.empty(),
    val selectedFiles: Set<FileLeaf.CommonFileLeaf> = emptySet(),
    val sortType: FileSortType = FileSortType.SortByName
)

val fileDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Suppress("FunctionName")
@Composable
fun FileList(fileTree: FileTree, selectedFiles: Set<FileLeaf.CommonFileLeaf>, sortType: FileSortType, onClick: (fileOrDir: FileLeaf) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.padding(10.dp)) {
            Text(text = fileTree.path, style = TextStyle(color = colorTextGray, fontSize = 15.sp), maxLines = 1)
        }
        Divider(modifier = Modifier.height(1.dp).fillMaxWidth())
        val fileAndDirs: List<FileLeaf> = fileTree.dirLeafs.sortDir(sortType) + fileTree.fileLeafs.sortFile(sortType)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(count = fileAndDirs.size, key = { i -> fileAndDirs[i].path }) { i ->
                val fileOrDir = fileAndDirs[i]
                val isDir = fileOrDir is FileLeaf.DirectoryFileLeaf
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .height(65.dp)
                                    .clickable(
                                            interactionSource = MutableInteractionSource(),
                                            indication = rememberRipple(bounded = true),
                                            onClick = { onClick(fileOrDir) }
                                    ),
                            verticalAlignment = Alignment.CenterVertically) {
                        rememberScrollState(initial = 0)
                        Spacer(Modifier.width(20.dp))
                        Image(
                                painter = painterResource(if (isDir) "images/folder_outline.xml" else "images/file_outline.xml"),
                                contentDescription = null,
                                modifier = Modifier.width(25.dp).height(25.dp))
                        Spacer(Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = fileOrDir.name,
                                    style = TextStyle(color = colorTextBlack, fontSize = 17.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                val dateString = remember(fileOrDir.lastModified) {
                                    val dateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(fileOrDir.lastModified), ZoneId.systemDefault())
                                    fileDateTimeFormatter.format(dateTime)
                                }
                                Text(text = dateString,
                                        style = TextStyle(color = colorTextGray, fontSize = 14.sp),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(text = if (isDir)
                                    "${(fileOrDir as FileLeaf.DirectoryFileLeaf).childrenCount} files"
                                else (fileOrDir as FileLeaf.CommonFileLeaf).size.toSizeString(),
                                        style = TextStyle(color = colorTextGray, fontSize = 14.sp),
                                        maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.width(15.dp))
                        if (isDir) {
                            Image(
                                    painter = painterResource("images/chevron_right.xml"),
                                    contentDescription = null
                            )
                        } else {
                            Checkbox(checked = selectedFiles.contains(fileOrDir),
                                    onCheckedChange = null)
                        }
                        Spacer(Modifier.width(15.dp))
                    }
                    Box(Modifier.fillMaxWidth().padding(start = 65.dp)) {
                        Divider(modifier = Modifier.fillMaxWidth().height(1.dp))
                    }
                }
            }
        }
    }
}

class MyFolderContent(val fileTransferScreen: FileTransferScreen) : BaseScreen<MyFolderContentState>(defaultState = MyFolderContentState()) {

    override fun initData() {
        launch {
            updateState {
                MyFolderContentState(fileTree = Optional.of(createLocalRootTree()))
            }.await()
        }
    }

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        Box(modifier = Modifier.fillMaxSize()) {
            val state = bindState().distinctUntilChanged().subscribeAsState(MyFolderContentState())
            state.value.apply {
                val fileTree = fileTree.getOrNull()
                if (fileTree != null) {
                    FileList(
                        fileTree = fileTree,
                        selectedFiles = selectedFiles,
                        sortType = sortType
                    ) { fileOrDir: FileLeaf ->
                        launch {
                            val tree = bindState().map { it.fileTree }.firstOrError().await().getOrNull()
                            if (tree != null) {
                                updateState { oldState ->
                                    when (fileOrDir) {
                                        is FileLeaf.CommonFileLeaf -> {
                                            val oldSelectedFiles = oldState.selectedFiles
                                            val newSelectedFiles = if (oldSelectedFiles.contains(fileOrDir)) {
                                                oldSelectedFiles - fileOrDir
                                            } else {
                                                oldSelectedFiles + fileOrDir
                                            }
                                            oldState.copy(selectedFiles = newSelectedFiles)
                                        }

                                        is FileLeaf.DirectoryFileLeaf -> {
                                            oldState.copy(fileTree = Optional.of(tree.newLocalSubTree(fileOrDir)), selectedFiles = emptySet())
                                        }
                                    }
                                }.await()
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
                                fileTransferScreen.fileExplore
                                    .requestSendFilesSuspend(exploreFiles)
                            }.onSuccess {
                                updateState { oldState ->
                                    oldState.copy(selectedFiles = emptySet())
                                }.await()
                                fileTransferScreen.sendFiles(files = exploreFiles)
                            }.onFailure {
                                JvmLog.e(TAG, "Request send msg error: ${it.message}", it)
                            }
                        }
                    }
                }) {
                    Image(painter = painterResource("images/share_variant_outline.xml"), contentDescription = null)
                }
            }
        }
    }

    fun back(): Boolean {
        val fileTree = bindState().firstOrError().blockingGet().fileTree.getOrNull()
        return if (fileTree == null || fileTree.isRootFileTree()) {
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
        launch {
            updateState { oldState ->
                val oldTree = oldState.fileTree.getOrNull()
                if (oldTree == null || oldTree.isRootFileTree()) {
                    oldState.copy(
                        fileTree = Optional.of(createLocalRootTree()),
                        selectedFiles = emptySet()
                    )
                } else {
                    val parentTree = oldTree.parentTree
                    val dirLeaf = parentTree?.dirLeafs?.find { it.path == oldTree.path }
                    if (parentTree != null && dirLeaf != null) {
                        oldState.copy(
                            fileTree = Optional.of(parentTree.newLocalSubTree(dirLeaf)),
                            selectedFiles = emptySet()
                        )
                    } else {
                        oldState.copy(selectedFiles = emptySet())
                    }
                }
            }.await()
        }
    }

    companion object {
        private const val TAG = "MyFolderContent"
    }
}
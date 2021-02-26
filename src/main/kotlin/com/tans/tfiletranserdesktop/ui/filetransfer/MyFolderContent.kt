package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorXmlResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.file.*
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.colorTextBlack
import com.tans.tfiletranserdesktop.ui.resources.colorTextGray
import com.tans.tfiletranserdesktop.utils.getSizeString
import io.reactivex.Single
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.streams.toList

enum class FileSortType {
    SortByDate,
    SortByName
}

fun List<CommonFileLeaf>.sortFile(sortType: FileSortType): List<CommonFileLeaf> = when (sortType) {
    FileSortType.SortByDate -> {
        sortedByDescending { it.lastModified }
    }
    FileSortType.SortByName -> {
        sortedBy { it.name }
    }
}

fun List<DirectoryFileLeaf>.sortDir(sortType: FileSortType): List<DirectoryFileLeaf> = when (sortType) {
    FileSortType.SortByDate -> {
        sortedByDescending { it.lastModified }
    }
    FileSortType.SortByName -> {
        sortedBy { it.name }
    }
}

data class MyFolderContentState(
    val fileTree: FileTree = newRootFileTree(),
    val selectedFiles: Set<CommonFileLeaf> = emptySet(),
    val sortType: FileSortType = FileSortType.SortByName
)

val fileDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")

@Composable
fun FileList(fileTree: FileTree, selectedFiles: Set<CommonFileLeaf>, sortType: FileSortType) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.padding(10.dp)) {
            Text(text = fileTree.path, style = TextStyle(color = colorTextGray, fontSize = 15.sp), maxLines = 1)
        }
        Divider(modifier = Modifier.height(1.dp).fillMaxWidth())
        val fileAndDirs: List<FileLeaf> = fileTree.dirLeafs.sortDir(sortType) + fileTree.fileLeafs.sortFile(sortType)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(count = fileAndDirs.size, key = { i -> fileAndDirs[i].path }) { i ->
                val fileOrDir = fileAndDirs[i]
                val isDir = fileOrDir is DirectoryFileLeaf
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                            modifier = Modifier
                                    .fillMaxWidth()
                                    .height(65.dp)
                                    .clickable(
                                            interactionSource = MutableInteractionSource(),
                                            indication = rememberRipple(bounded = true),
                                            onClick = {

                                            }
                                    ),
                            verticalAlignment = Alignment.CenterVertically) {

                        Spacer(Modifier.width(20.dp))
                        Image(
                                imageVector = vectorXmlResource(if (isDir) "images/folder_outline.xml" else "images/file_outline.xml"),
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
                                    "${(fileOrDir as DirectoryFileLeaf).childrenCount} files"
                                else getSizeString((fileOrDir as CommonFileLeaf).size),
                                        style = TextStyle(color = colorTextGray, fontSize = 14.sp),
                                        maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.width(15.dp))
                        if (isDir) {
                            Image(
                                    imageVector = vectorXmlResource("images/chevron_right.xml"),
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
            bindState()
                .map { it.fileTree }
                .distinctUntilChanged()
                .flatMapSingle { oldTree ->
                    if (!oldTree.notNeedRefresh) {
                        updateState { oldState ->
                            val path =
                                if (oldTree.isRootFileTree()) Paths.get(FileConstants.USER_HOME) else Paths.get(
                                    FileConstants.USER_HOME + oldTree.path
                                )
                            val children = Files.list(path).map { p ->
                                if (Files.isDirectory(p)) {
                                    DirectoryYoungLeaf(
                                        name = p.fileName.toString(),
                                        childrenCount = Files.list(p).let { s ->
                                            val size = s.count()
                                            s.close()
                                            size
                                        },
                                        lastModified = Files.getLastModifiedTime(p).toMillis()
                                    )
                                } else {
                                    FileYoungLeaf(
                                        name = p.fileName.toString(),
                                        size = Files.size(p),
                                        lastModified = Files.getLastModifiedTime(p).toMillis()
                                    )
                                }
                            }.toList()
                            oldState.copy(
                                fileTree = children.refreshFileTree(oldTree),
                                selectedFiles = emptySet()
                            )
                        }
                            .map { }
                            .onErrorResumeNext {
                                it.printStackTrace()
                                Single.just(Unit)
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
            val state = bindState().distinctUntilChanged().subscribeAsState(MyFolderContentState())
            state.value.apply {
                FileList(
                    fileTree = fileTree,
                    selectedFiles = selectedFiles,
                    sortType = sortType
                )
            }

            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                FloatingActionButton(onClick = {}) {
                    Image(imageVector = vectorXmlResource("images/share_variant_outline.xml"), contentDescription = null)
                }
            }
        }
    }

    fun back(): Boolean {
        return if (bindState().firstOrError().blockingGet().fileTree.isRootFileTree()) {
            false
        } else {
            updateState { state ->
                if (state.fileTree.parentTree == null) state else MyFolderContentState(
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
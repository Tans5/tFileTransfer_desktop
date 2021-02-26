package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import com.tans.tfiletranserdesktop.ui.resources.colorTextGray
import io.reactivex.Single
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.nio.file.Files
import java.nio.file.Paths
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

@Composable
fun FileList(fileTree: FileTree, selectedFiles: Set<CommonFileLeaf>, sortType: FileSortType) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.padding(10.dp)) {
            Text(text = fileTree.path, style = TextStyle(color = colorTextGray, fontSize = 15.sp))
        }
        Divider(modifier = Modifier.height(1.dp).fillMaxWidth())
        val fileAndDirs: List<FileLeaf> = fileTree.dirLeafs.sortDir(sortType) + fileTree.fileLeafs.sortFile(sortType)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(count = fileAndDirs.size, key = { i -> fileAndDirs[i].path }) { i ->
                val fileOrDir = fileAndDirs[i]
                Row(modifier = Modifier.fillMaxWidth().height(75.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (fileOrDir is CommonFileLeaf) "File: " else "Dir: "} ${fileOrDir.name}")
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

}
package com.tans.tfiletranserdesktop.file

import com.tans.tfiletranserdesktop.utils.getCurrentOs
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreDir
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp
import java.io.File
import java.nio.file.*

val FILE_SYSTEM: FileSystem = FileSystems.getDefault()
val FILE_SEPARATOR: String = FILE_SYSTEM.separator
val USER_HOME: String = System.getProperty("user.home")

val userHomeDir: File by lazy {
    File(USER_HOME)
}

val downloadDir: File by lazy {
    val file = File("${USER_HOME}${FILE_SEPARATOR}Downloads", "tFileTransfer")
    if (!file.isDirectory) {
        file.mkdirs()
    }
    file
}

val USER_NAME = System.getProperty("user.name") ?: ""
val DEVICE_NAME = getCurrentOs().name

val LOCAL_DEVICE = "$USER_NAME's $DEVICE_NAME"

const val FILE_TRANSFER_MAX_CONNECTION = 8
const val FILE_TRANSFER_BUFFER_SIZE = 512 * 1024L

fun ScanDirReq.scanChildren(): ScanDirResp {
    val fixedRequestPath = requestPath.trim()
    val fileSeparator = File.separator
    return if (fixedRequestPath.isEmpty() || fixedRequestPath == fileSeparator) {
        // Read root file
        val userHomeExploreFile = FileExploreDir(
            name = "User Home",
            path = userHomeDir.canonicalPath,
            childrenCount = userHomeDir.listFiles()?.size ?: 0,
            lastModify = userHomeDir.lastModified()
        )
        val rootFiles = File.listRoots() ?: emptyArray()
        val otherExploreFiles = rootFiles
            .filter { !userHomeDir.hasTargetParent(it) }
            .map {
                FileExploreDir(
                    name = it.canonicalPath,
                    path = it.path,
                    childrenCount = it.listFiles()?.size ?: 0,
                    lastModify = it.lastModified()
                )
            }
        ScanDirResp(
            path = fileSeparator,
            childrenDirs = listOf(userHomeExploreFile) + otherExploreFiles,
            childrenFiles = emptyList()
        )
    } else {
        val currentFile = File(fixedRequestPath)
        return if (currentFile.isDirectory && currentFile.canRead()) {
            try {
                val children = currentFile.listFiles() ?: emptyArray<File>()
                val childrenDirs = mutableListOf<FileExploreDir>()
                val childrenFiles = mutableListOf<FileExploreFile>()
                for (c in children) {
                    if (c.canRead()) {
                        if (c.isDirectory) {
                            childrenDirs.add(c.toFileExploreDir())
                        } else {
                            if (c.length() > 0) {
                                childrenFiles.add(c.toFileExploreFile())
                            }
                        }
                    }
                }
                ScanDirResp(
                    path = requestPath,
                    childrenDirs = childrenDirs,
                    childrenFiles = childrenFiles
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                ScanDirResp(
                    path = requestPath,
                    childrenDirs = emptyList(),
                    childrenFiles = emptyList()
                )
            }
        } else {
            ScanDirResp(
                path = requestPath,
                childrenDirs = emptyList(),
                childrenFiles = emptyList()
            )
        }
    }
}

fun File.toFileExploreDir(): FileExploreDir {
    return if (isDirectory) {
        FileExploreDir(
            name = name,
            path = this.canonicalPath,
            childrenCount = listFiles()?.size ?: 0,
            lastModify = lastModified()
        )
    } else {
        error("${this.canonicalPath} is not dir.")
    }
}

fun File.toFileExploreFile(): FileExploreFile {
    return if (isFile) {
        FileExploreFile(
            name = name,
            path = this.canonicalPath,
            size = length(),
            lastModify = lastModified()
        )
    } else {
        error("${this.canonicalPath} is not file")
    }
}

fun List<FileLeaf.CommonFileLeaf>.toExploreFiles(): List<FileExploreFile> {
    return this.map {
        FileExploreFile(
            name = it.name,
            path = it.path,
            size = it.size,
            lastModify = it.lastModified
        )
    }
}

fun File.hasTargetParent(targetParent: File): Boolean {
    return if (canonicalPath == targetParent.canonicalPath) {
        true
    } else {
        val parent = parentFile
        if (parent == null) {
            false
        } else {
            if (parent.canonicalPath == targetParent.canonicalPath) {
                true
            } else {
                parent.hasTargetParent(targetParent)
            }
        }
    }
}
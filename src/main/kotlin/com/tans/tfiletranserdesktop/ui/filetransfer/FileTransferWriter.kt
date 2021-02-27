package com.tans.tfiletranserdesktop.ui.filetransfer

import com.tans.tfiletranserdesktop.file.CommonFileLeaf
import com.tans.tfiletranserdesktop.file.FileConstants
import com.tans.tfiletranserdesktop.net.commonNetBufferPool
import com.tans.tfiletranserdesktop.net.filetransporter.FolderChildrenShareWriterHandle
import com.tans.tfiletranserdesktop.net.filetransporter.RequestFilesShareWriterHandle
import com.tans.tfiletranserdesktop.net.filetransporter.RequestFolderChildrenShareWriterHandle
import com.tans.tfiletranserdesktop.net.filetransporter.SendMessageShareWriterHandle
import com.tans.tfiletranserdesktop.net.model.File
import com.tans.tfiletranserdesktop.net.model.Folder
import com.tans.tfiletranserdesktop.net.model.ResponseFolderModel
import com.tans.tfiletranserdesktop.utils.writeSuspendSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.streams.toList

suspend fun newRequestFolderChildrenShareWriterHandle(
    path: String
): RequestFolderChildrenShareWriterHandle {
    val pathData = path.toByteArray(Charsets.UTF_8)
    return RequestFolderChildrenShareWriterHandle(
        pathSize = pathData.size
    ) { outputStream ->
        val writer = Channels.newChannel(outputStream)
        val buffer = commonNetBufferPool.requestBuffer()
        try {
            writer.writeSuspendSize(buffer, pathData)
        } finally {
            commonNetBufferPool.recycleBuffer(buffer)
        }
    }
}

suspend fun newFolderChildrenShareWriterHandle(
    parentPath: String
): FolderChildrenShareWriterHandle {
    val jsonData = withContext(Dispatchers.IO) {
        val path = Paths.get(FileConstants.USER_HOME + parentPath)
        val children = if (Files.isReadable(path)) {
            Files.list(path)
                .filter { Files.isReadable(it) }
                .map { p ->
                    val name = p.fileName.toString()
                    val lastModify = OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()),
                        ZoneId.systemDefault()
                    )
                    val pathString =
                        if (parentPath.endsWith(FileConstants.FILE_SEPARATOR)) parentPath + name else parentPath + FileConstants.FILE_SEPARATOR + name
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
                }.toList()
        } else {
            emptyList()
        }
        val responseFolder = ResponseFolderModel(
            path = parentPath,
            childrenFiles = children.filterIsInstance<File>(),
            childrenFolders = children.filterIsInstance<Folder>()
        )
        FolderChildrenShareWriterHandle.getJsonString(responseFolder).toByteArray(Charsets.UTF_8)
    }

    return FolderChildrenShareWriterHandle(
        filesJsonSize = jsonData.size
    ) { outputStream ->
        val writer = Channels.newChannel(outputStream)
        val byteBuffer = commonNetBufferPool.requestBuffer()
        try {
            writer.writeSuspendSize(byteBuffer, jsonData)
        } finally {
            commonNetBufferPool.recycleBuffer(byteBuffer)
        }
    }
}

suspend fun newRequestFilesShareWriterHandle(
    files: List<File>
): RequestFilesShareWriterHandle {
    val jsonData = RequestFilesShareWriterHandle.getJsonString(files).toByteArray(Charsets.UTF_8)
    return RequestFilesShareWriterHandle(
        filesJsonDataSize = jsonData.size
    ) { outputStream ->
        val writer = Channels.newChannel(outputStream)
        val byteBuffer = commonNetBufferPool.requestBuffer()
        try {
            writer.writeSuspendSize(byteBuffer, jsonData)
        } finally {
            commonNetBufferPool.recycleBuffer(byteBuffer)
        }
    }
}

suspend fun newSendMessageShareWriterHandle(
    message: String
): SendMessageShareWriterHandle {
    val messageData = message.toByteArray(Charsets.UTF_8)
    return SendMessageShareWriterHandle(
        messageSize = messageData.size
    ) { outputStream ->
        val writer = Channels.newChannel(outputStream)
        val buffer = commonNetBufferPool.requestBuffer()
        try {
            writer.writeSuspendSize(buffer, messageData)
        } finally {
            commonNetBufferPool.recycleBuffer(buffer)
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
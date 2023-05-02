package com.tans.tfiletranserdesktop.net

import com.tans.tfiletranserdesktop.file.FileConstants
import com.tans.tfiletranserdesktop.utils.getCurrentOs
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

const val BROADCAST_RECEIVER_PORT = 6666
const val BROADCAST_LISTENER_PORT = 6667

const val BROADCAST_SERVER_ACCEPT: Byte = 0x00
const val BROADCAST_SERVER_DENY: Byte = 0x01

// TCP Porter
const val FILE_TRANSPORT_LISTEN_PORT = 6668

// TCP Porter
const val MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT = 6669

val USER_NAME = System.getProperty("user.name") ?: ""
val DEVICE_NAME = getCurrentOs().name

val LOCAL_DEVICE = "$USER_NAME's $DEVICE_NAME"

// 512 KB
const val NET_BUFFER_SIZE = 1024 * 512

val commonNetBufferPool = NetBufferPool(
    poolSize = 100,
    bufferSize = NET_BUFFER_SIZE
)

val downloadDir: Path by lazy {
    val result = Paths.get("${FileConstants.USER_HOME}${FileConstants.FILE_SEPARATOR}Downloads", "tFileTransfer")
    if (!Files.exists(result)) {
        Files.createDirectories(result)
    }
    result
}
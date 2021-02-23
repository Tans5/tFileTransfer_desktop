package com.tans.tfiletranserdesktop.utils

import com.tans.tfiletranserdesktop.file.FileConstants.GB
import com.tans.tfiletranserdesktop.file.FileConstants.KB
import com.tans.tfiletranserdesktop.file.FileConstants.MB
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

fun Path.newChildFile(name: String): Path {
    val childPath = Paths.get(toAbsolutePath().toString(), name)
    return if (Files.exists(childPath)) {
        val regex1 = "((.|\\s)+)-(\\d+)(\\..+)$".toRegex()
        val regex2 = "((.|\\s)+)(\\..+)\$".toRegex()
        val regex3 = "((.|\\s)+)-(\\d+)$".toRegex()
        when {
            regex1.matches(name) -> {
                val values = regex1.find(name)!!.groupValues
                newChildFile("${values[1]}-${values[3].toInt() + 1}${values[4]}")
            }
            regex2.matches(name) -> {
                val values = regex2.find(name)!!.groupValues
                newChildFile("${values[1]}-1${values[3]}")
            }
            regex3.matches(name) -> {
                val values = regex3.find(name)!!.groupValues
                newChildFile("${values[1]}-${values[3].toInt() + 1}")
            }
            else -> newChildFile("$name-1")
        }
    } else {
        Files.createFile(childPath)
        childPath
    }
}

fun getSizeString(size: Long): String = when (size) {
    in 0 until KB -> "${size}B"
    in KB until MB -> "${size.toDouble() / KB}KB"
    in MB until GB -> "${size.toDouble() / MB}MB"
    in GB until Long.MAX_VALUE -> "${size.toDouble() / GB}GB"
    else -> ""
}

// 1MB
const val FILE_BUFFER_SIZE: Int = 1024 * 1024

/**
 * return the size is 16.
 */
fun Path.getFileMd5(): ByteArray {
    if (!Files.exists(this) || Files.isDirectory(this)) {
        error("Wrong file path: ${this.toAbsolutePath()}")
    }
    val md5 = MessageDigest.getInstance("MD5")
    val fileChannel = FileChannel.open(this, StandardOpenOption.READ)
    val byteBuffer = ByteBuffer.allocate(FILE_BUFFER_SIZE)
    fileChannel.use {
        while (true) {
            byteBuffer.clear()
            if (fileChannel.read(byteBuffer) == -1) {
                break
            }
            byteBuffer.flip()
            md5.update(byteBuffer)
        }
    }
    return md5.digest()
}

/**
 * return the size is 16.
 */
fun Path.getFilePathMd5(): ByteArray {
    val pathStringData = toAbsolutePath().toString().toByteArray(Charsets.UTF_8)
    val md5 = MessageDigest.getInstance("MD5")
    return md5.digest(pathStringData)
}

fun ByteBuffer.readFrom(source: ByteBuffer, size: Int) {
    clear()
    val mySize = capacity()
    val sourceSize = source.capacity()
    if (mySize < size || sourceSize < size) {
        error("Wrong Size: $size")
    }
    for (i in 0 until size) {
        put(source.get())
    }
    flip()
}
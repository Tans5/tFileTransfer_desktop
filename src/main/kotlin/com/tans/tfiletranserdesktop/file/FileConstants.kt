package com.tans.tfiletranserdesktop.file

import java.nio.file.FileSystem
import java.nio.file.FileSystems

object FileConstants {
    val FILE_SYSTEM: FileSystem = FileSystems.getDefault()
    val FILE_SEPARATOR: String = FILE_SYSTEM.separator
    val USER_HOME: String = System.getProperty("user.home")
    const val KB = 1024
    const val MB = 1024 * 1024
    const val GB = 1024 * 1024 * 1024
}
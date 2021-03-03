package com.tans.tfiletranserdesktop.utils


enum class DesktopOs {
    Windows,
    MacOsX,
    Linux
}

val currentUseOs = getCurrentOs()

fun getCurrentOs(): DesktopOs {
    val os = System.getProperty("os.name")
    return when {
        os.contains("Windows") -> DesktopOs.Windows
        os.contains("Mac") -> DesktopOs.MacOsX
        else -> DesktopOs.Linux
    }
}


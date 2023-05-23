package com.tans.tfiletranserdesktop.file

import java.io.File


fun File.toFileLeaf(): FileLeaf.CommonFileLeaf {
    return FileLeaf.CommonFileLeaf(
        name = name,
        path = canonicalPath,
        size = length(),
        lastModified = lastModified()
    )
}

fun File.toDirLeaf(): FileLeaf.DirectoryFileLeaf {
    return FileLeaf.DirectoryFileLeaf(
        name = name,
        path = canonicalPath,
        childrenCount = listFiles()?.size?.toLong() ?: 0L,
        lastModified = lastModified()
    )
}

fun File.childrenLeafs(): Pair<List<FileLeaf.DirectoryFileLeaf>, List<FileLeaf.CommonFileLeaf>> {
    val children = listFiles() ?: emptyArray<File>()
    val resultFiles = mutableListOf<FileLeaf.CommonFileLeaf>()
    val resultDirs = mutableListOf<FileLeaf.DirectoryFileLeaf>()
    for (c in children) {
        try {
            if (c.canRead()) {
                if (c.isFile) {
                    if (c.length() > 0) {
                        resultFiles.add(c.toFileLeaf())
                    }
                } else {
                    resultDirs.add(c.toDirLeaf())
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    return resultDirs to resultFiles
}

fun createLocalRootTree(): FileTree {
    val fileSeparator = File.separator
    val rootFiles = File.listRoots() ?: emptyArray()
    return if (rootFiles.isEmpty() || !userHomeDir.canRead()) {
        FileTree(
            dirLeafs = emptyList(),
            fileLeafs = emptyList(),
            path = fileSeparator,
            parentTree = null
        )
    } else {
        val userHomeDirLeaf = FileLeaf.DirectoryFileLeaf(
            name = "User Home",
            path = userHomeDir.canonicalPath,
            childrenCount = userHomeDir.listFiles()?.size?.toLong() ?: 0L,
            lastModified = userHomeDir.lastModified()
        )
        val othersDirLeafs = rootFiles
            .filter { !userHomeDir.hasTargetParent(it) }
            .map {
                FileLeaf.DirectoryFileLeaf(
                    name = it.canonicalPath,
                    path = it.canonicalPath,
                    childrenCount = it.listFiles()?.size?.toLong() ?: 0L,
                    lastModified = it.lastModified()
                )
            }
        FileTree(
            dirLeafs = listOf(userHomeDirLeaf) + othersDirLeafs,
            fileLeafs = emptyList(),
            path = fileSeparator,
            parentTree = null
        )
    }
}

fun FileTree.newLocalSubTree(
    dirLeaf: FileLeaf.DirectoryFileLeaf
): FileTree {
    val file = File(dirLeaf.path)
    val (dirLeafs, fileLeafs) = file.childrenLeafs()
    return FileTree(
        dirLeafs = dirLeafs,
        fileLeafs = fileLeafs,
        path = file.canonicalPath,
        parentTree = this
    )
}
package com.tans.tfiletranserdesktop.net.filetransporter

import com.tans.tfiletranserdesktop.core.Stateable
import com.tans.tfiletranserdesktop.file.FileConstants
import com.tans.tfiletranserdesktop.net.FILE_TRANSPORT_LISTEN_PORT
import com.tans.tfiletranserdesktop.net.commonNetBufferPool
import com.tans.tfiletranserdesktop.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx2.await
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousSocketChannel


const val VERSION_INT: Byte = 0x01


@Throws(IOException::class)
suspend fun FileTransporter.launchFileTransport(
        isServer: Boolean,
        handle: suspend FileTransporter.() -> Unit
) = coroutineScope {
    launch(Dispatchers.IO) {
        if (isServer) {
            startAsServer()
        } else {
            startAsClient()
        }
    }
    launch(Dispatchers.IO) { handle.invoke(this@launchFileTransport) }
}


class FileTransporter(private val localAddress: InetAddress,
                      private val remoteAddress: InetAddress,
                      private val localFileSystemSeparator: String = FileConstants.FILE_SEPARATOR): Stateable<String> by Stateable("") {

    private val requestFolderChildrenShareReaderHandle = RequestFolderChildrenShareReaderHandle()
    private val folderChildrenShareReaderHandle = FolderChildrenShareReaderHandle()
    private val requestFileShareReaderHandle = RequestFilesShareReaderHandle()
    private val fileShareReaderHandle = FilesShareReaderHandle()
    private val sendMessageReaderHandle = SendMessageReaderHandle()

    val writerHandleChannel = Channel<FileTransporterWriterHandle>(Channel.UNLIMITED)

    @Throws(IOException::class)
    internal suspend fun startAsClient() {
        val sc = openAsynchronousSocketChannel()
        delay(500)
        val buffer = commonNetBufferPool.requestBuffer()
        val result = kotlin.runCatching {
            sc.use {
                sc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOptionSuspend(StandardSocketOptions.SO_KEEPALIVE, true)
                sc.connectSuspend(InetSocketAddress(remoteAddress, FILE_TRANSPORT_LISTEN_PORT))
                sc.readSuspendSize(buffer, 1)
                if (buffer.get() == VERSION_INT) {
                    val separatorBytes = localFileSystemSeparator.toByteArray(Charsets.UTF_8)
                    sc.writeSuspendSize(buffer, separatorBytes.size.toBytes() + separatorBytes)
                    sc.readSuspendSize(buffer, 4)
                    val separatorSize = buffer.asIntBuffer().get()
                    sc.readSuspendSize(buffer, separatorSize)
                    val remoteSeparator = String(buffer.copyAvailableBytes(), Charsets.UTF_8)
                    updateState { remoteSeparator }.await()
                    handleAction(sc, remoteSeparator)
                } else {
                    throw VersionCheckError
                }
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
        if (result.isFailure) {
            stateStore.onError(result.exceptionOrNull()!!)
            throw result.exceptionOrNull()!!
        }
    }

    @Throws(IOException::class)
    internal suspend fun startAsServer() {
        val ssc = openAsynchronousServerSocketChannelSuspend()
        val buffer = commonNetBufferPool.requestBuffer()
        val result = kotlin.runCatching {
            ssc.use {
                ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                ssc.bindSuspend(InetSocketAddress(localAddress, FILE_TRANSPORT_LISTEN_PORT), 1)
                val sc = ssc.acceptSuspend()
                sc.use {
                    sc.writeSuspendSize(buffer, arrayOf(VERSION_INT).toByteArray())
                    sc.readSuspendSize(buffer, 4)
                    val size = buffer.asIntBuffer().get()
                    sc.readSuspendSize(buffer, size)
                    val remoteSeparator = String(buffer.copyAvailableBytes(), Charsets.UTF_8)
                    val localSeparatorData = localFileSystemSeparator.toByteArray(Charsets.UTF_8)
                    sc.writeSuspendSize(buffer, localSeparatorData.size.toBytes() + localSeparatorData)
                    updateState { remoteSeparator }.await()
                    handleAction(sc, remoteSeparator)
                }
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
        if (result.isFailure) {
            stateStore.onError(result.exceptionOrNull()!!)
            throw result.exceptionOrNull()!!
        }
    }

    /**
     * @return remote device file separator.
     */
    suspend fun whenConnectReady(): String = bindState().filter { it.isNotEmpty() }
        .firstOrError()
        .await()

    suspend fun requestFolderChildrenShareChain(readerHandleChain: ReaderHandleChain<Unit>) {
        requestFolderChildrenShareReaderHandle.newChain(readerHandleChain)
    }

    suspend fun folderChildrenShareChain(readerHandleChain: ReaderHandleChain<Unit>) {
        folderChildrenShareReaderHandle.newChain(readerHandleChain)
    }

    suspend fun requestFilesShareChain(readerHandleChain: ReaderHandleChain<Unit>) {
        requestFileShareReaderHandle.newChain(readerHandleChain)
    }

    suspend fun filesShareDownloader(downloader: FileDownloader) {
        fileShareReaderHandle.newDownloader(downloader)
    }

    suspend fun sendMessageChain(readerHandleChain: ReaderHandleChain<Unit>) {
        sendMessageReaderHandle.newChain(readerHandleChain)
    }

    suspend fun startWriterHandleWhenFinish(writerHandle: FileTransporterWriterHandle) {
        writerHandleChannel.send(writerHandle)
        writerHandle.bindState()
                .filter { it == FileTransporterWriterHandle.Companion.TransporterWriterState.Finish }
                .firstOrError()
                .await()
    }

    private suspend fun handleAction(sc: AsynchronousSocketChannel, remoteFileSeparator: String) = coroutineScope {

        // Read
        launch {
            val actionBuffer = commonNetBufferPool.requestBuffer()
            val result = kotlin.runCatching {
                while (true) {
                    sc.readSuspendSize(actionBuffer, 1)
                    when (val actionCode = actionBuffer.get()) {
                        FileNetAction.RequestFolderChildrenShare.actionCode -> requestFolderChildrenShareReaderHandle.handle(sc)
                        FileNetAction.FolderChildrenShare.actionCode -> folderChildrenShareReaderHandle.handle(sc)
                        FileNetAction.RequestFilesShare.actionCode -> requestFileShareReaderHandle.handle(sc)
                        FileNetAction.FilesShare.actionCode -> fileShareReaderHandle.handle(sc)
                        FileNetAction.SendMessage.actionCode -> sendMessageReaderHandle.handle(sc)
                        else -> error("Unknown Action Code: $actionCode")
                    }
                }
            }
            commonNetBufferPool.recycleBuffer(actionBuffer)
            if (result.isFailure) {
                throw result.exceptionOrNull()!!
            }
        }

        // Write
        launch {
            while (true) {
                val writerHandle = writerHandleChannel.receive()
                writerHandle.updateState(FileTransporterWriterHandle.Companion.TransporterWriterState.Start)
                writerHandle.handle(sc)
                writerHandle.updateState(FileTransporterWriterHandle.Companion.TransporterWriterState.Finish)
            }
        }
    }

    companion object {
        object VersionCheckError : IOException("Version Check Error")
    }

}
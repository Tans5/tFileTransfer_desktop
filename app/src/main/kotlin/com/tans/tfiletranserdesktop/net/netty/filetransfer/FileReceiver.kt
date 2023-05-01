package com.tans.tfiletranserdesktop.net.netty.filetransfer

import com.tans.tfiletranserdesktop.logs.Log
import com.tans.tfiletranserdesktop.net.MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT
import com.tans.tfiletranserdesktop.net.model.FileMd5
import com.tans.tfiletranserdesktop.net.netty.common.NettyPkg
import com.tans.tfiletranserdesktop.net.netty.common.handler.toBytes
import com.tans.tfiletranserdesktop.net.netty.common.handler.writePkg
import com.tans.tfiletranserdesktop.net.netty.common.setDefaultHandler
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

private val ioExecutor = Dispatchers.IO.asExecutor()


const val MAX_CLIENT_CONNECTIONS: Int = 15
// 10 MB
const val MIN_CLIENT_FRAME_SIZE: Long = 1024 * 1024 * 10L

typealias ConnectionCancelObserver = (notifyToRemote: Boolean) -> Boolean

// Client
fun downloadFileObservable(
    fileMd5: FileMd5,
    serverAddress: InetAddress,
    saveFile: Path
): Observable<Long> {

    return Observable.create { emitter ->
        val fileData = fileMd5.file
        val fileMd5ByteArray = fileMd5.md5
        val fileSize = fileData.size
        val downloadProgress = AtomicLong(0L)

        val realFile = saveFile.toFile().apply { if (this.exists()) { delete() }; createNewFile() }
        val randomAccessFile = RandomAccessFile(realFile, "rw")
        randomAccessFile.use { randomAccessFile.setLength(fileSize) }

        val connectionCancelObserver = LinkedBlockingDeque<ConnectionCancelObserver>()

        val (frameSize: Long, frameCount: Int) = if (fileSize <= MIN_CLIENT_FRAME_SIZE * MAX_CLIENT_CONNECTIONS) {
            MIN_CLIENT_FRAME_SIZE to (fileSize / MIN_CLIENT_FRAME_SIZE).toInt() + if (fileSize % MIN_CLIENT_FRAME_SIZE != 0L) 1 else 0
        } else {
            val frameSize = fileSize / max(MAX_CLIENT_CONNECTIONS - 1, 1)
            frameSize to max((if (fileSize % frameSize > 0L) MAX_CLIENT_CONNECTIONS else MAX_CLIENT_CONNECTIONS - 1), 1)
        }
        val downloadState = AtomicBoolean(true)

        fun emitterNextOrComplete() {
            if (downloadState.get()) {
                val p = downloadProgress.get()
                if (!emitter.isDisposed) {
                    Log.d("Receive file ${fileData.name} process: ${String.format("%.2f", p.toDouble() / fileSize.toDouble() * 100.0)}%")
                    emitter.onNext(p)
                }
                if (p >= fileSize) {
                    Log.d("Receive file ${fileData.name} finish.")
                    downloadState.set(false)
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
            }
        }

        fun tryChancelConnection(notifyToServer: Boolean, throwable: Throwable?) {
            if (downloadState.compareAndSet(true, false)) {
                if (downloadProgress.get() >= fileSize) {
                    emitterNextOrComplete()
                } else {
                    Log.e("Receive file error", throwable)
                    var hasSendToServer = false
                    for (o in connectionCancelObserver) {
                        if (notifyToServer) {
                            if (hasSendToServer) {
                                o(false)
                            } else {
                                hasSendToServer = o(true)
                            }
                        } else {
                            o(false)
                        }
                    }
                    if (realFile.exists()) {
                        realFile.delete()
                    }
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("User cancel or error."))
                    }
                }
            }
        }

        emitter.setCancellable {
            tryChancelConnection(true, Throwable("User canceled."))
        }


        fun downloadFrame(
            start: Long,
            end: Long
        ) {
            ioExecutor.execute {
                val currentFrameSize = end - start
                val localDownloadSize = AtomicLong(0)
                if (downloadState.get() && currentFrameSize > 0) {
                    val childEventLoopGroup = NioEventLoopGroup(MAX_CLIENT_CONNECTIONS, ioExecutor)
                    var c: Channel? = null
                    val co: ConnectionCancelObserver = { notifyServer ->
                        if (c?.isActive == true) {
                            if (notifyServer) {
                                try {
                                    c?.writePkg(NettyPkg.ServerFinishPkg("Client cancel"))
                                } catch (t: Throwable) {
                                    t.printStackTrace()
                                }
                                c?.close()
                                true
                            } else {
                                c?.close()
                                false
                            }
                        } else {
                            false
                        }
                    }
                    try {
                        connectionCancelObserver.add(co)
                        val bootstrap = Bootstrap()
                            .group(childEventLoopGroup)
                            .channel(NioSocketChannel::class.java)
                            .option(ChannelOption.TCP_NODELAY, true)
                            .handler(object : ChannelInitializer<NioSocketChannel>() {
                                override fun initChannel(ch: NioSocketChannel?) {
                                    ch?.pipeline()
                                        ?.setDefaultHandler()
                                        ?.addLast(object : ChannelDuplexHandler() {
                                            override fun channelActive(ctx: ChannelHandlerContext?) {
                                                if (ctx != null) {
                                                    ioExecutor.execute {
                                                        try {
                                                            val bytes = fileMd5ByteArray + start.toBytes() + end.toBytes()
                                                            ctx.channel().writePkg(NettyPkg.BytesPkg(bytes))
                                                        } catch (t: Throwable) {
                                                            tryChancelConnection(false, t)
                                                        }
                                                    }
                                                }
                                            }

                                            override fun channelRead(
                                                ctx: ChannelHandlerContext?,
                                                msg: Any?
                                            ) {
                                                if (msg is NettyPkg) {
                                                    ioExecutor.execute {
                                                        when (msg) {
                                                            is NettyPkg.BytesPkg -> {
                                                                try {
                                                                    val randomWriteFile =
                                                                        RandomAccessFile(realFile, "rw").apply {
                                                                            seek(start + localDownloadSize.get())
                                                                        }
                                                                    randomWriteFile.use {
                                                                        val s = msg.bytes.size.toLong()
                                                                        it.write(msg.bytes)
                                                                        localDownloadSize.addAndGet(s)
                                                                        downloadProgress.addAndGet(s)
                                                                        emitterNextOrComplete()
                                                                    }
                                                                } catch (t: Throwable) {
                                                                    tryChancelConnection(false, t)
                                                                }
                                                            }
                                                            NettyPkg.TimeoutPkg -> {
                                                                tryChancelConnection(false, Throwable("Timeout"))
                                                            }

                                                            is NettyPkg.ClientFinishPkg -> {
                                                                tryChancelConnection(false, Throwable("Finish"))
                                                            }
                                                            else -> {

                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        })
                                }

                                override fun exceptionCaught(
                                    ctx: ChannelHandlerContext?,
                                    cause: Throwable?
                                ) {
                                    tryChancelConnection(false, cause)
                                }
                            })
                        c = bootstrap.connect(InetSocketAddress(serverAddress, MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT)).sync().channel()
                        c?.closeFuture()?.sync()
                    } catch (t: Throwable) {
                        tryChancelConnection(false, t)
                    } finally {
                        childEventLoopGroup.shutdownGracefully()
                        connectionCancelObserver.remove(co)
                    }

                }
            }
        }

        (0 until frameCount)
            .map { i ->
                val start = i * frameSize
                val end = if (start + frameSize > fileSize) {
                    fileSize
                } else {
                    start + frameSize
                }
                downloadFrame(start, end)
            }

    }
}
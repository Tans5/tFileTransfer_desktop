package com.tans.tfiletranserdesktop.net.netty.fileexplore

import com.tans.tfiletranserdesktop.logs.Log
import com.tans.tfiletranserdesktop.net.FILE_TRANSPORT_LISTEN_PORT
import com.tans.tfiletranserdesktop.net.LOCAL_DEVICE
import com.tans.tfiletranserdesktop.net.model.*
import com.tans.tfiletranserdesktop.net.netty.common.NettyPkg
import com.tans.tfiletranserdesktop.net.netty.common.handler.writePkg
import com.tans.tfiletranserdesktop.net.netty.common.handler.writePkgBlockReply
import com.tans.tfiletranserdesktop.net.netty.common.setDefaultHandler
import com.tans.tfiletranserdesktop.utils.ioExecutor
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean


private const val FILE_EXPLORE_VERSION = 2

private fun FileExploreContentModel.toFileExploreBaseJsonString(): String {
    val (type, content) = when (this) {
        is FileExploreHandshakeModel -> FILE_MODEL_TYPE_HANDSHAKE to moshi.adapter(FileExploreHandshakeModel::class.java).toJson(this)
        is RequestFolderModel -> FILE_MODEL_TYPE_REQUEST_FOLDER to moshi.adapter(RequestFolderModel::class.java).toJson(this)
        is ShareFolderModel -> FILE_MODEL_TYPE_SHARE_FOLDER to moshi.adapter(ShareFolderModel::class.java).toJson(this)
        is RequestFilesModel -> FILE_MODEL_TYPE_REQUEST_FILES to moshi.adapter(RequestFilesModel::class.java).toJson(this)
        is ShareFilesModel -> FILE_MODEL_TYPE_SHARE_FILES to moshi.adapter(ShareFilesModel::class.java).toJson(this)
        is MessageModel -> FILE_MODEL_TYPE_MESSAGE to moshi.adapter(MessageModel::class.java).toJson(this)
    }
    val model = FileExploreBaseModel(
        type = type,
        content = content
    )
    return moshi.adapter(FileExploreBaseModel::class.java).toJson(model)
}

inline fun <reified T> String.fromJson(): T? {
    return moshi.adapter(T::class.java).fromJson(this)
}

inline fun <reified T> T.toJson(): String? {
    return moshi.adapter(T::class.java).toJson(this)
}

private fun String.toFileContentModel(): FileExploreContentModel? {
    val baseModel = moshi.adapter(FileExploreBaseModel::class.java).fromJson(this)
    return if (baseModel != null) {
        when (baseModel.type) {
            FILE_MODEL_TYPE_HANDSHAKE -> {
                baseModel.content.fromJson<FileExploreHandshakeModel>()
            }
            FILE_MODEL_TYPE_REQUEST_FOLDER -> {
                baseModel.content.fromJson<RequestFolderModel>()
            }
            FILE_MODEL_TYPE_SHARE_FOLDER -> {
                baseModel.content.fromJson<ShareFolderModel>()
            }
            FILE_MODEL_TYPE_REQUEST_FILES -> {
                baseModel.content.fromJson<RequestFilesModel>()
            }
            FILE_MODEL_TYPE_SHARE_FILES -> {
                baseModel.content.fromJson<ShareFilesModel>()
            }
            FILE_MODEL_TYPE_MESSAGE -> {
                baseModel.content.fromJson<MessageModel>()
            }
            else -> null
        }
    } else {
        null
    }
}

fun startFileExploreServer(localAddress: InetAddress): FileExploreConnection {
    var serverChannel: Channel? = null
    var clientChannel: Channel? = null
    val connection = FileExploreConnection(
        closeConnection = { notifyRemote ->
            if (notifyRemote && clientChannel?.isActive == true) {
                clientChannel?.writePkg(NettyPkg.ClientFinishPkg("Close"))
            }
            clientChannel?.close()
            serverChannel?.close()
        },
        sendFileExploreContent = { content, wait ->
            val channel = clientChannel
            if (channel?.isActive == true) {
                val msg = NettyPkg.JsonPkg(json = content.toFileExploreBaseJsonString())
                if (wait) {
                    channel.writePkgBlockReply(msg)
                } else {
                    channel.writePkg(msg)
                }
            }
        },
        isConnectionActiveCallback = {
            clientChannel?.isActive == true
        }
    )
    ioExecutor.execute {
        val clientConnected = AtomicBoolean(false)
        val connectionEventGroup = NioEventLoopGroup(1, ioExecutor)
        val rwEventGroup = NioEventLoopGroup(1, ioExecutor)
        try {
            val server = ServerBootstrap()
                .group(connectionEventGroup, rwEventGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel?) {
                        if (ch != null && clientConnected.compareAndSet(false, true)) {
                            ch.pipeline()
                                .setDefaultHandler()
                                .addLast(object : ChannelDuplexHandler() {
                                    override fun channelActive(ctx: ChannelHandlerContext?) {
                                        super.channelActive(ctx)
                                        val handshakeModel = FileExploreHandshakeModel(
                                            version = FILE_EXPLORE_VERSION,
                                            pathSeparator = File.separator,
                                            deviceName = LOCAL_DEVICE
                                        )
                                        ctx?.channel()?.writePkg(NettyPkg.JsonPkg(json = handshakeModel.toFileExploreBaseJsonString()))
                                    }

                                    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                                        super.channelRead(ctx, msg)
                                        if (msg != null && msg is NettyPkg && ctx != null) {
                                            ioExecutor.execute {
                                                when (msg) {
                                                    is NettyPkg.JsonPkg -> {
                                                        when (val model = msg.json.toFileContentModel()) {
                                                            is FileExploreHandshakeModel -> {
                                                                connection.connectionActive(model)
                                                                clientChannel = ctx.channel()
                                                            }
                                                            else -> {
                                                                if (model != null) {
                                                                    connection.newRemoteFileExploreContent(model)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    is NettyPkg.ServerFinishPkg, is NettyPkg.TimeoutPkg -> {
                                                        connection.close(false)
                                                        Log.e("File explore close or heartbeat timeout", null)
                                                    }
                                                    is NettyPkg.HeartBeatPkg -> {
                                                        Log.d("File explore receive heartbeat.")
                                                    }
                                                    else -> {
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    override fun channelInactive(ctx: ChannelHandlerContext?) {
                                        super.channelInactive(ctx)
                                        connection.close(false)
                                    }

                                    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                                        connection.close(false)
                                        Log.e("File explore error.", cause)
                                    }
                                })
                        } else {
                            ch?.close()
                        }
                    }
                })
            val channel = server.bind(InetSocketAddress(localAddress, FILE_TRANSPORT_LISTEN_PORT)).sync().channel()
            serverChannel = channel
            channel.closeFuture().sync()
        } catch (t: Throwable) {
            Log.e("File transfer start error", t)
            connection.close(false)
        } finally {
            connectionEventGroup.shutdownGracefully()
            rwEventGroup.shutdownGracefully()
        }
    }
    return connection
}

fun connectToFileExploreServer(remoteAddress: InetAddress): FileExploreConnection {
    var channel: Channel? = null
    val connection = FileExploreConnection(
        closeConnection = { notifyRemote ->
            if (notifyRemote && channel?.isActive == true) {
                channel?.writePkg(NettyPkg.ServerFinishPkg("Close"))
            }
            channel?.close()
        },
        sendFileExploreContent = { content, wait ->
            val channelLocal = channel
            if (channelLocal?.isActive == true) {
                val msg = NettyPkg.JsonPkg(json = content.toFileExploreBaseJsonString())
                if (wait) {
                    channelLocal.writePkgBlockReply(msg)
                } else {
                    channelLocal.writePkg(msg)
                }
            }
        },
        isConnectionActiveCallback = {
            channel?.isActive == true
        }
    )
    ioExecutor.execute {
        val eventGroup = NioEventLoopGroup(1, ioExecutor)
        Thread.sleep(100)
        try {
            val client = Bootstrap()
                .group(eventGroup)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel?) {
                        ch?.pipeline()
                            ?.setDefaultHandler()
                            ?.addLast(object : ChannelDuplexHandler() {

                                override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                                    if (msg != null && msg is NettyPkg && ctx != null) {
                                        ioExecutor.execute {
                                            when (msg) {
                                                is NettyPkg.JsonPkg -> {
                                                    when (val model = msg.json.toFileContentModel()) {
                                                        is FileExploreHandshakeModel -> {
                                                            val handshakeModel = FileExploreHandshakeModel(
                                                                version = FILE_EXPLORE_VERSION,
                                                                pathSeparator = File.separator,
                                                                deviceName = LOCAL_DEVICE
                                                            )
                                                            ctx.channel().writePkgBlockReply(NettyPkg.JsonPkg(handshakeModel.toFileExploreBaseJsonString()))
                                                            connection.connectionActive(model)
                                                        }
                                                        else -> {
                                                            if (model != null) {
                                                                connection.newRemoteFileExploreContent(model)
                                                            }
                                                        }
                                                    }
                                                }
                                                is NettyPkg.ClientFinishPkg, is NettyPkg.TimeoutPkg -> {
                                                    connection.close(false)
                                                    Log.e("File explore close or heartbeat timeout", null)
                                                }
                                                is NettyPkg.HeartBeatPkg -> {
                                                    Log.d("File explore receive heartbeat.")
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }

                                override fun channelInactive(ctx: ChannelHandlerContext?) {
                                    super.channelInactive(ctx)
                                    connection.close(false)
                                }
                                override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                                    connection.close(false)
                                    Log.e("File explore error.", cause)
                                }
                            })
                    }
                })

            val channelLocal = client.connect(InetSocketAddress(remoteAddress, FILE_TRANSPORT_LISTEN_PORT)).sync().channel()
            channel = channelLocal
            channelLocal.closeFuture().sync()
        } catch (t: Throwable) {
            Log.e("File transfer start error", t)
            connection.close(false)
        } finally {
            eventGroup.shutdownGracefully()
        }
    }
    return connection
}
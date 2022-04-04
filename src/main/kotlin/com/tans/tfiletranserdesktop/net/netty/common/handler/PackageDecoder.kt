package com.tans.tfiletranserdesktop.net.netty.common.handler

import com.tans.tfiletranserdesktop.net.netty.common.*
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent

class PackageDecoder : ByteToMessageDecoder() {

    override fun decode(ctx: ChannelHandlerContext, read: ByteBuf, out: MutableList<Any>) {
        read.markReaderIndex()
        when (read.readByte()) {
            HEART_BEHAT_PKG -> {
                out.add(NettyPkg.HeartBeatPkg)
            }
            CLIENT_FINISH_PKG -> {
                val bytes = read.readBytes()
                val msg = bytes.toString(Charsets.UTF_8)
                out.add(NettyPkg.ClientFinishPkg(msg = msg))
            }
            SERVER_FINISH_PKG -> {
                val bytes = read.readBytes()
                val msg = bytes.toString(Charsets.UTF_8)
                out.add(NettyPkg.ServerFinishPkg(msg = msg))
            }
            BYTES_PKG -> {
                val index = read.readInt().toUInt()
                val bytes = read.readBytes()
                out.add(NettyPkg.BytesPkg(bytes = bytes, pkgIndex = index))
                ctx.reply(index)
            }
            JSON_PKG -> {
                val index = read.readInt().toUInt()
                val bytes = read.readBytes()
                val json = bytes.toString(Charsets.UTF_8)
                out.add(NettyPkg.JsonPkg(json = json, pkgIndex = index))
                ctx.reply(index)
            }
            TEXT_PKG -> {
                val index = read.readInt().toUInt()
                val bytes = read.readBytes()
                val text = bytes.toString(Charsets.UTF_8)
                out.add(NettyPkg.TextPkg(text = text, pkgIndex = index))
                ctx.reply(index)
            }
            RESPONSE_PKG -> {
                val index = read.readInt().toUInt()
                out.add(NettyPkg.ResponsePkg(index))
            }
            else -> {
                out.add(read.resetReaderIndex())
            }
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        super.userEventTriggered(ctx, evt)
        if (evt is IdleStateEvent && evt.state() == IdleState.ALL_IDLE) {
            ctx?.fireChannelRead(NettyPkg.TimeoutPkg)
        }
    }

    private fun ChannelHandlerContext.reply(index: UInt) {
        writeAndFlush(NettyPkg.ResponsePkg(index))
    }

}
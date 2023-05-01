package com.tans.tfiletranserdesktop.net.netty.common.handler

import com.tans.tfiletranserdesktop.net.netty.common.*
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class PackageEncoder : MessageToByteEncoder<NettyPkg>() {

    override fun encode(ctx: ChannelHandlerContext, msg: NettyPkg, out: ByteBuf) {
        when (msg) {
            NettyPkg.HeartBeatPkg -> {
                out.writeByte(HEART_BEHAT_PKG.toInt())
            }
            is NettyPkg.ClientFinishPkg -> {
                out.writeByte(CLIENT_FINISH_PKG.toInt())
                out.writeBytes(msg.msg.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.ServerFinishPkg -> {
                out.writeByte(SERVER_FINISH_PKG.toInt())
                out.writeBytes(msg.msg.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.BytesPkg -> {
                out.writeByte(BYTES_PKG.toInt())
                out.writeBytes(msg.pkgIndex!!.toBytes())
                out.writeBytes(msg.bytes)
            }
            is NettyPkg.JsonPkg -> {
                out.writeByte(JSON_PKG.toInt())
                out.writeBytes(msg.pkgIndex!!.toBytes())
                out.writeBytes(msg.json.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.TextPkg -> {
                out.writeByte(TEXT_PKG.toInt())
                out.writeBytes(msg.pkgIndex!!.toBytes())
                out.writeBytes(msg.text.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.ResponsePkg -> {
                out.writeByte(RESPONSE_PKG.toInt())
                out.writeBytes(msg.pkgIndex.toBytes())
            }
            else -> {

            }
        }
    }

    private fun UInt.toBytes(): ByteArray = toLong().toBytes().takeLast(4).toByteArray()

}


fun Long.toBytes(): ByteArray = ByteArray(8) { index ->
    val slide = (7 - index) * 8
    (this and ((0x00_00_00_00_00_00_00_FF).toLong() shl slide) ushr slide).toByte()
}

fun ByteArray.toLong(): Long {
    var result = 0L
    if (size != 8) {
        error("Byte array size is not 8")
    } else {
        for ((index, b) in this.withIndex()) {
            val shiftCount = (7 - index) * 8
            val a = b.toUByte().toULong().toLong()
            result = (a shl shiftCount) or result
        }
        return result
    }
}
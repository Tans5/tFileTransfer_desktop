package com.tans.tfiletranserdesktop.net.netty.common.handler

import com.tans.tfiletranserdesktop.utils.ioExecutor
import com.tans.tfiletranserdesktop.net.netty.common.NettyPkg
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class HeartbeatChecker(private val durationSeconds: Int) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ioExecutor.execute {
            do {
                ctx.writeAndFlush(NettyPkg.HeartBeatPkg)
                Thread.sleep(durationSeconds * 1000L)
            } while (ctx.channel().isActive)
        }
    }
}
package com.tans.tfiletranserdesktop.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

class NetBufferPool(poolSize: Int, bufferSize: Int) {

    private val pools = Channel<ByteBuffer>(Channel.UNLIMITED)
    init {
        // Init Pool
        runBlocking {
            for (i in 0 until poolSize) {
                pools.send(ByteBuffer.allocate(bufferSize))
            }
        }
    }

    suspend fun requestBuffer() = pools.receive()


    fun requestBufferBlock(): ByteBuffer = runBlocking { pools.receive() }

    suspend fun recycleBuffer(buffer: ByteBuffer) {
        buffer.clear()
        pools.send(buffer)
    }

    fun recycleBufferBlock(buffer: ByteBuffer) = runBlocking {
        buffer.clear()
        pools.send(buffer)
    }

}
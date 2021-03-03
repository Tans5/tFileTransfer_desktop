package com.tans.tfiletranserdesktop.net

import com.tans.tfiletranserdesktop.utils.*
import com.tans.tfiletranserdesktop.core.Stateable
import io.reactivex.Observable
import kotlinx.coroutines.*
import kotlinx.coroutines.rx2.await
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import kotlin.jvm.Throws
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val BROADCAST_RECEIVER_PORT = 6666
const val BROADCAST_LISTENER_PORT = 6667

const val BROADCAST_SERVER_ACCEPT: Byte = 0x00
const val BROADCAST_SERVER_DENY: Byte = 0x01


// 512 KB
const val NET_BUFFER_SIZE = 1024 * 512

val commonNetBufferPool = NetBufferPool(
        poolSize = 100,
        bufferSize = NET_BUFFER_SIZE
)

typealias RemoteDevice = Pair<SocketAddress, String>

/**
 * if this method return without error, it means the user accept connect request.
 */
@Throws(IOException::class)
suspend fun launchBroadcastSender(
    // Unit: milli second
    broadcastDelay: Long = 300,
    broadMessage: String,
    localAddress: InetAddress,
    noneBroadcast: Boolean = false,
    acceptRequest: suspend (remoteAddress: SocketAddress, remoteDevice: String) -> Boolean): RemoteDevice = coroutineScope {
        val broadcastSender = BroadcastSender(
                broadcastDelay = broadcastDelay,
                broadMessage = broadMessage,
                localAddress = localAddress)
        val broadcastJob = launch(context = Dispatchers.IO) { if (noneBroadcast) broadcastSender.startNoneBroadcastSender() else broadcastSender.startBroadcastSender() }
        val result = withContext(context = Dispatchers.IO) { broadcastSender.startBroadcastListener(acceptRequest) }
        broadcastJob.cancel()
        result
}

class BroadcastSender(
    // Unit: milli second
    val broadcastDelay: Long = 300,
    val broadMessage: String,
    val localAddress: InetAddress) {


    @Throws(IOException::class)
    internal suspend fun startBroadcastSender() {
        val broadcastAddress = localAddress.getBroadcastAddress().first
        val dc = openDatagramChannel()
        val buffer = commonNetBufferPool.requestBuffer()
        val result = kotlin.runCatching {
            dc.use {
                dc.setOptionSuspend(StandardSocketOptions.SO_BROADCAST, true)
                dc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                buffer.put(broadMessage.toByteArray(Charsets.UTF_8).let {
                    if (it.size > NET_BUFFER_SIZE) {
                        it.take(NET_BUFFER_SIZE).toByteArray()
                    } else {
                        it
                    }
                })
                while (true) {
                    buffer.flip()
                    dc.sendSuspend(buffer, InetSocketAddress(broadcastAddress, BROADCAST_RECEIVER_PORT))
                    delay(broadcastDelay)
                }
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
        if (result.isFailure) {
            throw result.exceptionOrNull()!!
        }
    }

    @Throws(IOException::class)
    internal suspend fun startNoneBroadcastSender() {
        val subInt = localAddress.getBroadcastAddress().second.toInt()
        val subNets = localAddress.getSubNetAllAddress(subInt)
        val dc = openDatagramChannel()
        val buffer = commonNetBufferPool.requestBuffer()
        val result = kotlin.runCatching {
            dc.use {
                dc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                buffer.put(broadMessage.toByteArray(Charsets.UTF_8).let {
                    if (it.size > NET_BUFFER_SIZE) {
                        it.take(NET_BUFFER_SIZE).toByteArray()
                    } else {
                        it
                    }
                })
                while (true) {
                    for (subNet in subNets) {
                        buffer.flip()
                        kotlin.runCatching { dc.sendSuspend(buffer, InetSocketAddress(subNet, BROADCAST_RECEIVER_PORT)) }
                    }
                    delay(broadcastDelay)
                }
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
        if (result.isFailure) {
            throw result.exceptionOrNull()!!
        }
    }

    /**
     * If accept the connect request, close listener.
     *
     * 4 bytes: Remote Device Info Length. (Client)
     * Length bytes: Remote Device Info (Client)
     * 1 bytes: 1. 0x00: accept 2. 0x01: deny (Server)
     *
     */
    @Throws(IOException::class)
    internal suspend fun startBroadcastListener(acceptRequest: suspend (remoteAddress: SocketAddress, remoteDevice: String) -> Boolean): RemoteDevice {
        val ssc = openAsynchronousServerSocketChannelSuspend()
        var result: RemoteDevice? = null
        val buffer = commonNetBufferPool.requestBuffer()
        val runResult = kotlin.runCatching {
            ssc.use {
                ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                ssc.bindSuspend(InetSocketAddress(localAddress, BROADCAST_LISTENER_PORT), 1)
                while (true) {
                    val isAccept = ssc.acceptSuspend().use { clientSsc ->
                        clientSsc.readSuspendSize(buffer, 4)
                        // 1. Get message size
                        val remoteDeviceInfoSize = min(max(buffer.asIntBuffer().get(), 0), NET_BUFFER_SIZE)

                        // 2. Get remote device info.
                        clientSsc.readSuspendSize(buffer, remoteDeviceInfoSize)
                        val remoteInfo = String(buffer.copyAvailableBytes(), Charsets.UTF_8)

                        // 3. Accept or deny.
                        if (acceptRequest(clientSsc.remoteAddress, remoteInfo)) {
                            clientSsc.writeSuspendSize(buffer, ByteArray(1) { BROADCAST_SERVER_ACCEPT })
                            result = clientSsc.remoteAddress to remoteInfo
                            true
                        } else {
                            clientSsc.writeSuspendSize(buffer, ByteArray(1) { BROADCAST_SERVER_DENY })
                            false
                        }
                    }
                    if (isAccept) { break }
                }
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
        if (runResult.isFailure) {
            throw runResult.exceptionOrNull()!!
        }
        return result ?: error("Unknown error!!")
    }
}

@Throws(IOException::class)
suspend fun launchBroadcastReceiver(localAddress: InetAddress, timeoutRemove: Long = 5000, checkDuration: Long = 2000,
                                    noneBroadcast: Boolean = false,
                                    handle: suspend BroadcastReceiver.(receiverJob: Job) -> Unit) = coroutineScope {
    val broadcastReceiver = BroadcastReceiver(localAddress, timeoutRemove,checkDuration)
    val receiverJob: Job = launch (Dispatchers.IO) { broadcastReceiver.startBroadcastReceiver(noneBroadcast) }
    handle(broadcastReceiver, receiverJob)
}

class BroadcastReceiver(
        val localAddress: InetAddress,
        // TimeUnit: milli seconds
        private val timeoutRemove: Long = 5000,
        // TimeUnit: milli seconds
        private val checkDuration: Long = 2000
) : Stateable<List<Pair<RemoteDevice, Long>>> by Stateable(emptyList()) {
    private val broadcast = localAddress.getBroadcastAddress().first

    @Throws(IOException::class)
    internal suspend fun startBroadcastReceiver(noneBroadcast: Boolean = false) {

        coroutineScope {
            // Remote out of date devices.
            launch {
                while (true) {
                    val oldState = bindState().firstOrError().await()
                    val now = System.currentTimeMillis()
                    if (oldState.isNotEmpty() && oldState.any { abs(now - it.second) > timeoutRemove }) {
                        updateState { state ->
                            state.filter {
                                abs(now - it.second) <= timeoutRemove
                            }
                        }.await()
                    }
                    delay(checkDuration)
                }
            }

            // Receive broadcast
            launch {
                val dc = openDatagramChannel()
                dc.socket().soTimeout = Int.MAX_VALUE
                dc.setOptionSuspend(StandardSocketOptions.SO_BROADCAST, true)
                dc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                dc.bindSuspend(InetSocketAddress(if (noneBroadcast || currentUseOs == DesktopOs.Windows) localAddress else broadcast, BROADCAST_RECEIVER_PORT))
                val byteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
                while (true) {
                    byteBuffer.clear()
                    val remoteAddress = dc.receiveSuspend(byteBuffer)
                    byteBuffer.flip()
                    val remoteDeviceInfo = String(byteBuffer.copyAvailableBytes(), Charsets.UTF_8)
                    newRemoteDeviceComing(remoteAddress to remoteDeviceInfo)
                }
            }
        }
    }

    fun bindRemoveDevice(): Observable<List<RemoteDevice>> = bindState()
            .map { s -> s.map { it.first } }
            .distinctUntilChanged()

    /**
     * @see BroadcastSender startBroadcastListener method.
     */
    @Throws(IOException::class)
    suspend fun connectTo(address: InetAddress, yourDeviceInfo: String): Boolean {
        val sc = openAsynchronousSocketChannel()
        return sc.use {
            sc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
            sc.setOptionSuspend(StandardSocketOptions.SO_KEEPALIVE, true)
            sc.connectSuspend(InetSocketAddress(address, BROADCAST_LISTENER_PORT))
            val sendData = yourDeviceInfo.toByteArray(Charsets.UTF_8).let {
                if (it.size > NET_BUFFER_SIZE) {
                    it.take(NET_BUFFER_SIZE).toByteArray()
                } else {
                    it
                }
            }
            val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE + 4)
            sc.writeSuspendSize(buffer, sendData.size.toBytes() + sendData)
            sc.readSuspendSize(buffer, 1)
            val result = buffer.get()
            result == BROADCAST_SERVER_ACCEPT
        }
    }

    private suspend fun newRemoteDeviceComing(device: RemoteDevice) {
        updateState { oldState ->
            val now = System.currentTimeMillis()
            if (!oldState.any { it.first.second == device.second }) {
                oldState + (device to now)
            } else {
                oldState.map {
                    if (it.first.second == device.second) {
                        it.first to now
                    } else {
                        it
                    }
                }
            }
        }.await()
    }
}
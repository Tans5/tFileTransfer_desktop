package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.*
import com.tans.tfiletransporter.netty.extensions.*
import com.tans.tfiletransporter.netty.tcp.NettyTcpClientConnectionTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executor

object TcpClientTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val ioExecutor: Executor = Dispatchers.IO.asExecutor()
        val localAddress = findLocalAddressV4()[0]
        val t = NettyTcpClientConnectionTask(
            serverAddress = localAddress,
            serverPort = 1996
        )
            .withClient<ConnectionClientImpl>(log = TestLog)
            .withServer<ConnectionServerClientImpl>(log = TestLog)
        t.addObserver(object : NettyConnectionObserver {
            override fun onNewState(
                nettyState: NettyTaskState,
                task: INettyConnectionTask
            ) {
                println("ClientTaskState: $nettyState")
                if (nettyState is NettyTaskState.ConnectionActive) {
                    ioExecutor.execute {
                        repeat(1000) {
                            Thread.sleep(2000)
                            if (t.getCurrentState() !is NettyTaskState.ConnectionActive) return@execute
                            t.request(
                                type = 0,
                                request = "Hello, Server",
                                requestClass = String::class.java,
                                responseClass = String::class.java,
                                callback = object : IClientManager.RequestCallback<String> {
                                    override fun onSuccess(
                                        type: Int,
                                        messageId: Long,
                                        localAddress: InetSocketAddress?,
                                        remoteAddress: InetSocketAddress?,
                                        d: String
                                    ) {
                                        println("Request result: $d from $remoteAddress reply")
                                    }

                                    override fun onFail(errorMsg: String) {
                                       println("Request fail: $errorMsg")
                                    }

                                }
                            )
                        }
                    }
                }
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {
                println("Receive message from server: ${msg.body.toString(Charsets.UTF_8)}")
            }
        })
        t.startTask()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}
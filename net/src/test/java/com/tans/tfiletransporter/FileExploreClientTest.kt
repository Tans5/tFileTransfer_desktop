package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.FileExploreRequestHandler
import com.tans.tfiletransporter.transferproto.fileexplore.bindSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.connectSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.handshakeSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.model.DownloadFilesReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.DownloadFilesResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.SendFilesReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.SendFilesResp
import com.tans.tfiletransporter.transferproto.fileexplore.waitClose
import kotlinx.coroutines.runBlocking

object FileExploreClientTest {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val address = findLocalAddressV4()[0]
        val fileExplore = FileExplore(
            log = TestLog,
            scanDirRequest = object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
                override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp? {
                    return null
                }
            },
            sendFilesRequest = object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
                override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp? {
                    return null
                }
            },
            downloadFileRequest = object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
                override fun onRequest(
                    isNew: Boolean,
                    request: DownloadFilesReq
                ): DownloadFilesResp? {
                    return null
                }
            }
        )
        fileExplore.connectSuspend(address)
        println("Client: connection success.")
        val handShake = fileExplore.handshakeSuspend()
        println("Client: handshake success: $handShake")
        fileExplore.waitClose()
        println("Client: closed")
    }
}
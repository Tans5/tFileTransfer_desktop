package com.tans.tfiletranserdesktop.ui.localconnection

import com.tans.tfiletranserdesktop.file.LOCAL_DEVICE
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.utils.toJson
import com.tans.tfiletransporter.netty.toInt
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanServer
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanServerObserver
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanState
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeShare
import com.tans.tfiletransporter.transferproto.qrscanconn.startQRCodeScanServerSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import net.glxn.qrgen.core.image.ImageType
import net.glxn.qrgen.javase.QRCode
import java.net.InetAddress

class QRCodeServerDialog(
    private val localAddress: InetAddress,
    private val requestTransferFile: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit
) : BaseStatableDialog<QRCodeServerDialog.Companion.QRCodeServerState>(
    defaultState = QRCodeServerState(),
    cancelRequest = cancelRequest
) {

    private val qrcodeServer: QRCodeScanServer by lazy {
        QRCodeScanServer(log = JvmLog)
    }

    override fun initData() {
        launch(Dispatchers.IO) {
            qrcodeServer.addObserver(object : QRCodeScanServerObserver {
                override fun requestTransferFile(remoteDevice: RemoteDevice) {
                    JvmLog.d(TAG, "Receive request: $remoteDevice")
                    this@QRCodeServerDialog.requestTransferFile(remoteDevice)
                    cancel()
                }

                override fun onNewState(state: QRCodeScanState) {
                    JvmLog.d(TAG, "Qrcode server state: $state")
                }
            })
            runCatching {
                qrcodeServer.startQRCodeScanServerSuspend(localAddress = localAddress)
            }.onSuccess {
                JvmLog.d(TAG, "Bind address success.")
                runCatching {
                    val qrcodeContent = QRCodeShare(
                        version = TransferProtoConstant.VERSION,
                        deviceName = LOCAL_DEVICE,
                        address = localAddress.toInt()
                    ).toJson()!!
                    QRCode.from(qrcodeContent)
                        .withSize(320, 320)
                        .to(ImageType.PNG)
                        .stream()
                        .toByteArray()
                }.onSuccess {
                    updateState { state -> state.copy(qrcodeImages = it) }.await()
                }.onFailure {
                    JvmLog.e(TAG, "Create qrcode fail: ${it.message}", it)
                    cancel()
                }
            }.onFailure {
                val eMsg = "Bind address: $localAddress fail"
                JvmLog.e(TAG, eMsg)
                cancel()
            }
        }
    }
    override fun DialogContent() {

    }

    override fun stop() {
        super.stop()
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            qrcodeServer.closeConnectionIfActive()
        }
    }

    companion object {
        private const val TAG = "QRCodeServerDialog"

        data class QRCodeServerState(
            val qrcodeImages: ByteArray = byteArrayOf()
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as QRCodeServerState

                return qrcodeImages.contentEquals(other.qrcodeImages)
            }

            override fun hashCode(): Int {
                return qrcodeImages.contentHashCode()
            }
        }
    }
}
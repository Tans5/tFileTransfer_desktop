package com.tans.tfiletranserdesktop.ui.localconnection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.ui.resources.stringBroadcastSenderDialogCancel
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

@Composable
fun showQRCodeServerDialog(
    localAddress: InetAddress,
    localDeviceInfo: String,
    requestTransferFile: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit) {
    val d = QRCodeServerDialog(
        localAddress,
        localDeviceInfo,
        requestTransferFile,
        cancelRequest
    )
    d.initData()
    d.start()
}

class QRCodeServerDialog(
    private val localAddress: InetAddress,
    private val localDeviceInfo: String,
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
                        deviceName = localDeviceInfo,
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

    @Composable
    override fun DialogContent() {
        val imageByteArray = bindState().map { it.qrcodeImages }.subscribeAsState(byteArrayOf()).value
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            if (imageByteArray .isNotEmpty()) {
                Image(
                    bitmap = org.jetbrains.skia.Image.makeFromEncoded(imageByteArray).toComposeImageBitmap(),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(20.dp),
                    contentDescription = null
                )
            } else {
                Image(
                    painter = ColorPainter(Color.White),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentDescription = null,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { cancel() }
                ) {
                    Text(stringBroadcastSenderDialogCancel)
                }
            }
        }
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
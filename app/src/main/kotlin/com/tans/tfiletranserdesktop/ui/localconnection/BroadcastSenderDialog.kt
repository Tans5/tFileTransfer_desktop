package com.tans.tfiletranserdesktop.ui.localconnection

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.ui.resources.*
import com.tans.tfiletransporter.netty.getBroadcastAddress
import com.tans.tfiletransporter.transferproto.broadcastconn.*
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

@Composable
fun showBroadcastSenderDialog(
    localAddress: InetAddress,
    broadMessage: String,
    receiveConnect: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit) {
    val dialog = BroadcastSenderDialog(
        localAddress = localAddress,
        broadMessage = broadMessage,
        receiveConnect = receiveConnect,
        cancelRequest = cancelRequest
    )
    dialog.initData()
    dialog.start()
}


@Suppress("FunctionName")
class BroadcastSenderDialog(
    private val localAddress: InetAddress,
    private val broadMessage: String,
    private val receiveConnect: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit
) : BaseStatableDialog<Unit>(
    defaultState = Unit,
    cancelRequest = cancelRequest
) {

    private val sender: AtomicReference<BroadcastSender?> = AtomicReference(null)

    override fun initData() {
        launch {
            launch {
                val sender = BroadcastSender(
                    deviceName = broadMessage,
                    log = JvmLog
                )
                this@BroadcastSenderDialog.sender.set(sender)
                runCatching {
                    withContext(Dispatchers.IO) {
                        sender.startSenderSuspend(localAddress, localAddress.getBroadcastAddress().first)
                    }
                }.onSuccess {
                    JvmLog.d(TAG, "Start sender success.")
                    sender.addObserver(object : BroadcastSenderObserver {
                        override fun requestTransferFile(remoteDevice: RemoteDevice) {
                            receiveConnect(remoteDevice)
                            cancel()
                        }

                        override fun onNewState(state: BroadcastSenderState) {
                        }
                    })
                    sender.waitClose()
                    JvmLog.d(TAG, "Sender closed")
                    cancel()
                }.onFailure {
                    JvmLog.e(TAG, "Start sender error: ${it.message}")
                    cancel()
                }
            }
        }
    }

    @Composable
    override fun DialogContent() {
        Loading()
    }

    @Composable
    fun Loading() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringBroadcastSenderDialogTitle,
                style = styleDialogTitle
            )
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
        sender.get()?.let {
            sender.set(null)
            Dispatchers.IO.asExecutor().execute {
                Thread.sleep(1000L)
                it.closeConnectionIfActive()
            }
        }
    }

    companion object {
        private const val TAG = "BroadcastSenderDialog"
    }
}
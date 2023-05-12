package com.tans.tfiletranserdesktop.ui.broadcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.logs.JvmLog
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.ui.resources.*
import com.tans.tfiletranserdesktop.utils.DesktopOs
import com.tans.tfiletranserdesktop.utils.currentUseOs
import com.tans.tfiletransporter.netty.getBroadcastAddress
import com.tans.tfiletransporter.transferproto.broadcastconn.*
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

@Composable
fun showBroadcastReceiverDialog(
    localAddress: InetAddress,
    localDeviceInfo: String,
    connectTo: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit
) {
    val dialog = BroadcastReceiverDialog(
        localAddress = localAddress,
        localDeviceInfo = localDeviceInfo,
        connectTo = connectTo,
        cancelRequest = cancelRequest
    )
    dialog.initData()
    dialog.start()
}

data class BroadcastReceiverState(
    val searchedDevices: List<RemoteDevice> = emptyList(),
    val showLoading: Boolean = false,
)

@Suppress("FunctionName")
class BroadcastReceiverDialog(
    val localAddress: InetAddress,
    val localDeviceInfo: String,
    val connectTo: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit
) : BaseStatableDialog<BroadcastReceiverState>(
    defaultState = BroadcastReceiverState(),
    cancelRequest = cancelRequest
) {

    private val receiver: AtomicReference<BroadcastReceiver?> = AtomicReference(null)

    override fun initData() {
        launch {
            val receiver = BroadcastReceiver(deviceName = localDeviceInfo, log = JvmLog)
            this@BroadcastReceiverDialog.receiver.get()?.closeConnectionIfActive()
            this@BroadcastReceiverDialog.receiver.set(receiver)
            runCatching {
                withContext(Dispatchers.IO) {
                    receiver.startReceiverSuspend(if (currentUseOs == DesktopOs.Windows) localAddress else localAddress.getBroadcastAddress().first)
                }
            }.onSuccess {
                receiver.addObserver(object : BroadcastReceiverObserver {
                    override fun onNewBroadcast(
                        remoteDevice: RemoteDevice
                    ) {}
                    override fun onNewState(state: com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiverState) {}
                    override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {
                        launch { updateState { s -> s.copy(searchedDevices = remoteDevices, showLoading = remoteDevices.isEmpty()) }.await() }
                    }
                })
                receiver.waitCloseSuspend()
                JvmLog.d(TAG, "Canceled")
                cancel()
            }.onFailure {
                JvmLog.e(TAG, "Receiver error: ${it.message}", it)
                cancel()
            }
        }
    }

    @Composable
    override fun DialogContent() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringBroadcastReceiverDialogTitle,
                style = styleDialogTitle
            )

            Spacer(Modifier.height(6.dp))
            val showLoading = bindState().map { it.showLoading }.distinctUntilChanged().subscribeAsState(false)
            if (showLoading.value) {
               Loading()
            } else {
                val devices = bindState().map { it.searchedDevices }.distinctUntilChanged().subscribeAsState(emptyList())
                if (devices.value.isEmpty()) {
                    Loading()
                } else {
                    DevicesContent(devices = devices.value)
                }
            }

            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { cancel() }
                ) {
                    Text(stringBroadcastReceiverDialogCancel)
                }
            }
        }
    }

    @Composable
    fun Loading() {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    @Composable
    fun DevicesContent(devices: List<RemoteDevice>) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp)
        ) {
            items(
                count = devices.size,
                key = { i -> devices[i].remoteAddress }
            ) { i ->
                val device = devices[i]
                Column(
                    modifier = Modifier.fillMaxWidth().height(55.dp).padding(start = 5.dp)
                        .clickable {
                            launch {
                                val receiver = receiver.get()
                                if (receiver != null) {
                                    runCatching {
                                        receiver.requestFileTransferSuspend(device.remoteAddress.address)
                                    }.onSuccess {
                                        connectTo(device)
                                    }.onFailure {
                                        JvmLog.e(TAG, "Request transfer file fail: ${it.message}", it)
                                    }
                                } else {
                                    JvmLog.e(TAG, "Receiver is null.")
                                }
                                cancel()
                            }
                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = device.deviceName,
                        style = TextStyle(color = colorTextBlack, fontSize = 14.sp, fontWeight = FontWeight.W500),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = device.remoteAddress.hostString,
                        style = TextStyle(color = colorTextGray, fontSize = 12.sp, fontWeight = FontWeight.W500),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    override fun stop() {
        super.stop()
        this.receiver.get()?.let {
            it.closeConnectionIfActive()
            this.receiver.set(null)
        }
    }

    companion object {
        private const val TAG = "BroadcastReceiverDialog"
    }
}
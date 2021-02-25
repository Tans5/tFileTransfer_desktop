package com.tans.tfiletranserdesktop.ui.broadcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.net.RemoteDevice
import com.tans.tfiletranserdesktop.net.launchBroadcastReceiver
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.ui.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*

@Composable
fun showBroadcastReceiverDialog(
    localAddress: InetAddress,
    noneBroadcast: Boolean,
    localDeviceInfo: String,
    connectTo: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit
) {
    val dialog = BroadcastReceiverDialog(
        localAddress = localAddress,
        noneBroadcast = noneBroadcast,
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
    val requestConnectTo: Optional<RemoteDevice> = Optional.empty(),
    val connectHasAccept: Optional<RemoteDevice> = Optional.empty()
)

class BroadcastReceiverDialog(
    val localAddress: InetAddress,
    val noneBroadcast: Boolean,
    val localDeviceInfo: String,
    val connectTo: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit
) : BaseStatableDialog<BroadcastReceiverState>(
    defaultState = BroadcastReceiverState(),
    cancelRequest = cancelRequest
) {

    override fun initData() {
        launch {
            val result = kotlin.runCatching {
                launchBroadcastReceiver(
                    localAddress = localAddress,
                    noneBroadcast = noneBroadcast,
                    handle = { receiverJob ->
                        val stateJob = launch {
                            bindState()
                                .map { it.map { (device, _) -> device } }
                                .distinctUntilChanged()
                                .flatMapSingle { newDevices ->
                                    this@BroadcastReceiverDialog.updateState { it.copy(searchedDevices = newDevices) }
                                }
                                .ignoreElements()
                                .await()
                        }
                        val requestJob = launch {
                            this@BroadcastReceiverDialog.bindState()
                                .map { it.requestConnectTo }
                                .filter { it.isPresent }
                                .flatMapSingle {
                                    rxSingle(Dispatchers.IO) {
                                        this@BroadcastReceiverDialog.updateState { it.copy(showLoading = true) }.await()
                                        val address = (it.get().first as InetSocketAddress).address
                                        if (connectTo(
                                            address = address,
                                            yourDeviceInfo = localDeviceInfo
                                        )) {
                                            this@BroadcastReceiverDialog.updateState { oldState -> oldState.copy(connectHasAccept = it) }.await()
                                        }
                                        this@BroadcastReceiverDialog.updateState { it.copy(showLoading = false) }.await()
                                    }
                                }
                                .ignoreElements()
                                .await()
                        }
                        stateJob.join()
                        requestJob.join()
                        receiverJob.cancel("Dialog close")
                    }
                )
            }
            if (result.isFailure) { result.exceptionOrNull()?.printStackTrace() }
            this@BroadcastReceiverDialog.cancel()
        }

        launch {
            val accept = bindState().map { it.connectHasAccept }.filter { it.isPresent }.firstOrError().map { it.get() }.await()
            connectTo(accept)
            this@BroadcastReceiverDialog.cancel()
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
                key = { i -> devices[i].first }
            ) { i ->
                Column(
                    modifier = Modifier.fillMaxWidth().height(55.dp).padding(start = 5.dp)
                        .clickable {

                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    val device = devices[i]
                    Text(
                        text = device.second,
                        style = TextStyle(color = colorTextBlack, fontSize = 14.sp, fontWeight = FontWeight.W500),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = (device.first as InetSocketAddress).hostString,
                        style = TextStyle(color = colorTextGray, fontSize = 12.sp, fontWeight = FontWeight.W500),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
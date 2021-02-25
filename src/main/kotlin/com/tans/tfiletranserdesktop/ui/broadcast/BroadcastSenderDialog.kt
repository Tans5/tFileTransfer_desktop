package com.tans.tfiletranserdesktop.ui.broadcast

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.net.RemoteDevice
import com.tans.tfiletranserdesktop.net.launchBroadcastSender
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.ui.resources.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*

@Composable
fun showBroadcastSenderDialog(
    localAddress: InetAddress,
    noneBroadcast: Boolean,
    broadMessage: String,
    receiveConnect: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit) {
    val dialog = BroadcastSenderDialog(
        localAddress = localAddress,
        noneBroadcast = noneBroadcast,
        broadMessage = broadMessage,
        receiveConnect = receiveConnect,
        cancelRequest = cancelRequest
    )
    dialog.initData()
    dialog.start()
}

sealed class BroadcastSenderScreenType {
    object Loading : BroadcastSenderScreenType()
    class ConnectRequest(val remoteDevice: RemoteDevice) : BroadcastSenderScreenType()
}

sealed class ConnectReply(val remoteDevice: RemoteDevice) {
    class Accept(remoteDevice: RemoteDevice) : ConnectReply(remoteDevice)
    class Deny(remoteDevice: RemoteDevice) : ConnectReply(remoteDevice)
}

data class BroadcastSenderState(
    val screenType: BroadcastSenderScreenType = BroadcastSenderScreenType.Loading,
    val replay: Optional<ConnectReply> = Optional.empty()
)

class BroadcastSenderDialog(
    private val localAddress: InetAddress,
    private val noneBroadcast: Boolean,
    private val broadMessage: String,
    private val receiveConnect: (remoteDevice: RemoteDevice) -> Unit,
    cancelRequest: () -> Unit
) : BaseStatableDialog<BroadcastSenderState>(
    defaultState = BroadcastSenderState(),
    cancelRequest = cancelRequest
) {

    override fun initData() {
        launch {
            val result = runCatching {
                launchBroadcastSender(
                    localAddress = localAddress,
                    noneBroadcast = noneBroadcast,
                    broadMessage = broadMessage
                ) { remoteAddress, remoteDevice ->
                    val remoteDeviceInfo = remoteAddress to remoteDevice
                    updateState { oldState ->
                        oldState.copy(screenType = BroadcastSenderScreenType.ConnectRequest(remoteDeviceInfo))
                    }.await()
                    when (bindState().map { it.replay }.filter { it.isPresent && it.get().remoteDevice == remoteDeviceInfo }.firstOrError().await().get()) {
                        is ConnectReply.Accept -> true
                        is ConnectReply.Deny -> false
                    }
                }
            }
            if (result.isFailure) {
                result.exceptionOrNull()?.printStackTrace()
            } else {
                receiveConnect(result.getOrThrow())
            }
            cancel()
        }
    }

    @Composable
    override fun DialogContent() {
        val screenType = bindState().map { it.screenType }.distinctUntilChanged().subscribeAsState(BroadcastSenderScreenType.Loading)
        when (val type = screenType.value) {
            BroadcastSenderScreenType.Loading -> Loading()
            is BroadcastSenderScreenType.ConnectRequest -> ConnectRequest(type.remoteDevice)
        }
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

    @Composable
    fun ConnectRequest(remoteDevice: RemoteDevice) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringBroadcastRequestDialogTitle,
                style = styleDialogTitle
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = remoteDevice.second,
                style = styleDialogBody
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = (remoteDevice.first as InetSocketAddress).address.hostAddress,
                style = styleDialogBody
            )

            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        launch {
                            updateState { oldState ->
                                oldState.copy(
                                    replay = Optional.of(ConnectReply.Deny(remoteDevice)),
                                    screenType = BroadcastSenderScreenType.Loading
                                )
                            }.await()
                        }
                    }
                ) {
                    Text(stringBroadcastRequestDialogDeny)
                }
                Spacer(modifier = Modifier.width(10.dp))
                TextButton(
                    onClick = {
                        launch {
                            updateState { oldState ->
                                oldState.copy(replay = Optional.of(ConnectReply.Accept(remoteDevice)))
                            }.await()
                        }
                    }
                ) {
                    Text(stringBroadcastRequestDialogAccept)
                }
            }
        }
    }
}
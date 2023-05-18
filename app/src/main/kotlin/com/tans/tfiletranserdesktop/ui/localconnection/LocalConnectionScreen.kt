package com.tans.tfiletranserdesktop.ui.localconnection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.file.LOCAL_DEVICE
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.filetransfer.FileTransferScreen
import com.tans.tfiletranserdesktop.ui.resources.*
import com.tans.tfiletransporter.netty.findLocalAddressV4
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import java.net.InetAddress
import java.util.*

sealed class LocalConnectionDialogEvent(val time: Long) {
    class QRCodeServerDialog(time: Long) : LocalConnectionDialogEvent(time)

    class BroadcastReceiverDialog(time: Long) : LocalConnectionDialogEvent(time)
    class BroadcastSenderDialog(time: Long) : LocalConnectionDialogEvent(time)
    class None(time: Long) : LocalConnectionDialogEvent(time)
}

data class LocalConnectionState(
    val addresses: List<InetAddress> = emptyList(),
    val selectAddressIndex: Optional<Int> = Optional.empty<Int>(),
    val localDeviceInfo: String = "",
    val dialogEvent: LocalConnectionDialogEvent = LocalConnectionDialogEvent.None(System.currentTimeMillis())
)

class LocalConnectionScreen : BaseScreen<LocalConnectionState>(LocalConnectionState()) {

    override fun initData() {
        launch {
            updateState { oldState ->
                val localAddresses = findLocalAddressV4()
                oldState.copy(
                    addresses = localAddresses,
                    selectAddressIndex = if (localAddresses.isEmpty()) Optional.empty<Int>() else Optional.of(0),
                    localDeviceInfo = LOCAL_DEVICE
                )
            }.await()
        }
    }

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringAppName)
                    }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    modifier = Modifier.width(700.dp).offset(y = 25.dp),
                    shape = RoundedCornerShape(4.dp),
                    elevation = 4.dp,
                    backgroundColor = colorWhite
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp)
                    ) {
                        Text(text = stringLocalConnectionTitle, style = TextStyle(color = colorTextBlack, fontSize = 16.sp, fontWeight = FontWeight.W400))
                        Spacer(Modifier.height(5.dp))
                        Text(text = stringLocalConnectionTips, style = TextStyle(color = colorTextGray, fontSize = 14.sp))
                        Spacer(Modifier.height(5.dp))
                        val deviceInfo = bindState().map { it.localDeviceInfo }.distinctUntilChanged().subscribeAsState("")
                        Text(text = "$stringLocalConnectionLocalDevice ${deviceInfo.value}", style = TextStyle(color = colorTextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(5.dp))
                        Text(text = stringLocalConnectionLocalAddress, style = TextStyle(color = colorTextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(3.dp))
                        val addresses = bindState().map { it.addresses }.distinctUntilChanged().subscribeAsState(emptyList())
                        val selectedIndex = bindState().map { it.selectAddressIndex }.distinctUntilChanged().subscribeAsState(Optional.empty())
                        for ((index, address) in addresses.value.withIndex()) {
                            Row {
                                Spacer(Modifier.width(3.dp))
                                RadioButton(selected = index == selectedIndex.value.orElse(-1), onClick = {
                                    launch {
                                        updateState { oldState -> oldState.copy(selectAddressIndex = Optional.of(index)) }.await()
                                    }
                                })
                                Spacer(Modifier.width(2.dp))
                                Text(text = address.hostAddress, style = TextStyle(color = colorTextGray, fontSize = 14.sp, fontWeight = FontWeight.W500),
                                    modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        actionButton(scope = this, text = stringLocalConnectionShowQRCode) {
                            launch {
                                updateState { oldState ->
                                    oldState.copy(dialogEvent = LocalConnectionDialogEvent.QRCodeServerDialog(time = System.currentTimeMillis()))
                                }.await()
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        actionButton(scope = this, text = stringLocalConnectionAsReceiver) {
                            launch {
                                updateState { oldState ->
                                    oldState.copy(dialogEvent = LocalConnectionDialogEvent.BroadcastReceiverDialog(time = System.currentTimeMillis()))
                                }.await()
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        actionButton(scope = this, text = stringLocalConnectionAsSender) {
                            launch {
                                updateState { oldState ->
                                    oldState.copy(dialogEvent = LocalConnectionDialogEvent.BroadcastSenderDialog(time = System.currentTimeMillis()))
                                }.await()
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                val dialogEvent = bindState().distinctUntilChanged { t1, t2 ->  t1.dialogEvent.time == t2.dialogEvent.time}.map {
                    when {
                        it.addresses.isEmpty() || it.selectAddressIndex.isEmpty -> {
                            Optional.empty()
                        }
                        else -> {
                            Optional.of(it)
                        }
                    }
                }.subscribeAsState(Optional.empty())

                if (dialogEvent.value.isPresent) {
                    val state = dialogEvent.value.get()
                    val selectAddress = state.addresses[state.selectAddressIndex.get()]
                    when (state.dialogEvent) {
                        is LocalConnectionDialogEvent.QRCodeServerDialog -> showQRCodeServerDialog(
                            localAddress = selectAddress,
                            localDeviceInfo = state.localDeviceInfo,
                            requestTransferFile = { remoteDevice ->
                                screenRoute.routeTo(
                                    FileTransferScreen(
                                        localAddress = selectAddress,
                                        remoteDevice = remoteDevice,
                                        asServer = true
                                    )
                                )
                            }) {
                            launch {
                                updateState { oldState -> oldState.copy(dialogEvent = LocalConnectionDialogEvent.None(System.currentTimeMillis())) }.await()
                            }
                        }

                        is LocalConnectionDialogEvent.BroadcastReceiverDialog -> showBroadcastReceiverDialog(
                            localAddress = selectAddress,
                            localDeviceInfo = state.localDeviceInfo,
                            connectTo = { remoteDevice ->
                                screenRoute.routeTo(
                                    FileTransferScreen(
                                        localAddress = selectAddress,
                                        remoteDevice = remoteDevice,
                                        asServer = false
                                    )
                                )
                            }) {
                            launch {
                                updateState { oldState -> oldState.copy(dialogEvent = LocalConnectionDialogEvent.None(System.currentTimeMillis())) }.await()
                            }
                        }

                        is LocalConnectionDialogEvent.BroadcastSenderDialog -> showBroadcastSenderDialog(
                            localAddress = selectAddress,
                            broadMessage = state.localDeviceInfo,
                            receiveConnect = { remoteDevice ->
                                screenRoute.routeTo(
                                    FileTransferScreen(
                                        localAddress = selectAddress,
                                        remoteDevice = remoteDevice,
                                        asServer = true
                                    )
                                )
                            }) {
                            launch {
                                updateState { oldState -> oldState.copy(dialogEvent = LocalConnectionDialogEvent.None(System.currentTimeMillis())) }.await()
                            }
                        }

                        is LocalConnectionDialogEvent.None -> {  }
                    }
                }
            }
        }
    }

    @Composable
    private fun actionButton(scope: ColumnScope, text: String, onClick: () -> Unit) =  OutlinedButton(
        onClick = onClick,
        modifier = with(scope) { Modifier.align(alignment = Alignment.CenterHorizontally).width(450.dp).height(55.dp) },
        border = BorderStroke(ButtonDefaults.OutlinedBorderSize, colorTeal200)
    ) {
        Text(text = text)
    }

}
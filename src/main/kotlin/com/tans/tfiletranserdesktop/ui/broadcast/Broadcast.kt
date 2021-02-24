package com.tans.tfiletranserdesktop.ui.broadcast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.*
import com.tans.tfiletranserdesktop.utils.findLocalAddressV4
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.net.InetAddress
import java.util.*

sealed class BroadcastDialogEvent(val time: Long) {
    class ReceiverDialog(time: Long) : BroadcastDialogEvent(time)
    class SenderDialog(time: Long) : BroadcastDialogEvent(time)
    class None(time: Long) : BroadcastDialogEvent(time)
}

data class BroadcastState(
    val addresses: List<InetAddress> = emptyList(),
    val selectAddressIndex: Optional<Int> = Optional.empty<Int>(),
    val localDeviceInfo: String = "",
    val useSystemBroadcast: Boolean = false,
    val dialogEvent: BroadcastDialogEvent = BroadcastDialogEvent.None(System.currentTimeMillis())
)

class Broadcast : BaseScreen<BroadcastState>(BroadcastState()) {

    override fun initData() {
        launch {
            updateState { oldState ->
                val localAddresses = findLocalAddressV4()
                val username = System.getProperty("user.name")
                val deviceName = System.getProperty("os.name")
                oldState.copy(
                    addresses = localAddresses,
                    selectAddressIndex = if (localAddresses.isEmpty()) Optional.empty<Int>() else Optional.of(0),
                    localDeviceInfo = "$username's $deviceName"
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
                        Text(text = stringBroadcastTitle, style = TextStyle(color = colorTextBlack, fontSize = 16.sp, fontWeight = FontWeight.W400))
                        Spacer(Modifier.height(5.dp))
                        Text(text = stringBroadcastTips, style = TextStyle(color = colorTextGray, fontSize = 14.sp))
                        Spacer(Modifier.height(5.dp))
                        val deviceInfo = bindState().map { it.localDeviceInfo }.distinctUntilChanged().subscribeAsState("")
                        Text(text = "$stringBroadcastLocalDevice ${deviceInfo.value}", style = TextStyle(color = colorTextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(5.dp))
                        Text(text = stringBroadcastLocalAddress, style = TextStyle(color = colorTextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold))
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

                        Row {
                            Text(text = stringBroadcastUseSystemBroadcast, style = TextStyle(color = colorTextGray, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.CenterVertically))
                            Spacer(Modifier.width(5.dp))
                            val useSystemBroadcast = bindState().map { it.useSystemBroadcast }.distinctUntilChanged().subscribeAsState(false)
                            Switch(
                                checked = useSystemBroadcast.value,
                                onCheckedChange = {
                                    launch {
                                        updateState { oldState ->
                                            oldState.copy(useSystemBroadcast = !useSystemBroadcast.value)
                                        }.await()
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary),
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = {
                                launch {
                                    updateState { oldState ->
                                        oldState.copy(dialogEvent = BroadcastDialogEvent.ReceiverDialog(time = System.currentTimeMillis()))
                                    }.await()
                                }
                            },
                            modifier = Modifier.align(alignment = Alignment.CenterHorizontally).width(450.dp).height(55.dp),
                            border = BorderStroke(ButtonDefaults.OutlinedBorderSize, colorTeal200)
                        ) {
                            Text(text = stringBroadcastAsReceiver)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = {
                                launch {
                                    updateState { oldState ->
                                        oldState.copy(dialogEvent = BroadcastDialogEvent.SenderDialog(time = System.currentTimeMillis()))
                                    }.await()
                                }
                            },
                            modifier = Modifier.align(alignment = Alignment.CenterHorizontally).width(450.dp).height(55.dp),
                            border = BorderStroke(ButtonDefaults.OutlinedBorderSize, colorTeal200)
                        ) {
                            Text(text = stringBroadcastAsSender)
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
                    val noneBroadcast = !state.useSystemBroadcast
                    val time = state.dialogEvent.time
                    when (state.dialogEvent) {
                        is BroadcastDialogEvent.ReceiverDialog -> showBroadcastReceiverDialog(localAddress = selectAddress, noneBroadcast = noneBroadcast, time = time)

                        is BroadcastDialogEvent.SenderDialog -> showBroadcastSenderDialog(localAddress = selectAddress, noneBroadcast = noneBroadcast, time = time)

                        is BroadcastDialogEvent.None -> {  }
                    }
                }
            }
        }
    }

}
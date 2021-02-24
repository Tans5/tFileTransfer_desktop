package com.tans.tfiletranserdesktop.ui.broadcast

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.net.launchBroadcastSender
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.ui.resources.stringBroadcastSenderDialogCancel
import com.tans.tfiletranserdesktop.ui.resources.stringBroadcastSenderDialogTitle
import com.tans.tfiletranserdesktop.ui.resources.styleDialogTitle
import kotlinx.coroutines.launch
import java.net.InetAddress

@Composable
fun showBroadcastSenderDialog(localAddress: InetAddress, noneBroadcast: Boolean, time: Long, broadMessage: String) {
    val dialog = BroadcastSenderDialog(localAddress, noneBroadcast, broadMessage)
    dialog.initData()
    dialog.start()
}

class BroadcastSenderDialog(private val localAddress: InetAddress, private val noneBroadcast: Boolean, private val broadMessage: String) : BaseStatableDialog<Unit>(Unit) {

    override fun initData() {
        launch {
            val result = runCatching {
                launchBroadcastSender(localAddress = localAddress, noneBroadcast = noneBroadcast, broadMessage = broadMessage) { remoteAddress, remoteDevice ->

                    false
                }
            }
            cancel()
        }
    }

    @Composable
    override fun DialogContent() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringBroadcastSenderDialogTitle,
                style = styleDialogTitle
            )

            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
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
}
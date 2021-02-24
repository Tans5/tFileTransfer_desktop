package com.tans.tfiletranserdesktop.ui.broadcast

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.ui.dialogs.BaseStatableDialog
import com.tans.tfiletranserdesktop.ui.resources.*
import java.net.InetAddress

@Composable
fun showBroadcastReceiverDialog(localAddress: InetAddress, noneBroadcast: Boolean, time: Long) {
    val dialog = BroadcastReceiverDialog()
    dialog.initData()
    dialog.start()
}

class BroadcastReceiverDialog : BaseStatableDialog<Unit>(Unit) {

    @Composable
    override fun DialogContent() {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringBroadcastReceiverDialogTitle,
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
                    Text(stringBroadcastReceiverDialogCancel)
                }
            }
        }
    }
}
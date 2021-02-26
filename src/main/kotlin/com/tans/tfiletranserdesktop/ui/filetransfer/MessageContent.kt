package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute

data class MessageContentState(
    val unit: Unit = Unit
)

class MessageContent(
    val fileTransferScreen: FileTransferScreen
) : BaseScreen<MessageContentState>(MessageContentState()) {

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        Text("Message")
    }

}
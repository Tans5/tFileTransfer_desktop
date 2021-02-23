package com.tans.tfiletranserdesktop.ui.broadcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.dialogs.LoadingDialog
import com.tans.tfiletranserdesktop.ui.filetransfer.FileTransfer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

class Broadcast : BaseScreen<Boolean>(true) {


    override fun initData() {
        launch {
            delay(2000)
            updateState {
                false
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
                        Text("tFileTransfer")
                    }
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Broadcast",
                    modifier = Modifier.clickable {
                        screenRoute.routeTo(FileTransfer())
                    }
                )
            }

            val isLoading = bindState().distinctUntilChanged().subscribeAsState(false)
            if (isLoading.value) {
                LoadingDialog()
            }
        }
    }

}
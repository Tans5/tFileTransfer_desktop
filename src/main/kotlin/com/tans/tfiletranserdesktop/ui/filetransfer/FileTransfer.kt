package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute

class FileTransfer : BaseScreen<Unit>(Unit) {

    @Composable
    override fun start(screenRoute: ScreenRoute) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text("tFileTransfer")
                    },
                    navigationIcon = {
                        IconButton(onClick = { screenRoute.back() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "ArrowBack")
                        }
                    }
                )
            }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("FileTransfer")
            }
        }
    }
}
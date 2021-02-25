package com.tans.tfiletranserdesktop.ui.filetransfer

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.ui.BaseScreen
import com.tans.tfiletranserdesktop.ui.ScreenRoute
import com.tans.tfiletranserdesktop.ui.resources.colorTeal200
import com.tans.tfiletranserdesktop.ui.resources.colorTextGray
import com.tans.tfiletranserdesktop.ui.resources.colorWhite

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
            },
            bottomBar = {
                BottomAppBar(backgroundColor = colorWhite, contentColor = colorWhite) {
                    TabRow(
                        selectedTabIndex = 0,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        indicator = {},
                        backgroundColor = colorWhite,
                        contentColor = colorWhite
                    ) {
                        Tab(
                            selected = true,
                            selectedContentColor = colorTeal200,
                            unselectedContentColor = colorTextGray,
                            onClick = {

                            }
                        ) {
                            Text("MY FOLDER")
                        }

                        Tab(
                            selected = false,
                            selectedContentColor = colorTeal200,
                            unselectedContentColor = colorTextGray,
                            onClick = {

                            }
                        ) {
                            Text("REMOTE FOLDER")
                        }

                        Tab(
                            selected = false,
                            selectedContentColor = colorTeal200,
                            unselectedContentColor = colorTextGray,
                            onClick = {

                            }
                        ) {
                            Text("MESSAGE")
                        }
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("FileTransfer")
            }
        }
    }
}
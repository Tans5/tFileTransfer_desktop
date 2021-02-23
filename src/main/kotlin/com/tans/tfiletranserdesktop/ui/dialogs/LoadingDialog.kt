package com.tans.tfiletranserdesktop.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tans.tfiletranserdesktop.ui.dialogBgColor

@Composable
fun LoadingDialog() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = dialogBgColor
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
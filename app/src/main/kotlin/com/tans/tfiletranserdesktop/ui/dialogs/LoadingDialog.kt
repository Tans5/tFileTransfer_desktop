package com.tans.tfiletranserdesktop.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tans.tfiletranserdesktop.ui.resources.colorTransparent

@Composable
fun LoadingDialog() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorTransparent
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable {  }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
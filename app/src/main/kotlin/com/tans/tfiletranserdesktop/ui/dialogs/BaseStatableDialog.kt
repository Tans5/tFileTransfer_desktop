package com.tans.tfiletranserdesktop.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tans.tfiletranserdesktop.core.Stateable
import com.tans.tfiletranserdesktop.ui.resources.colorWhite
import com.tans.tfiletranserdesktop.ui.resources.colorDialogBg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel



@Suppress("FunctionName")
abstract class BaseStatableDialog<State : Any>(defaultState: State, val cancelRequest: () -> Unit = {  }) :
    Stateable<State> by Stateable(defaultState),
    CoroutineScope by CoroutineScope(Dispatchers.IO) {

    open fun initData() {

    }

    @Composable
    fun start() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorDialogBg
        ) {
            Box(modifier = Modifier.fillMaxSize().clickable { }, contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.width(350.dp),
                    backgroundColor = colorWhite,
                    shape = RoundedCornerShape(4.dp),
                    elevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 15.dp, top = 17.dp, end = 15.dp, bottom = 5.dp)
                    ) {
                        DialogContent()
                    }
                }
            }
        }
    }

    fun cancel() {
        stop()
        cancelRequest()
    }

    @Composable
    abstract fun DialogContent()

    open fun stop() {
        cancel("Dialog Cancel.")
    }

}
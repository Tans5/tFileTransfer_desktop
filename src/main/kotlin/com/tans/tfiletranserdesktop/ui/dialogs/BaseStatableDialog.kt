package com.tans.tfiletranserdesktop.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tans.tfiletranserdesktop.core.Stateable
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.colorWhite
import com.tans.tfiletranserdesktop.ui.colorDialogBg
import com.tans.tfiletranserdesktop.ui.colorTextBlack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

val dialogTitleStyle = TextStyle(
    fontSize = 20.sp,
    color = colorTextBlack,
    fontWeight = FontWeight.Bold
)

val dialogBodyStyle = TextStyle(
    fontSize = 16.sp,
    color = colorTextBlack,
    fontWeight = FontWeight.Normal
)

abstract class BaseStatableDialog<State>(defaultState: State) :
    Stateable<Pair<Boolean, State>> by Stateable(true to defaultState),
    CoroutineScope by CoroutineScope(Dispatchers.Default) {

    open fun initData() {

    }

    @Composable
    fun start() {
        val isShow = bindState().map { it.first }.distinctUntilChanged().subscribeAsState(true)
        if (isShow.value) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorDialogBg
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.width(350.dp),
                        backgroundColor = colorWhite,
                        shape = RoundedCornerShape(4.dp),
                        elevation = 8.dp
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 15.dp, top = 17.dp, end = 15.dp, bottom = 5.dp)) {
                            DialogContent()
                        }
                    }
                }
            }
        } else {
            stop()
        }
    }

    @Composable
    abstract fun DialogContent()

    protected fun cancel() {
        launch {
            updateState { it.copy(first = false) }.await()
        }
    }

    private fun stop() {
        cancel("Dialog Cancel.")
    }

}
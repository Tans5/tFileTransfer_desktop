package com.tans.tfiletranserdesktop.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.tans.tfiletranserdesktop.core.Stateable
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.broadcast.Broadcast
import com.tans.tfiletranserdesktop.ui.resources.defaultThemeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

object EmptyScreen : BaseScreen<Unit>(Unit) {

    @Composable
    override fun start(screenRoute: ScreenRoute) {}

}

@Composable
fun startDefaultScreenRoute() {
    val screenRoute = ScreenRoute()
    screenRoute.start()
    screenRoute.routeTo(Broadcast())
}

class ScreenRoute : Stateable<List<BaseScreen<*>>> by Stateable(emptyList()), CoroutineScope by CoroutineScope(Dispatchers.Default) {

    @Composable
    internal fun start() {
        MaterialTheme(
            colors = defaultThemeColors
        ) {
            val screen = bindState().filter { it.isNotEmpty() }.map { it.lastOrNull()!! }.distinctUntilChanged().subscribeAsState(EmptyScreen)
            screen.value.start(this)
        }
    }

    fun routeTo(screen: BaseScreen<*>) {
        launch {
            updateState {
                screen.initData()
                it + screen
            }.await()
        }
    }

    fun back() {
        launch {
            updateState {
                if (it.size <= 1) {
                    it
                } else {
                    val screen = it.lastOrNull()
                    screen!!.stop(this@ScreenRoute)
                    it - screen
                }
            }.await()
        }
    }
}
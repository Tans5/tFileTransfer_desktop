package com.tans.tfiletranserdesktop.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import com.tans.tfiletranserdesktop.core.Stateable
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import com.tans.tfiletranserdesktop.ui.localconnection.LocalConnectionScreen
import com.tans.tfiletranserdesktop.ui.resources.defaultThemeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await

object EmptyScreen : BaseScreen<Unit>(Unit) {

    @Composable
    override fun start(screenRoute: ScreenRoute) {}

}

@Composable
fun startDefaultScreenRoute(frameWindowScope: FrameWindowScope) {
    val screenRoute = ScreenRoute(frameWindowScope)
    screenRoute.start()
    screenRoute.routeTo(LocalConnectionScreen())
}

class ScreenRoute(val frameWindowScope: FrameWindowScope) : Stateable<List<BaseScreen<*>>> by Stateable(emptyList()), CoroutineScope by CoroutineScope(Dispatchers.Default) {

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
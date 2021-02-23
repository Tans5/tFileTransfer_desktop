package com.tans.tfiletranserdesktop.ui

import androidx.compose.runtime.Composable
import com.tans.tfiletranserdesktop.core.Stateable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

abstract class BaseScreen<State>(defaultState: State) : Stateable<State> by Stateable(defaultState),
    CoroutineScope by CoroutineScope(Dispatchers.Default) {

    open fun initData() {

    }

    @Composable
    abstract fun start(screenRoute: ScreenRoute)

    open fun stop(screenRoute: ScreenRoute) {
        cancel("Screen Cancel.")
    }
}
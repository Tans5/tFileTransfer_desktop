package com.tans.tfiletranserdesktop.rxasstate

import androidx.compose.runtime.*
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable

@Composable
fun <R : Any, T : R> Observable<T>.subscribeAsState(initial: R): State<R> =
    asState(initial) { subscribe(it) }

@Composable
private inline fun <T, S> S.asState(
    initial: T,
    crossinline subscribe: S.((T) -> Unit) -> Disposable
): State<T> {
    val state = remember { mutableStateOf(initial) }
    DisposableEffect(this) {
        val disposable = subscribe {
            state.value = it
        }
        onDispose { disposable.dispose() }
    }
    return state
}
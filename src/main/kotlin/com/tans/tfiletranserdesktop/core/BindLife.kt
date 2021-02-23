package com.tans.tfiletranserdesktop.core

import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable

interface BindLife {
    val lifeCompositeDisposable: CompositeDisposable

    fun <T> Observable<T>.bindLife() {
        lifeCompositeDisposable.add(this.subscribe({
            println("Next: ${it.toString()}")
        }, {
            println(it.toString())
        }, {
            println("Complete")
        }))
    }

    fun Completable.bindLife() {
        lifeCompositeDisposable.add(this.subscribe({
            println("Complete")
        }, {
            println(it.toString())
        }))
    }

    fun <T> Single<T>.bindLife() {
        lifeCompositeDisposable.add(this.subscribe({
            println(it.toString())
        }, {
            println(it.toString())
        }))
    }

    fun <T> Maybe<T>.bindLife() {
        lifeCompositeDisposable.add(this.subscribe ({
            println("Success: $it")
        }, {
            println( it.toString())
        }, {
            println("Complete")
        }))
    }
}

fun BindLife(): BindLife = object : BindLife {
    override val lifeCompositeDisposable: CompositeDisposable = CompositeDisposable()
}
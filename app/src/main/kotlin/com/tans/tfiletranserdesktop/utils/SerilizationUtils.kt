package com.tans.tfiletranserdesktop.utils

import com.squareup.moshi.Moshi


val defaultMoshi: Moshi by lazy {
    Moshi.Builder().build()
}

inline fun <reified T : Any> T.toJson(): String? {
    return try {
        defaultMoshi.adapter(T::class.java).toJson(this)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

inline fun <reified T : Any> String.fromJson(): T? {
    return try {
        defaultMoshi.adapter(T::class.java).fromJson(this)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}


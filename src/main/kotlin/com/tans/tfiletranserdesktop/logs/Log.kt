package com.tans.tfiletranserdesktop.logs



object Log {
    private const val TAG = "tFileTransfer"

    fun d(msg: String) {
        println("[$TAG] Debug: $msg")
    }

    fun e(msg: String, throwable: Throwable?) {
        println("[$TAG] Error: $msg")
        throwable?.printStackTrace()
    }

}
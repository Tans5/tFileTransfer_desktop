package com.tans.tfiletranserdesktop.logs

import com.tans.tfiletransporter.ILog

object JvmLog : ILog {

    override fun d(tag: String, msg: String) {
        println("[D][$tag]: $msg")
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        println("[E][$tag]: $msg")
        throwable?.printStackTrace()
    }
}
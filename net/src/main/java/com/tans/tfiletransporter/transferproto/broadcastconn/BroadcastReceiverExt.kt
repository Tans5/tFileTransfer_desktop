package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileResp
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress

suspend fun BroadcastReceiver.startReceiverSuspend(localAddress: InetAddress, broadcastAddress: InetAddress,) = suspendCancellableCoroutine<Unit> { cont ->
    startBroadcastReceiver(
        localAddress,
        broadcastAddress,
        object : SimpleCallback<Unit> {
            override fun onError(errorMsg: String) {
                cont.resumeExceptionIfActive(Throwable(errorMsg))
            }
            override fun onSuccess(data: Unit) {
                cont.resumeIfActive(Unit)
            }
        }
    )
}

suspend fun BroadcastReceiver.requestFileTransferSuspend(targetAddress: InetAddress) = suspendCancellableCoroutine<BroadcastTransferFileResp> { cont ->
    requestFileTransfer(
        targetAddress,
        object : SimpleCallback<BroadcastTransferFileResp> {
            override fun onError(errorMsg: String) {
                cont.resumeExceptionIfActive(Throwable(errorMsg))
            }

            override fun onSuccess(data: BroadcastTransferFileResp) {
                cont.resumeIfActive(data)
            }
        }
    )
}

suspend fun BroadcastReceiver.waitCloseSuspend() = suspendCancellableCoroutine<Unit> { cont ->
    addObserver(object : BroadcastReceiverObserver {
        init {
            cont.invokeOnCancellation {
                removeObserver(this)
            }
        }

        override fun onNewState(state: BroadcastReceiverState) {
            if (state is BroadcastReceiverState.NoConnection) {
                cont.resumeIfActive(Unit)
                removeObserver(this)
            }
        }

        override fun onNewBroadcast(remoteDevice: RemoteDevice) {}

        override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {}
    })
}
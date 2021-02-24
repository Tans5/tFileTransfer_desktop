import com.tans.tfiletranserdesktop.net.launchBroadcastReceiver
import com.tans.tfiletranserdesktop.net.launchBroadcastSender
import com.tans.tfiletranserdesktop.utils.findLocalAddressV4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SystemDirGetTest {

    @Test
    fun getMacDir () {
        val homePathString = System.getProperty("user.home")
        val homePath = Paths.get(homePathString)
        Files.list(homePath)
            .forEach { p ->
                println(p.fileName)
            }
    }

    @Test
    fun broadcastSenderTest() = runBlocking {
        val job = launch(Dispatchers.IO) {
            val address = findLocalAddressV4()[0]
            val result = kotlin.runCatching {
                launchBroadcastSender(
                    broadMessage = "Windows 10",
                    localAddress = address,
                    noneBroadcast = false,
                    acceptRequest = { remoteAddress, remoteDevice ->
                        false
                    }
                )
            }
            if (result.isFailure) {
                result.exceptionOrNull()?.printStackTrace()
            }

        }
        job.join()
    }

    @Test
    fun broadcastReceiverTest() = runBlocking {
        val job = launch(Dispatchers.IO) {
            val address = findLocalAddressV4()[0]
            val result = kotlin.runCatching {
                launchBroadcastReceiver(
                    localAddress = address,
                    noneBroadcast = false,
                    handle = {
                        bindState()
                            .doOnNext {
                                it.map { it.first.second }
                                    .forEach { string ->
                                        println(string)
                                    }
                            }
                            .subscribe()
                    }
                )
            }
            if (result.isFailure) {
                result.exceptionOrNull()?.printStackTrace()
            }
        }

        job.join()
    }
}
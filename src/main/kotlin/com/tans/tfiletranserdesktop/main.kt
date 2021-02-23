import androidx.compose.desktop.Window
import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tans.tfiletranserdesktop.rxasstate.subscribeAsState
import io.reactivex.Observable

fun main() = Window {
    var text by remember { mutableStateOf("Hello, World!") }

    val obs = Observable.just("Hello, File Transfer..").subscribeAsState("")

    MaterialTheme {

        Button(onClick = {
            text = obs.value
        }) {
            Text(text)
        }
    }
}
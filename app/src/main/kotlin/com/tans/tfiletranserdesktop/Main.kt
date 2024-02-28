
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import com.tans.tfiletranserdesktop.ui.resources.stringAppName
import com.tans.tfiletranserdesktop.ui.startDefaultScreenRoute

fun main() = singleWindowApplication(resizable = true, title = stringAppName, state = WindowState(width = 800.dp, height = 800.dp)) {
    startDefaultScreenRoute(this)
}
import androidx.compose.desktop.Window
import com.tans.tfiletranserdesktop.ui.resources.stringAppName
import com.tans.tfiletranserdesktop.ui.startDefaultScreenRoute

fun main() = Window(resizable = false, title = stringAppName) { startDefaultScreenRoute() }
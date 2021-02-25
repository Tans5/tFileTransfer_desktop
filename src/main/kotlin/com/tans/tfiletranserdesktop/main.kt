import androidx.compose.desktop.Window
import androidx.compose.ui.unit.IntSize
import com.tans.tfiletranserdesktop.ui.resources.stringAppName
import com.tans.tfiletranserdesktop.ui.startDefaultScreenRoute

fun main() = Window(resizable = false, title = stringAppName, size = IntSize(800, 800)) { startDefaultScreenRoute() }
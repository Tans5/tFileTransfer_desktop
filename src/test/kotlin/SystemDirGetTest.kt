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
}
import Sharding.Device
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit


val APP_PACKAGE = "com.martinchamarro.recipes.debug"
val TEST_RUNNER = "$APP_PACKAGE.test/android.support.test.runner.AndroidJUnitRunner"
val APK_PATH = "~/Desktop/app-debug.apk"
val TEST_APK_PATH = "~/Desktop/app-debug-androidTest.apk"

val ADB_DEVICES_REGEX = """([\w-]{5,})[\t\s]*(device)""".toRegex()
val CLASS_REGEX = """class=([\w.]*)""".toRegex()
val TEST_REGEX = """test=([\w]*)""".toRegex()


fun getListOfDevices(): List<Device> {
    println("=====> Getting list of devices")
    val output = "adb devices".runCommand()
    return ADB_DEVICES_REGEX
        .findAll(output)
        .map { it.groupValues }
        .map { Device(serial = it[1], status = it[2]) }
        .toList()
}


fun Device.installApk(path: String) {
    println("=====> Installing APK from $path into $serial")
    "adb -s $serial install $path".runCommand()
}

fun Device.getListOfUITest(): List<Test> {
    val output = "adb -s $serial shell am instrument -w -r -e log true $TEST_RUNNER".runCommand()
    return CLASS_REGEX.findAll(output)
        .zip(TEST_REGEX.findAll(output))
        .map { Test(it.first.groupValues[1], it.second.groupValues[1]) }
        .toList()
        .distinct()
}


fun main() {
    // The first step is to get the list of connected devices
    val devices = getListOfDevices()
    if (devices.isEmpty()) {
        println("====> The list of devices is empty")
        return
    }
    println("=====> Devices:")
    devices.forEach { println(it) }

    // It is necessary to install the APKs to one of the devices
    val device = devices.first()
    device.apply {
        installApk(APK_PATH)
        installApk(TEST_APK_PATH)
    }

    // Get the list of tests from the first device
    val tests = device.getListOfUITest()
    if (tests.isEmpty()) {
        println("=====> The list of tests is empty")
        return
    }
    println("=====> Tests:")
    tests.forEach { println(it) }
}


main()


/**
 *
 * Extension that runs a command and returns the output
 *
 */
fun String.runCommand(workingDir: File = File(".")): String
        = ProcessBuilder(*this.split("\\s".toRegex()).toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start().apply {
            waitFor(60, TimeUnit.SECONDS)
        }.inputStream.bufferedReader().readText()


/**
 *
 * Data class for the ADB devices
 *
 */
data class Device(val serial: String, val status: String)


/**
 *
 * Data class to store the UI test info
 *
 */
data class Test(val className: String, val testName: String) {
    override fun toString() = "$className#$testName"
}
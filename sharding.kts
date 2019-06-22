import Sharding.Device
import java.io.File
import java.io.IOException
import java.lang.System.exit
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

val ERROR_CODE_NO_DEVICES = 1
val ERROR_CODE_NO_TESTS = 2
val THREAD_TIMEOUT_SECONDS = 10

val APP_PACKAGE = "com.asos.app.acceptance.debug"
val TEST_RUNNER = "$APP_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
val APK_PATH = "~/Desktop/app-debug.apk"
val TEST_APK_PATH = "~/Desktop/app-debug-androidTest.apk"

val ADB_DEVICES_REGEX = """([\w-]{5,})[\t\s]*(device)""".toRegex()
val CLASS_REGEX = """class=([\w.]*)""".toRegex()
val TEST_REGEX = """test=([\w]*)""".toRegex()


/**
 *
 * Uses the command "adb devices" to get the list of connected devices.
 * Parses the names and statuses using [ADB_DEVICES_REGEX].
 *
 */
fun getListOfDevices(): List<Device> {
    println("=====> Getting list of devices")
    val output = "adb devices".runCommand()
    return ADB_DEVICES_REGEX
        .findAll(output)
        .map { it.groupValues }
        .map { Device(serial = it[1], status = it[2]) }
        .toList()
}


/**
 *
 * Installs an APK into a device using the command "adb -s <device> install <apk_path>"
 *
 */
fun Device.installApk(path: String) {
    println("=====> Installing APK from $path into $serial")
    "adb -s $serial install $path".runCommand()
}


/**
 *
 * Gets the list of UI tests using the command "adb -s <device> shell am instrument -e log true <test_runner>"
 * Uses te regular expressions [CLASS_REGEX] and [TEST_REGEX] to get the test classes and names
 *
 */
fun Device.getListOfUITest(): List<Test> {
    println("=====> Getting list of UI tests")
    val output = "adb -s $serial shell am instrument -w -r -e log true $TEST_RUNNER".runCommand()
    println("=====> Parsing the lists of tests")
    return CLASS_REGEX.findAll(output)
        .zip(TEST_REGEX.findAll(output))
        .map { Test(it.first.groupValues[1], it.second.groupValues[1]) }
        .toList()
        .distinct()
}


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
                waitFor(THREAD_TIMEOUT_SECONDS, SECONDS)
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


/**
 *
 * Main method of the script
 *
 */
fun main(args: Array<String>) {

    // Step 1) get the list of devices
    val devices = getListOfDevices()
    if (devices.isEmpty()) {
        println("ERROR: The list of devices is empty")
        exit(ERROR_CODE_NO_DEVICES)
    }
    println("=====> Devices(${devices.size}):")
    devices.forEach { println(it) }

    // Step 2) install the APKs to the first device
    val device = devices.first()
    device.apply {
        installApk(APK_PATH)
        installApk(TEST_APK_PATH)
    }

    // Step 3) get the full list of UI tests
    val tests = device.getListOfUITest()
    if (tests.isEmpty()) {
        println("ERROR: The list of tests is empty")
        exit(ERROR_CODE_NO_TESTS)
    }
    println("=====> Tests(${tests.size}):")
    tests.forEach { println(it) }

}

main(args)
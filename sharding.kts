import Sharding.Device
import java.io.File
import java.io.IOException
import java.lang.System.exit
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

val ERROR_CODE_NO_DEVICES = 1
val ERROR_CODE_NO_TESTS = 2
val ERROR_CODE_NO_SHARDS = 3

val THREAD_TIMEOUT_MILLIS = 5_000L

val DEBUG = true
val NUM_SHARDS = 4
val SHARD_INDEX = 0

val APP_PACKAGE = "com.asos.app.acceptance.debug"
val TEST_RUNNER = "$APP_PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
val APK_PATH = "~/Desktop/app-debug.apk"
val TEST_APK_PATH = "~/Desktop/app-debug-androidTest.apk"

val ADB_DEVICES_REGEX = """([\w-]{5,})[\t\s]*(device)""".toRegex()
val CLASS_REGEX = """class=([\w.]*)""".toRegex()
val TEST_REGEX = """test=([\w]*)""".toRegex()


fun log(any: Any) {
    if (DEBUG) println(any.toString())
}


/**
 *
 * Prints an error message and finish the execution of the script
 *
 */
fun exitWithError(message: String, errorCode: Int) {
    println("ERROR: $message")
    exit(errorCode)
}


/**
 *
 * Uses the command "adb devices" to get the list of connected devices.
 * Parses the names and statuses using [ADB_DEVICES_REGEX].
 *
 */
fun getListOfDevices(): List<Device> {
    log("=====> Getting list of devices")
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
    log("=====> Installing APK from $path into $serial")
    "adb -s $serial install $path".runCommand()
}


/**
 *
 * Gets the list of UI tests using the command "adb -s <device> shell am instrument -e log true <test_runner>"
 * Uses te regular expressions [CLASS_REGEX] and [TEST_REGEX] to get the test classes and names
 *
 */
fun Device.getListOfUITest(): List<Test> {
    log("=====> Getting list of UI tests")
    val output = "adb -s $serial shell am instrument -w -r -e log true $TEST_RUNNER".runCommand()
    log("=====> Parsing the lists of tests")
    return CLASS_REGEX.findAll(output)
            .zip(TEST_REGEX.findAll(output))
            .map { Test(it.first.groupValues[1], it.second.groupValues[1]) }
            .toList()
            .distinct()
}


fun createShards(tests: List<Test>): List<Shard> {
    log("=====> Creating shards")
    val testsPerShard = tests.size / NUM_SHARDS
    val shards = mutableListOf<Shard>()
    for (index in 0 until NUM_SHARDS) {
        val startPos = index * testsPerShard
        val lastPos = if (index != NUM_SHARDS - 1) startPos + testsPerShard else tests.size
        shards.add(Shard(index, tests.subList(startPos, lastPos)))
    }
    return shards
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
                waitFor(THREAD_TIMEOUT_MILLIS, MILLISECONDS)
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
    val fullName get() = "$className#$testName"
}

/**
 *
 * Data class for the Shards
 *
 */
data class Shard(val index: Int, val tests: List<Test>) {
    override fun toString() = "Shard(index=$index, numTest=${tests.size})"
}


/**
 *
 * Main method of the script
 *
 */
fun main(args: Array<String>) {

    log(args)

    // Step 1) get the list of devices
    val devices = getListOfDevices()
    if (devices.isEmpty()) {
        exitWithError("The list of devices is empty", ERROR_CODE_NO_DEVICES)
    }
    log("=====> Devices(${devices.size}):")
    devices.forEach { log(it) }

    // Step 2) install the APKs to the first device
    val device = devices.first()
    device.apply {
        installApk(APK_PATH)
        installApk(TEST_APK_PATH)
    }

    // Step 3) get the full list of UI tests
    val tests = device.getListOfUITest()
    if (tests.isEmpty()) {
        exitWithError("The list of tests is empty", ERROR_CODE_NO_TESTS)
    }
    log("=====> Tests(${tests.size}):")
    tests.forEach { log(it) }

    // Step 4) create the shards
    val shards = createShards(tests)
    if (shards.isEmpty()) {
        exitWithError("The list of shards is empty", ERROR_CODE_NO_SHARDS)
    }
    log("=====> Shards(${shards.size}):")
    shards.forEach { log(it) }

    // Step 4) return the list of fullnames of the selected shard
    val shard = shards[SHARD_INDEX]
    val fullNames = shard.tests
            .map { it.fullName }
            .joinToString(separator = ",")
    log("Shard:")
    println(fullNames)

}

main(args)
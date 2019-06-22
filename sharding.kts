import java.io.File
import java.io.IOException
import java.lang.System.exit
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS

val SUCCESS_CODE = 0
val ERROR_CODE_NO_PARAMETER = 1
val ERROR_CODE_NO_DEVICES = 2
val ERROR_CODE_NO_TESTS = 3
val ERROR_CODE_NO_SHARDS = 4

val THREAD_TIMEOUT_MILLIS = 1_000L

var DEBUG = false

val ADB_DEVICES_REGEX = """([\w-]{5,})[\t\s]*(device)""".toRegex()
val CLASS_REGEX = """class=([\w.]*)""".toRegex()
val TEST_REGEX = """test=([\w]*)""".toRegex()


fun log(any: Any) { if (DEBUG) println(any.toString()) }


/**
 *
 * Composes the test runner name using the app package
 *
 */
fun getTestRunnerName(appPackage: String) = "$appPackage.test/androidx.test.runner.AndroidJUnitRunner"


/**
 *
 * Prints the help menu
 *
 */
fun printHelp() {
    println("""

    Use:
    ----
    kotlinc -script sharding.kts <app_package> <apk_path> <test_apk_path> <shards_number> <shard_index> [<filter>] [<debug>]

    Mandatory params:
    -----------------
    <app_package>: full package of the app. Example: com.martinchamarro.app
    <apk_path>: relative or absolute path of the APK. Build with "./gradlew assembleAcceptanceDebug"
    <test_apk_path>: relative or absolute path of the test APK. Build with "./gradlew assembleAcceptanceDebugAndroidTest"
    <shards_number>: integer with the number of test groups
    <shard_index>: integer with the selected test group position
    <shard

    Optinal params:
    ---------------
    <filter>: a String to filter the tests using the full name
    <debug>: boolean to enable the logging

    """.trimIndent())
}


fun mandatoryParam(args: Array<String>, paramName: String, position: Int): String {
    val value = args.getOrNull(position) ?: ""
    if (value.isEmpty()) {
        exitWithError("The parameter $paramName is mandatory", ERROR_CODE_NO_PARAMETER, printHelp = true)
    }
    return value
}


/**
 *
 * Prints an error message and finish the execution of the script
 *
 */
fun exitWithError(message: String, errorCode: Int, printHelp: Boolean = false) {
    println("\nERROR: $message")
    if (printHelp) printHelp()
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
fun Device.getListOfUITest(packageName: String): List<Test> {
    log("=====> Getting list of UI tests")
    val testRunner = getTestRunnerName(packageName)
    val output = "adb -s $serial shell am instrument -w -r -e log true $testRunner".runCommand()
    log("=====> Parsing the lists of tests")
    return CLASS_REGEX.findAll(output)
            .zip(TEST_REGEX.findAll(output))
            .map { Test(it.first.groupValues[1], it.second.groupValues[1]) }
            .toList()
            .distinct()
}


/**
 *
 * Filters the tests ignoring the case
 *
 */
fun filter(tests: List<Test>, filter: String): List<Test> {
    log("=====> Filtering the tests with '$filter'")
    return tests.filter { it.fullName.contains(filter, ignoreCase = true) }
}


/**
 *
 * Splits the list of tests in shards
 *
 */
fun createShards(tests: List<Test>, shardsNumber: Int): List<Shard> {
    log("=====> Creating shards")
    val testsPerShard = tests.size / shardsNumber
    val shards = mutableListOf<Shard>()
    for (index in 0 until shardsNumber) {
        val startPos = index * testsPerShard
        val lastPos = if (index != shardsNumber - 1) startPos + testsPerShard else tests.size
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

    val packageName = mandatoryParam(args, "<app_package>", 0)
    val apkPath = mandatoryParam(args, "<apk_path>", 1)
    val testApkPath = mandatoryParam(args, "<test_apk_path>", 2)
    val shardsNumber = mandatoryParam(args, "<shards_number>", 3).toInt()
    val shardIndex = mandatoryParam(args, "<shard_index>", 4).toInt()
    val filter = args.getOrNull(5) ?: ""
    DEBUG = args.getOrNull(6)?.toBoolean() ?: false

    //
    // Step 1) get the list of devices
    //
    val devices = getListOfDevices()
    if (devices.isEmpty()) {
        exitWithError("The list of devices is empty", ERROR_CODE_NO_DEVICES)
    }
    log("=====> Devices(${devices.size}):")
    devices.forEach { log(it) }

    //
    // Step 2) install the APKs to the first device
    //
    val device = devices.first()
    device.apply {
        installApk(apkPath)
        installApk(testApkPath)
    }

    //
    // Step 3) get the full list of UI tests
    //
    val tests = device.getListOfUITest(packageName)
    if (tests.isEmpty()) {
        exitWithError("The list of tests is empty", ERROR_CODE_NO_TESTS)
    }
    log("=====> Tests(${tests.size}):")
    tests.forEach { log(it) }

    //
    // Step 4) filter the tests
    //
    val filteredTests = filter(tests, filter)
    log("=====> Filtered tests(${filteredTests.size}):")
    filteredTests.forEach { log(it) }

    //
    // Step 5) create the shards
    //
    val shards = createShards(filteredTests, shardsNumber)
    if (shards.isEmpty()) {
        exitWithError("The list of shards is empty", ERROR_CODE_NO_SHARDS)
    }
    log("=====> Shards(${shards.size}):")
    shards.forEach { log(it) }

    //
    // Step 6) return the list of test fullnames of the selected shard
    //
    val shard = shards[shardIndex]
    val fullNames = shard.tests
            .map { it.fullName }
            .joinToString(separator = ",")
    log("Shard:")
    println(fullNames)

}

main(args)
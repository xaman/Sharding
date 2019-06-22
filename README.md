### Sharding

Kotlin script to create shards of UI tests. The output is a comma-separated list that contains the names of the tests included in the shard.

#### Dependencies
 
 To install the Kotlin compiler on macOS:
 
 `brew install kotlin`
 
 To run the script:
 
 `kotlinc -script sharding.kts`

#### Parameters

```

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

```

Example:

`kotlinc -script sharding.kts com.asos.app.acceptance.debug app-debug.apk app-debug-androidTest.apk 5 0 true`
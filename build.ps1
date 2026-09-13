param([string]$Jdk = $env:JAVA_HOME,
      [string]$D8Classpath = $env:R8_JAR,
      [string]$AndroidSdk = $env:ANDROID_HOME,
      [ValidateSet('Debug','Release')][string]$BuildType = 'Debug',
      [switch]$Unsigned)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!$Jdk -or !(Test-Path (Join-Path $Jdk 'bin\javac.exe'))) {
    throw 'Set JAVA_HOME or pass -Jdk with a JDK directory.'
}
$platform = Join-Path $PSScriptRoot 'tools\platform\android-34\android.jar'
$buildTools = Join-Path $PSScriptRoot 'tools\build\android-14'
if ($AndroidSdk) {
    $platform = Join-Path $AndroidSdk 'platforms\android-34\android.jar'
    $buildTools = Join-Path $AndroidSdk 'build-tools\34.0.0'
}
if (!$D8Classpath) {
    & (Join-Path $PSScriptRoot 'prepare-d8.ps1')
    $D8Classpath = Join-Path $PSScriptRoot 'tools\r8-8.13.17.jar'
}
if (!(Test-Path -LiteralPath $D8Classpath -PathType Leaf)) {
    throw 'Missing D8: run prepare-d8.ps1 or pass -D8Classpath.'
}
if (!(Test-Path $platform)) { throw 'Missing tools/platform/android-34/android.jar; extract the Android 34 platform archive.' }
if ($Unsigned -and $BuildType -ne 'Release') { throw '-Unsigned is only supported for Release.' }
if ($BuildType -eq 'Release' -and !$Unsigned) {
    foreach ($name in @('PAGEH_KEYSTORE','PAGEH_KEY_ALIAS','PAGEH_STORE_PASSWORD','PAGEH_KEY_PASSWORD')) {
        if (![Environment]::GetEnvironmentVariable($name)) { throw "Missing release signing environment variable: $name" }
    }
    if (!(Test-Path -LiteralPath $env:PAGEH_KEYSTORE -PathType Leaf)) { throw 'Release keystore does not exist.' }
    if ([IO.Path]::GetFullPath($env:PAGEH_KEYSTORE) -eq (Join-Path $PSScriptRoot 'build\debug.keystore')) {
        throw 'Do not use the development keystore for Release.'
    }
}
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
$null = New-Item -ItemType Directory -Force build,build\classes,build\dex,build\generated,build\tests,dist
$manifest = New-Object System.Xml.XmlDocument
$manifest.Load((Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml'))
$androidNs = 'http://schemas.android.com/apk/res/android'
$version = $manifest.DocumentElement.GetAttribute('versionName', $androidNs)
if ($version -notmatch '^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$') { throw 'Invalid manifest versionName.' }
$null = $manifest.manifest.application.SetAttribute('debuggable', $androidNs, ($BuildType -eq 'Debug').ToString().ToLowerInvariant())
$manifest.Save((Join-Path $PSScriptRoot 'build\AndroidManifest.xml'))
$apkName = if ($BuildType -eq 'Debug') { "pageh-helper-$version.apk" } elseif ($Unsigned) { "pageh-helper-$version-release-unsigned.apk" } else { "pageh-helper-$version-release.apk" }
$apkPath = Join-Path $PSScriptRoot "dist\$apkName"
$classDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build\classes'))
if ($classDir -ne ([IO.Path]::GetFullPath($PSScriptRoot) + '\build\classes')) { throw 'Unexpected compiler output directory' }
Get-ChildItem -LiteralPath $classDir -Recurse -File -Filter '*.class' | Remove-Item -Force
& "$buildTools\aapt2.exe" compile --dir app\src\main\res -o build\resources.zip
Check-Exit
& "$buildTools\aapt2.exe" link -o build\unsigned.apk -I $platform --manifest build\AndroidManifest.xml --java build\generated build\resources.zip
Check-Exit
$sources = @(Get-ChildItem app\src\main\java,build\generated -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& "$Jdk\bin\javac.exe" -encoding UTF-8 --release 8 -Xlint:-options -classpath $platform -d build\classes $sources
Check-Exit
& "$Jdk\bin\jar.exe" --create --file build\classes.jar -C build\classes .
Check-Exit
& "$Jdk\bin\java.exe" -cp $D8Classpath com.android.tools.r8.D8 --min-api 26 --lib $platform --output build\dex build\classes.jar
Check-Exit
& "$Jdk\bin\jar.exe" --update --file build\unsigned.apk -C build\dex classes.dex
Check-Exit
& "$buildTools\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk
Check-Exit
if ($BuildType -eq 'Debug' -and !(Test-Path build\debug.keystore)) {
    & "$Jdk\bin\keytool.exe" -genkeypair -keystore build\debug.keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=PageH Development' -keyalg RSA -keysize 2048 -validity 10000
    Check-Exit
}
if ($Unsigned) {
    Copy-Item -LiteralPath build\aligned.apk -Destination $apkPath
} else {
    $signArgs = if ($BuildType -eq 'Debug') {
        @('--ks','build\debug.keystore','--ks-pass','pass:android')
    } else {
        @('--ks',$env:PAGEH_KEYSTORE,'--ks-key-alias',$env:PAGEH_KEY_ALIAS,
          '--ks-pass','env:PAGEH_STORE_PASSWORD','--key-pass','env:PAGEH_KEY_PASSWORD')
    }
    & "$Jdk\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" sign @signArgs --out $apkPath build\aligned.apk
    Check-Exit
    & "$Jdk\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" verify --verbose $apkPath
    Check-Exit
}
$badging = & "$buildTools\aapt2.exe" dump badging $apkPath
Check-Exit
if ($BuildType -eq 'Release' -and ($badging -match 'application-debuggable')) { throw 'Release APK is debuggable.' }
& "$buildTools\zipalign.exe" -c 4 $apkPath
Check-Exit
& "$Jdk\bin\javac.exe" --release 8 -Xlint:-options -d build\tests app\src\main\java\dev\pageh\helper\Direction.java tests\DirectionTest.java
Check-Exit
& "$Jdk\bin\java.exe" -cp build\tests DirectionTest
Check-Exit
& "$Jdk\bin\javac.exe" --release 8 -Xlint:-options -d build\tests app\src\main\java\dev\pageh\helper\ReadingWindows.java tests\ReadingWindowsTest.java
Check-Exit
& "$Jdk\bin\java.exe" -cp build\tests ReadingWindowsTest
Check-Exit
& "$Jdk\bin\javac.exe" --release 8 -Xlint:-options -d build\tests app\src\main\java\dev\pageh\helper\PageKeys.java app\src\main\java\dev\pageh\helper\TouchDecision.java tests\InputLogicTest.java
Check-Exit
& "$Jdk\bin\java.exe" -cp build\tests InputLogicTest
Check-Exit
& "$Jdk\bin\javac.exe" --release 8 -Xlint:-options -d build\tests tests\PassiveServiceTest.java
Check-Exit
& "$Jdk\bin\java.exe" -cp build\tests PassiveServiceTest
Check-Exit
& "$Jdk\bin\javac.exe" --release 8 -d build\tests app\src\main\java\dev\pageh\helper\TouchLease.java app\src\main\java\dev\pageh\helper\TouchDecision.java tests\TouchLeaseTest.java
Check-Exit
& "$Jdk\bin\java.exe" -cp build\tests TouchLeaseTest
Check-Exit
Get-Item $apkPath | Select-Object FullName,Length

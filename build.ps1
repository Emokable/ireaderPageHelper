param([string]$Jdk = $env:JAVA_HOME,
      [string]$D8Classpath = $env:R8_JAR)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (!$Jdk -or !(Test-Path (Join-Path $Jdk 'bin\javac.exe'))) {
    throw 'Set JAVA_HOME or pass -Jdk with a JDK directory.'
}
if (!$D8Classpath -or !(Test-Path -LiteralPath $D8Classpath -PathType Leaf)) {
    throw 'Set R8_JAR or pass -D8Classpath with a jar containing com.android.tools.r8.D8.'
}
$platform = Join-Path $PSScriptRoot 'tools\platform\android-34\android.jar'
$buildTools = Join-Path $PSScriptRoot 'tools\build\android-14'
if (!(Test-Path $platform)) { throw 'Missing tools/platform/android-34/android.jar; extract the Android 34 platform archive.' }
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
$null = New-Item -ItemType Directory -Force build,build\classes,build\dex,build\generated,build\tests,dist
$classDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build\classes'))
if ($classDir -ne ([IO.Path]::GetFullPath($PSScriptRoot) + '\build\classes')) { throw 'Unexpected compiler output directory' }
Get-ChildItem -LiteralPath $classDir -Recurse -File -Filter '*.class' | Remove-Item -Force
& "$buildTools\aapt2.exe" compile --dir app\src\main\res -o build\resources.zip
Check-Exit
& "$buildTools\aapt2.exe" link -o build\unsigned.apk -I $platform --manifest app\src\main\AndroidManifest.xml --java build\generated build\resources.zip
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
if (!(Test-Path build\debug.keystore)) {
    & "$Jdk\bin\keytool.exe" -genkeypair -keystore build\debug.keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=PageH Development' -keyalg RSA -keysize 2048 -validity 10000
    Check-Exit
}
& "$Jdk\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" sign --ks build\debug.keystore --ks-pass pass:android --out dist\pageh-helper-0.3.1.apk build\aligned.apk
Check-Exit
& "$Jdk\bin\java.exe" -jar "$buildTools\lib\apksigner.jar" verify --verbose dist\pageh-helper-0.3.1.apk
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
Get-Item dist\pageh-helper-0.3.1.apk | Select-Object FullName,Length

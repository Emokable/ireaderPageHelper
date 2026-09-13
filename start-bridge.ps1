param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
function Check-Exit { if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $LASTEXITCODE" } }
$identity = & $Adb shell id
if ($identity -notmatch 'uid=0\(root\)') { throw 'Current ADB session is not root. This script does not modify root settings.' }
$package = & $Adb shell pm list packages -U dev.pageh.helper
if ($package -notmatch 'package:dev\.pageh\.helper uid:(\d+)') { throw 'Install dist/pageh-helper-0.3.1.apk first.' }
$appUid = $Matches[1]
& $Adb push start-bridge.sh /data/local/tmp/pageh-start-bridge.sh
Check-Exit
& $Adb shell sh /data/local/tmp/pageh-start-bridge.sh $appUid --check
if ($LASTEXITCODE -eq 0) { return }
if ($LASTEXITCODE -ne 3) { throw 'Existing bridge identity check failed' }
& $Adb push build\dex\classes.dex /data/local/tmp/pageh-helper.dex
Check-Exit
& $Adb shell chmod 444 /data/local/tmp/pageh-helper.dex
Check-Exit
& $Adb shell sh /data/local/tmp/pageh-start-bridge.sh $appUid
Check-Exit
& $Adb shell cat /data/local/tmp/pageh-bridge.log

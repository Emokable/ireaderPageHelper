param([string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
& $Adb push (Join-Path $PSScriptRoot 'stop-bridge.sh') /data/local/tmp/pageh-stop-bridge.sh
if ($LASTEXITCODE -ne 0) { throw 'Could not upload stop script' }
& $Adb shell sh /data/local/tmp/pageh-stop-bridge.sh
if ($LASTEXITCODE -ne 0) { throw 'Could not stop PageH bridge' }

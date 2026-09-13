$ErrorActionPreference = 'Stop'
$toolDir = Join-Path $PSScriptRoot 'tools'
$null = New-Item -ItemType Directory -Force -Path $toolDir
$jarPath = Join-Path $toolDir 'r8-8.13.17.jar'
$expected = 'D31FD0DC751D48740009CDD9A485126ACB1D0D14C59B9F05579479940F4ADF74'
if (!(Test-Path -LiteralPath $jarPath)) {
    Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.13.17/r8-8.13.17.jar' -OutFile $jarPath
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $jarPath).Hash -ne $expected) {
    throw 'R8 checksum mismatch. Inspect tools/r8-8.13.17.jar; do not use it.'
}
Write-Host 'Google R8 8.13.17 SHA256 verified.'

param([Parameter(Mandatory=$true)][string]$Serial, [string]$Adb='adb')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$bundle = Join-Path $root 'research/private-content.zip'
$runtime = Join-Path $root 'research/ut99-android-runtime-system.zip'
if (-not (Test-Path -LiteralPath $bundle -PathType Leaf)) { throw 'Run scripts/prepare_private_content.py first.' }
if (-not (Test-Path -LiteralPath $runtime -PathType Leaf)) { throw 'Run scripts/prepare_shared_runtime.py first.' }
function Invoke-Adb([string[]]$Arguments) {
    & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
}
Invoke-Adb @('get-state')
& $Adb -s $Serial shell test -e /sdcard/UT99
if ($LASTEXITCODE -eq 0) { throw 'The phone already has /sdcard/UT99. Refusing to overwrite it.' }
if ($LASTEXITCODE -ne 1) { throw 'Could not check the phone storage folder.' }
$temporary = '/data/local/tmp/ut99-shared-content.zip'
try {
    Invoke-Adb @('push', $bundle, $temporary)
    Invoke-Adb @('shell', 'mkdir', '/sdcard/UT99')
    # The private archive can contain stock and patched package names that
    # differ only in case. On Android shared storage those names may alias;
    # archive order puts the matching 469e version last.
    Invoke-Adb @('shell', 'unzip', '-oq', $temporary, '-d', '/sdcard/UT99', '-x', "'core-load/*'")
    Invoke-Adb @('push', $runtime, '/data/local/tmp/ut99-shared-runtime.zip')
    Invoke-Adb @('shell', 'unzip', '-oq', '/data/local/tmp/ut99-shared-runtime.zip', '-d', '/sdcard/UT99/System')
} finally {
    & $Adb -s $Serial shell rm -f $temporary
    & $Adb -s $Serial shell rm -f /data/local/tmp/ut99-shared-runtime.zip
}
Write-Host 'Game assets and the experimental runtime are in Internal storage/UT99.'

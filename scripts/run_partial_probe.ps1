param([Parameter(Mandatory=$true)][string]$Serial, [string]$Adb = 'adb')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageName = 'org.ut99android.host'
$privateDir = "/data/user/0/$packageName/files/partial"
function Invoke-Adb([string[]]$AdbArgs) {
    & $Adb -s $Serial @AdbArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ')" }
}
$names = @('libutlibcprobe.so', 'libc.so.6', 'libutpartialfixture.so')
foreach ($name in $names) {
    $source = Join-Path $projectRoot "research/libc-probe-build/$name"
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing synthetic probe input: $source" }
}
Invoke-Adb @('get-state')
Invoke-Adb @('install','-r','--abi','arm64-v8a',(Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('shell','am','force-stop',$packageName)
Invoke-Adb @('shell','run-as',$packageName,'mkdir','-p',$privateDir)
foreach ($name in $names) {
    $source = Join-Path $projectRoot "research/libc-probe-build/$name"
    $dest = "$privateDir/$name"
    Invoke-Adb @('push',$source,'/data/local/tmp/ut99-partial-item')
    Invoke-Adb @('shell','run-as',$packageName,'rm','-f',$dest)
    Invoke-Adb @('shell','run-as',$packageName,'cp','/data/local/tmp/ut99-partial-item',$dest)
    Invoke-Adb @('shell','run-as',$packageName,'chmod','400',$dest)
}
Invoke-Adb @('shell','am','start','-n',"$packageName/.MainActivity",'--es','probe','partial')
Write-Host "Inspect: $Adb -s $Serial logcat -d -s UT99Host:I '*:S'"

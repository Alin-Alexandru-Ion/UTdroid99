param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('core','math','gcc','errno','mutex','condition','ctype','stat','format','event','cxx','inline','inline_global','inline_reference')]
    [string]$Probe = 'core',
    [string]$Adb = 'adb',
    [string]$NdkRoot = ''
)
$ErrorActionPreference = 'Stop'
if (-not $NdkRoot) {
    if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME or pass -NdkRoot.' }
    $NdkRoot = Join-Path $env:ANDROID_HOME 'ndk/23.2.8568313'
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$packageName = 'org.ut99android.host'
$privateDir = "/data/user/0/$packageName/files"
function Invoke-Adb([string[]]$AdbArgs) {
    & $Adb -s $Serial @AdbArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($AdbArgs -join ' ')" }
}
$items = @(
    @('research/Core.so', 'Core.so'),
    @('research/sdl-soname-build/sdl/libSDL2-2.0.so.0', 'libSDL2-2.0.so.0'),
    @('research/libdl-bridge-build/libdl.so.2', 'libdl.so.2'),
    @('research/cxx-bridge-build/libstdc++.so.6', 'libstdc++.so.6'),
    @('research/libm-bridge-build/libm.so.6', 'libm.so.6'),
    @('research/libgcc-bridge-build/libgcc_s.so.1', 'libgcc_s.so.1'),
    @('research/libc-probe-build/libutlibcprobe.so', 'libutlibcprobe.so'),
    @('research/libc-probe-build/libutpthreadprobe.so', 'libutpthreadprobe.so'),
    @('research/libc-probe-build/libuteventfixture.so', 'libuteventfixture.so'),
    @('research/libc-probe-build/libutfileprobe.so', 'libutfileprobe.so'),
    @('research/libc-probe-build/libutfilefixture.so', 'libutfilefixture.so'),
    @('research/ctype-reference.bin', 'ctype-reference.bin'),
    @('research/cxx-bridge-build/libcxxthrower.so', 'libcxxthrower.so'),
    @('research/cxx-bridge-build/libcxxfixture.so', 'libcxxfixture.so'),
    @('research/cxx-bridge-build/libcxxinline_thrower.so', 'libcxxinline_thrower.so'),
    @('research/cxx-bridge-build/libcxxinline_fixture.so', 'libcxxinline_fixture.so'),
    @('research/cxx-baseline-build/libcxxref_thrower.so', 'libcxxref_thrower.so'),
    @('research/cxx-baseline-build/libcxxref_fixture.so', 'libcxxref_fixture.so'),
    @((Join-Path $NdkRoot 'toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so'), 'libc++_shared.so')
)
foreach ($item in $items) {
    $source = if ([IO.Path]::IsPathRooted($item[0])) { $item[0] } else { Join-Path $projectRoot $item[0] }
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing probe input: $source" }
}
$core = Join-Path $projectRoot 'research/Core.so'
if ((Get-FileHash -LiteralPath $core -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'f0ef4d6bd3b2bf13c4afb8e0ad0c3ea6be6a63182b9d64a67554136e03c3837d') { throw 'Core.so hash mismatch' }
Invoke-Adb @('get-state')
Invoke-Adb @('install','-r','--abi','arm64-v8a',(Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('shell','am','force-stop',$packageName)
Invoke-Adb @('shell','run-as',$packageName,'mkdir','-p',$privateDir)
foreach ($item in $items) {
    $source = if ([IO.Path]::IsPathRooted($item[0])) { $item[0] } else { Join-Path $projectRoot $item[0] }
    $dest = "$privateDir/$($item[1])"
    Invoke-Adb @('push',$source,'/data/local/tmp/ut99-probe-item')
    Invoke-Adb @('shell','run-as',$packageName,'rm','-f',$dest)
    Invoke-Adb @('shell','run-as',$packageName,'cp','/data/local/tmp/ut99-probe-item',$dest)
    Invoke-Adb @('shell','run-as',$packageName,'chmod','400',$dest)
}
Invoke-Adb @('shell','am','start','-n',"$packageName/.MainActivity",'--es','probe',$Probe)
Write-Host "Inspect the probe result with: $Adb -s $Serial logcat -d -s UT99Host:I '*:S'"

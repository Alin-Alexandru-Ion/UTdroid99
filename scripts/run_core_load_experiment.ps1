param([Parameter(Mandatory=$true)][string]$Serial, [string]$Adb='adb', [switch]$Engine, [switch]$JumpBaseline, [switch]$Launcher, [switch]$Start, [switch]$SDL, [switch]$Content, [switch]$RuntimeOnly, [switch]$NoLaunch, [switch]$GLProbe)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$package='org.ut99android.host'
$directory="/data/user/0/$package/files/core-load"
$items=@(
    @('research/Core.so','Core.so'),
    @('research/SystemARM64/Engine.so','Engine.so'),
    @('research/launcher-probe/Render.so','Render.so'),
    @('research/launcher-probe/IpDrv.so','IpDrv.so'),
    @('research/SystemARM64/Default.ini','Default.ini'),
    @('research/SystemARM64/DefUser.ini','DefUser.ini'),
    @('research/SystemARM64/Default.ini','UnrealTournament.ini'),
    @('research/SystemARM64/DefUser.ini','User.ini'),
    @('research/pc-reference/System/Core.int','Core.int'),
    @('research/libc-probe-build/load-only/libc.so.6','libc.so.6'),
    @('research/libc-probe-build/load-only/ld-linux-aarch64.so.1','ld-linux-aarch64.so.1'),
    @('research/libc-probe-build/load-only/libutjumpfixture.so','libutjumpfixture.so'),
    @('research/libc-probe-build/load-only/libutjumpbaseline.so','libutjumpbaseline.so'),
    @('research/libc-probe-build/load-only/libuticonvfixture.so','libuticonvfixture.so'),
    @('research/libc-probe-build/load-only/libpthread.so.0','libpthread.so.0'),
    @('research/launcher-probe/ut-launch-tls.so','ut-launch.so'),
    @('research/libc-probe-build/libutlibcprobe.so','libutlibcprobe.so'),
    @('research/sdl-soname-build/sdl/libSDL2-2.0.so.0','libSDL2-2.0.so.0'),
    @('research/libdl-bridge-build/libdl.so.2','libdl.so.2'),
    @('research/cxx-bridge-build/libstdc++.so.6','libstdc++.so.6'),
    @('research/libm-bridge-build/libm.so.6','libm.so.6'),
    @('research/libgcc-bridge-build/libgcc_s.so.1','libgcc_s.so.1')
)
function Invoke-Adb([string[]]$Arguments) {
    & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
}
foreach ($item in $items) { if (-not (Test-Path -LiteralPath (Join-Path $root $item[0]) -PathType Leaf)) { throw "Missing $($item[0])" } }
if ((Get-FileHash -LiteralPath (Join-Path $root 'research/Core.so')).Hash -ne 'F0EF4D6BD3B2BF13C4AFB8E0AD0C3EA6BE6A63182B9D64A67554136E03C3837D') { throw 'Core hash mismatch' }
if ((Get-FileHash -LiteralPath (Join-Path $root 'research/SystemARM64/Engine.so')).Hash -ne '7C08C8B166684F7A10B9B88E6BFE0AA965E982BE5B9508BCF32061B1730F4853') { throw 'Engine hash mismatch' }
if ((Get-FileHash -LiteralPath (Join-Path $root 'research/launcher-probe/ut-launch-tls.so')).Hash -ne 'FAFD3829192F7F829C9B40BF2D7ECE98439DD5DE2E5A4D27E8EAB67E815AF0A4') { throw 'Launcher probe hash mismatch' }
Invoke-Adb @('get-state')
Invoke-Adb @('install','-r','--abi','arm64-v8a',(Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'))
Invoke-Adb @('shell','am','force-stop',$package)
Invoke-Adb @('shell','run-as',$package,'mkdir','-p',$directory)
Invoke-Adb @('shell','run-as',$package,'mkdir','-p',"/data/user/0/$package/files/Textures","/data/user/0/$package/files/System")
if ($Content -and $RuntimeOnly) { throw 'Choose -Content or -RuntimeOnly, not both.' }
if ($Content -or $RuntimeOnly) {
    $bundle=Join-Path $root 'research/private-content.zip'
    if (-not (Test-Path -LiteralPath $bundle -PathType Leaf)) { throw 'Run scripts/prepare_private_content.py first' }
    $archive=[IO.Compression.ZipFile]::OpenRead($bundle)
    try { $nativeNames=@($archive.Entries | Where-Object { $_.FullName -match '^core-load/[A-Za-z0-9_.-]+\.so(?:\.[0-9]+)?$' } | ForEach-Object { $_.Name }) }
    finally { $archive.Dispose() }
    $writable=@("/data/user/0/$package/files/Textures/Palettes.utx","/data/user/0/$package/files/System/Core.int") + @($nativeNames | ForEach-Object { "$directory/$_" })
    foreach ($path in $writable) {
        & $Adb -s $Serial shell run-as $package test -f $path
        if ($LASTEXITCODE -eq 0) { Invoke-Adb @('shell','run-as',$package,'chmod','600',$path) }
    }
    Invoke-Adb @('push',$bundle,'/data/local/tmp/ut99-private-content.zip')
    if ($RuntimeOnly) {
        Invoke-Adb @('shell','run-as',$package,'unzip','-oq','/data/local/tmp/ut99-private-content.zip',"'core-load/*'",'-d',"/data/user/0/$package/files")
    } else {
        Invoke-Adb @('shell','run-as',$package,'unzip','-oq','/data/local/tmp/ut99-private-content.zip','-d',"/data/user/0/$package/files")
    }
    foreach ($name in $nativeNames) { Invoke-Adb @('shell','run-as',$package,'chmod','400',"$directory/$name") }
    Invoke-Adb @('shell','rm','-f','/data/local/tmp/ut99-private-content.zip')
}
foreach ($item in $items) {
    Invoke-Adb @('push',(Join-Path $root $item[0]),'/data/local/tmp/ut99-core-load-item')
    Invoke-Adb @('shell','run-as',$package,'rm','-f',"$directory/$($item[1])")
    Invoke-Adb @('shell','run-as',$package,'cp','/data/local/tmp/ut99-core-load-item',"$directory/$($item[1])")
    $mode = if ($item[1] -in @('UnrealTournament.ini','User.ini')) { '600' } else { '400' }
    Invoke-Adb @('shell','run-as',$package,'chmod',$mode,"$directory/$($item[1])")
}
$requiredContent = @(
    @('research/pc-reference/Textures/Palettes.utx',"/data/user/0/$package/files/Textures/Palettes.utx"),
    @('research/pc-reference/System/Core.int',"/data/user/0/$package/files/System/Core.int")
)
foreach ($item in $requiredContent) {
    $source=Join-Path $root $item[0]
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing authorized content: $source" }
    Invoke-Adb @('push',$source,'/data/local/tmp/ut99-core-load-item')
    Invoke-Adb @('shell','run-as',$package,'rm','-f',$item[1])
    Invoke-Adb @('shell','run-as',$package,'cp','/data/local/tmp/ut99-core-load-item',$item[1])
    Invoke-Adb @('shell','run-as',$package,'chmod','400',$item[1])
}
$probe = if ($JumpBaseline) { 'jump_baseline' } elseif ($Start) { 'launcher_start' } elseif ($Launcher) { 'launcher_load' } elseif ($Engine) { 'engine_load' } else { 'core_load' }
if ($NoLaunch) { Write-Host 'Runtime staged; launch from the app storage setup screen.' }
elseif ($GLProbe) { Invoke-Adb @('shell','am','start','-n',"$package/.ExperimentalSDLActivity",'--es','mode','gl_probe') }
elseif ($SDL) { Invoke-Adb @('shell','am','start','-n',"$package/.ExperimentalSDLActivity") }
else { Invoke-Adb @('shell','am','start','-n',"$package/.MainActivity",'--es','probe',$probe) }
Write-Host 'Read UT99Host logs and Android crash buffer; a load may execute constructors.'

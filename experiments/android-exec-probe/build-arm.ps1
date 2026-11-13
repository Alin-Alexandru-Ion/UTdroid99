$ErrorActionPreference = 'Stop'
$sdk = $env:ANDROID_HOME
if (-not $sdk) { throw 'Set ANDROID_HOME to your Android SDK directory' }
$jdk = $env:JAVA_HOME
if (-not $jdk) { throw 'Set JAVA_HOME to your JDK directory' }
$out = Join-Path $PSScriptRoot '..\..\research\android-arm-exec-probe'
$existing = Join-Path $PSScriptRoot '..\..\research\android-exec-probe'
$runtime = Join-Path $PSScriptRoot '..\..\research\linux-native-runtime\arm64-v8a'
New-Item -ItemType Directory -Force -Path $out,(Join-Path $out 'classes'),(Join-Path $out 'dex') | Out-Null
$env:TEMP = $out; $env:TMP = $out; $env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
$jar = Join-Path $sdk 'platforms\android-36\android.jar'
$bin = Join-Path $sdk 'build-tools\36.0.0'
& (Join-Path $jdk 'bin\javac.exe') -source 11 -target 11 -classpath $jar -d (Join-Path $out 'classes') (Join-Path $PSScriptRoot 'ArmProbeActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& (Join-Path $bin 'd8.bat') --lib $jar --min-api 34 --output (Join-Path $out 'dex') (Join-Path $out 'classes\org\ut99android\armprobe\ArmProbeActivity.class')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
$unsigned = Join-Path $out 'unsigned.apk'
Copy-Item -Force (Join-Path $PSScriptRoot 'AndroidManifest-arm.xml') (Join-Path $out 'AndroidManifest.xml')
& (Join-Path $bin 'aapt.exe') package -f -M (Join-Path $out 'AndroidManifest.xml') -I $jar -F $unsigned
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }
& py -3 -c "import zipfile,sys,pathlib; z=zipfile.ZipFile(sys.argv[1],'a',compression=zipfile.ZIP_DEFLATED); out,old,runtime=map(pathlib.Path,sys.argv[2:]); proot=(runtime/'libutproot.so').read_bytes(); pairs=[(b'/data/data/com.termux/files/usr/lib\x00',b'\x24ORIGIN'),(b'libtalloc.so.2\x00',b'libtalloc.so')]; [(None if proot.count(a)==1 else (_ for _ in ()).throw(ValueError(a))) for a,b in pairs]; exec('for a,b in pairs: proot=proot.replace(a,b+bytes(len(a)-len(b)))'); z.write(out/'dex/classes.dex','classes.dex'); z.writestr('lib/arm64-v8a/libutproot.so',proot); z.write(runtime/'libutprootloader.so','lib/arm64-v8a/libutprootloader.so'); z.write(runtime/'libtalloc.so.2','lib/arm64-v8a/libtalloc.so'); z.write(runtime/'libandroid-shmem.so','lib/arm64-v8a/libandroid-shmem.so'); [z.write(runtime/n,'assets/'+n) for n in ('libtalloc.so.2','libandroid-shmem.so')]; [z.write(old/n,'assets/'+n) for n in ('arm-uname','arm-ld-linux.so','arm-libc.so')]; z.close()" $unsigned $out $existing $runtime
if ($LASTEXITCODE -ne 0) { throw 'APK assembly failed' }
& (Join-Path $bin 'apksigner.bat') sign --ks (Join-Path $existing 'test.keystore') --ks-key-alias test --ks-pass pass:android --key-pass pass:android --out (Join-Path $out 'arm-probe.apk') $unsigned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }


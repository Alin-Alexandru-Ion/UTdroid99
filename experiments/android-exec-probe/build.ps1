$ErrorActionPreference = 'Stop'
$sdk = $env:ANDROID_HOME
if (-not $sdk) { throw 'Set ANDROID_HOME to your Android SDK directory' }
$jdk = $env:JAVA_HOME
if (-not $jdk) { throw 'Set JAVA_HOME to your JDK directory' }
$src = $PSScriptRoot
$out = Join-Path $src '..\..\research\android-exec-probe'
New-Item -ItemType Directory -Force -Path $out,(Join-Path $out 'classes'),(Join-Path $out 'dex'),(Join-Path $out 'lib\x86_64') | Out-Null
$env:TEMP = $out
$env:TMP = $out
$androidJar = Join-Path $sdk 'platforms\android-36\android.jar'
$bin = Join-Path $sdk 'build-tools\36.0.0'
$env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
& (Join-Path $jdk 'bin\javac.exe') -source 11 -target 11 -classpath $androidJar -d (Join-Path $out 'classes') (Join-Path $src 'ProbeActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
& (Join-Path $bin 'd8.bat') --lib $androidJar --min-api 28 --output (Join-Path $out 'dex') (Join-Path $out 'classes\org\ut99android\execprobe\ProbeActivity.class')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
$clang = Join-Path $sdk 'ndk\23.2.8568313\toolchains\llvm\prebuilt\windows-x86_64\bin\x86_64-linux-android31-clang.cmd'
& $clang -fPIE -pie (Join-Path $src 'probe.c') -o (Join-Path $out 'lib\x86_64\libexecfixture.so')
if ($LASTEXITCODE -ne 0) { throw 'native fixture compilation failed' }
$unsigned = Join-Path $out 'unsigned.apk'
& (Join-Path $bin 'aapt.exe') package -f -M (Join-Path $src 'AndroidManifest.xml') -I $androidJar -F $unsigned
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }
& py -3 -c "import zipfile,sys,pathlib; p=pathlib.Path(sys.argv[2]); z=zipfile.ZipFile(sys.argv[1],'a',compression=zipfile.ZIP_DEFLATED); z.write(p/'dex/classes.dex','classes.dex'); z.write(p/'lib/x86_64/libexecfixture.so','lib/x86_64/libexecfixture.so'); z.write(p/'libguestloader.so','lib/x86_64/libguestloader.so'); z.write(p/'proot','lib/x86_64/libprobeproot.so'); z.write(p/'proot-loader','lib/x86_64/libprootloader.so'); z.write(p/'qemu-aarch64','lib/x86_64/libqemuprobe.so'); [z.write(p/n,'assets/'+n) for n in ('guest-libc.so','libtalloc.so.2','libandroid-shmem.so','arm-uname','arm-ld-linux.so','arm-libc.so')]; [z.write(x,'assets/qemu/'+x.name) for x in (p/'qemu-deps').iterdir()]; z.close()" $unsigned $out
if ($LASTEXITCODE -ne 0) { throw 'APK assembly failed' }
$key = Join-Path $out 'test.keystore'
if (-not (Test-Path $key)) {
    & (Join-Path $jdk 'bin\keytool.exe') -genkeypair -keystore $key -storepass android -keypass android -alias test -keyalg RSA -validity 365 -dname 'CN=UTdroid99 execution probe'
    if ($LASTEXITCODE -ne 0) { throw 'test key generation failed' }
}
& (Join-Path $bin 'apksigner.bat') sign --ks $key --ks-key-alias test --ks-pass pass:android --key-pass pass:android --out (Join-Path $out 'exec-probe.apk') $unsigned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }

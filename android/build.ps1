# Builds Horizon.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = "$sdk\build-tools\35.0.0"
$jar  = "$sdk\platforms\android-35\android.jar"
$out  = "$here\build"

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\gen","$out\classes","$out\dex" | Out-Null
Copy-Item "$here\..\web\index.html" "$here\assets\index.html" -Force

& "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 compile failed' }
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$here\AndroidManifest.xml" `
    -A "$here\assets" --java "$out\gen" "$out\res.zip" `
    --min-sdk-version 30 --target-sdk-version 35 --version-code 1 --version-name 1.0
if ($LASTEXITCODE) { throw 'aapt2 link failed' }

$srcs = Get-ChildItem -Recurse "$here\src","$out\gen" -Filter *.java | ForEach-Object FullName
& javac -nowarn -source 11 -target 11 -encoding UTF-8 -classpath $jar -d "$out\classes" @srcs
if ($LASTEXITCODE) { throw 'javac failed' }

# one jar instead of every .class on the command line (Windows limits command length)
& jar cf "$out\classes.jar" -C "$out\classes" .
if ($LASTEXITCODE) { throw 'jar failed' }
& "$bt\d8.bat" --release --lib $jar --min-api 30 --output "$out\dex" "$out\classes.jar"
if ($LASTEXITCODE) { throw 'd8 failed' }

# Add classes.dex to the resource APK.
Copy-Item "$out\base.apk" "$out\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\unsigned.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", 'classes.dex') | Out-Null
$zip.Dispose()

& "$bt\zipalign.exe" -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'zipalign failed' }

$ks = "$here\horizon-debug.keystore"
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias horizon `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Horizon Launcher Debug" 2>$null
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$here\..\Horizon.apk" "$out\aligned.apk"
if ($LASTEXITCODE) { throw 'apksigner failed' }
& "$bt\apksigner.bat" verify --print-certs "$here\..\Horizon.apk" | Select-Object -First 1
Get-Item "$here\..\Horizon.apk" | Select-Object FullName, Length

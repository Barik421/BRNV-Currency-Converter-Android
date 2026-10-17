param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!(Test-Path (Join-Path $root 'signing.properties'))) {
    throw 'Local signing.properties is missing. Configure the upload key before building a distributable release.'
}
if (!(Test-Path (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Set JAVA_HOME to a JDK.' }
$env:JAVA_HOME = $JavaHome
Push-Location $root
try {
    & ./gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:bundleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release checks failed. Do not upload this build.' }
    Get-FileHash 'app/build/outputs/apk/release/app-release.apk', 'app/build/outputs/bundle/release/app-release.aab' -Algorithm SHA256
} finally { Pop-Location }

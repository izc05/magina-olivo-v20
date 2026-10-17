param([string]$Serial='emulator-5614',[string]$Sdk="$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference='Stop'
$adb=Join-Path $Sdk 'platform-tools/adb.exe'
if($Serial -notmatch '^emulator-\d+$' -or (& $adb -s $Serial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Synthetic emulator only' }
$fixture=[guid]::NewGuid().ToString()
$class='com.isivoltpro.maginaolivo.FarmLinkProcessTest'
$package='com.isivoltpro.maginaolivo.dev'
foreach($phase in @('persist','recover')) {
    $result=& $adb -s $Serial shell am instrument -w -e class $class -e qaG1bPhase $phase -e qaG1bFixture $fixture "$package.test/androidx.test.runner.AndroidJUnitRunner"
    $result | Write-Output
    if(($result -join "`n") -notmatch 'OK \(1 test\)' -or ($result -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED') { throw "Synthetic phase $phase failed" }
    if($phase -eq 'persist') {
        & $adb -s $Serial shell am force-stop $package
        if($LASTEXITCODE -ne 0) { throw 'Force-stop failed' }
        Write-Output 'PASS: durable consent committed; process force-stopped without clearing data'
    }
}
Write-Output 'PASS: encrypted cached identity, exact farm selection, original data and bytes recovered offline; account B denied'

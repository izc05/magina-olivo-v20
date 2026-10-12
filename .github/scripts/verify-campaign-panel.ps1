param(
    [Parameter(Mandatory = $true)][string]$Adb,
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$EvidenceDirectory = "app/build/campaign-panel-qa"
)

$ErrorActionPreference = "Stop"
if ($Serial -notmatch '^emulator-\d+$') { throw "Select an emulator explicitly; physical devices are not allowed." }
function Invoke-Device {
    $result = & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $args" }
    return $result
}
if ((Invoke-Device shell getprop ro.kernel.qemu).Trim() -ne "1") { throw "Selected device is not an emulator." }

# Use the already installed DEV/test builds. Never clear retained app data.
$size = (Invoke-Device shell wm size) -join "`n"
$effectiveSize = [regex]::Match($size, 'Override size: (\d+)x(\d+)')
if (-not $effectiveSize.Success) { $effectiveSize = [regex]::Match($size, 'Physical size: (\d+)x(\d+)') }
if (-not $effectiveSize.Success) { throw "Cannot determine the effective display width." }
$width = [int]$effectiveSize.Groups[1].Value
$densityBefore = (Invoke-Device shell wm density) -join "`n"
$overrideBefore = [regex]::Match($densityBefore, 'Override density: (\d+)').Groups[1].Value
$fontBefore = (Invoke-Device shell settings get system font_scale).Trim()
$testClass = "com.isivoltpro.maginaolivo.Cr012SurfacesTest"
$methods = @(
    "actualCampaignHasSeparateProductionAndCompactCostsWithoutWorkerLists",
    "campaignConfirmedPortionDisclosesUnpricedResources",
    "campaignPanelIsReadableAndChartsStayFoldedInLight",
    "campaignPanelIsReadableAndChartsStayFoldedInDark"
)
$selection = ($methods | ForEach-Object { "$testClass#$_" }) -join ","
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
try {
    foreach ($dp in @(360, 390)) {
        foreach ($font in @("1.0", "1.3")) {
            $density = [int][Math]::Round($width * 160 / $dp)
            Invoke-Device shell wm density $density | Out-Null
            Invoke-Device shell settings put system font_scale $font | Out-Null
            $name = "$($dp)dp-font$font"
            @("target_dp=$dp", "requested_density=$density", "font_scale=$font", $size, (Invoke-Device shell wm density)) |
                Set-Content -LiteralPath "$EvidenceDirectory/$name-display.txt"
            $output = Invoke-Device shell am instrument -w -e class $selection com.isivoltpro.maginaolivo.dev.test/androidx.test.runner.AndroidJUnitRunner
            $output | Set-Content -LiteralPath "$EvidenceDirectory/$name-tests.txt"
            if (($output -join "`n") -notmatch '(?m)^OK \(4 tests\)\s*$') { throw "Panel regression failed for $name; see saved output." }
            Write-Output "$name PASS (4 tests, light and dark)"
        }
    }
    Invoke-Device pull /sdcard/Android/data/com.isivoltpro.maginaolivo.dev/files/cr012-slice4 $EvidenceDirectory
} finally {
    if ($overrideBefore) { Invoke-Device shell wm density $overrideBefore | Out-Null }
    else { Invoke-Device shell wm density reset | Out-Null }
    if ($fontBefore -eq "null") { Invoke-Device shell settings delete system font_scale | Out-Null }
    else { Invoke-Device shell settings put system font_scale $fontBefore | Out-Null }
}

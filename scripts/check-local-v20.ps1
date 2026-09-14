<#
.SYNOPSIS
  Comprueba si este equipo está listo para ejecutar Mágina V20 en local.

.DESCRIPTION
  No instala, reinicia, ni modifica servicios. Informa del estado de Node, pnpm,
  Docker, virtualización y los puertos usados por la web, API y base de datos.
#>
[CmdletBinding()]
param(
  [switch]$RequireDocker
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$results = [System.Collections.Generic.List[object]]::new()
$failed = $false

function Add-Check {
  param(
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][ValidateSet('OK', 'AVISO', 'FALTA')][string]$Status,
    [Parameter(Mandatory = $true)][string]$Detail
  )

  $script:results.Add([pscustomobject]@{ Componente = $Name; Estado = $Status; Detalle = $Detail })
  if ($Status -eq 'FALTA') { $script:failed = $true }
}

function Command-Output {
  param([string]$Command, [string[]]$Arguments)
  try {
    $output = & $Command @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($output | Out-String).Trim()
  } catch {
    return $null
  }
}

Push-Location $repositoryRoot
try {
  $node = Command-Output node @('--version')
  if ($node -match '^v22\.') { Add-Check 'Node.js' 'OK' $node }
  elseif ($node) { Add-Check 'Node.js' 'FALTA' "Se requiere Node 22; se ha encontrado $node." }
  else { Add-Check 'Node.js' 'FALTA' 'No se ha encontrado Node.js 22 en PATH.' }

  $pnpm = Command-Output pnpm @('--version')
  if ($pnpm -and [version]$pnpm -ge [version]'10.15.1' -and [version]$pnpm -lt [version]'11.0.0') { Add-Check 'pnpm' 'OK' $pnpm }
  elseif ($pnpm) { Add-Check 'pnpm' 'FALTA' "Se requiere pnpm 10.15.x; se ha encontrado $pnpm." }
  else { Add-Check 'pnpm' 'FALTA' 'No se ha encontrado pnpm 10.15.x en PATH.' }

  $processor = Get-CimInstance Win32_Processor | Select-Object -First 1
  if ($processor.VirtualizationFirmwareEnabled -eq $true) {
    Add-Check 'Virtualización' 'OK' 'La virtualización de firmware está activa.'
  } else {
    Add-Check 'Virtualización' 'FALTA' 'Activa Intel VT-x/AMD-V en BIOS o UEFI y reinicia el equipo.'
  }

  $docker = Command-Output docker @('version', '--format', '{{.Server.Version}}')
  if ($docker) { Add-Check 'Docker Desktop' 'OK' "Motor activo ($docker)." }
  elseif ($RequireDocker) { Add-Check 'Docker Desktop' 'FALTA' 'Instala e inicia Docker Desktop después de activar la virtualización.' }
  else { Add-Check 'Docker Desktop' 'AVISO' 'El motor no está disponible todavía; será necesario para API y PostgreSQL.' }

  $wsl = Command-Output wsl.exe @('--status')
  if ($wsl -match 'Default Version:\s*2|Versión predeterminada:\s*2') { Add-Check 'WSL 2' 'OK' 'WSL 2 está configurado.' }
  else { Add-Check 'WSL 2' 'AVISO' 'Docker Desktop comprobará WSL 2 al iniciarse.' }

  foreach ($port in 3001, 3002, 5432) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) { Add-Check "Puerto $port" 'AVISO' "Está en uso por PID $($listener.OwningProcess)." }
    else { Add-Check "Puerto $port" 'OK' 'Disponible.' }
  }

  $results | Format-Table -AutoSize
  if ($failed) {
    Write-Host "`nEl equipo aún no está listo para el stack completo." -ForegroundColor Yellow
    exit 1
  }

  Write-Host "`nEquipo listo para ejecutar .\scripts\start-local-v20.ps1" -ForegroundColor Green
} finally {
  Pop-Location
}

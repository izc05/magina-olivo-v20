<#
.SYNOPSIS
  Arranca el stack de desarrollo local de Mágina V20 con una sola orden.

.DESCRIPTION
  Prepara PostgreSQL/PostGIS, inicia la API en el puerto 3002 y la web en el
  puerto 3001. Los registros se guardan en artifacts/local-api*.log y
  artifacts/local-web*.log. No contacta con el mini PC ni con staging.
#>
[CmdletBinding()]
param(
  [switch]$SeedDemo
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$artifactsDirectory = Join-Path $repositoryRoot 'artifacts'
$apiUrl = 'http://127.0.0.1:3002/health'
$webUrl = 'http://127.0.0.1:3001/'

function Assert-PortAvailable {
  param([Parameter(Mandatory = $true)][int]$Port)
  $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($listener) { throw "El puerto $Port ya está en uso por PID $($listener.OwningProcess). Cierra ese proceso o usa la instancia local que ya está iniciada." }
}

function Wait-LocalUrl {
  param([Parameter(Mandatory = $true)][string]$Url, [Parameter(Mandatory = $true)][string]$Name)
  $deadline = (Get-Date).AddSeconds(75)
  do {
    try {
      $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) { return }
    } catch { }
    Start-Sleep -Seconds 2
  } while ((Get-Date) -lt $deadline)
  throw "$Name no respondió en 75 segundos. Revisa los registros en $artifactsDirectory."
}

function Start-LocalProcess {
  param(
    [Parameter(Mandatory = $true)][string]$Command,
    [Parameter(Mandatory = $true)][string]$StandardOutput,
    [Parameter(Mandatory = $true)][string]$StandardError
  )

  $escapedRoot = $repositoryRoot.Replace("'", "''")
  $script = "& { Set-Location -LiteralPath '$escapedRoot'; $Command }"
  Start-Process -FilePath powershell.exe -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $script) -WindowStyle Hidden -RedirectStandardOutput $StandardOutput -RedirectStandardError $StandardError -PassThru
}

Assert-PortAvailable -Port 3001
Assert-PortAvailable -Port 3002

& (Join-Path $PSScriptRoot 'start-local-v20.ps1') -SeedDemo:$SeedDemo
if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar la base local.' }

New-Item -ItemType Directory -Force -Path $artifactsDirectory | Out-Null
$apiLog = Join-Path $artifactsDirectory 'local-api.log'
$apiErrorLog = Join-Path $artifactsDirectory 'local-api-error.log'
$webLog = Join-Path $artifactsDirectory 'local-web.log'
$webErrorLog = Join-Path $artifactsDirectory 'local-web-error.log'
Remove-Item -LiteralPath $apiLog, $apiErrorLog, $webLog, $webErrorLog -Force -ErrorAction SilentlyContinue

$apiCommand = "`$env:DATABASE_URL = 'postgresql://magina:magina@127.0.0.1:5432/magina_v20'; `$env:PORT = '3002'; `$env:HOST = '127.0.0.1'; `$env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:3001,http://localhost:3001'; pnpm dev:api"
$webCommand = "`$env:NEXT_PUBLIC_API_URL = 'http://127.0.0.1:3002'; pnpm dev -- --hostname 127.0.0.1 --port 3001"
$apiProcess = $null
$webProcess = $null

try {
  $apiProcess = Start-LocalProcess -Command $apiCommand -StandardOutput $apiLog -StandardError $apiErrorLog
  Wait-LocalUrl -Url $apiUrl -Name 'La API local'
  $webProcess = Start-LocalProcess -Command $webCommand -StandardOutput $webLog -StandardError $webErrorLog
  Wait-LocalUrl -Url $webUrl -Name 'La web local'

  @{ apiPid = $apiProcess.Id; webPid = $webProcess.Id; startedAt = (Get-Date).ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $artifactsDirectory 'local-stack.json') -Encoding utf8

  Write-Host ''
  Write-Host 'Mágina V20 está lista en local.' -ForegroundColor Green
  Write-Host 'Web: http://127.0.0.1:3001'
  Write-Host 'API: http://127.0.0.1:3002/health'
  Write-Host "Registros: $artifactsDirectory"
} catch {
  if ($webProcess) { Stop-Process -Id $webProcess.Id -Force -ErrorAction SilentlyContinue }
  if ($apiProcess) { Stop-Process -Id $apiProcess.Id -Force -ErrorAction SilentlyContinue }
  throw
}

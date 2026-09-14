<#
.SYNOPSIS
  Prepara los servicios de Mágina V20 para desarrollo local.

.DESCRIPTION
  Arranca únicamente PostgreSQL/PostGIS en Docker, aplica las migraciones y
  muestra las variables que separan la web local (3001) de la API local (3002).
  No usa ni modifica ningún servidor remoto.

.PARAMETER SeedDemo
  Carga los datos de demostración incluidos en el repositorio.

.PARAMETER StartApi
  Arranca la API en la terminal actual al terminar la preparación.
#>
[CmdletBinding()]
param(
  [switch]$SeedDemo,
  [switch]$StartApi
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repositoryRoot 'infra/docker-compose.dev.yml'
$databaseUrl = 'postgresql://magina:magina@127.0.0.1:5432/magina_v20'
$containerName = 'magina-v20-postgres'
$preflightScript = Join-Path $PSScriptRoot 'check-local-v20.ps1'

function Invoke-LocalPsqlFile {
  param([Parameter(Mandatory = $true)][string]$Path)

  $filename = Split-Path -Leaf $Path
  $escapedFilename = $filename.Replace("'", "''")
  $alreadyApplied = docker exec $containerName psql -U magina -d magina_v20 -At -c "SELECT 1 FROM schema_migrations WHERE filename = '$escapedFilename' LIMIT 1;"
  if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar el historial de migraciones local." }
  if (($alreadyApplied | Out-String).Trim() -eq '1') {
    Write-Host "Migración ya aplicada: $filename"
    return
  }

  Write-Host "Aplicando migración: $filename"
  Get-Content -LiteralPath $Path -Raw |
    docker exec -i $containerName psql -U magina -d magina_v20 -v ON_ERROR_STOP=1
  if ($LASTEXITCODE -ne 0) {
    throw "No se pudo aplicar $filename."
  }

  docker exec $containerName psql -U magina -d magina_v20 -v ON_ERROR_STOP=1 -c "INSERT INTO schema_migrations (filename) VALUES ('$escapedFilename');" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "La migración $filename se aplicó pero no se pudo registrar en el historial local." }
}

& $preflightScript -RequireDocker
if ($LASTEXITCODE -ne 0) { throw 'Corrige los requisitos indicados por el diagnóstico antes de preparar el entorno local.' }

Push-Location $repositoryRoot
try {
  docker compose -f $composeFile up -d postgres
  if ($LASTEXITCODE -ne 0) { throw 'No se pudo arrancar PostgreSQL/PostGIS local.' }

  $deadline = (Get-Date).AddMinutes(2)
  do {
    $health = docker inspect --format '{{.State.Health.Status}}' $containerName 2>$null
    if ($health -eq 'healthy') { break }
    Start-Sleep -Seconds 2
  } while ((Get-Date) -lt $deadline)

  if ($health -ne 'healthy') { throw 'PostgreSQL/PostGIS no alcanzó el estado healthy en dos minutos.' }

  $migrationLedgerExists = docker exec $containerName psql -U magina -d magina_v20 -At -c "SELECT to_regclass('public.schema_migrations') IS NOT NULL;"
  if ($LASTEXITCODE -ne 0) { throw 'No se pudo inspeccionar la base de datos local.' }
  if (($migrationLedgerExists | Out-String).Trim() -ne 't') {
    $existingTables = docker exec $containerName psql -U magina -d magina_v20 -At -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar el estado de la base de datos local.' }
    if ([int]($existingTables | Out-String).Trim() -gt 0) {
      throw 'La base local ya contiene tablas pero no tiene historial de migraciones. No se modificará para proteger sus datos. Revisa el contenedor antes de continuar.'
    }
    docker exec $containerName psql -U magina -d magina_v20 -v ON_ERROR_STOP=1 -c 'CREATE TABLE schema_migrations (filename text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el historial de migraciones local.' }
  }

  Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'database/migrations') -Filter '*.sql' |
    Sort-Object Name |
    ForEach-Object { Invoke-LocalPsqlFile -Path $_.FullName }

  if ($SeedDemo) {
    Invoke-LocalPsqlFile -Path (Join-Path $repositoryRoot 'database/seeds/001_demo.sql')
  }

  Write-Host ''
  Write-Host 'Base de datos local preparada.' -ForegroundColor Green
  Write-Host 'Web: http://127.0.0.1:3001'
  Write-Host 'API: http://127.0.0.1:3002'
  Write-Host ''
  Write-Host 'Para otra terminal de web:'
  Write-Host '$env:NEXT_PUBLIC_API_URL = ''http://127.0.0.1:3002'''
  Write-Host 'pnpm dev -- --hostname 127.0.0.1 --port 3001'
  Write-Host ''
  Write-Host 'Para otra terminal de API:'
  Write-Host "`$env:DATABASE_URL = '$databaseUrl'"
  Write-Host '$env:PORT = ''3002'''
  Write-Host '$env:HOST = ''127.0.0.1'''
  Write-Host '$env:CORS_ALLOWED_ORIGINS = ''http://127.0.0.1:3001,http://localhost:3001'''
  Write-Host 'pnpm dev:api'

  if ($StartApi) {
    $env:DATABASE_URL = $databaseUrl
    $env:PORT = '3002'
    $env:HOST = '127.0.0.1'
    $env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:3001,http://localhost:3001'
    pnpm dev:api
  }
} finally {
  Pop-Location
}

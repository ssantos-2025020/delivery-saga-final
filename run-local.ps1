# Levanta los 4 microservicios + PostgreSQL en Windows (sin Docker).
# Requisitos: Maven, Java 17+, PostgreSQL local (user/psw postgres/postgres).
# Uso:
#   powershell -ExecutionPolicy Bypass -File run-local.ps1
# Los logs quedan en target/logs/*.log

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$mvn = "mvn"
$jwtSecret = "microservicios-secreto-firma-jwt-super-seguro-2024!!"
$internalKey = "internal-super-key-2024"
$serverUser = "delivery"
$serverPass = "admin"

function Ejecutar-Psql([string[]]$sql) {
    $psql = "psql"
    if (-not (Test-Path $psql)) { $psql = "psql" }
    $env:PGPASSWORD = "postgres"
    & $psql -h localhost -p 5432 -U postgres -v ON_ERROR_STOP=1 -tAc $sql 'postgres'
}

function Ensure-Db([string]$name) {
    $exists = Ejecutar-Psql "SELECT 1 FROM pg_database WHERE datname='$name'"
    if ($exists -notmatch "1") {
        Ejecutar-Psql "CREATE DATABASE $name OWNER $serverUser" | Out-Null
        Write-Host "Base creada: $name"
    } else {
        Write-Host "Base ya existe: $name"
    }
}

Write-Host "==> Verificando rol y bases de datos..."
$roleExists = Ejecutar-Psql "SELECT 1 FROM pg_roles WHERE rolname='$serverUser'"
if ($roleExists -notmatch "1") {
    Ejecutar-Psql "CREATE ROLE $serverUser LOGIN PASSWORD '$serverPass'" | Out-Null
    Write-Host "Rol creado: $serverUser"
} else {
    Write-Host "Rol ya existe: $serverUser"
}
Ensure-Db "auth_db"
Ensure-Db "catalogo_db"
Ensure-Db "pedidos_db"

Write-Host "==> Compilando (mvn clean package -DskipTests)..."
Push-Location $root
& $mvn -q -B clean package -DskipTests
if ($LASTEXITCODE -ne 0) { Pop-Location; exit 1 }
Pop-Location

$logDir = Join-Path $root "target\logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Start-Service([string]$name, [int]$port, [string]$jar, [hashtable]$envs) {
    Write-Host "==> Arrancando $name en :$port"
    foreach ($k in $envs.Keys) { Set-Item -Path "Env:$k" -Value $envs[$k] }
    Start-Process -FilePath "java" -ArgumentList "-jar", "`"$jar`"" `
        -RedirectStandardOutput (Join-Path $logDir "$name.log") `
        -RedirectStandardError (Join-Path $logDir "$name.err.log") `
        -WindowStyle Hidden
    Remove-Item Env:JWT_SECRET,Env:JWT_EXPIRATION_MS,Env:INTERNAL_API_KEY,Env:DB_URL,Env:DB_USERNAME,Env:DB_PASSWORD,Env:CATALOGO_URL,Env:AUTH_SERVICE_URL,Env:CATALOGO_SERVICE_URL,Env:PEDIDOS_SERVICE_URL -ErrorAction SilentlyContinue
}

Start-Service "auth-service" 8081 (Join-Path $root "auth-service\target\auth-service-1.0.0.jar") @{
    DB_URL = "jdbc:postgresql://localhost:5432/auth_db"
    DB_USERNAME = $serverUser; DB_PASSWORD = $serverPass
    JWT_SECRET = $jwtSecret; JWT_EXPIRATION_MS = "86400000"
}

Start-Service "catalogo-service" 8082 (Join-Path $root "catalogo-service\target\catalogo-service-1.0.0.jar") @{
    DB_URL = "jdbc:postgresql://localhost:5432/catalogo_db"
    DB_USERNAME = $serverUser; DB_PASSWORD = $serverPass
    INTERNAL_API_KEY = $internalKey
}

Start-Service "pedidos-service" 8083 (Join-Path $root "pedidos-service\target\pedidos-service-1.0.0.jar") @{
    DB_URL = "jdbc:postgresql://localhost:5432/pedidos_db"
    DB_USERNAME = $serverUser; DB_PASSWORD = $serverPass
    INTERNAL_API_KEY = $internalKey
    CATALOGO_URL = "http://localhost:8082"
}

Start-Service "api-gateway" 8080 (Join-Path $root "api-gateway\target\api-gateway-1.0.0.jar") @{
    AUTH_SERVICE_URL = "http://localhost:8081"
    CATALOGO_SERVICE_URL = "http://localhost:8082"
    PEDIDOS_SERVICE_URL = "http://localhost:8083"
    JWT_SECRET = $jwtSecret
}

Write-Host ""
Write-Host "Listo. API en http://localhost:8080 (logs en target\logs)."

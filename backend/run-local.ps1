$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env and fill the local values first."
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $key = $matches[1].Trim()
        $value = $matches[2]
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

$env:DB_USERNAME = $env:MYSQL_USER
$env:DB_PASSWORD = $env:MYSQL_PASSWORD
$env:DB_URL = "jdbc:mysql://localhost:$($env:MYSQL_PORT)/$($env:MYSQL_DATABASE)?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"

Push-Location $PSScriptRoot
try {
    & ".\mvnw.cmd" -s ".mvn\settings.xml" spring-boot:run
    $mavenExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $mavenExitCode

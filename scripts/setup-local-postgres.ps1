[CmdletBinding()]
param(
    [string]$DatabaseName = "wastecollect",
    [string]$DatabaseUser = "wastecollect",
    [string]$DatabasePassword = "change-me-for-local-use",
    [string]$AdministratorUser = "postgres",
    [string]$AdministratorPassword = $env:POSTGRES_ADMIN_PASSWORD,
    [string]$PostgresInstallDirectory = "C:\Program Files\PostgreSQL\16"
)

$ErrorActionPreference = "Stop"

foreach ($identifier in @($DatabaseName, $DatabaseUser, $AdministratorUser)) {
    if ($identifier -notmatch '^[a-z_][a-z0-9_]*$') {
        throw "PostgreSQL identifiers must contain only lowercase letters, numbers, and underscores."
    }
}

$psql = Join-Path $PostgresInstallDirectory "bin\psql.exe"
if (-not (Test-Path -LiteralPath $psql -PathType Leaf)) {
    throw "psql was not found at '$psql'. Pass the correct PostgreSQL installation directory."
}

if ([string]::IsNullOrWhiteSpace($AdministratorPassword)) {
    $securePassword = Read-Host "Password for PostgreSQL administrator '$AdministratorUser'" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $AdministratorPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}

function Invoke-Psql {
    param(
        [Parameter(Mandatory)][string]$Database,
        [Parameter(Mandatory)][string]$Sql,
        [switch]$TuplesOnly
    )

    $arguments = @('-w', '-X', '-h', 'localhost', '-U', $AdministratorUser, '-d', $Database, '-v', 'ON_ERROR_STOP=1')
    if ($TuplesOnly) { $arguments += @('-A', '-t') }
    $arguments += @('-c', $Sql)

    $result = & $psql @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed with exit code $LASTEXITCODE."
    }
    return $result
}

$escapedDatabasePassword = $DatabasePassword.Replace("'", "''")
$env:PGPASSWORD = $AdministratorPassword
try {
    $roleExists = Invoke-Psql -Database postgres -TuplesOnly `
        -Sql "SELECT 1 FROM pg_roles WHERE rolname = '$DatabaseUser';"
    if ($roleExists -contains '1') {
        Invoke-Psql -Database postgres `
            -Sql "ALTER ROLE $DatabaseUser WITH LOGIN PASSWORD '$escapedDatabasePassword';"
    }
    else {
        Invoke-Psql -Database postgres `
            -Sql "CREATE ROLE $DatabaseUser WITH LOGIN PASSWORD '$escapedDatabasePassword';"
    }

    $databaseExists = Invoke-Psql -Database postgres -TuplesOnly `
        -Sql "SELECT 1 FROM pg_database WHERE datname = '$DatabaseName';"
    if ($databaseExists -contains '1') {
        Invoke-Psql -Database postgres -Sql "ALTER DATABASE $DatabaseName OWNER TO $DatabaseUser;"
    }
    else {
        Invoke-Psql -Database postgres -Sql "CREATE DATABASE $DatabaseName OWNER $DatabaseUser;"
    }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $AdministratorPassword = $null
}

$env:PGPASSWORD = $DatabasePassword
try {
    & $psql -w -X -h localhost -U $DatabaseUser -d $DatabaseName -v ON_ERROR_STOP=1 `
        -c 'SELECT current_database(), current_user;'
    if ($LASTEXITCODE -ne 0) { throw "The project database login check failed." }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "Local PostgreSQL is ready for WasteCollect."

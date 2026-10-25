param(
    [string]$DatabaseName = 'library_db',
    [string]$OutputDirectory = (Join-Path $env:LOCALAPPDATA 'LibraryAccounting\backups'),
    [string]$ClientDefaultsFile = (Join-Path $env:USERPROFILE '.library-accounting-backup.cnf'),
    [string]$DumpExecutable
)

$ErrorActionPreference = 'Stop'

if ($DatabaseName -notmatch '^[A-Za-z0-9_]+$') {
    throw 'DatabaseName may contain only letters, digits and underscores.'
}

if (!(Test-Path -LiteralPath $ClientDefaultsFile -PathType Leaf)) {
    throw "Credentials file not found: $ClientDefaultsFile. Create it with a [client] section, user, password, host and port."
}

if (!$DumpExecutable) {
    if ($env:MARIADB_HOME) {
        $candidate = Join-Path $env:MARIADB_HOME 'bin\mariadb-dump.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $DumpExecutable = $candidate
        }
    }

    if (!$DumpExecutable) {
        $command = Get-Command mariadb-dump.exe, mysqldump.exe -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) {
            $DumpExecutable = $command.Source
        }
    }

    if (!$DumpExecutable) {
        $candidate = Get-ChildItem -Path "$env:ProgramFiles\MariaDB*\bin\mariadb-dump.exe" -File `
            -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($candidate) {
            $DumpExecutable = $candidate.FullName
        }
    }
}

if (!$DumpExecutable -or !(Test-Path -LiteralPath $DumpExecutable -PathType Leaf)) {
    throw 'Could not find mariadb-dump.exe. Add MariaDB bin to PATH, set MARIADB_HOME, or pass -DumpExecutable.'
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$baseName = "$DatabaseName-$timestamp"
$partialDump = Join-Path $OutputDirectory "$baseName.sql.partial"
$sqlDump = Join-Path $OutputDirectory "$baseName.sql"
$zipFile = Join-Path $OutputDirectory "$baseName.zip"

try {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $DumpExecutable
    $startInfo.Arguments = "--defaults-extra-file=`"$ClientDefaultsFile`" --single-transaction --quick --routines --events --triggers --databases $DatabaseName --result-file=`"$partialDump`""
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $errorOutput = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    if ($process.ExitCode -ne 0) {
        throw "mariadb-dump failed with exit code $($process.ExitCode): $errorOutput"
    }
    if (!(Test-Path -LiteralPath $partialDump -PathType Leaf) -or (Get-Item -LiteralPath $partialDump).Length -eq 0) {
        throw 'mariadb-dump completed without producing a non-empty dump.'
    }

    Move-Item -LiteralPath $partialDump -Destination $sqlDump
    Compress-Archive -LiteralPath $sqlDump -DestinationPath $zipFile -CompressionLevel Optimal
    Remove-Item -LiteralPath $sqlDump -Force

    $cutoff = (Get-Date).AddDays(-30)
    Get-ChildItem -LiteralPath $OutputDirectory -Filter "$DatabaseName-*.zip" -File |
        Where-Object { $_.LastWriteTime -lt $cutoff } |
        Remove-Item -Force

    Write-Output "Backup created: $zipFile"
}
catch {
    Remove-Item -LiteralPath $partialDump, $sqlDump, $zipFile -Force -ErrorAction SilentlyContinue
    throw
}
finally {
    if ($process) {
        $process.Dispose()
    }
}

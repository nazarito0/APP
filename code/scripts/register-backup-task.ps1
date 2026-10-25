param(
    [datetime]$At = (Get-Date -Hour 23 -Minute 0 -Second 0),
    [string]$TaskName = 'LibraryAccounting-MariaDB-Backup'
)

$ErrorActionPreference = 'Stop'
$backupScript = Join-Path $PSScriptRoot 'backup-mariadb.ps1'
if (!(Test-Path -LiteralPath $backupScript -PathType Leaf)) {
    throw "Backup script not found: $backupScript"
}

$powerShell = Join-Path $env:WINDIR 'System32\WindowsPowerShell\v1.0\powershell.exe'
$action = New-ScheduledTaskAction -Execute $powerShell `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$backupScript`""
$trigger = New-ScheduledTaskTrigger -Daily -At $At
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" `
    -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
    -Principal $principal -Settings $settings `
    -Description 'Daily compressed MariaDB backup for the library accounting application.' `
    -Force | Out-Null

Write-Output "Scheduled '$TaskName' daily at $($At.ToString('HH:mm')) for $env:USERNAME."
Write-Output 'The task runs while this Windows user is logged in.'

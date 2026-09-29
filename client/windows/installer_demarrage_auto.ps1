$ErrorActionPreference = "Stop"

$taskName = "TelecomHand Client"
$installDir = "C:\Users\Public\TelecomHand"
$sourceDir = Join-Path $PSScriptRoot "dist"

if (-not (Test-Path (Join-Path $sourceDir "TelecomHand-Windows.exe"))) {
    $sourceDir = $PSScriptRoot
}

$sourceExe = Join-Path $sourceDir "TelecomHand-Windows.exe"
if (-not (Test-Path $sourceExe)) {
    throw "TelecomHand-Windows.exe est introuvable a cote de l'installateur ou dans le dossier dist."
}

New-Item -ItemType Directory -Path $installDir -Force | Out-Null
Copy-Item -LiteralPath $sourceExe -Destination (Join-Path $installDir "TelecomHand-Windows.exe") -Force

$sourceLauncher = Join-Path $PSScriptRoot "telecomhand_autostart.cmd"
if (-not (Test-Path $sourceLauncher)) {
    $sourceLauncher = Join-Path $sourceDir "telecomhand_autostart.cmd"
}
if (-not (Test-Path $sourceLauncher)) {
    throw "telecomhand_autostart.cmd est introuvable."
}
Copy-Item -LiteralPath $sourceLauncher -Destination (Join-Path $installDir "telecomhand_autostart.cmd") -Force

$sourceConfig = Join-Path $sourceDir "config.json"
if (Test-Path $sourceConfig) {
    $installedConfig = Join-Path $installDir "config.json"
    if (-not (Test-Path $installedConfig)) {
        Copy-Item -LiteralPath $sourceConfig -Destination $installedConfig
    }
}

$userId = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$launcher = Join-Path $installDir "telecomhand_autostart.cmd"
$action = New-ScheduledTaskAction `
    -Execute "$env:SystemRoot\System32\cmd.exe" `
    -Argument "/d /c call $launcher"
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $userId
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew

Register-ScheduledTask `
    -TaskName $taskName `
    -Action $action `
    -Trigger $trigger `
    -Principal $principal `
    -Settings $settings `
    -Description "Client TelecomHand interactif pour le controle a distance" `
    -Force | Out-Null

Start-ScheduledTask -TaskName $taskName
Write-Host "TelecomHand est installe et se lancera automatiquement a chaque ouverture de session."
Write-Host "Dossier : $installDir"

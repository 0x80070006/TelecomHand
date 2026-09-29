$ErrorActionPreference = "Stop"

$serviceName = "TelecomHandService"
$agentRoot = "C:\Users\Public\TelecomHand"
$serviceRoot = Join-Path $env:ProgramData "TelecomHand"
$sourceDir = Join-Path $PSScriptRoot "dist"
if (-not (Test-Path (Join-Path $sourceDir "TelecomHand-Windows.exe"))) {
    $sourceDir = $PSScriptRoot
}

$agentSource = Join-Path $sourceDir "TelecomHand-Windows.exe"
$serviceSource = Join-Path $sourceDir "TelecomHand-Service.exe"
if (-not (Test-Path $agentSource) -or -not (Test-Path $serviceSource)) {
    throw "Les executables TelecomHand sont introuvables."
}

Stop-ScheduledTask -TaskName "TelecomHand Client" -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName "TelecomHand Client" -Confirm:$false -ErrorAction SilentlyContinue
Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
Get-Process -Name "TelecomHand-Windows" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1

New-Item -ItemType Directory -Path $agentRoot,$serviceRoot -Force | Out-Null
Copy-Item -LiteralPath $agentSource -Destination (Join-Path $agentRoot "TelecomHand-Windows.exe") -Force
Copy-Item -LiteralPath $serviceSource -Destination (Join-Path $serviceRoot "TelecomHand-Service.exe") -Force

$configSource = Join-Path $sourceDir "config.json"
$configTarget = Join-Path $agentRoot "config.json"
if ((Test-Path $configSource) -and -not (Test-Path $configTarget)) {
    Copy-Item -LiteralPath $configSource -Destination $configTarget
}

sc.exe delete $serviceName 2>$null | Out-Null
Start-Sleep -Seconds 1
$serviceExe = Join-Path $serviceRoot "TelecomHand-Service.exe"
sc.exe create $serviceName binPath= "`"$serviceExe`"" start= auto obj= LocalSystem DisplayName= "TelecomHand Remote Control" | Out-Null
sc.exe description $serviceName "Supervise le client TelecomHand dans la session Windows active." | Out-Null
sc.exe failure $serviceName reset= 0 actions= restart/5000/restart/5000/restart/5000 | Out-Null
sc.exe failureflag $serviceName 1 | Out-Null
Start-Service -Name $serviceName

Set-Service -Name "Tailscale" -StartupType Automatic
sc.exe failure Tailscale reset= 0 actions= restart/5000/restart/5000/restart/5000 | Out-Null
sc.exe failureflag Tailscale 1 | Out-Null
Start-Service -Name "Tailscale"

$tailscaleUi = "C:\Program Files\Tailscale\tailscale-ipn.exe"
if (Test-Path $tailscaleUi) {
    Set-ItemProperty -Path "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run" -Name "Tailscale" -Value "`"$tailscaleUi`""
    if (-not (Get-Process -Name "tailscale-ipn" -ErrorAction SilentlyContinue)) {
        Start-Process -FilePath $tailscaleUi
    }
}

Write-Host "TelecomHand et Tailscale sont configures pour rester actifs."

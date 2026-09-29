$ErrorActionPreference = "Stop"

$serviceName = "TelecomHandService"
Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
sc.exe delete $serviceName 2>$null | Out-Null
Get-Process -Name "TelecomHand-Windows" -ErrorAction SilentlyContinue | Stop-Process -Force

$serviceRoot = Join-Path $env:ProgramData "TelecomHand"
$agentRoot = "C:\Users\Public\TelecomHand"
if (Test-Path -LiteralPath $serviceRoot) {
    Remove-Item -LiteralPath $serviceRoot -Recurse -Force
}
if (Test-Path -LiteralPath $agentRoot) {
    Remove-Item -LiteralPath $agentRoot -Recurse -Force
}

Write-Host "Le service et le client TelecomHand ont ete supprimes. Tailscale est conserve."

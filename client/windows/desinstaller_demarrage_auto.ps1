$ErrorActionPreference = "Stop"

$taskName = "TelecomHand Client"
$installDir = "C:\Users\Public\TelecomHand"

Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
Get-Process -Name "TelecomHand-Windows" -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -like "$installDir*" } |
    Stop-Process -Force

if (Test-Path $installDir) {
    Remove-Item -LiteralPath $installDir -Recurse -Force
}

Write-Host "Le demarrage automatique et le client TelecomHand ont ete supprimes."

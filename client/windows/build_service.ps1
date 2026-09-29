$ErrorActionPreference = "Stop"

$SourceRoot = Split-Path -Parent $PSScriptRoot
$ProjectRoot = Split-Path -Parent $SourceRoot
$BuildRoot = Join-Path $ProjectRoot ".build-windows-service"
$Venv = Join-Path $BuildRoot "venv"

New-Item -ItemType Directory -Force $BuildRoot | Out-Null
python -m venv $Venv
& "$Venv\Scripts\python.exe" -m pip install --upgrade pip
& "$Venv\Scripts\python.exe" -m pip install pyinstaller pywin32
& "$Venv\Scripts\pyinstaller.exe" --noconfirm --clean --onefile `
  --name TelecomHand-Service `
  --hidden-import win32timezone `
  --distpath "$PSScriptRoot\dist" `
  --workpath "$BuildRoot\work" `
  --specpath "$BuildRoot" `
  "$PSScriptRoot\telecomhand_windows_service.py"

Write-Host "Service cree dans $PSScriptRoot\dist" -ForegroundColor Green

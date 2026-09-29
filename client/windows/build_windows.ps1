$ErrorActionPreference = "Stop"
$SourceRoot = Split-Path -Parent $PSScriptRoot
$ProjectRoot = Split-Path -Parent $SourceRoot
$BuildRoot = Join-Path $ProjectRoot ".build-windows-client"
$Venv = Join-Path $BuildRoot "venv"

New-Item -ItemType Directory -Force $BuildRoot | Out-Null
python -m venv $Venv
& "$Venv\Scripts\python.exe" -m pip install --upgrade pip
& "$Venv\Scripts\python.exe" -m pip install pyinstaller pyautogui pillow pystray winrt-runtime winrt-Windows.Foundation winrt-Windows.Foundation.Collections winrt-Windows.System winrt-Windows.Storage winrt-Windows.Storage.Streams winrt-Windows.Storage.Search winrt-Windows.Storage.Provider winrt-Windows.Storage.FileProperties winrt-Windows.Media.Control
& "$Venv\Scripts\pyinstaller.exe" --noconfirm --clean --onefile --noconsole `
  --name TelecomHand-Windows `
  --distpath "$PSScriptRoot\dist" `
  --workpath "$BuildRoot\work" `
  --specpath "$BuildRoot" `
  "$SourceRoot\telecomhand_client.py"

Copy-Item "$PSScriptRoot\LISEZ-MOI.txt" "$PSScriptRoot\dist\LISEZ-MOI.txt" -Force
Write-Host "Client créé dans $PSScriptRoot\dist" -ForegroundColor Green

@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where java >nul 2>nul || (
  echo [FEHLER] Java wurde nicht gefunden. Fuer Minecraft/Forge 1.8.9 bitte Java 8 verwenden.
  pause
  exit /b 1
)

if not exist ".buildtools\gradle-2.14.1\bin\gradle.bat" (
  echo [INFO] Lade Gradle 2.14.1 ...
  if not exist ".buildtools" mkdir ".buildtools"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-2.14.1-bin.zip' -OutFile '.buildtools\gradle.zip'; Expand-Archive -Force '.buildtools\gradle.zip' '.buildtools'; Remove-Item '.buildtools\gradle.zip'"
  if errorlevel 1 goto :error
)

call ".buildtools\gradle-2.14.1\bin\gradle.bat" --no-daemon clean build
if errorlevel 1 goto :error

echo.
echo [FERTIG] JAR: build\libs\ByteBitShop-1.0.2.jar
pause
exit /b 0

:error
echo.
echo [FEHLER] Build fehlgeschlagen.
pause
exit /b 1

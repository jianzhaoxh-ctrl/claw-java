@echo off
REM ============================================================
REM  ClawDesktop Windows 打包脚本 (jpackage + WiX)
REM  生成 .msi 安装包
REM
REM  前置条件：
REM    1. JDK 21+（含 jpackage 工具）
REM    2. WiX Toolset 3.x（https://wixtoolset.org）
REM       安装后确保 candle.exe / light.exe 在 PATH 或设置 WIX_PATH
REM    3. Maven（构建 fat jar）
REM
REM  用法：
REM    scripts\package-windows.bat
REM ============================================================
setlocal enabledelayedexpansion

set APP_NAME=ClawDesktop
set APP_VERSION=0.1.0
set APP_VENDOR=OpenClaw
set MAIN_CLASS=com.openclaw.desktop.DesktopApplication
set MODULE_NAME=com.openclaw.desktop
set TARGET_DIR=target
set PACKAGING_DIR=packaging
set ICON_DIR=%PACKAGING_DIR%\icons
set ICON_PATH=%ICON_DIR%\icon-256.png
set OUTPUT_DIR=%PACKAGING_DIR%\dist\windows

echo === ClawDesktop Windows Packaging ===

REM 1. 检查 jpackage
where jpackage >nul 2>&1
if errorlevel 1 (
    echo [ERROR] jpackage not found. Ensure JDK 21+ is installed.
    exit /b 1
)

REM 2. 检查 WiX
set WIX_PATH=%WIX_PATH%
if "%WIX_PATH%"=="" (
    where candle >nul 2>&1
    if errorlevel 1 (
        echo [WARN] WiX not found in PATH. Set WIX_PATH or add WiX bin to PATH.
        echo [INFO] Will skip MSI, generate app image only.
        set SKIP_MSI=1
    )
)

REM 3. 构建 fat jar
echo [1/5] Building fat jar with Maven...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Maven build failed.
    exit /b 1
)

REM 4. 生成图标
echo [2/5] Generating icons...
if not exist "%ICON_DIR%\icon-256.png" (
    mkdir "%ICON_DIR%" 2>nul
    java -cp "%TARGET_DIR%\claw-java-0.1.0-SNAPSHOT.jar" com.openclaw.desktop.util.IconGenerator "%ICON_DIR%"
    if errorlevel 1 (
        echo [WARN] Icon generation failed, using default.
        set ICON_PATH=
    )
)

REM 5. 创建输出目录
mkdir "%OUTPUT_DIR%" 2>nul

REM 6. jpackage 生成应用镜像
echo [3/5] Creating application image...
set JPACKAGE_ARGS=--type app-image
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --name %APP_NAME%
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --app-version %APP_VERSION%
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --vendor "%APP_VENDOR%"
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --input "%TARGET_DIR%"
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --main-jar claw-java-0.1.0-SNAPSHOT.jar
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --main-class %MAIN_CLASS%
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --dest "%OUTPUT_DIR%"
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --java-options "--enable-native-access=ALL-UNNAMED"
set JPACKAGE_ARGS=%JPACKAGE_ARGS% --java-options "-Xms256m -Xmx1024m"

if exist "%ICON_PATH%" (
    set JPACKAGE_ARGS=%JPACKAGE_ARGS% --icon "%ICON_PATH%"
)

jpackage %JPACKAGE_ARGS%
if errorlevel 1 (
    echo [ERROR] jpackage app-image failed.
    exit /b 1
)

echo [INFO] App image created: %OUTPUT_DIR%\%APP_NAME%

REM 7. 生成 MSI（需要 WiX）
if "%SKIP_MSI%"=="1" (
    echo [4/5] Skipping MSI (WiX not found)
    echo [5/5] Done. App image: %OUTPUT_DIR%\%APP_NAME%
    exit /b 0
)

echo [4/5] Building MSI installer...
jpackage --type msi ^
    --app-image "%OUTPUT_DIR%\%APP_NAME%" ^
    --name %APP_NAME% ^
    --app-version %APP_VERSION% ^
    --vendor "%APP_VENDOR%" ^
    --dest "%OUTPUT_DIR%" ^
    --win-menu-group "%APP_VENDOR%" ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser

if errorlevel 1 (
    echo [ERROR] MSI build failed.
    exit /b 1
)

echo [5/5] Done.
echo === Packaging Complete ===
echo MSI installer: %OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.msi
echo App image:     %OUTPUT_DIR%\%APP_NAME%
endlocal

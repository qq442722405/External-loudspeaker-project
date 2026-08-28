@echo off
chcp 65001 >nul
setlocal
echo ========================================
echo             喊话.apk 手动打包
echo ========================================
echo.

where gradle >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Gradle。
    echo 请安装 Gradle 8.x/9.x 并加入 PATH。
    pause
    exit /b 1
)

call gradle :app:assembleDebug --no-daemon
if errorlevel 1 (
    echo.
    echo [失败] Gradle 打包失败。
    pause
    exit /b 1
)

copy /y "app\build\outputs\apk\debug\app-debug.apk" "喊话.apk" >nul

echo.
echo ========================================
echo [成功] 已生成：喊话.apk
echo ========================================
pause

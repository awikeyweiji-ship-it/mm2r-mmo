@echo off
chcp 65001 >nul
cd /d %~dp0
echo [1/2] 本地核心流程验证...
call bash tests/run_core_tests.sh
if errorlevel 1 (
  echo 如果没有 Bash，可用 Android Studio 打开工程并直接 Gradle Build。
)
echo [2/2] 构建安卓测试安装包...
where gradle >nul 2>nul
if errorlevel 1 (
  echo 未找到 Gradle。请安装 Android Studio + Gradle 8.9，然后打开此工程执行 assembleDebug。
  pause
  exit /b 2
)
call gradle --no-daemon :app:assembleDebug
if errorlevel 1 (
  echo 构建失败，请检查 Android SDK Platform 35 与网络依赖。
  pause
  exit /b 1
)
echo 构建成功：app\build\outputs\apk\debug\app-debug.apk
pause

#!/bin/sh
set -e
gradle :app:assembleDebug --no-daemon
cp app/build/outputs/apk/debug/app-debug.apk 喊话.apk
echo "已生成：喊话.apk"

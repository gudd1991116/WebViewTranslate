#!/bin/bash

# 查看当前应用的日志脚本

echo "=========================================="
echo "  WebView翻译 应用日志查看工具"
echo "=========================================="
echo ""
echo "应用包名: com.example.webviewtranslate"
echo "日志标签: Translate, TranslateService"
echo ""
echo "按 Ctrl+C 停止查看"
echo "=========================================="
echo ""

# 方法1：使用标签过滤（推荐，最清晰）
echo "使用标签过滤日志..."
adb logcat -s Translate:D TranslateService:D *:S


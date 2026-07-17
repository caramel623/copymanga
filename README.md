# 拷貝WEB

拷貝WEB 是以 Android WebView 包裝漫畫網站並提供原生閱讀與下載功能的 Android 應用程式。本分支目前版本為 `1.6.1`，最低支援 Android 6.0（API 23）。

## 主要功能

- 網站登入、書架及漫畫章節瀏覽
- 上下連續或左右翻頁閱讀
- 閱讀方向、預載、畫質、快取及重試設定
- 章節上一話／下一話導覽
- 輕小說簡體轉繁體
- 自訂網站入口網址
- 登入帳密可選擇加密儲存於本機 Android Keystore
- 章節圖片背景收集、進度顯示、資料夾或 ZIP 下載
- 自訂下載位置與章節檔名 `001.JPG`、`002.JPG` 等

## 開發環境

- Windows 10/11
- JDK 17
- Android SDK Platform 36、Build Tools 36.0.0、Platform Tools
- Gradle Wrapper 8.11.1（已包含於儲存庫）
- Android Gradle Plugin 8.6.1
- Kotlin 2.1.0

不需要 Python、Node.js、`.venv`、`requirements.txt`、`package.json` 或 `node_modules`。

## 快速建置

```powershell
git clone -b agent/beta1 https://github.com/caramel623/copymanga.git
cd copymanga
Copy-Item local.properties.example local.properties
# 編輯 local.properties，填入新電腦的 Android SDK 路徑
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

完整移轉與設定方式請閱讀 [TRANSFER_GUIDE.md](TRANSFER_GUIDE.md)，目前專案狀態請閱讀 [PROJECT_STATUS.md](PROJECT_STATUS.md)。

## 安全注意事項

不要提交 `local.properties`、`app/signing.properties`、任何 keystore、密碼、Cookie、Token、帳號資料、手機下載內容或使用者資料。正式發布前必須另外規劃並安全保管正式簽章金鑰。

## 授權

本專案沿用儲存庫中的 [LICENSE](LICENSE)。

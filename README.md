# 拷貝WEB

拷貝WEB 是以 Android WebView 包裝漫畫網站並提供原生閱讀與下載功能的 Android 應用程式。本分支目前正式版本為 `2.2.2-web`，最低支援 Android 6.0（API 23）。

> 本儲存庫為 `fumiama/webreader` 的 fork，**供個人使用**：預設搭配 `mangacopy.com`（漫畫）與 `api.copy3000.com`（輕小說 API），簽章使用本地 `debug.keystore`（見下方簽章說明），未發布至 Play Store。

## 主要功能

- 網站登入、書架及漫畫章節瀏覽
- 上下連續或左右翻頁閱讀
- 閱讀方向、預載、畫質、快取及重試設定
- 章節上一話／下一話導覽
- 背景靜默載入真實章節頁，讓網站端記錄「已觀看」進度
- 輕小說書架、閱讀歷史、書籤、簡體轉繁體
- 輕小說淺色／深色／黑色主題，狀態列與底部列隨主題變色
- 漫畫／小說讀者可選擇「縮小不擋狀態列／底部列」（自動偵測每支手機的系統列寬度）
- 自訂網站入口網址
- 登入帳密可選擇加密儲存於本機 Android Keystore
- 章節圖片背景收集、進度顯示、資料夾或 ZIP 下載
- 自訂下載位置與章節檔名 `001.JPG`、`002.JPG` 等

## 簽章（個人使用）

`app/signing.properties` 指向 `Sensitive_Signing/debug.keystore`，debug 與 release 共用同一把金鑰。因此測試版與正式版簽章一致，新版可直接覆蓋安裝（不需先卸載）；但請妥善保管該 keystore，遺失後之後的版本需要卸載重裝。正式開發者簽章尚未申請。

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

不要提交 `local.properties`、`app/signing.properties`、任何 keystore、密碼、Cookie、Token、帳號資料、手機下載內容或使用者資料。

## 授權

本專案沿用儲存庫中的 [LICENSE](LICENSE)。

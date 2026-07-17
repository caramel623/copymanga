# Windows 開發環境移轉指南

## 1. 新電腦需要安裝的軟體

必要項目：

- Git for Windows
- 64 位元 JDK 17（目前驗證環境為 Eclipse Temurin 17.0.19）
- Android Studio，或 Android SDK Command-line Tools
- Android SDK Platform 36
- Android SDK Build Tools 36.0.0
- Android SDK Platform Tools（包含 ADB；目前驗證版本為 37.0.0）

實機測試可能另外需要手機廠牌的 Windows USB 驅動程式。本專案使用 Samsung SM-A305GN 測試時，Windows 必須能在 `adb devices` 中看到並授權裝置。

本專案不是 Python 或 Node.js 專案，不需要建立 Python 虛擬環境，也不需要安裝 `requirements.txt`、`package.json`、lock 檔或 `node_modules`。

## 2. 從 GitHub 還原

目前最新開發內容在 `agent/beta1` 分支：

```powershell
git clone -b agent/beta1 https://github.com/caramel623/copymanga.git
cd copymanga
git status
```

若未指定分支，Clone 後請執行：

```powershell
git fetch --all --tags
git switch agent/beta1
```

## 3. Java 與 Android SDK

確認 Java：

```powershell
java -version
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17'
```

確認 SDK 已安裝 Platform 36、Build Tools 36.0.0 及 Platform Tools。Android Studio 可透過 SDK Manager 安裝；命令列環境也可使用 `sdkmanager`。

## 4. 設定檔恢復

建立本機 SDK 設定：

```powershell
Copy-Item local.properties.example local.properties
notepad local.properties
```

將 `sdk.dir` 改成新電腦的 Android SDK 絕對路徑。例如：

```properties
sdk.dir=C\:\\Users\\YOUR_WINDOWS_USER\\AppData\\Local\\Android\\Sdk
```

`local.properties` 只記錄本機 SDK 路徑，不應提交。

若需要沿用既有 APK 簽章，請先從安全離線備份還原 keystore，再執行：

```powershell
Copy-Item app\signing.properties.example app\signing.properties
notepad app\signing.properties
```

填入 keystore 路徑、別名與密碼。真實的 `app/signing.properties` 和 keystore 都被 Git 忽略，禁止上傳 GitHub。

注意：目前 Gradle 腳本只把 `app/signing.properties` 套用到 `debug` signing config；正式上架前應另行審查並建立可靠的 `release` 簽章流程，不要把密碼寫入 `build.gradle`。

## 5. 第一次啟動與建置

在 PowerShell 中執行：

```powershell
.\gradlew.bat --version
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

APK 會建立在：

```text
app\build\outputs\apk\debug\app-debug.apk
```

安裝至已授權手機：

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n top.fumiama.copymangaweb/.activity.MainActivity
```

若新電腦未還原與目前手機 APK 相同的簽章，`adb install -r` 會回報簽章不一致。此時不要直接移除手機程式，除非已備份登入、設定與下載資料；正確方式是還原原簽章金鑰後重新建置。

## 6. 測試方式

本機單元測試：

```powershell
.\gradlew.bat testDebugUnitTest
```

實機儀器測試：

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest
```

目前儀器測試仍是範例測試，而且預期套件名稱與實際 `applicationId` 不一致，可能失敗；請保留並記錄錯誤，不要忽略。主要功能仍需實機手動驗證：登入、書架、章節選擇、閱讀器返回、上下連續閱讀、下一章，以及資料夾／ZIP 下載。

## 7. 執行期設定與使用者資料

下列內容不是專案原始碼：

- 網站入口、閱讀與下載偏好：儲存在 Android 應用程式私有資料中。
- 已儲存帳密：由 Android Keystore 加密，通常無法直接移轉到另一台手機或另一個 Windows 電腦。
- 下載的漫畫、ZIP、指定資料夾內容：屬於使用者資料，若需要保留，請從手機另行備份。
- GitHub CLI、Git 認證、Android Studio 個人設定：應在新電腦重新登入或設定，不可放入專案。

## 8. 常見錯誤排除

### 找不到 Java 或 `JAVA_HOME`

確認使用 JDK 17，並重新開啟 PowerShell：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

### SDK location not found

確認 `local.properties` 存在且 `sdk.dir` 指向新電腦的 SDK。

### 缺少 Android 36

透過 Android Studio SDK Manager 安裝 Android SDK Platform 36 與 Build Tools 36.0.0。

### ADB 顯示 unauthorized

解鎖手機、重新插拔 USB，接受手機上的 RSA 授權；必要時執行：

```powershell
adb kill-server
adb start-server
adb devices
```

### APK 無法覆蓋安裝

若顯示簽章不一致，還原原本的 keystore。不要為了安裝而直接解除安裝，除非已確認手機資料可丟棄。

### 網站頁面或下載流程失效

網站 DOM 與網址可能變動。優先使用 ADB logcat 檢查 `MyWC`、`MyJS`、`MyJSH`、`Mydl` 標籤，再檢查 `app/src/main/assets/i.js`、`h.js` 與 `SiteConfig.kt`。

## 9. 禁止放上 GitHub、必須安全備份

- `C:\Users\<使用者>\.android\debug.keystore`（若要覆蓋既有 Debug APK）
- 任何正式發行 `.jks`／`.keystore` 與其密碼
- `app/signing.properties`
- 密碼、Cookie、Token、API 金鑰及 GitHub 認證
- 手機應用程式資料、下載漫畫、ZIP 與其他私人資料
- 如需保留，目前原電腦的 `local.properties` 可另存，但在新電腦重新建立通常更安全

`.gradle/`、`.kotlin/`、`app/build/`、APK 建置輸出與 IDE 快取都可重新產生，不需要搬移。

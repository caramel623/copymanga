# PROJECT STATUS — 拷貝WEB

更新日期：2026-07-17

目前分支：`agent/beta1`

目前應用程式版本：`1.6.1`（versionCode `20260717`）
遠端儲存庫：<https://github.com/caramel623/copymanga>

## 專案用途

這是 Android WebView 漫畫閱讀器。可在網站登入及使用書架，將章節網址交給隱藏 WebView 收集圖片，再以原生閱讀器顯示或下載圖片。

## 已完成功能

- 預設國際版網站入口 `https://www.mangacopy.com/`
- 使用者可修改網站入口網址
- 登入帳密可選擇儲存，資料只存在本機並透過 Android Keystore 加密
- 書架與漫畫章節瀏覽
- 上下連續閱讀及左右翻頁模式
- 閱讀畫質、預載、速度、重試、快取及閱讀方向設定
- 長章節先取得前 50 張後啟動閱讀器，後續背景追加
- 上一章／下一章按鈕位於第一張上方與最後一張下方
- 閱讀器返回章節選擇頁；章節頁再次返回可回到書架（V1.6.1 Hotfix）
- 輕小說簡體轉繁體，預設開啟
- 下載位置、資料夾模式或每章 ZIP 設定
- 圖片依 `001.JPG`、`002.JPG` 格式命名
- 下載背景網址收集與圖片下載分階段顯示進度
- 修正網站頁碼停在最後一張之前，造成網址收集不完成
- 修正圖片網址未寫入章節陣列，造成下載無聲結束
- 下載錯誤與網址缺失會明確回報並寫入 `Mydl` 日誌

## 尚未完成／需要改進

- 建立真正的 release keystore 與 CI／正式簽章流程；目前 GitHub APK 是 Debug 簽章建置。
- 將目前範例測試改成涵蓋返回歷史、網址收集與下載狀態的有效測試。
- 修正 instrumented test 中過時的預期套件名稱。
- 持續降低程式對網站 DOM class 名稱及網址格式的耦合。
- 檢查並整理舊版亂碼字串與部分過時 Android API 警告。
- 若要在預設分支直接 Clone 最新版本，需決定是否將 `agent/beta1` 合併到 GitHub 預設分支。

## 已知問題與風險

- 網站結構或防護機制改動時，`i.js`／`h.js` 掃描可能失效。
- 書架「自動按作品更新時間排序」依賴網站 DOM 操作，網站變更後可能失效；設定頁中的舊預設排序項目已曾被判定無效。
- `connectedDebugAndroidTest` 目前的範例測試預期 `top.fumiama.copymanga`，實際 applicationId 是 `top.fumiama.copymangaweb`，因此預期會失敗。
- Android Gradle Plugin 8.6.1 官方驗證到 compileSdk 35，但專案使用 compileSdk 36；建置目前成功但會顯示警告。
- Glide compiler 使用 `annotationProcessor`，Gradle 會警告 Kotlin 專案應改用 `kapt`。
- Debug keystore 若遺失，新電腦建置的 APK 無法直接覆蓋現有安裝版本。

## 重要技術決策

- 可見 WebView 使用行動版網站；隱藏 WebView 使用桌面版頁面解析章節與圖片網址。
- 網站入口透過 `SiteConfig` 管理，返回定位保存相對 path，避免自訂網域後指向舊 host。
- 閱讀器返回時搜尋 WebView 歷史中的同作品章節選擇頁，不再以 `loadUrl()` 新增錯誤歷史。
- 下載先收集完整圖片網址，再按順序請求圖片並依設定寫入資料夾或 ZIP。
- 網址收集完成判斷依有效 `data-src` 數量及頁面底部保護，不依賴網站 `.comicIndex` 必須等於總數。
- 登入資料透過 Android Keystore 加密；不建立可跨裝置搬移的明文設定。

## 主要檔案用途

- `app/src/main/assets/i.js`：行動版頁面修改、章節點擊、書架排序及登入欄位處理。
- `app/src/main/assets/h.js`：桌面版章節／圖片網址掃描及閱讀器分批啟動。
- `MainActivity.kt`：WebView 管理、閱讀器啟動、網址與返回歷史協調。
- `ViewMangaActivity.kt`：原生漫畫閱讀器、閱讀模式、章節導覽與返回。
- `DlActivity.kt`：下載章節選擇、下載執行與進度 UI。
- `MangaDlTools.kt`：圖片網址陣列、HTTP 下載、資料夾／ZIP 寫入及錯誤回報。
- `SettingsActivity.kt`：閱讀、下載、網址、登入與其他設定。
- `SiteConfig.kt`：預設／自訂網站入口驗證與允許網域。
- `LocalCredentialStore.kt`：Android Keystore 本機加密登入資料。
- `WebViewClient.kt`：頁面完成後延遲注入 JavaScript 與請求處理。

## 最近正在處理的工作

最近完成 V1.6.1 Hotfix：閱讀器返回章節選擇頁後，再按返回不會回到舊章節網址並重新啟動閱讀器。實機已確認返回行為正確。

## 建議下一步

1. 提交本次移轉文件、範例設定及 `.gitignore` 更新。
2. 在新電腦 Clone `agent/beta1`，安裝 JDK 17 與 Android SDK 36。
3. 安全還原 Debug／Release 簽章並驗證可覆蓋安裝。
4. 修正 instrumented test applicationId，新增返回與下載流程測試。
5. 規劃正式 release signing config，避免繼續用 Debug APK 當正式發行檔。

## 最近測試結果

- V1.6.1 `assembleDebug`：成功。
- 2026-07-17 `testDebugUnitTest`：成功；現有 1 個範例單元測試通過。
- 2026-07-17 `assembleDebug`：成功。
- 2026-07-17 `connectedDebugAndroidTest`：未執行；`adb devices` 沒有連線裝置。不可視為通過。
- 2026-07-17 `assembleRelease`：執行超過 4 分鐘後因工具時限終止，沒有產生 Release APK，也沒有取得明確 Gradle 失敗；狀態為未驗證，不可視為通過。
- 建置警告：AGP 8.6.1／compileSdk 36 組合超出官方已測範圍、`android.defaults.buildfeatures.buildconfig` 將棄用、Glide compiler 應由 `annotationProcessor` 遷移至 `kapt`。
- 實體 Samsung SM-A305GN：閱讀器返回章節選擇頁後，再返回書架成功。
- 閱讀器、下載入口、背景網址收集、圖片下載進度：使用者確認正常。

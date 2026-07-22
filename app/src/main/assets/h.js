javascript:
if (typeof (loaded) == "undefined") {
    var loaded = true;
    function scanChapters(chapter) {
        var chapterList = chapter.getElementsByClassName("tab-pane fade show active")[0].getElementsByTagName("ul")[0].getElementsByTagName("a");
        var chapterArr = Array();
        for (var i = 0; i < chapterList.length; i++) {
            chapterArr.push(JSON.constructor());
            chapterArr[i]["name"] = chapterList[i].title;
            chapterArr[i]["url"] = chapterList[i].href;
        }
        return chapterArr;
    }
    function smoothLoadChapter(speed, interval) {
        let prevHeight = document.body.scrollHeight;
        let lastTime = 0;
        let ticking = false;
        let sentImages = 0;
        let finished = false;
        let bottomReachedAt = 0;
        const collectedChapterUrls = Array();
        const collectedChapterUrlSet = Object.create(null);
        const backgroundDownload = GM.isDownloadMode();
        const effectiveSpeed = backgroundDownload ? Math.min(speed, 80) : speed;
        const effectiveInterval = backgroundDownload ? Math.max(interval, 120) : interval;
        function chapterHeader() {
            var nextChapter = document.getElementsByClassName("comicContent-next")[0].getElementsByTagName("a")[0].href;
            var prevChapter = document.getElementsByClassName("comicContent-prev")[1].getElementsByTagName("a")[0].href;
            if(nextChapter == location.href) nextChapter = "null";
            if(prevChapter == location.href) prevChapter = "null";
            return document.title.split(" - ")[1] + " " + location.href.substring(location.href.lastIndexOf("/")+1) + "\n" + nextChapter + "\n" + prevChapter;
        }
        if (!backgroundDownload) GM.startChapter(chapterHeader());
        function declaredChapterCount() {
            var element = document.getElementsByClassName("comicCount")[0];
            return parseInt(element && element.innerText) || 0;
        }
        function emitReaderChunk(finalChunk) {
            if (backgroundDownload) return;
            var declaredCount = declaredChapterCount();
            var end = declaredCount > 0
                ? Math.min(collectedChapterUrls.length, declaredCount)
                : collectedChapterUrls.length;
            var available = collectedChapterUrls.slice(sentImages, end);
            var amount = available.length;
            if (amount <= 0) return;
            var chunk = available.slice(0, amount).join("\n");
            GM.loadChapterChunk(chunk, false, finalChunk);
            sentImages += amount;
        }
        function requestTick() {
            if (!finished && !ticking) {
                ticking = true;
                if (document.hidden) GM.scheduleCollectorTick();
                else requestAnimationFrame(step);
            }
        }
        window.cmCollectorTick = function () {
            if (!finished && ticking) step(performance.now());
        };
        function rememberChapterUrls(urls) {
            for (var i = 0; i < urls.length; i++) {
                var url = urls[i];
                if (url && !collectedChapterUrlSet[url]) {
                    collectedChapterUrlSet[url] = true;
                    collectedChapterUrls.push(url);
                }
            }
            return collectedChapterUrls;
        }
        function decryptChapterUrls() {
            try {
                if (typeof contentKey !== "string" || typeof cct !== "string" ||
                    typeof webpackJsonp === "undefined") return Array();
                if (typeof window.cmWebpackRequire !== "function") {
                    webpackJsonp.push([[987654], {
                        987654: function (module, exports, require) {
                            window.cmWebpackRequire = require;
                        }
                    }, [[987654]]]);
                }
                var crypto = null;
                var moduleIds = [0, 5, 6];
                for (var i = 0; i < moduleIds.length; i++) {
                    var candidate = window.cmWebpackRequire(moduleIds[i]);
                    if (candidate && candidate.AES && candidate.enc) {
                        crypto = candidate;
                        break;
                    }
                }
                if (!crypto) return Array();
                var iv = crypto.enc.Utf8.parse(contentKey.substring(0, 16));
                var key = crypto.enc.Utf8.parse(cct);
                var cipher = crypto.enc.Base64.stringify(
                    crypto.enc.Hex.parse(contentKey.substring(16))
                );
                var plain = crypto.AES.decrypt(cipher, key, {
                    iv: iv,
                    mode: crypto.mode.CBC,
                    padding: crypto.pad.Pkcs7
                }).toString(crypto.enc.Utf8);
                var data = JSON.parse(plain);
                var urls = Array();
                for (var j = 0; j < data.length; j++) {
                    if (data[j] && data[j].url) urls.push(data[j].url);
                }
                return urls;
            } catch (e) {
                return Array();
            }
        }
        function collectChapterUrls() {
            var content = document.getElementsByClassName("container-fluid comicContent")[0];
            if (!content) return collectedChapterUrls;
            var images = content.getElementsByTagName("li");
            for (var i = 0; i < images.length; i++) {
                var img = images[i].getElementsByTagName("img")[0];
                var url = img && img.dataset && img.dataset.src;
                rememberChapterUrls([url]);
            }
            return collectedChapterUrls;
        }
        function finishChapter(urls, source) {
            if (finished || urls.length <= 0) return false;
            var declaredCount = declaredChapterCount();
            if (declaredCount > 0 && urls.length !== declaredCount) return false;
            finished = true;
            GM.reportCollectorCount(declaredCount, urls.length, source);
            if (backgroundDownload) {
                GM.setLoadingDialog(false);
                GM.loadChapter(chapterHeader() + "\n" + urls.join("\n"));
            }
            else emitReaderChunk(true);
            return true;
        }
        var encryptedUrls = decryptChapterUrls();
        if (encryptedUrls.length > 0) {
            var encryptedUnique = Array();
            var encryptedSet = Object.create(null);
            for (var encryptedIndex = 0; encryptedIndex < encryptedUrls.length; encryptedIndex++) {
                var encryptedUrl = encryptedUrls[encryptedIndex];
                if (encryptedUrl && !encryptedSet[encryptedUrl]) {
                    encryptedSet[encryptedUrl] = true;
                    encryptedUnique.push(encryptedUrl);
                }
            }
            var declaredCount = declaredChapterCount();
            if (declaredCount <= 0 || encryptedUnique.length === declaredCount) {
                rememberChapterUrls(encryptedUnique);
                finishChapter(collectedChapterUrls, "encrypted");
                return;
            }
            GM.reportCollectorCount(declaredCount, encryptedUnique.length, "encrypted_mismatch");
        }
        function step(timestamp) {
            if (!lastTime) lastTime = timestamp;
            const elapsed = timestamp - lastTime;
            if (elapsed >= effectiveInterval) {
                const index = parseInt(document.getElementsByClassName("comicIndex")[0].innerText) || 0;
                const count = parseInt(document.getElementsByClassName("comicCount")[0].innerText) || 0;
                const chapterUrls = collectChapterUrls();
                const progress = backgroundDownload ? Math.max(index, chapterUrls.length) : index;
                if (backgroundDownload) GM.setLoadingDialogProgress("背景載入 " + progress, count.toString());
                emitReaderChunk(false);
                /* The visible page counter can remain at 22/23 even when the
                   final image and all URLs are ready. Complete from the collected
                   URLs instead of waiting for comicIndex to reach comicCount. */
                if (count > 0 && chapterUrls.length === count && finishChapter(chapterUrls, "dom")) return;
                if (document.hidden) window.scrollTo(0, document.body.scrollHeight);
                else window.scrollBy(0, effectiveSpeed);
                lastTime = timestamp;
                const currentHeight = document.body.scrollHeight;
                if (Math.round(window.innerHeight+window.scrollY+0.5) >= currentHeight) { /*避免小数不符无法触发*/
                    if (!bottomReachedAt) bottomReachedAt = Date.now();
                    if (currentHeight === prevHeight && Date.now() - bottomReachedAt >= 2000) {
                        if (count <= 0 && finishChapter(chapterUrls, "dom_unknown_count")) return;
                        if (count > 0 && chapterUrls.length < count) {
                            window.scrollBy(0, -1);
                            window.scrollBy(0, 1);
                        }
                    }
                    prevHeight = currentHeight;
                } else bottomReachedAt = 0;
            }
            ticking = false;
            requestTick();
        }
        requestTick();
    }
    function modify() {
        var url = location.href;
        if(url.indexOf("/chapter/") > 0){
            GM.rememberChapterSelectionUrl(url);
            if (GM.isDownloadMode()) GM.setLoadingDialog(true);
            smoothLoadChapter(GM.getChapterLoadSpeed(), 16);
        } else {
            var json = Array();
            var chapters = document.getElementsByClassName("upLoop")[0].children;
            var newObj = null;
            for(var i = 0; i < chapters.length; i++) {
                if(i % 2) {
                    newObj["chapters"] = scanChapters(chapters[i]);
                    json.push(newObj);
                    newObj = null;
                }
                else {
                    newObj = JSON.constructor();
                    newObj["name"] = chapters[i].innerText;
                }
            }
            GM.setTitle(document.getElementsByTagName("h6")[0].title);
            GM.setFab(JSON.stringify(json));
        }
    }
    modify();
} else modify();

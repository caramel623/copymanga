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
        let readerStarted = false;
        let finished = false;
        let bottomReachedAt = 0;
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
        function emitReaderChunk(finalChunk) {
            if (backgroundDownload) return;
            var images = document.getElementsByClassName("container-fluid comicContent")[0].getElementsByTagName("li");
            var available = Array();
            for (var i = sentImages; i < images.length; i++) {
                var img = images[i].getElementsByTagName("img")[0];
                if (!img || !img.dataset.src) break;
                available.push(img.dataset.src);
            }
            var amount = finalChunk ? available.length : Math.floor(available.length / 50) * 50;
            if (amount <= 0) return;
            var chunk = available.slice(0, amount).join("\n");
            GM.loadChapterChunk((readerStarted ? "" : chapterHeader() + "\n") + chunk, !readerStarted, finalChunk);
            readerStarted = true;
            sentImages += amount;
        }
        function requestTick() {
            if (!finished && !ticking) {
                ticking = true;
                requestAnimationFrame(step);
            }
        }
        function collectChapterUrls() {
            var content = document.getElementsByClassName("container-fluid comicContent")[0];
            if (!content) return Array();
            var images = content.getElementsByTagName("li");
            var urls = Array();
            for (var i = 0; i < images.length; i++) {
                var img = images[i].getElementsByTagName("img")[0];
                if (img && img.dataset && img.dataset.src) urls.push(img.dataset.src);
            }
            return urls;
        }
        function finishChapter(urls) {
            if (finished || urls.length <= 0) return false;
            finished = true;
            GM.setLoadingDialog(false);
            if (backgroundDownload) GM.loadChapter(chapterHeader() + "\n" + urls.join("\n"));
            else emitReaderChunk(true);
            return true;
        }
        function step(timestamp) {
            if (!lastTime) lastTime = timestamp;
            const elapsed = timestamp - lastTime;
            if (elapsed >= effectiveInterval) {
                const index = parseInt(document.getElementsByClassName("comicIndex")[0].innerText) || 0;
                const count = parseInt(document.getElementsByClassName("comicCount")[0].innerText) || 0;
                const chapterUrls = collectChapterUrls();
                const progress = backgroundDownload ? Math.max(index, chapterUrls.length) : index;
                GM.setLoadingDialogProgress((backgroundDownload ? "背景載入 " : "") + progress, count.toString());
                emitReaderChunk(false);
                /* The visible page counter can remain at 22/23 even when the
                   final image and all URLs are ready. Complete from the collected
                   URLs instead of waiting for comicIndex to reach comicCount. */
                if (backgroundDownload && count > 0 && chapterUrls.length >= count && finishChapter(chapterUrls)) return;
                window.scrollBy(0, effectiveSpeed);
                lastTime = timestamp;
                const currentHeight = document.body.scrollHeight;
                if (Math.round(window.innerHeight+window.scrollY+0.5) >= currentHeight) { /*避免小数不符无法触发*/
                    if (!bottomReachedAt) bottomReachedAt = Date.now();
                    if (currentHeight === prevHeight && (!backgroundDownload || Date.now() - bottomReachedAt >= 2000)) {
                        if (finishChapter(chapterUrls)) return;
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
            GM.setLoadingDialog(true);
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

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
        let batchStart = 0;
        let sentImages = 0;
        let readerStarted = false;
        const backgroundDownload = GM.isDownloadMode();
        const batchSize = backgroundDownload ? Math.min(5, Math.max(1, GM.getDownloadBatchSize())) : 999999;
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
            if (!ticking) {
                ticking = true;
                requestAnimationFrame(step);
            }
        }
        function step(timestamp) {
            if (!lastTime) lastTime = timestamp;
            const elapsed = timestamp - lastTime;
            if (elapsed >= effectiveInterval) {
                const index = parseInt(document.getElementsByClassName("comicIndex")[0].innerText) || 0;
                const count = parseInt(document.getElementsByClassName("comicCount")[0].innerText) || 0;
                GM.setLoadingDialogProgress((backgroundDownload ? "背景載入 " : "") + index, count.toString());
                emitReaderChunk(false);
                if (backgroundDownload && batchStart == 0) batchStart = index;
                if (backgroundDownload && index - batchStart >= batchSize) {
                    batchStart = index;
                    ticking = false;
                    const wait = 1000 + Math.floor(Math.random() * 19001);
                    setTimeout(requestTick, wait);
                    return;
                }
                window.scrollBy(0, effectiveSpeed);
                lastTime = timestamp;
                const currentHeight = document.body.scrollHeight;
                if (Math.round(window.innerHeight+window.scrollY+0.5) >= currentHeight) { /*避免小数不符无法触发*/
                    if (currentHeight === prevHeight) {
                        var images = document.getElementsByClassName("container-fluid comicContent")[0].getElementsByTagName("li");
                        var result = chapterHeader();
                        for(var i = 0; i < images.length; i++) result += "\n" + images[i].getElementsByTagName("img")[0].dataset.src;
                        GM.setLoadingDialog(false);
                        if (backgroundDownload) GM.loadChapter(result);
                        else emitReaderChunk(true);
                        return;
                    }
                    prevHeight = currentHeight;
                }
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

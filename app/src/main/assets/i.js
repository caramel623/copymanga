javascript:
if (typeof (loaded) == "undefined") {
    var loaded = true;
    var invoke = {
        preUrl: "",
        bookrackSorted: false,
        bookrackSortStage: 0,
        bookrackSortRetries: 0,
        pinTitle: function () {
            var game = document.getElementsByName("exchange");
            if (game.length) game[0].hidden = true;
        },
        applyWebDarkMode: function () {
            var styleId = "cm-web-dark-style";
            var enabled = GM.isWebDarkModeEnabled();
            document.documentElement.classList.toggle("cm-web-dark", enabled);
            var existing = document.getElementById(styleId);
            if (!enabled) {
                if (existing) existing.remove();
                return;
            }
            if (existing) return;
            var style = document.createElement("style");
            style.id = styleId;
            style.textContent = [
                ":root.cm-web-dark{color-scheme:dark;background:#071522!important;}",
                ":root.cm-web-dark body{background:#071522!important;color:#f1f5f9!important;}",
                ":root.cm-web-dark body *{color:#f1f5f9!important;border-color:#294866!important;}",
                ":root.cm-web-dark body div,:root.cm-web-dark body main,:root.cm-web-dark body section,:root.cm-web-dark body article,:root.cm-web-dark body aside,:root.cm-web-dark body header,:root.cm-web-dark body footer,:root.cm-web-dark body nav,:root.cm-web-dark body ul,:root.cm-web-dark body ol,:root.cm-web-dark body li,:root.cm-web-dark body form,:root.cm-web-dark body table,:root.cm-web-dark body thead,:root.cm-web-dark body tbody,:root.cm-web-dark body tr,:root.cm-web-dark body td,:root.cm-web-dark body th{background-color:#071522!important;}",
                ":root.cm-web-dark body *::before,:root.cm-web-dark body *::after{border-color:#294866!important;}",
                ":root.cm-web-dark a,:root.cm-web-dark a *{color:#8ab4f8!important;}",
                ":root.cm-web-dark input,:root.cm-web-dark textarea,:root.cm-web-dark select,:root.cm-web-dark option,:root.cm-web-dark button,:root.cm-web-dark [role='button'],:root.cm-web-dark [contenteditable='true']{background:#14304d!important;color:#f1f5f9!important;border-color:#294866!important;}",
                ":root.cm-web-dark input::placeholder,:root.cm-web-dark textarea::placeholder{color:#b8c7d9!important;opacity:1!important;}",
                ":root.cm-web-dark dialog,:root.cm-web-dark [role='dialog'],:root.cm-web-dark [class*='modal'],:root.cm-web-dark [class*='Modal'],:root.cm-web-dark [class*='popup'],:root.cm-web-dark [class*='Popup'],:root.cm-web-dark [class*='drawer'],:root.cm-web-dark [class*='Drawer']{background:#0d2238!important;color:#f1f5f9!important;border-color:#294866!important;}",
                ":root.cm-web-dark [class*='mask'],:root.cm-web-dark [class*='Mask'],:root.cm-web-dark [class*='overlay'],:root.cm-web-dark [class*='Overlay']{background-color:rgba(2,10,18,.86)!important;}",
                ":root.cm-web-dark hr{background:#294866!important;border-color:#294866!important;}",
                ":root.cm-web-dark img,:root.cm-web-dark picture,:root.cm-web-dark video,:root.cm-web-dark canvas{filter:none!important;opacity:1!important;}",
                ":root.cm-web-dark ::selection{background:#b8c7d9!important;color:#071522!important;}"
            ].join("");
            (document.head || document.documentElement).appendChild(style);
        },
        prepareBookrackSortControls: function () {
            if (document.getElementById("cm-bookrack-sort")) return;
            var panel = document.createElement("div");
            panel.id = "cm-bookrack-sort";
            panel.style.cssText = "position:fixed;left:8px;top:8px;z-index:99999;";
            var button = document.createElement("button");
            button.type = "button";
            button.innerText = "⚙";
            button.title = "書架排序";
            button.style.cssText = "width:34px;height:34px;border:0;border-radius:17px;background:rgba(255,255,255,.92);box-shadow:0 2px 8px #999;font-size:20px;line-height:34px;padding:0;";
            var box = document.createElement("div");
            box.style.cssText = "display:none;margin-top:6px;background:white;padding:8px;border-radius:8px;box-shadow:0 2px 8px #999;";
            button.addEventListener("click", function () {
                box.style.display = box.style.display == "none" ? "block" : "none";
            });
            var field = document.createElement("select");
            field.style.cssText = "display:block;width:150px;margin-bottom:6px;";
            [["update", "作品更新時間"], ["added", "加入書架時間"], ["read", "閱讀時間"]].forEach(function (item) {
                field.add(new Option(item[1], item[0]));
            });
            var direction = document.createElement("select");
            direction.style.cssText = "display:block;width:150px;";
            [["desc", "由新到舊"], ["asc", "由舊到新"]].forEach(function (item) {
                direction.add(new Option(item[1], item[0]));
            });
            field.value = GM.getBookrackSortField();
            direction.value = GM.getBookrackSortDirection();
            var changed = function () {
                GM.setBookrackSort(field.value, direction.value);
                invoke.bookrackSorted = false;
                invoke.bookrackSortStage = 0;
                invoke.bookrackSortRetries = 0;
                invoke.sortBookrackByUpdateTime();
            };
            field.addEventListener("change", changed);
            direction.addEventListener("change", changed);
            box.appendChild(field);
            box.appendChild(direction);
            panel.appendChild(button);
            panel.appendChild(box);
            document.body.appendChild(panel);
        },
        parseBookrackTime: function (text) {
            if (!text) return 0;
            var now = Date.now();
            var relative = text.match(/(\d+)\s*(秒|分鐘|分钟|小時|小时|天|日|週|周|月|年)前/);
            if (relative) {
                var value = parseInt(relative[1]) || 0;
                var unit = relative[2];
                var minute = 60 * 1000;
                var hour = 60 * minute;
                var day = 24 * hour;
                if (unit == "秒") return now - value * 1000;
                if (unit == "分鐘" || unit == "分钟") return now - value * minute;
                if (unit == "小時" || unit == "小时") return now - value * hour;
                if (unit == "天" || unit == "日") return now - value * day;
                if (unit == "週" || unit == "周") return now - value * 7 * day;
                if (unit == "月") return now - value * 30 * day;
                if (unit == "年") return now - value * 365 * day;
            }
            var full = text.match(/(20\d{2})[年\/\-.](\d{1,2})[月\/\-.](\d{1,2})(?:[日\s]+(\d{1,2})[:：](\d{1,2}))?/);
            if (full) return new Date(parseInt(full[1]), parseInt(full[2]) - 1, parseInt(full[3]), parseInt(full[4]) || 0, parseInt(full[5]) || 0).getTime();
            var short = text.match(/(^|[^0-9])(\d{1,2})[月\/\-.](\d{1,2})(?:[日\s]+(\d{1,2})[:：](\d{1,2}))?/);
            if (short) return new Date(new Date().getFullYear(), parseInt(short[2]) - 1, parseInt(short[3]), parseInt(short[4]) || 0, parseInt(short[5]) || 0).getTime();
            return 0;
        },
        extractBookrackSortTime: function (item, field) {
            var text = (item.innerText || "").replace(/\s+/g, " ").trim();
            var labels = {
                update: ["作品更新時間", "更新時間", "更新", "最新"],
                added: ["加入書架時間", "加到書架時間", "收藏時間", "加入", "收藏"],
                read: ["閱讀時間", "最近閱讀時間", "最後閱讀時間", "閱讀", "讀到"]
            }[field] || [];
            for (var i = 0; i < labels.length; i++) {
                var pos = text.indexOf(labels[i]);
                if (pos >= 0) {
                    var near = text.substring(pos, Math.min(text.length, pos + 80));
                    var value = this.parseBookrackTime(near);
                    if (value) return value;
                }
            }
            return this.parseBookrackTime(text);
        },
        applyBookrackFallbackSort: function () {
            if (location.href.indexOf("/bookrack") < 0) return false;
            var anchors = Array.prototype.slice.call(document.querySelectorAll("a[href*='/comic/'], a[href*='/details/comic/']"));
            var items = [];
            anchors.forEach(function (anchor) {
                var item = anchor.closest("li, .col, [class*='col-'], [class*='item'], [class*='Item'], [class*='card'], [class*='Card'], [class*='comic'], [class*='Comic']");
                if (!item) {
                    item = anchor;
                    for (var i = 0; i < 4 && item.parentElement; i++) {
                        item = item.parentElement;
                        if ((item.innerText || "").length > 20 && item.querySelector("img")) break;
                    }
                }
                if (item && items.indexOf(item) < 0 && !item.closest("#cm-bookrack-sort")) items.push(item);
            });
            if (items.length < 2) return false;
            var parent = items[0].parentElement;
            if (!parent || !items.every(function (item) { return item.parentElement == parent; })) return false;
            var field = GM.getBookrackSortField();
            var desc = GM.getBookrackSortDirection() != "asc";
            var sorted = items.map(function (item, index) {
                return { item: item, index: index, time: invoke.extractBookrackSortTime(item, field) };
            });
            if (!sorted.some(function (entry) { return entry.time > 0; })) return false;
            sorted.sort(function (a, b) {
                if (a.time == b.time) return a.index - b.index;
                return desc ? b.time - a.time : a.time - b.time;
            });
            sorted.forEach(function (entry) { parent.appendChild(entry.item); });
            return true;
        },
        openBookrackSortMenu: function () {
            if (location.href.indexOf("/bookrack") < 0) return false;
            var vw = window.innerWidth || document.documentElement.clientWidth;
            var vh = window.innerHeight || document.documentElement.clientHeight;
            var candidates = Array.prototype.slice.call(document.querySelectorAll(
                "button, [role='button'], [aria-haspopup], [class*='sort'], [class*='Sort'], [class*='filter'], [class*='Filter'], [class*='menu'], [class*='Menu'], i, svg"
            ));
            var scored = [];
            candidates.forEach(function (element) {
                if (!element || element.closest("#cm-bookrack-sort")) return;
                var target = element.closest("button, [role='button'], [aria-haspopup], a, div") || element;
                if (!target || target.closest("#cm-bookrack-sort")) return;
                if (scored.some(function (entry) { return entry.target == target; })) return;
                var rect = target.getBoundingClientRect();
                if (rect.width <= 0 || rect.height <= 0 || rect.bottom < 0 || rect.right < 0 || rect.top > vh || rect.left > vw) return;
                if (rect.left < vw * 0.45 || rect.top > Math.max(140, vh * 0.28)) return;
                var text = ((target.innerText || "") + " " + (target.getAttribute("aria-label") || "") + " " + (target.title || "") + " " + (target.className || "")).toLowerCase();
                var score = 0;
                if (/排序|排列|篩選|筛选|操作|sort|filter|menu|more/.test(text)) score += 10;
                if (rect.right > vw - 96) score += 4;
                if (rect.top < 96) score += 3;
                if (rect.width <= 72 && rect.height <= 72) score += 2;
                if (score > 0) scored.push({ target: target, score: score });
            });
            scored.sort(function (a, b) { return b.score - a.score; });
            if (!scored.length) return false;
            scored[0].target.click();
            return true;
        },
        sortBookrackByUpdateTime: function () {
            if (this.bookrackSorted || location.href.indexOf("/bookrack") < 0) return;
            this.prepareBookrackSortControls();
            var fieldLabels = {
                update: ["作品更新時間", "按作品更新時間", "更新時間", "按更新時間"],
                added: ["加入書架時間", "加到書架時間", "收藏時間"],
                read: ["閱讀時間", "最近閱讀時間", "最後閱讀時間"]
            };
            var directionLabels = {
                desc: ["由新到舊", "新到舊", "降冪", "降序", "最新優先"],
                asc: ["由舊到新", "舊到新", "升冪", "升序", "最舊優先"]
            };
            var labels = this.bookrackSortStage == 0
                ? fieldLabels[GM.getBookrackSortField()]
                : directionLabels[GM.getBookrackSortDirection()];
            var elements = document.querySelectorAll("option, button, label, [role='option'], [role='menuitem'], li, span, div");
            for (var i = 0; i < elements.length; i++) {
                var element = elements[i];
                if (element.closest("#cm-bookrack-sort")) continue;
                if (labels.indexOf(element.innerText.trim()) < 0) continue;

                if (element.tagName == "OPTION") {
                    var select = element.parentElement;
                    select.value = element.value;
                    select.dispatchEvent(new Event("change", { bubbles: true }));
                } else {
                    var target = element.closest("button, label, [role='option'], [role='menuitem'], li") || element;
                    target.click();
                }
                if (this.bookrackSortStage == 0) {
                    this.bookrackSortStage = 1;
                    this.bookrackSortRetries = 0;
                    setTimeout(function () { invoke.sortBookrackByUpdateTime(); }, 300);
                } else {
                    this.bookrackSorted = true;
                    this.bookrackSortStage = 0;
                    this.bookrackSortRetries = 0;
                    setTimeout(function () { invoke.applyBookrackFallbackSort(); }, 800);
                }
                return;
            }

            this.bookrackSortRetries++;
            if (this.bookrackSortRetries == 2 || this.bookrackSortRetries == 8) {
                if (this.openBookrackSortMenu()) {
                    setTimeout(function () { invoke.sortBookrackByUpdateTime(); }, 500);
                    return;
                }
            }
            if (this.bookrackSortRetries >= 20) {
                this.applyBookrackFallbackSort();
                this.bookrackSorted = true;
                this.bookrackSortStage = 0;
                this.bookrackSortRetries = 0;
                return;
            }
            setTimeout(function () { invoke.sortBookrackByUpdateTime(); }, 300);
        },
        prepareEncryptedLogin: function () {
            var password = document.querySelector('input[type="password"]');
            if (!password || document.getElementById("cm-local-login")) return;
            var inputs = Array.prototype.slice.call(document.querySelectorAll('input'));
            var passwordIndex = inputs.indexOf(password);
            var account = inputs.slice(0, passwordIndex).reverse().find(function (input) {
                return input.type == "text" || input.type == "email" || input.type == "tel";
            });
            if (!account) return;

            var label = document.createElement("label");
            label.id = "cm-local-login";
            label.style.cssText = "display:block;padding:10px 4px;color:#555;font-size:14px;";
            var checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.checked = GM.hasSavedLogin(location.href);
            label.appendChild(checkbox);
            label.appendChild(document.createTextNode(" 僅在此裝置加密儲存並自動填入"));
            (password.parentElement || password).insertAdjacentElement("afterend", label);

            if (checkbox.checked) {
                account.value = GM.getSavedAccount(location.href);
                password.value = GM.getSavedPassword(location.href);
                account.dispatchEvent(new Event("input", { bubbles: true }));
                password.dispatchEvent(new Event("input", { bubbles: true }));
            }
            checkbox.addEventListener("change", function () {
                if (!checkbox.checked) GM.clearSavedLogin();
            });
            var form = password.closest("form");
            var save = function () { if (checkbox.checked) GM.saveLogin(location.href, account.value, password.value); };
            if (form) form.addEventListener("submit", save);
            else document.querySelectorAll('button').forEach(function (button) { button.addEventListener("click", save); });
        },
        convertRanobeToTraditional: function () {
            if (!GM.isRanobeTraditionalEnabled()) {
                if (window.cmRanobeObserver) window.cmRanobeObserver.disconnect();
                window.cmRanobeObserver = null;
                return;
            }
            var url = location.href.toLowerCase();
            var isRanobe = url.indexOf("ranobe") >= 0 || url.indexOf("novel") >= 0 ||
                Array.prototype.some.call(document.querySelectorAll(".van-tabbar-item, .van-tab"), function (tab) {
                    return tab.innerText.trim() == "輕小說" && (tab.className.indexOf("active") >= 0);
                });
            if (!isRanobe) {
                if (window.cmRanobeObserver) window.cmRanobeObserver.disconnect();
                window.cmRanobeObserver = null;
                return;
            }
            var convert = function (root) {
                var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                    acceptNode: function (node) {
                        var parent = node.parentElement;
                        if (!parent || !node.nodeValue.trim() || /^(SCRIPT|STYLE|TEXTAREA|INPUT|CODE|PRE)$/.test(parent.tagName))
                            return NodeFilter.FILTER_REJECT;
                        return NodeFilter.FILTER_ACCEPT;
                    }
                });
                var nodes = Array();
                while (walker.nextNode()) nodes.push(walker.currentNode);
                nodes.forEach(function (node) {
                    var converted = GM.toTraditionalChinese(node.nodeValue);
                    if (converted != node.nodeValue) node.nodeValue = converted;
                });
            };
            convert(document.body);
            if (!window.cmRanobeObserver) {
                window.cmRanobeObserver = new MutationObserver(function (mutations) {
                    mutations.forEach(function (mutation) {
                        mutation.addedNodes.forEach(function (node) {
                            if (node.nodeType == Node.ELEMENT_NODE) convert(node);
                            else if (node.nodeType == Node.TEXT_NODE && node.parentElement) convert(node.parentElement);
                        });
                    });
                });
                window.cmRanobeObserver.observe(document.body, { childList: true, subtree: true });
            }
        },
        notCallGM: function (url) {
            if (this.preUrl == url) return false;
            else {
                this.preUrl = url;
                return true;
            }
        },
        clickClass: function (name, index) { document.getElementsByClassName(name)[index].click(); },
        clickClassCenter: function (name, index) {
            var ev = document.createEvent('HTMLEvents');
            ev.clientX = innerWidth / 2;
            ev.clientY = innerHeight / 2;
            ev.initEvent('click', false, true);
            document.getElementsByClassName(name)[index].dispatchEvent(ev);
        },
        resetPreUrl: function () { this.preUrl = ""; },
        loadChapter: function () { this.clickClassCenter("comicContentPopupImageItem", 0); GM.loadComic(location.href); },
        prepareComicLinks: function () {
            if (window.cmComicLinkHandlerInstalled || !GM.shouldOpenComicInNewWebView()) return;
            window.cmComicLinkHandlerInstalled = true;
            document.addEventListener("click", function (event) {
                var anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
                var card = event.target && event.target.closest ? event.target.closest(".comicItem") : null;
                var href = anchor ? (anchor.href || "") : "";
                var path = "";
                if (href) {
                    try { path = new URL(href, location.href).pathname; } catch (_) { return; }
                }
                if (path.indexOf("/details/comic/") < 0 && card) {
                    var image = card.querySelector("img");
                    var source = image
                        ? (image.getAttribute("data-src") || image.getAttribute("src") || "")
                        : "";
                    var match = source.match(/\/comic\/([^/]+)\/cover\//i) ||
                        source.match(/\/[a-z0-9]\/([a-z0-9_-]+)\/cover\//i);
                    if (match) href = location.origin + "/h5/details/comic/" + match[1];
                }
                if (!href || (path.indexOf("/details/comic/") < 0 && !card)) return;
                event.preventDefault();
                event.stopPropagation();
                event.stopImmediatePropagation();
                GM.openComicInNewWebView(href);
            }, true);
        },
        prepareChapterLinks: function () {
            if (window.cmChapterLinkHandlerInstalled || !GM.shouldInterceptChapterLinks()) return;
            window.cmChapterLinkHandlerInstalled = true;

            var chapterComponent = function () {
                var item = document.querySelector(".chapterItem");
                var component = item && item.__vue__;
                for (var depth = 0; component && depth < 12; depth++, component = component.$parent) {
                    if (component.pageData && Array.isArray(component.pageData.list)) return component;
                }
                return null;
            };
            var chapterUrl = function (pathWord, uuid) {
                return location.origin + "/comic/" + pathWord + "/chapter/" + uuid;
            };
            document.addEventListener("click", function (event) {
                var item = event.target && event.target.closest ? event.target.closest(".chapterItem") : null;
                var continueButton = event.target && event.target.closest
                    ? event.target.closest("button")
                    : null;
                if (!item && (!continueButton || !/(續看|继续|繼續|開始閱讀|开始阅读)/.test(continueButton.innerText || ""))) {
                    return;
                }

                var component = chapterComponent();
                if (!component) return;
                var chapter = null;
                if (item) {
                    var list = item.closest(".chapterList");
                    var items = list ? Array.prototype.filter.call(list.children, function (child) {
                        return child.classList && child.classList.contains("chapterItem");
                    }) : [];
                    var index = items.indexOf(item);
                    chapter = index >= 0 ? component.pageData.list[index] : null;
                } else if (component.lastBrowse && component.lastBrowse.chapter_uuid) {
                    chapter = {
                        uuid: component.lastBrowse.chapter_uuid,
                        comic_path_word: component.lastBrowse.path_word,
                        name: component.lastBrowse.chapter_name,
                        prev: null,
                        next: null
                    };
                }
                if (!chapter || !chapter.uuid || !chapter.comic_path_word) return;

                var currentUrl = chapterUrl(chapter.comic_path_word, chapter.uuid);
                var previousUrl = chapter.prev ? chapterUrl(chapter.comic_path_word, chapter.prev) : "";
                var nextUrl = chapter.next ? chapterUrl(chapter.comic_path_word, chapter.next) : "";
                var title = document.querySelector(".headerContentTitle");
                event.preventDefault();
                event.stopPropagation();
                event.stopImmediatePropagation();
                GM.openChapterInReader(
                    currentUrl,
                    chapter.name || "",
                    title ? title.innerText.trim() : "",
                    previousUrl,
                    nextUrl
                );
            }, true);
        },
        prepareNovelLinks: function () {
            if (window.cmNovelLinkHandlerInstalled) return;
            window.cmNovelLinkHandlerInstalled = true;
            document.addEventListener("click", function (event) {
                if (event.target && event.target.closest && event.target.closest("#cm-open-local-novel-shelf")) return;
                var anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
                var card = event.target && event.target.closest ? event.target.closest(".comicItem") : null;
                var href = anchor ? (anchor.href || "") : "";
                if (!href && card) {
                    var image = card.querySelector("img[src*='/book/'], img[data-src*='/book/']");
                    var source = image ? (image.getAttribute("data-src") || image.src || "") : "";
                    var match = source.match(/\/book\/([^/]+)\/cover\//i);
                    if (match) href = location.origin + "/book/" + match[1];
                }
                if (!href) return;
                var path = "";
                try { path = new URL(href, location.href).pathname; } catch (_) { return; }
                var isNovelDetail = /\/(?:book|novel)\/[^/?#]+\/?$/i.test(path) &&
                    !/\/(?:bookrack|discover|search|ranking)\/?$/i.test(path);
                if (!isNovelDetail) return;
                event.preventDefault();
                event.stopPropagation();
                GM.openNovel(href);
            }, true);
        },
        prepareNovelShelfEntryButton: function () {
            var existing = document.getElementById("cm-open-local-novel-shelf");
            if (location.pathname.indexOf("/h5/discover") < 0) {
                if (existing) existing.remove();
                return;
            }
            var resizeToHalfCard = function (target) {
                var card = document.querySelector(".comicItem");
                if (!card) return;
                var rect = card.getBoundingClientRect();
                if (!rect.width || !rect.height) return;
                var width = Math.round(rect.width / 2);
                var height = Math.round(rect.height / 2);
                target.style.width = width + "px";
                target.style.height = height + "px";
                target.style.borderRadius = Math.max(16, Math.round(width / 3)) + "px";
            };
            var darkShelfIcon = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NyIgaGVpZ2h0PSIxNDYiIHZpZXdCb3g9IjAgMCA2NyAxNDYiIHJvbGU9ImltZyIgYXJpYS1sYWJlbD0i5oiR55qE5pu45p62Ij4KICA8cmVjdCB4PSIxLjUiIHk9IjEuNSIgd2lkdGg9IjY0IiBoZWlnaHQ9IjE0MyIgcng9IjkiIGZpbGw9IiMxMDJCNEMiIHN0cm9rZT0iIzZGQThENiIgc3Ryb2tlLXdpZHRoPSIzIi8+CiAgPGcgc3Ryb2tlPSIjQjlEN0U4IiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lam9pbj0icm91bmQiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCI+CiAgICA8cGF0aCBkPSJNMTMgMTA1VjQ1UTMzLjUgMzEgNTQgNDVWMTA1IiBmaWxsPSJub25lIi8+CiAgICA8cGF0aCBkPSJNMTkgNzZWNDloOHYyN3oiIGZpbGw9IiNGMkE2NUEiLz4KICAgIDxwYXRoIGQ9Ik0yOSA3NlY0M2g5djMzeiIgZmlsbD0iIzcwQjdFOCIvPgogICAgPHBhdGggZD0iTTQwIDc2VjUxaDh2MjV6IiBmaWxsPSIjOUJDQjgzIi8+CiAgICA8cGF0aCBkPSJNMTQgNzZoMzl2N0gxNHoiIGZpbGw9IiNEMUExNUQiLz4KICAgIDxwYXRoIGQ9Ik0xOSAxMDNWODZsNyAydjE1eiIgZmlsbD0iIzcwQjdFOCIgdHJhbnNmb3JtPSJyb3RhdGUoMTIgMjIuNSA5NC41KSIvPgogICAgPHBhdGggZD0iTTI5IDEwM1Y4NGg5djE5eiIgZmlsbD0iI0YyQTY1QSIvPgogICAgPHBhdGggZD0iTTQwIDEwM1Y4N2g4djE2eiIgZmlsbD0iIzlCQ0I4MyIvPgogICAgPHBhdGggZD0iTTE0IDEwM2gzOXY3SDE0eiIgZmlsbD0iI0QxQTE1RCIvPgogICAgPHBhdGggZD0iTTE1IDgzdjI3TTUyIDgzdjI3IiBmaWxsPSJub25lIi8+CiAgPC9nPgo8L3N2Zz4K";
            var lightShelfIcon = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI2NyIgaGVpZ2h0PSIxNDYiIHZpZXdCb3g9IjAgMCA2NyAxNDYiIHJvbGU9ImltZyIgYXJpYS1sYWJlbD0i5oiR55qE5pu45p62Ij4KICA8cmVjdCB4PSIxLjUiIHk9IjEuNSIgd2lkdGg9IjY0IiBoZWlnaHQ9IjE0MyIgcng9IjkiIGZpbGw9IiNGRkY4RTkiIHN0cm9rZT0iIzBCNEY0QiIgc3Ryb2tlLXdpZHRoPSIzIi8+CiAgPGcgc3Ryb2tlPSIjMEI0RjRCIiBzdHJva2Utd2lkdGg9IjMiIHN0cm9rZS1saW5lam9pbj0icm91bmQiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCI+CiAgICA8IS0tIHNoZWxmIHVwcmlnaHRzIGFuZCB0b3AgYXJjaCAtLT4KICAgIDxwYXRoIGQ9Ik0xMyAxMDVWNDVRMzMuNSAzMSA1NCA0NVYxMDUiIGZpbGw9Im5vbmUiLz4KICAgIDwhLS0gdXBwZXIgYm9va3MgLS0+CiAgICA8cGF0aCBkPSJNMTkgNzZWNDloOHYyN3oiIGZpbGw9IiNGNDlBM0EiLz4KICAgIDxwYXRoIGQ9Ik0yOSA3NlY0M2g5djMzeiIgZmlsbD0iIzRCOUJENCIvPgogICAgPHBhdGggZD0iTTQwIDc2VjUxaDh2MjV6IiBmaWxsPSIjODlBQzZDIi8+CiAgICA8IS0tIHVwcGVyIHNoZWxmIC0tPgogICAgPHBhdGggZD0iTTE0IDc2aDM5djdIMTR6IiBmaWxsPSIjQzM4RDRFIi8+CiAgICA8IS0tIGxvd2VyIGJvb2tzIC0tPgogICAgPHBhdGggZD0iTTE5IDEwM1Y4Nmw3IDJ2MTV6IiBmaWxsPSIjNEI5QkQ0IiB0cmFuc2Zvcm09InJvdGF0ZSgxMiAyMi41IDk0LjUpIi8+CiAgICA8cGF0aCBkPSJNMjkgMTAzVjg0aDl2MTl6IiBmaWxsPSIjRjQ5QTNBIi8+CiAgICA8cGF0aCBkPSJNNDAgMTAzVjg3aDh2MTZ6IiBmaWxsPSIjODlBQzZDIi8+CiAgICA8IS0tIGxvd2VyIHNoZWxmIGFuZCBsZWdzIC0tPgogICAgPHBhdGggZD0iTTE0IDEwM2gzOXY3SDE0eiIgZmlsbD0iI0MzOEQ0RSIvPgogICAgPHBhdGggZD0iTTE1IDgzdjI3TTUyIDgzdjI3IiBmaWxsPSJub25lIi8+CiAgPC9nPgo8L3N2Zz4K";
            var applyShelfIcon = function (target) {
                var image = target.querySelector("img");
                if (!image) return;
                image.src = document.documentElement.classList.contains("cm-web-dark") ? darkShelfIcon : lightShelfIcon;
            };
            if (existing) {
                resizeToHalfCard(existing);
                applyShelfIcon(existing);
                return;
            }
            var button = document.createElement("button");
            button.id = "cm-open-local-novel-shelf";
            button.type = "button";
            button.setAttribute("aria-label", "進入本地輕小說書架");
            button.innerHTML = '<img alt="本地書架" draggable="false">';
            button.title = "進入本地輕小說書架";
            button.style.cssText = "position:fixed;right:14px;bottom:92px;z-index:99999;width:67px;height:147px;box-sizing:border-box;display:block;border:0!important;border-radius:0;padding:0!important;background:transparent!important;box-shadow:none!important;overflow:visible;";
            button.querySelector("img").style.cssText = "display:block;width:100%;height:100%;pointer-events:none;user-select:none;filter:drop-shadow(0 4px 6px rgba(0,0,0,.55));";
            applyShelfIcon(button);
            button.addEventListener("click", function (event) {
                event.preventDefault(); event.stopPropagation(); event.stopImmediatePropagation();
                GM.openNovelLocalShelf();
            }, true);
            document.body.appendChild(button);
            resizeToHalfCard(button);
            setTimeout(function () { resizeToHalfCard(button); }, 600);
            setTimeout(function () { resizeToHalfCard(button); }, 1800);
        },
        urlChangeListener: function (todo) {
            setInterval(function () { if (invoke.notCallGM(location.href)) { todo(); } }, 1000);
        }
    };
    function modify() {
        var url = location.href;
        GM.onVisiblePage(url);
        GM.hideFab();
        invoke.applyWebDarkMode();
        invoke.prepareEncryptedLogin();
        invoke.convertRanobeToTraditional();
        invoke.prepareComicLinks();
        invoke.prepareChapterLinks();
        invoke.prepareNovelLinks();
        invoke.prepareNovelShelfEntryButton();
        if (url.endsWith("/index")) {
            invoke.pinTitle();
        }
        else if (url.indexOf("/bookrack") > 0) {
            invoke.sortBookrackByUpdateTime();
        }
        else {
            var bookrackControls = document.getElementById("cm-bookrack-sort");
            if (bookrackControls) bookrackControls.remove();
            invoke.bookrackSorted = false;
            invoke.bookrackSortStage = 0;
            invoke.bookrackSortRetries = 0;
        }
        if (url.indexOf("/comicContent/") > 0) setTimeout(function () { invoke.loadChapter() }, 1000);
        else if (url.indexOf("/details/comic/") > 0) GM.loadComic(url);
        else if (url.indexOf("/personal") > 0) {
            GM.enterProfile();
        }
    }
    modify();
    invoke.preUrl = location.href;
    invoke.urlChangeListener(modify);
} else {
    setTimeout(modify, 1280);
}

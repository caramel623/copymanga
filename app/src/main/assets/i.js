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
        prepareBookrackSortControls: function () {
            if (document.getElementById("cm-bookrack-sort")) return;
            var panel = document.createElement("div");
            panel.id = "cm-bookrack-sort";
            panel.style.cssText = "position:fixed;right:8px;top:8px;z-index:99999;background:white;padding:6px;border-radius:8px;box-shadow:0 2px 8px #999;";
            var field = document.createElement("select");
            [["update", "作品更新時間"], ["added", "加入書架時間"], ["read", "閱讀時間"]].forEach(function (item) {
                field.add(new Option(item[1], item[0]));
            });
            var direction = document.createElement("select");
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
            panel.appendChild(field);
            panel.appendChild(direction);
            document.body.appendChild(panel);
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
                }
                return;
            }

            this.bookrackSortRetries++;
            if (this.bookrackSortRetries >= 20) {
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
        urlChangeListener: function (todo) {
            setInterval(function () { if (invoke.notCallGM(location.href)) { todo(); } }, 1000);
        }
    };
    function modify() {
        var url = location.href;
        GM.hideFab();
        invoke.prepareEncryptedLogin();
        invoke.convertRanobeToTraditional();
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
    invoke.urlChangeListener(modify);
} else {
    setTimeout(modify, 1280);
}

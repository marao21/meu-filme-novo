package com.example.sniffer

object InjectedJs {

    const val DEFAULT_TMDB_KEY = "fb362501c06a4f4a286ed80b6073283a"

    fun getTmdbAutoFillScript(apiKey: String = DEFAULT_TMDB_KEY): String {
        return """
        (function() {
            var key = "$apiKey";
            try {
                var storageKeys = [
                    'tmdb_api_key', 'tmdb_key', 'tmdbKey', 'apiKey',
                    'TMDB_KEY', 'api_key', 'filmemeu_tmdb_key', 'tmdb'
                ];
                storageKeys.forEach(function(k) {
                    try {
                        localStorage.setItem(k, key);
                        sessionStorage.setItem(k, key);
                    } catch(e) {}
                });

                function setReactInputValue(input, val) {
                    try {
                        var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                        if (setter && setter.set) {
                            setter.set.call(input, val);
                        } else {
                            input.value = val;
                        }
                    } catch(e) {
                        input.value = val;
                    }
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                }

                function tryAutoFill() {
                    var inputs = document.querySelectorAll('input');
                    for (var i = 0; i < inputs.length; i++) {
                        var inp = inputs[i];
                        var ph = (inp.getAttribute('placeholder') || '').toLowerCase();
                        var id = (inp.id || '').toLowerCase();
                        var name = (inp.name || '').toLowerCase();
                        var aria = (inp.getAttribute('aria-label') || '').toLowerCase();

                        if (ph.indexOf('tmdb') !== -1 || ph.indexOf('chave') !== -1 ||
                            id.indexOf('tmdb') !== -1 || name.indexOf('tmdb') !== -1 ||
                            aria.indexOf('tmdb') !== -1) {

                            setReactInputValue(inp, key);

                            var container = inp.closest('form') || inp.parentElement || document.body;
                            var buttons = container.querySelectorAll('button, input[type="submit"], div[role="button"], a');
                            for (var b = 0; b < buttons.length; b++) {
                                var btn = buttons[b];
                                var txt = (btn.innerText || btn.textContent || btn.value || '').toLowerCase();
                                if (txt.indexOf('salvar') !== -1) {
                                    setTimeout(function() {
                                        try { btn.click(); } catch(e) {}
                                    }, 200);
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }

                tryAutoFill();
                setTimeout(tryAutoFill, 600);
                setTimeout(tryAutoFill, 1500);
                setTimeout(tryAutoFill, 3000);
            } catch(e) {}
        })();
        """.trimIndent()
    }

    fun getSnifferScript(tmdbKey: String = DEFAULT_TMDB_KEY): String {
        val tmdbInjection = getTmdbAutoFillScript(tmdbKey)
        return """
        (function() {
            try {
                // Auto-fill TMDB key
                $tmdbInjection

                // Helper to enter fullscreen
                function triggerFullscreen(v) {
                    try {
                        var fsButtons = document.querySelectorAll(
                            '[aria-label*="fullscreen" i], [title*="fullscreen" i], [class*="fullscreen" i], button'
                        );
                        for (var i = 0; i < fsButtons.length; i++) {
                            var btn = fsButtons[i];
                            var lbl = (btn.getAttribute('aria-label') || btn.title || btn.className || '').toLowerCase();
                            if (lbl.indexOf('fullscreen') !== -1 || lbl.indexOf('tela cheia') !== -1) {
                                btn.click();
                                return;
                            }
                        }
                        var target = v || document.querySelector('video');
                        if (target) {
                            if (target.requestFullscreen) {
                                target.requestFullscreen().catch(function(){});
                            } else if (target.webkitRequestFullscreen) {
                                target.webkitRequestFullscreen();
                            }
                        }
                    } catch(e) {}
                }

                // Web Player Remote & Keyboard Controller - only used when called by the app
                window.__toggleWebPlayerPlayPause = function() {
                    try {
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            var v = videos[i];
                            if (v) {
                                if (!v.paused && !v.ended) {
                                    v.pause();
                                    return "paused";
                                } else {
                                    v.play().catch(function(){});
                                    setTimeout(function() { triggerFullscreen(v); }, 180);
                                    return "playing_fullscreen";
                                }
                            }
                        }
                    } catch(e) {}
                    return "none";
                };

                window.__seekWebPlayer = function(seconds) {
                    try {
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            var v = videos[i];
                            if (v) {
                                v.currentTime = Math.max(0, (v.currentTime || 0) + seconds);
                            }
                        }
                    } catch(e) {}
                };

                // Ensure #np-tempo (00:00 / 00:00) is NEVER focusable and always skipped
                (function() {
                    try {
                        var style = document.createElement('style');
                        style.id = 'skip-tempo-style';
                        style.textContent = [
                            '#np-tempo, .np-tempo { outline: none !important; border: none !important; box-shadow: none !important; pointer-events: none !important; user-select: none !important; -webkit-user-select: none !important; }',
                            '.np-bt:focus, .np-trilho:focus, #np-voltar:focus, #np-baixar:focus { outline: 3px solid #ffb300 !important; outline-offset: 2px !important; }'
                        ].join('\n');
                        if (document.head && !document.getElementById('skip-tempo-style')) {
                            document.head.appendChild(style);
                        }
                    } catch(e) {}
                })();

                function cleanTempoFocus() {
                    try {
                        var t = document.getElementById('np-tempo') || document.querySelector('.np-tempo');
                        if (t) {
                            t.removeAttribute('tabindex');
                            t.setAttribute('tabindex', '-1');
                            if (document.activeElement === t) {
                                var next = document.getElementById('np-trilho') || document.getElementById('np-play');
                                if (next) next.focus();
                            }
                        }
                    } catch(e) {}
                }
                setInterval(cleanTempoFocus, 400);

                // Precise TV Remote / Keyboard navigation for Web Player:
                // Skips 00:00 / 00:00 and connects every button cleanly
                document.addEventListener('keydown', function(e) {
                    try {
                        var cur = document.activeElement;
                        if (!cur) return;
                        var key = e.key;
                        var code = e.keyCode;

                        var isRight = (key === 'ArrowRight' || code === 39);
                        var isLeft = (key === 'ArrowLeft' || code === 37);
                        var isUp = (key === 'ArrowUp' || code === 38);
                        var isDown = (key === 'ArrowDown' || code === 40);

                        // If user is on np-play and presses Right -> skip np-tempo, jump straight to np-trilho (slider)!
                        if (cur.id === 'np-play' && isRight) {
                            var trilho = document.getElementById('np-trilho');
                            if (trilho) {
                                e.preventDefault();
                                e.stopPropagation();
                                trilho.focus();
                                return;
                            }
                        }

                        // If on np-trilho and presses Left -> jump back to np-play (skipping np-tempo)!
                        if (cur.id === 'np-trilho' && isLeft) {
                            var playBtn = document.getElementById('np-play');
                            if (playBtn) {
                                e.preventDefault();
                                e.stopPropagation();
                                playBtn.focus();
                                return;
                            }
                        }

                        // If on np-trilho and presses Right -> jump to np-mudo (volume)!
                        if (cur.id === 'np-trilho' && isRight) {
                            var mudoBtn = document.getElementById('np-mudo');
                            if (mudoBtn) {
                                e.preventDefault();
                                e.stopPropagation();
                                mudoBtn.focus();
                                return;
                            }
                        }

                        // If on np-mudo and presses Left -> jump to np-trilho!
                        if (cur.id === 'np-mudo' && isLeft) {
                            var trilho2 = document.getElementById('np-trilho');
                            if (trilho2) {
                                e.preventDefault();
                                e.stopPropagation();
                                trilho2.focus();
                                return;
                            }
                        }

                        // If on np-mudo and presses Right -> jump to np-cheia (fullscreen)!
                        if (cur.id === 'np-mudo' && isRight) {
                            var cheiaBtn = document.getElementById('np-cheia');
                            if (cheiaBtn) {
                                e.preventDefault();
                                e.stopPropagation();
                                cheiaBtn.focus();
                                return;
                            }
                        }

                        // If on np-cheia and presses Left -> jump to np-mudo!
                        if (cur.id === 'np-cheia' && isLeft) {
                            var mudoBtn2 = document.getElementById('np-mudo');
                            if (mudoBtn2) {
                                e.preventDefault();
                                e.stopPropagation();
                                mudoBtn2.focus();
                                return;
                            }
                        }

                        // If on any bottom control and presses Up -> jump to np-voltar
                        if ((cur.id === 'np-play' || cur.id === 'np-trilho' || cur.id === 'np-mudo' || cur.id === 'np-cheia') && isUp) {
                            var voltarBtn = document.getElementById('np-voltar');
                            if (voltarBtn) {
                                e.preventDefault();
                                e.stopPropagation();
                                voltarBtn.focus();
                                return;
                            }
                        }

                        // If on np-voltar and presses Right -> jump to np-baixar
                        if (cur.id === 'np-voltar' && isRight) {
                            var baixarBtn = document.getElementById('np-baixar');
                            if (baixarBtn) {
                                e.preventDefault();
                                e.stopPropagation();
                                baixarBtn.focus();
                                return;
                            }
                        }

                        // If on np-baixar and presses Left -> jump to np-voltar
                        if (cur.id === 'np-baixar' && isLeft) {
                            var voltarBtn2 = document.getElementById('np-voltar');
                            if (voltarBtn2) {
                                e.preventDefault();
                                e.stopPropagation();
                                voltarBtn2.focus();
                                return;
                            }
                        }

                        // If on np-voltar or np-baixar and presses Down -> jump to np-play
                        if ((cur.id === 'np-voltar' || cur.id === 'np-baixar') && isDown) {
                            var playBtn2 = document.getElementById('np-play');
                            if (playBtn2) {
                                e.preventDefault();
                                e.stopPropagation();
                                playBtn2.focus();
                                return;
                            }
                        }

                        // If focus ever lands on np-tempo, immediately skip to np-trilho
                        if (cur.id === 'np-tempo' || (cur.classList && cur.classList.contains('np-tempo'))) {
                            var nextTarget = document.getElementById('np-trilho') || document.getElementById('np-play');
                            if (nextTarget) {
                                e.preventDefault();
                                nextTarget.focus();
                            }
                        }
                    } catch(err) {}
                }, true);

                // Block external links, ads, and popups (e.g. Shopee, market://, intent://)
                function isAllowedUrl(u) {
                    if (!u || typeof u !== 'string') return true;
                    var lower = u.toLowerCase();
                    if (lower.indexOf('youtube.com') !== -1 ||
                        lower.indexOf('youtu.be') !== -1 ||
                        lower.indexOf('googlevideo.com') !== -1 ||
                        lower.indexOf('ytimg.com') !== -1) {
                        return true;
                    }
                    if (lower.indexOf('shopee') !== -1 ||
                        lower.indexOf('market://') === 0 ||
                        lower.indexOf('intent://') === 0 ||
                        lower.indexOf('whatsapp://') === 0 ||
                        lower.indexOf('telegram://') === 0 ||
                        lower.indexOf('aliexpress') !== -1 ||
                        lower.indexOf('mercadolivre') !== -1 ||
                        lower.indexOf('doubleclick') !== -1 ||
                        lower.indexOf('adsterra') !== -1 ||
                        lower.indexOf('bet365') !== -1) {
                        return false;
                    }
                    return true;
                }

                document.addEventListener('click', function(e) {
                    var a = e.target.closest('a');
                    if (a) {
                        var href = a.getAttribute('href') || a.href || '';
                        if (!isAllowedUrl(href)) {
                            e.preventDefault();
                            e.stopPropagation();
                            return false;
                        }
                        if (a.target === '_blank') {
                            a.target = '_self';
                        }
                    }
                }, true);

                if (window.__filmemeu_sniffer_active) return;
                window.__filmemeu_sniffer_active = true;

                window.__isUserInCatalog = false;
                window.__isUserInMovie = false;
                window.__currentMovieTitle = null;

                function isGenericTitle(str) {
                    if (!str || typeof str !== 'string') return true;
                    var s = str.trim().toLowerCase();
                    return s === '' ||
                           s === 'baixar vídeo drama' ||
                           s === 'baixar video drama' ||
                           s.indexOf('baixar vídeo') === 0 ||
                           s.indexOf('baixar video') === 0 ||
                           s === 'filmemeu' ||
                           s === 'untitled' ||
                           s === 'filme detectado' ||
                           s === 'reproduzindo' ||
                           s === 'site aberto' ||
                           s.length < 2;
                }

                function getBestMovieTitle(candidate) {
                    if (window.__currentMovieTitle && !isGenericTitle(window.__currentMovieTitle)) {
                        return window.__currentMovieTitle;
                    }
                    var dt = document.getElementById('detalhe-titulo');
                    if (dt && dt.textContent && !isGenericTitle(dt.textContent)) {
                        return dt.textContent.trim();
                    }
                    var npt = document.getElementById('np-titulo');
                    if (npt && npt.textContent && !isGenericTitle(npt.textContent)) {
                        return npt.textContent.trim();
                    }
                    var prev = document.getElementById('previa-titulo');
                    if (prev && prev.textContent && !isGenericTitle(prev.textContent)) {
                        return prev.textContent.trim();
                    }
                    if (candidate && !isGenericTitle(candidate)) {
                        return candidate.trim();
                    }
                    return "";
                }

                function checkCatalogAndMovieState() {
                    try {
                        var catalog = document.getElementById('catalog');
                        var siteFrame = document.getElementById('site-frame');
                        var nossoPlayer = document.getElementById('nosso-player');
                        var detalhe = document.getElementById('detalhe');
                        var htmlNoTitulo = document.documentElement.classList.contains('no-titulo');

                        var catalogVisible = catalog && !catalog.classList.contains('hidden');
                        var frameVisible = siteFrame && !siteFrame.classList.contains('hidden');
                        var npVisible = nossoPlayer && !nossoPlayer.classList.contains('hidden');
                        var detalheVisible = detalhe && !detalhe.classList.contains('hidden');

                        if (detalhe && detalhe.querySelector('#detalhe-titulo')) {
                            var dtText = detalhe.querySelector('#detalhe-titulo').textContent;
                            if (dtText && !isGenericTitle(dtText)) {
                                window.__currentMovieTitle = dtText.trim();
                            }
                        }
                        if (nossoPlayer && nossoPlayer.querySelector('#np-titulo')) {
                            var nptText = nossoPlayer.querySelector('#np-titulo').textContent;
                            if (nptText && !isGenericTitle(nptText)) {
                                window.__currentMovieTitle = nptText.trim();
                            }
                        }

                        // detalheVisible is true when viewing the movie synopsis, poster, and trailer
                        var inMovie = frameVisible || npVisible || detalheVisible;
                        var inCatalog = catalogVisible && !frameVisible && !npVisible && !detalheVisible && !htmlNoTitulo;

                        if (inCatalog) {
                            if (window.__isUserInMovie || !window.__isUserInCatalog) {
                                window.__isUserInMovie = false;
                                window.__isUserInCatalog = true;
                                window.__currentMovieTitle = null;
                                if (window.AndroidBridge && window.AndroidBridge.onNavigatedToCatalog) {
                                    window.AndroidBridge.onNavigatedToCatalog();
                                }
                            }
                        } else if (inMovie) {
                            if (!window.__isUserInMovie) {
                                window.__isUserInMovie = true;
                                window.__isUserInCatalog = false;
                                var title = getBestMovieTitle();
                                if (window.AndroidBridge && window.AndroidBridge.onMovieStateChanged) {
                                    window.AndroidBridge.onMovieStateChanged(true, title);
                                }
                            }
                        }
                    } catch(e) {}
                }
                window.__checkCatalogState = checkCatalogAndMovieState;
                setInterval(checkCatalogAndMovieState, 400);

                window.__returnToMovieAudioSelection = function() {
                    try {
                        var np = document.getElementById('nosso-player');
                        if (np) {
                            np.classList.add('hidden');
                            var v = np.querySelector('video');
                            if (v) { v.pause(); v.src = ''; }
                        }
                        var sf = document.getElementById('site-frame');
                        if (sf) {
                            sf.classList.add('hidden');
                            var ifr = sf.querySelector('iframe');
                            if (ifr) ifr.src = 'about:blank';
                        }
                        var dt = document.getElementById('detalhe');
                        if (dt) {
                            dt.classList.remove('hidden');
                        }
                        var cat = document.getElementById('catalog');
                        if (cat) {
                            cat.classList.add('hidden');
                        }
                        document.documentElement.classList.add('no-titulo');

                        window.__isUserInMovie = true;
                        window.__isUserInCatalog = false;

                        var dtTitle = document.getElementById('detalhe-titulo');
                        var title = (dtTitle && dtTitle.textContent) ? dtTitle.textContent.trim() : window.__currentMovieTitle;
                        if (window.AndroidBridge && window.AndroidBridge.onMovieStateChanged) {
                            window.AndroidBridge.onMovieStateChanged(true, title);
                        }
                    } catch(e) {}
                };

                window.__goToCatalog = function() {
                    try {
                        if (typeof voltarParaCatalogo === 'function') {
                            try { voltarParaCatalogo(false); } catch(e) {}
                        }
                        var btns = [
                            document.getElementById('btn-catalog'),
                            document.getElementById('detalhe-voltar'),
                            document.querySelector('.btn-voltar')
                        ];
                        btns.forEach(function(b) {
                            if (b) { try { b.click(); } catch(e) {} }
                        });

                        var sf = document.getElementById('site-frame');
                        if (sf) {
                            sf.classList.add('hidden');
                            var ifr = sf.querySelector('iframe');
                            if (ifr) ifr.src = 'about:blank';
                        }
                        var np = document.getElementById('nosso-player');
                        if (np) {
                            np.classList.add('hidden');
                            var v = np.querySelector('video');
                            if (v) { v.pause(); v.src = ''; }
                        }
                        var dt = document.getElementById('detalhe');
                        if (dt) dt.classList.add('hidden');

                        var cat = document.getElementById('catalog');
                        if (cat) cat.classList.remove('hidden');

                        document.documentElement.classList.remove('no-titulo');

                        window.__isUserInMovie = false;
                        window.__isUserInCatalog = true;
                        window.__currentMovieTitle = null;

                        if (window.AndroidBridge && window.AndroidBridge.onNavigatedToCatalog) {
                            window.AndroidBridge.onNavigatedToCatalog();
                        }
                    } catch(e) {}
                };

                document.addEventListener('click', function(e) {
                    try {
                        var toCatalog = e.target.closest('#btn-catalog, #detalhe-voltar');
                        if (toCatalog) {
                            window.__isUserInMovie = false;
                            window.__isUserInCatalog = true;
                            window.__currentMovieTitle = null;
                            if (window.AndroidBridge && window.AndroidBridge.onNavigatedToCatalog) {
                                window.AndroidBridge.onNavigatedToCatalog();
                            }
                            return;
                        }

                        var npVoltar = e.target.closest('#np-voltar');
                        if (npVoltar) {
                            // Returning from web player to movie audio selection (Image 2)
                            window.__isUserInMovie = true;
                            window.__isUserInCatalog = false;
                            var dtTitle = document.getElementById('detalhe-titulo');
                            var title = (dtTitle && dtTitle.textContent) ? dtTitle.textContent.trim() : window.__currentMovieTitle;
                            if (window.AndroidBridge && window.AndroidBridge.onMovieStateChanged) {
                                window.AndroidBridge.onMovieStateChanged(true, title);
                            }
                            return;
                        }

                        var card = e.target.closest('.cat-card, .poster');
                        if (card) {
                            var titleEl = card.querySelector('.cat-title, .poster-title, h3, h4');
                            var t = titleEl ? titleEl.textContent : (card.getAttribute('title') || '');
                            if (t && !isGenericTitle(t)) {
                                var cleanT = t.trim();
                                window.__currentMovieTitle = cleanT;
                                if (window.AndroidBridge && window.AndroidBridge.onMovieSelected) {
                                    window.AndroidBridge.onMovieSelected(cleanT);
                                }
                            }
                            return;
                        }

                        var btnAssistir = e.target.closest('#detalhe-assistir');
                        if (btnAssistir) {
                            var dt = document.getElementById('detalhe-titulo');
                            var t2 = (dt && dt.textContent ? dt.textContent.trim() : '') || window.__currentMovieTitle;
                            if (t2 && !isGenericTitle(t2)) {
                                window.__currentMovieTitle = t2;
                                window.__isUserInMovie = true;
                                window.__isUserInCatalog = false;
                                if (window.AndroidBridge && window.AndroidBridge.onMovieStateChanged) {
                                    window.AndroidBridge.onMovieStateChanged(true, t2);
                                }
                            }
                        }
                    } catch(err) {}
                }, true);

                window.addEventListener('popstate', function() {
                    setTimeout(checkCatalogAndMovieState, 150);
                });
                window.addEventListener('hashchange', function() {
                    setTimeout(checkCatalogAndMovieState, 150);
                });

                function notifyNative(url, title, durationSec) {
                    if (!url || typeof url !== 'string') return;
                    try {
                        var lower = url.toLowerCase();
                        if (lower.indexOf('youtube.com') !== -1 || lower.indexOf('googlevideo.com') !== -1) return;
                        if (lower.indexOf('.ts') !== -1 && lower.indexOf('.m3u8') === -1) return;
                        
                        var catalog = document.getElementById('catalog');
                        var siteFrame = document.getElementById('site-frame');
                        var nossoPlayer = document.getElementById('nosso-player');
                        var isCatalog = catalog && !catalog.classList.contains('hidden') &&
                                        (!siteFrame || siteFrame.classList.contains('hidden')) &&
                                        (!nossoPlayer || nossoPlayer.classList.contains('hidden'));
                        if (isCatalog) {
                            return;
                        }
                        
                        var movieTitle = getBestMovieTitle(title);
                        if (isGenericTitle(movieTitle)) {
                            movieTitle = "";
                        }
                        
                        if (window.AndroidBridge && window.AndroidBridge.onVideoFound) {
                            window.AndroidBridge.onVideoFound(url, movieTitle, durationSec || 0);
                        }
                    } catch(e) {}
                }

                // Hook HTMLVideoElement
                function inspectVideo(video) {
                    if (!video) return;
                    try {
                        var src = video.currentSrc || video.src;
                        if (src && src.indexOf('blob:') !== 0) {
                            var dur = Math.round(video.duration || 0);
                            notifyNative(src, document.title, dur);
                        }
                        
                        var sources = video.getElementsByTagName('source');
                        for (var i = 0; i < sources.length; i++) {
                            var sSrc = sources[i].src;
                            if (sSrc && sSrc.indexOf('blob:') !== 0) {
                                notifyNative(sSrc, document.title, Math.round(video.duration || 0));
                            }
                        }

                        video.addEventListener('loadedmetadata', function() {
                            try {
                                var s = video.currentSrc || video.src;
                                var d = Math.round(video.duration || 0);
                                notifyNative(s, document.title, d);
                            } catch(e) {}
                        });
                        video.addEventListener('durationchange', function() {
                            try {
                                var s = video.currentSrc || video.src;
                                var d = Math.round(video.duration || 0);
                                notifyNative(s, document.title, d);
                            } catch(e) {}
                        });
                    } catch(e) {}
                }

                function scanAllVideos() {
                    try {
                        var videos = document.querySelectorAll('video');
                        videos.forEach(inspectVideo);

                        var iframes = document.querySelectorAll('iframe');
                        iframes.forEach(function(ifr) {
                            try {
                                var src = ifr.src || '';
                                if (src.indexOf('.m3u8') !== -1 || src.indexOf('.mp4') !== -1) {
                                    notifyNative(src, document.title, 0);
                                }
                            } catch(e) {}
                        });
                    } catch(e) {}
                }

                // Mutation observer for dynamically added videos
                if (window.MutationObserver) {
                    var observer = new MutationObserver(function(mutations) {
                        try {
                            mutations.forEach(function(mutation) {
                                mutation.addedNodes.forEach(function(node) {
                                    if (node.nodeType === 1) {
                                        if (node.tagName === 'VIDEO') {
                                            inspectVideo(node);
                                        } else if (node.querySelectorAll) {
                                            var innerVideos = node.querySelectorAll('video');
                                            innerVideos.forEach(inspectVideo);
                                        }
                                    }
                                });
                            });
                        } catch(e) {}
                    });

                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true });
                    } else {
                        document.addEventListener('DOMContentLoaded', function() {
                            if (document.body) {
                                observer.observe(document.body, { childList: true, subtree: true });
                            }
                        });
                    }
                }

                // Intercept XMLHttpRequest
                var origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    try {
                        if (url && typeof url === 'string') {
                            var lower = url.toLowerCase();
                            if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('.mp4') !== -1) {
                                notifyNative(url, document.title, 0);
                            }
                        }
                    } catch(e) {}
                    return origOpen.apply(this, arguments);
                };

                // Intercept fetch
                var origFetch = window.fetch;
                if (origFetch) {
                    window.fetch = function() {
                        try {
                            var arg = arguments[0];
                            var url = typeof arg === 'string' ? arg : (arg && arg.url ? arg.url : '');
                            if (url) {
                                var lower = url.toLowerCase();
                                if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('.mp4') !== -1) {
                                    notifyNative(url, document.title, 0);
                                }
                            }
                        } catch(e) {}
                        return origFetch.apply(this, arguments);
                    };
                }

                // Safe window.open wrapper that prevents breaking player scripts
                var origWindowOpen = window.open;
                window.open = function(url, target, features) {
                    try {
                        if (url && typeof url === 'string') {
                            var lower = url.toLowerCase();
                            if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('.mp4') !== -1) {
                                notifyNative(url, document.title, 0);
                            }
                        }
                    } catch(e) {}
                    return {
                        closed: false,
                        close: function(){},
                        focus: function(){},
                        postMessage: function(){},
                        document: { write: function(){} }
                    };
                };

                scanAllVideos();
                setInterval(scanAllVideos, 2500);
                window.__scanVideos = scanAllVideos;
            } catch(globalErr) {}
        })();
        """.trimIndent()
    }
}

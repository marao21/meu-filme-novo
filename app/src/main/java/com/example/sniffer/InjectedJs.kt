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

                // Helper to find the center play button
                function findCenterPlayButton() {
                    var targets = document.querySelectorAll(
                        'button, [role="button"], .vjs-big-play-button, [class*="play" i], [aria-label*="play" i], svg'
                    );
                    var cx = window.innerWidth / 2;
                    var cy = window.innerHeight / 2;
                    var best = null;
                    var minDist = 999999;
                    for (var j = 0; j < targets.length; j++) {
                        var target = targets[j];
                        var rect = target.getBoundingClientRect();
                        if (rect.width >= 35 && rect.width <= 220 && rect.height >= 35 && rect.height <= 220) {
                            var dist = Math.hypot(rect.left + rect.width / 2 - cx, rect.top + rect.height / 2 - cy);
                            if (dist < minDist && dist < 260) {
                                minDist = dist;
                                best = target;
                            }
                        }
                    }
                    return best;
                }

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

                // Focus center play button on movie player screen
                function focusCenterPlayButton() {
                    try {
                        var btn = findCenterPlayButton();
                        if (btn) {
                            btn.setAttribute('tabindex', '0');
                            btn.focus();
                            btn.style.outline = '4px solid #F59E0B';
                            btn.style.boxShadow = '0 0 25px rgba(245, 158, 11, 0.9)';
                            btn.style.borderRadius = '50%';
                        }
                    } catch(e) {}
                }

                // Web Player Remote & Keyboard Controller
                window.__toggleWebPlayerPlayPause = function() {
                    try {
                        var videos = document.querySelectorAll('video');
                        for (var i = 0; i < videos.length; i++) {
                            var v = videos[i];
                            if (v) {
                                if (!v.paused && !v.ended) {
                                    // VIDEO IS RUNNING -> PAUSE IT
                                    v.pause();
                                    return "paused";
                                } else {
                                    // VIDEO IS PAUSED -> PLAY AND LAUNCH FULLSCREEN
                                    v.play().catch(function(){});
                                    setTimeout(function() { triggerFullscreen(v); }, 180);
                                    return "playing_fullscreen";
                                }
                            }
                        }

                        // Video hasn't started yet -> click center play button and go fullscreen!
                        var centerBtn = findCenterPlayButton();
                        if (centerBtn) {
                            centerBtn.click();
                            setTimeout(function() {
                                var v = document.querySelector('video');
                                triggerFullscreen(v);
                            }, 250);
                            return "clicked_center_fullscreen";
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

                // Add keyboard shortcuts to page for wireless keyboards & TV Box remotes
                if (!window.__filmemeu_keyboard_hooked) {
                    window.__filmemeu_keyboard_hooked = true;
                    window.addEventListener('keydown', function(e) {
                        var tag = (e.target && e.target.tagName || '').toLowerCase();
                        if (tag === 'input' || tag === 'textarea') return;

                        // Space or Enter (code 13) or OK (code 13 or 32): Toggle Play/Pause and Fullscreen
                        if (e.keyCode === 32 || e.code === 'Space' || e.keyCode === 13 || e.key === 'Enter' || e.keyCode === 179) {
                            e.preventDefault();
                            window.__toggleWebPlayerPlayPause();
                        } else if (e.keyCode === 37) { // Left arrow: Seek -10s
                            e.preventDefault();
                            window.__seekWebPlayer(-10);
                        } else if (e.keyCode === 39) { // Right arrow: Seek +10s
                            e.preventDefault();
                            window.__seekWebPlayer(10);
                        }
                    }, true);
                }

                // Automatically focus the center play button when arriving at the player screen
                setInterval(function() {
                    try {
                        var videos = document.querySelectorAll('video');
                        var isAnyPlaying = false;
                        for (var i = 0; i < videos.length; i++) {
                            if (!videos[i].paused && !videos[i].ended) {
                                isAnyPlaying = true;
                                break;
                            }
                        }
                        if (!isAnyPlaying) {
                            focusCenterPlayButton();
                        }
                    } catch(e) {}
                }, 1000);

                if (window.__filmemeu_sniffer_active) return;
                window.__filmemeu_sniffer_active = true;

                function notifyNative(url, title, durationSec) {
                    if (!url || typeof url !== 'string') return;
                    try {
                        var lower = url.toLowerCase();
                        if (lower.indexOf('youtube.com') !== -1 || lower.indexOf('googlevideo.com') !== -1) return;
                        if (lower.indexOf('.ts') !== -1 && lower.indexOf('.m3u8') === -1) return;
                        
                        var docTitle = title || document.title || "Filme Detectado";
                        docTitle = docTitle.replace(/[\/\\:*?"<>|]/g, "").trim();
                        
                        if (window.AndroidBridge && window.AndroidBridge.onVideoFound) {
                            window.AndroidBridge.onVideoFound(url, docTitle, durationSec || 0);
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
                                            focusCenterPlayButton();
                                        } else if (node.querySelectorAll) {
                                            var innerVideos = node.querySelectorAll('video');
                                            innerVideos.forEach(inspectVideo);
                                            if (innerVideos.length > 0) focusCenterPlayButton();
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

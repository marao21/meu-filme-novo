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

                // Auto visitor login & catalog unlock so #login-page never blocks the user on first launch,
                // BUT if the user explicitly clicked "Sair", respect their wish so they can log in or switch accounts!
                // CRITICAL: If the user already has an active account session, respect it and never force visitante mode!
                function autoUnlockCatalog() {
                    try {
                        if (window.__userLoggedOut || sessionStorage.getItem('bvd:logged_out') === '1') {
                            return;
                        }
                        var user = (window.bvdAuth && window.bvdAuth.usuarioAtual && window.bvdAuth.usuarioAtual());
                        if (user) {
                            try { localStorage.removeItem('bvd:visitante'); } catch(e) {}
                            return;
                        }
                        localStorage.setItem('bvd:visitante', '1');
                        localStorage.removeItem('bvd:place');
                        var lp = document.getElementById('login-page');
                        if (lp && !lp.classList.contains('hidden')) {
                            var bv = document.getElementById('btn-visitante');
                            if (bv) {
                                bv.click();
                            } else {
                                lp.classList.add('hidden');
                                document.documentElement.classList.remove('sem-conta', 'conferindo-conta');
                                var cat = document.getElementById('catalog');
                                if (cat) cat.classList.remove('hidden');
                            }
                        }
                    } catch(e) {}
                }
                autoUnlockCatalog();
                setTimeout(autoUnlockCatalog, 200);
                setTimeout(autoUnlockCatalog, 600);
                setTimeout(autoUnlockCatalog, 1500);

                // Update management:
                // 1. Intercept and hook APK download buttons (#btn-apk, #apk-faixa-baixar, .apk links)
                // 2. If app is updated, suppress #apk-faixa permanently so it never keeps showing up!
                function gerenciarAtualizacaoApk() {
                    try {
                        var isUpdated = true;
                        try {
                            if (window.AndroidBridge && typeof window.AndroidBridge.isAppUpdated === 'function') {
                                isUpdated = window.AndroidBridge.isAppUpdated();
                            }
                        } catch(e) {}

                        var isBlocked = false;
                        try {
                            if (window.AndroidBridge && typeof window.AndroidBridge.isMandatoryBlocked === 'function') {
                                isBlocked = window.AndroidBridge.isMandatoryBlocked();
                            }
                        } catch(e) {}

                        if (isBlocked) {
                            try {
                                var media = document.querySelectorAll('video, audio');
                                for (var m = 0; m < media.length; m++) {
                                    media[m].pause();
                                    media[m].src = '';
                                }
                            } catch(e) {}
                        }

                        function esconderFaixa() {
                            var faixa = document.getElementById('apk-faixa');
                            if (faixa) {
                                faixa.classList.add('hidden');
                                faixa.style.setProperty('display', 'none', 'important');
                            }
                        }

                        if (isUpdated) {
                            esconderFaixa();
                            try {
                                var guardado = JSON.parse(localStorage.getItem('bvd:apk-release') || 'null');
                                var marca = (guardado && guardado.marca) || '2026-11-04T21:14:21Z';
                                localStorage.setItem('bvd:apk-visto', marca);
                            } catch(e) {}
                        }

                        function hookBotoesApk() {
                            var alvos = document.querySelectorAll('#btn-apk, #apk-faixa-baixar, a[href*=".apk"], a[href*="meu-filme-novo-debug.apk"]');
                            for (var i = 0; i < alvos.length; i++) {
                                var el = alvos[i];
                                if (el.dataset.apkIntercepted) continue;
                                el.dataset.apkIntercepted = '1';
                                el.addEventListener('click', function(ev) {
                                    ev.preventDefault();
                                    ev.stopPropagation();
                                    var link = this.getAttribute('href') || 'https://github.com/marao21/meu-filme-novo/releases/download/apk-latest/meu-filme-novo-debug.apk';
                                    if (window.AndroidBridge && window.AndroidBridge.baixarAtualizacao) {
                                        window.AndroidBridge.baixarAtualizacao(link);
                                    }
                                }, true);
                            }
                        }

                        hookBotoesApk();
                        setTimeout(hookBotoesApk, 500);
                        setTimeout(hookBotoesApk, 1500);
                        setTimeout(hookBotoesApk, 3000);

                        if (isUpdated && window.MutationObserver) {
                            var obs = new MutationObserver(function() {
                                var f = document.getElementById('apk-faixa');
                                if (f && (!f.classList.contains('hidden') || f.style.display !== 'none')) {
                                    f.classList.add('hidden');
                                    f.style.setProperty('display', 'none', 'important');
                                }
                            });
                            obs.observe(document.body || document.documentElement, { childList: true, subtree: true, attributes: true });
                        }
                    } catch(e) {}
                }

                gerenciarAtualizacaoApk();
                setTimeout(gerenciarAtualizacaoApk, 300);
                setTimeout(gerenciarAtualizacaoApk, 1000);
                setTimeout(gerenciarAtualizacaoApk, 2500);

                // Listen for manual sign out / trocar login clicks
                document.addEventListener('click', function(e) {
                    var btnConta = e.target.closest('#btn-conta, .menu-sair');
                    if (btnConta) {
                        window.__userLoggedOut = true;
                        try { sessionStorage.setItem('bvd:logged_out', '1'); } catch(err) {}
                        try { localStorage.removeItem('bvd:visitante'); } catch(err) {}
                    }
                    var btnVis = e.target.closest('#btn-visitante');
                    if (btnVis) {
                        window.__userLoggedOut = false;
                        try { sessionStorage.removeItem('bvd:logged_out'); } catch(err) {}
                    }
                    var formAuth = e.target.closest('#form-auth, #form-login');
                    if (formAuth) {
                        window.__userLoggedOut = false;
                        try { sessionStorage.removeItem('bvd:logged_out'); } catch(err) {}
                    }
                }, true);

                // TV Box Menu Controller - smoothly opens/closes without double-click or accidental close
                window.__menuOpenedAt = 0;
                window.__toggleMenu = function() {
                    try {
                        var menuBtn = document.getElementById('btn-menu');
                        var painel = document.getElementById('menu-painel');
                        if (!menuBtn || !painel) return;
                        var willOpen = painel.classList.contains('hidden');
                        if (willOpen) {
                            window.__menuOpenedAt = Date.now();
                            painel.classList.remove('hidden');
                            menuBtn.setAttribute('aria-expanded', 'true');
                            // Delay focusing the first item so the remote's OK press doesn't accidentally trigger it!
                            setTimeout(function() {
                                if (painel && !painel.classList.contains('hidden')) {
                                    var firstItem = painel.querySelector('.menu-item');
                                    if (firstItem) {
                                        firstItem.focus();
                                    }
                                }
                            }, 250);
                        } else {
                            painel.classList.add('hidden');
                            menuBtn.setAttribute('aria-expanded', 'false');
                            menuBtn.focus();
                        }
                    } catch(e) {}
                };

                // Prevent accidental carryover clicks on menu items when pressing OK on #btn-menu
                document.addEventListener('click', function(e) {
                    var btnMenu = e.target.closest('#btn-menu, .menu-btn');
                    if (btnMenu) {
                        window.__menuOpenedAt = Date.now();
                    }
                    var menuItem = e.target.closest('.menu-item');
                    if (menuItem) {
                        var elapsed = Date.now() - (window.__menuOpenedAt || 0);
                        if (elapsed < 450) {
                            e.preventDefault();
                            e.stopPropagation();
                            e.stopImmediatePropagation();
                            return false;
                        }
                    }
                }, true);

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

                // TV Remote & Keyboard Controller - OK and Navigation for TV Box
                window.__handleTvRemoteOk = function() {
                    try {
                        var cur = document.activeElement;
                        var painel = document.getElementById('menu-painel');
                        var isMenuOpen = painel && !painel.classList.contains('hidden');

                        // If on menu button, native click will handle it - do NOT duplicate toggle!
                        if (cur && (cur.id === 'btn-menu' || cur.classList.contains('menu-btn'))) {
                            return "btn_menu_native";
                        }

                        // If inside open menu panel, guard against accidental clicks during cooldown
                        if (isMenuOpen && cur && cur.closest('#menu-painel')) {
                            if (Date.now() - (window.__menuOpenedAt || 0) < 450) {
                                return "menu_item_cooldown";
                            }
                            // Native key will activate it
                            return "menu_item_native";
                        }

                        var detalhe = document.getElementById('detalhe');
                        var isDetalheOpen = detalhe && !detalhe.classList.contains('hidden') && detalhe.style.display !== 'none';
                        if (isDetalheOpen) {
                            var btnAssistir = document.getElementById('detalhe-assistir') ||
                                document.querySelector('#detalhe-assistir, .detalhe-assistir, #btn-play-filme');
                            if (btnAssistir && (cur === btnAssistir || !cur || cur === document.body)) {
                                btnAssistir.click();
                                return "assistir_clicked";
                            }
                        }
                        var npPlay = document.getElementById('np-play');
                        if (npPlay && (cur === npPlay || cur.id === 'np-trilho')) {
                            npPlay.click();
                            return "np_play_clicked";
                        }
                    } catch(e) {}
                    return "none";
                };

                // Apply system theme colors and skip tempo focus
                (function() {
                    try {
                        var style = document.createElement('style');
                        style.id = 'skip-tempo-style';
                        style.textContent = [
                            ':root { --bg: #0f172a; --bg-soft: #141f36; --panel: #18233c; --line: #1e293b; --line-soft: #243150; --text: #f1f5f9; --muted: #94a3b8; --accent: #38bdf8; --accent-strong: #0ea5e9; --hls: #a78bfa; --mp4: #34d399; --warn: #fbbf24; --danger: #f87171; }',
                            '#np-tempo, .np-tempo { outline: none !important; border: none !important; box-shadow: none !important; pointer-events: none !important; user-select: none !important; -webkit-user-select: none !important; }',
                            ':focus, #btn-menu:focus, .menu-item:focus, #detalhe-assistir:focus, .btn-assistir:focus, .np-bt:focus, .np-trilho:focus, #np-voltar:focus, #np-baixar:focus, [tabindex="0"]:focus, .card:focus, .filme-card:focus { outline: 3px solid #38bdf8 !important; outline-offset: 2px !important; }',
                            '.menu-painel { background-color: #18233c !important; border: 1px solid #243150 !important; border-radius: 8px !important; box-shadow: 0 10px 25px rgba(5, 9, 18, 0.85) !important; padding: 6px !important; z-index: 99999 !important; }',
                            '.menu-item { color: #f1f5f9 !important; border-radius: 6px !important; }',
                            '.menu-item:focus, .menu-item:hover { background-color: #141f36 !important; color: #38bdf8 !important; }',
                            '#detalhe-assistir, .btn-assistir { background-color: #0ea5e9 !important; color: #0f172a !important; font-weight: bold !important; }',
                            '#nosso-player, .nosso-player { background: #0f172a !important; }',
                            '.np-controles { background: linear-gradient(to top, rgba(5, 9, 18, 0.95), rgba(5, 9, 18, 0.6), transparent) !important; }'
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
                        // Ensure #detalhe-assistir is focusable on TV Box
                        var dt = document.getElementById('detalhe');
                        if (dt && !dt.classList.contains('hidden')) {
                            var btn = document.getElementById('detalhe-assistir');
                            if (btn && !btn.hasAttribute('tabindex')) {
                                btn.setAttribute('tabindex', '0');
                            }
                            var dtTitle = document.getElementById('detalhe-titulo');
                            if (dtTitle && dtTitle.textContent && dtTitle.textContent.trim() && !isGenericTitle(dtTitle.textContent)) {
                                var dtFundo = document.getElementById('detalhe-fundo');
                                var dtMeta = document.getElementById('detalhe-meta');
                                var dtSin = document.getElementById('detalhe-sinopse');
                                var dtEp = document.getElementById('detalhe-episodio');
                                var rel = document.getElementById('relacionados');
                                window.__savedMovieDetail = {
                                    titulo: dtTitle.textContent.trim(),
                                    meta: dtMeta ? dtMeta.textContent.trim() : '',
                                    sinopse: dtSin ? dtSin.textContent.trim() : '',
                                    fundo: dtFundo ? dtFundo.style.backgroundImage : '',
                                    episodioHtml: dtEp ? dtEp.innerHTML : '',
                                    episodioHidden: dtEp ? dtEp.classList.contains('hidden') : true,
                                    relacionadosHtml: rel ? rel.innerHTML : ''
                                };
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

                        // If menu-painel is open, handle TV remote navigation inside it smoothly
                        var painel = document.getElementById('menu-painel');
                        if (painel && !painel.classList.contains('hidden')) {
                            var menuItems = Array.prototype.slice.call(painel.querySelectorAll('.menu-item'));
                            var currentIdx = menuItems.indexOf(cur);
                            if (isDown && menuItems.length > 0) {
                                e.preventDefault();
                                e.stopPropagation();
                                var nextIdx = currentIdx >= 0 ? (currentIdx + 1) % menuItems.length : 0;
                                menuItems[nextIdx].focus();
                                return;
                            }
                            if (isUp && menuItems.length > 0) {
                                e.preventDefault();
                                e.stopPropagation();
                                var prevIdx = currentIdx > 0 ? (currentIdx - 1) : (menuItems.length - 1);
                                menuItems[prevIdx].focus();
                                return;
                            }
                            if (isLeft || key === 'Escape' || code === 27) {
                                e.preventDefault();
                                e.stopPropagation();
                                window.__toggleMenu();
                                return;
                            }
                        }

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

                        var inMovie = frameVisible || npVisible || detalheVisible;
                        var inCatalog = catalogVisible && !frameVisible && !npVisible && !detalheVisible;

                        // Safety check: if neither catalog nor movie is marked visible but user was in a movie,
                        // prevent black screen void by restoring detalhe!
                        if (!inCatalog && !inMovie && window.__isUserInMovie && window.__savedMovieDetail) {
                            var dtSafe = document.getElementById('detalhe');
                            if (dtSafe) {
                                dtSafe.classList.remove('hidden');
                                document.documentElement.classList.add('no-titulo');
                                detalheVisible = true;
                                inMovie = true;
                            }
                        }

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
                        var sf = document.getElementById('site-frame');
                        var np = document.getElementById('nosso-player');
                        var hadPlayerOpen = (sf && !sf.classList.contains('hidden')) || (np && !np.classList.contains('hidden'));

                        // Let the site's popstate run if player frame was active
                        if (hadPlayerOpen) {
                            try {
                                window.dispatchEvent(new PopStateEvent('popstate'));
                            } catch(e) {}
                        }

                        function applyDetalheRestore() {
                            try {
                                if (np) {
                                    np.classList.add('hidden');
                                    var v = np.querySelector('video');
                                    if (v) { v.pause(); v.src = ''; }
                                }
                                if (sf) {
                                    sf.classList.add('hidden');
                                    var ifr = sf.querySelector('iframe');
                                    if (ifr) {
                                        try { ifr.contentWindow.postMessage('{"event":"command","func":"pauseVideo","args":""}', '*'); } catch(e){}
                                        ifr.src = 'about:blank';
                                    }
                                }

                                var cat = document.getElementById('catalog');
                                if (cat) cat.classList.add('hidden');

                                var dt = document.getElementById('detalhe');
                                if (dt) {
                                    dt.classList.remove('hidden');
                                }
                                document.documentElement.classList.add('no-titulo');

                                if (window.__savedMovieDetail) {
                                    var s = window.__savedMovieDetail;
                                    var dtTitle = document.getElementById('detalhe-titulo');
                                    if (dtTitle && (!dtTitle.textContent || !dtTitle.textContent.trim())) {
                                        dtTitle.textContent = s.titulo || '';
                                    }
                                    var dtMeta = document.getElementById('detalhe-meta');
                                    if (dtMeta && (!dtMeta.textContent || !dtMeta.textContent.trim())) {
                                        dtMeta.textContent = s.meta || '';
                                    }
                                    var dtSin = document.getElementById('detalhe-sinopse');
                                    if (dtSin && (!dtSin.textContent || !dtSin.textContent.trim())) {
                                        dtSin.textContent = s.sinopse || '';
                                    }
                                    var dtFundo = document.getElementById('detalhe-fundo');
                                    if (dtFundo && (!dtFundo.style.backgroundImage || dtFundo.style.backgroundImage === 'none')) {
                                        dtFundo.style.backgroundImage = s.fundo || '';
                                    }
                                    var dtEp = document.getElementById('detalhe-episodio');
                                    if (dtEp && s.episodioHtml && !dtEp.children.length) {
                                        dtEp.innerHTML = s.episodioHtml;
                                        if (s.episodioHidden) dtEp.classList.add('hidden'); else dtEp.classList.remove('hidden');
                                    }
                                    var rel = document.getElementById('relacionados');
                                    if (rel && s.relacionadosHtml && !rel.children.length) {
                                        rel.innerHTML = s.relacionadosHtml;
                                        rel.classList.remove('hidden');
                                    }
                                }

                                var btnAssistir = document.getElementById('detalhe-assistir');
                                if (btnAssistir) {
                                    btnAssistir.focus();
                                }

                                window.__isUserInMovie = true;
                                window.__isUserInCatalog = false;

                                var title = (document.getElementById('detalhe-titulo') || {}).textContent || window.__currentMovieTitle;
                                if (window.AndroidBridge && window.AndroidBridge.onMovieStateChanged) {
                                    window.AndroidBridge.onMovieStateChanged(true, title || '');
                                }
                            } catch(err) {}
                        }

                        applyDetalheRestore();
                        setTimeout(applyDetalheRestore, 50);
                        setTimeout(applyDetalheRestore, 150);
                        setTimeout(applyDetalheRestore, 300);
                        setTimeout(applyDetalheRestore, 600);
                    } catch(e) {}
                };

                window.__goToCatalog = function() {
                    try {
                        localStorage.removeItem('bvd:place');
                        localStorage.setItem('bvd:visitante', '1');

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
                        if (dt) {
                            dt.classList.add('hidden');
                            var tr = document.getElementById('detalhe-trailer');
                            if (tr) tr.textContent = '';
                        }
                        var rel = document.getElementById('relacionados');
                        if (rel) {
                            rel.classList.add('hidden');
                            rel.textContent = '';
                        }
                        var prev = document.getElementById('previa');
                        if (prev) prev.classList.add('hidden');

                        var lp = document.getElementById('login-page');
                        if (lp) lp.classList.add('hidden');

                        document.documentElement.classList.remove('no-titulo');
                        document.documentElement.classList.remove('sem-conta');
                        document.documentElement.classList.remove('conferindo-conta');
                        document.documentElement.classList.add('pagina-livre');

                        var cat = document.getElementById('catalog');
                        if (cat) cat.classList.remove('hidden');

                        var fil = document.getElementById('fileiras');
                        if (fil) fil.classList.remove('hidden');

                        var cg = document.getElementById('catalog-grid');
                        if (cg) cg.classList.remove('hidden');

                        var catKind = document.querySelector('input[name="cat-kind"]:checked') || document.querySelector('input[name="cat-kind"]');
                        if (catKind) {
                            try { catKind.dispatchEvent(new Event('change', { bubbles: true })); } catch(e) {}
                        }

                        window.__isUserInMovie = false;
                        window.__isUserInCatalog = true;
                        window.__isUserInServerFrame = false;
                        window.__currentMovieTitle = null;

                        if (window.AndroidBridge && window.AndroidBridge.onNavigatedToCatalog) {
                            window.AndroidBridge.onNavigatedToCatalog();
                        }

                        window.scrollTo({ top: 0, behavior: 'auto' });
                    } catch(e) {}
                };

                window.__handleBackNavigation = function() {
                    try {
                        // CASE 0: If menu-painel is open, dismiss the menu panel cleanly
                        var painel = document.getElementById('menu-painel');
                        if (painel && !painel.classList.contains('hidden')) {
                            painel.classList.add('hidden');
                            var menuBtn = document.getElementById('btn-menu');
                            if (menuBtn) {
                                menuBtn.setAttribute('aria-expanded', 'false');
                                menuBtn.focus();
                            }
                            return "menu_closed";
                        }

                        var sf = document.getElementById('site-frame');
                        var np = document.getElementById('nosso-player');
                        var dt = document.getElementById('detalhe');

                        var isFrameOpen = sf && !sf.classList.contains('hidden');
                        var isNossoPlayerOpen = np && !np.classList.contains('hidden');
                        var isDetalheOpen = dt && !dt.classList.contains('hidden');

                        // CASE 1: In server frame, Cloudflare verification, player loading (IMAGE 1)
                        if (isFrameOpen || isNossoPlayerOpen || window.__isUserInServerFrame) {
                            window.__isUserInServerFrame = false;
                            window.__returnToMovieAudioSelection();
                            return "returned_to_detalhe";
                        }

                        // CASE 2: On Movie Details / Mais Informações page (IMAGE 2)
                        if (isDetalheOpen || window.__isUserInMovie) {
                            window.__isUserInServerFrame = false;
                            window.__goToCatalog();
                            return "returned_to_catalog";
                        }

                        // CASE 3: In the catalog
                        return "in_catalog";
                    } catch(e) {
                        return "error";
                    }
                };

                document.addEventListener('click', function(e) {
                    try {
                        var toCatalog = e.target.closest('#btn-catalog, #detalhe-voltar, .detalhe-voltar');
                        if (toCatalog) {
                            e.preventDefault();
                            e.stopPropagation();
                            window.__goToCatalog();
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

                        function getBestSavedProgress(title, tmdbId) {
                            try {
                                var clean = (title || '').trim().toLowerCase();
                                var id = tmdbId || (window.__activeMovieMeta ? window.__activeMovieMeta.tmdb_id : '');
                                var raw = localStorage.getItem('bvd:progresso');
                                if (raw) {
                                    var map = JSON.parse(raw);
                                    if (map && typeof map === 'object') {
                                        if (id) {
                                            var direct = map['filme:' + id] || map['serie:' + id] || map[id];
                                            if (direct && typeof direct.segundo === 'number' && direct.segundo > 10) {
                                                return Math.round(direct.segundo);
                                            }
                                        }
                                        if (clean) {
                                            for (var k in map) {
                                                var entry = map[k];
                                                if (!entry) continue;
                                                var entryTitle = (entry.titulo || entry.title || '').trim().toLowerCase();
                                                if (entryTitle && (entryTitle === clean || entryTitle.indexOf(clean) !== -1 || clean.indexOf(entryTitle) !== -1)) {
                                                    if (typeof entry.segundo === 'number' && entry.segundo > 10) {
                                                        return Math.round(entry.segundo);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch(e) {}
                            return 0;
                        }

                        function queryAndReportSavedProgress(title, tmdbId) {
                            try {
                                if (!title || isGenericTitle(title)) return;
                                var clean = title.trim();
                                var id = tmdbId || (window.__activeMovieMeta ? window.__activeMovieMeta.tmdb_id : '');

                                // 1. Immediate local storage check
                                var localSec = getBestSavedProgress(clean, id);
                                if (localSec > 10 && window.AndroidBridge && window.AndroidBridge.onCloudProgressLoaded) {
                                    window.AndroidBridge.onCloudProgressLoaded(clean, localSec, 0);
                                }

                                // 2. Supabase cloud check for cross-device sync
                                if (window.bvdAuth && window.bvdAuth.cliente && window.bvdAuth.usuarioAtual) {
                                    var usuario = window.bvdAuth.usuarioAtual();
                                    if (usuario && usuario.id) {
                                        var query = window.bvdAuth.cliente.from('progresso').select('segundo, duracao, titulo, tmdb_id');
                                        if (id) {
                                            query = query.eq('tmdb_id', id);
                                        } else {
                                            query = query.ilike('titulo', '%' + clean + '%');
                                        }
                                        query.maybeSingle().then(function(res) {
                                            if (res && res.data && res.data.segundo && res.data.segundo > 10) {
                                                var cloudSec = Math.round(res.data.segundo);
                                                var cloudDur = Math.round(res.data.duracao || 0);
                                                if (window.AndroidBridge && window.AndroidBridge.onCloudProgressLoaded) {
                                                    window.AndroidBridge.onCloudProgressLoaded(clean, cloudSec, cloudDur);
                                                }
                                            }
                                        }).catch(function(err) {});
                                    }
                                }
                            } catch(e) {}
                        }

                        window.__syncProgressToWeb = function(title, second, duration) {
                            try {
                                if (!title || second <= 0) return;
                                var clean = title.trim();
                                var lower = clean.toLowerCase();
                                var id = (window.__activeMovieMeta ? window.__activeMovieMeta.tmdb_id : '') || '';
                                var kind = (window.__activeMovieMeta ? window.__activeMovieMeta.kind : 'filme') || 'filme';
                                var poster = (window.__activeMovieMeta ? window.__activeMovieMeta.poster : '') || '';
                                var backdrop = (window.__activeMovieMeta ? window.__activeMovieMeta.backdrop : '') || '';

                                // 1. Local storage bvd:progresso
                                var raw = localStorage.getItem('bvd:progresso') || '{}';
                                var map = {};
                                try { map = JSON.parse(raw) || {}; } catch(err) { map = {}; }
                                var targetKey = id ? (kind + ':' + id) : null;
                                if (!targetKey) {
                                    for (var k in map) {
                                        var it = map[k];
                                        if (it && (it.titulo || it.title || '').trim().toLowerCase() === lower) {
                                            targetKey = k;
                                            break;
                                        }
                                    }
                                }
                                if (!targetKey) {
                                    targetKey = 'filme:' + encodeURIComponent(lower.replace(/[^a-z0-9]/g, '_'));
                                }
                                var existing = map[targetKey] || {};
                                var dur = duration > 0 ? duration : (existing.duracao || 7200);
                                var acabou = dur > 0 && (second / dur >= 0.90);
                                map[targetKey] = {
                                    segundo: Math.round(second),
                                    duracao: Math.round(dur),
                                    titulo: existing.titulo || clean,
                                    poster: poster || existing.poster || '',
                                    backdrop: backdrop || existing.backdrop || '',
                                    terminou: acabou,
                                    visto_em: Date.now()
                                };
                                localStorage.setItem('bvd:progresso', JSON.stringify(map));

                                // 2. Dispatch event so catalog updates
                                window.dispatchEvent(new CustomEvent('bvd:progresso-atualizado', {
                                    detail: { titulo: clean, segundo: second, duracao: dur }
                                }));

                                // 3. CLOUD SUPABASE SYNC (For all other devices)
                                if (window.bvdAuth && window.bvdAuth.cliente && window.bvdAuth.usuarioAtual) {
                                    var usuario = window.bvdAuth.usuarioAtual();
                                    if (usuario && usuario.id) {
                                        var numericId = id ? parseInt(id, 10) : null;
                                        var tmdbVal = (numericId && !isNaN(numericId)) ? numericId : null;
                                        var payload = {
                                            user_id: usuario.id,
                                            tmdb_id: tmdbVal,
                                            kind: kind,
                                            titulo: clean,
                                            poster: poster || null,
                                            backdrop: backdrop || null,
                                            temporada: null,
                                            episodio: null,
                                            segundo: Math.round(second),
                                            duracao: Math.round(dur),
                                            terminou: acabou,
                                            visto_em: new Date().toISOString()
                                        };
                                        if (tmdbVal) {
                                            window.bvdAuth.cliente.from('progresso').upsert(payload, {
                                                onConflict: 'user_id,tmdb_id,kind'
                                            }).then(function(){}).catch(function(){});
                                        } else {
                                            window.bvdAuth.cliente.from('progresso').upsert(payload).then(function(){}).catch(function(){});
                                        }
                                    }
                                }
                            } catch(e) {}
                        };

                        var card = e.target.closest('.cat-card, .poster, .retomar-card');
                        if (card) {
                            var titleEl = card.querySelector('.cat-title, .poster-title, .retomar-titulo, h3, h4');
                            var t = titleEl ? titleEl.textContent : (card.getAttribute('aria-label') || card.getAttribute('title') || '');
                            var tmdbId = card.dataset.id || card.getAttribute('data-id') || '';
                            var badge = card.querySelector('.cat-badge');
                            var badgeTxt = badge ? badge.textContent.toLowerCase() : '';
                            var kind = (badgeTxt.indexOf('sér') !== -1 || badgeTxt.indexOf('ser') !== -1) ? 'serie' : (badgeTxt.indexOf('anim') !== -1 ? 'anime' : 'filme');
                            var imgEl = card.querySelector('img');
                            var poster = imgEl ? (imgEl.currentSrc || imgEl.src || '') : '';
                            if (t && !isGenericTitle(t)) {
                                var cleanT = t.trim();
                                window.__currentMovieTitle = cleanT;
                                window.__activeMovieMeta = {
                                    tmdb_id: tmdbId,
                                    title: cleanT,
                                    kind: kind,
                                    poster: poster,
                                    backdrop: ''
                                };
                                if (window.AndroidBridge && window.AndroidBridge.onMovieSelected) {
                                    window.AndroidBridge.onMovieSelected(cleanT);
                                }
                                queryAndReportSavedProgress(cleanT, tmdbId);
                            }
                            return;
                        }

                        var btnAssistir = e.target.closest('#detalhe-assistir, #destaque-play, .previa-abrir, .detalhe-assistir');
                        if (btnAssistir) {
                            var dtTitleSnap = document.getElementById('detalhe-titulo');
                            if (dtTitleSnap && dtTitleSnap.textContent && dtTitleSnap.textContent.trim()) {
                                var dtFundoSnap = document.getElementById('detalhe-fundo');
                                var dtMetaSnap = document.getElementById('detalhe-meta');
                                var dtSinSnap = document.getElementById('detalhe-sinopse');
                                var dtEpSnap = document.getElementById('detalhe-episodio');
                                var relSnap = document.getElementById('relacionados');
                                window.__savedMovieDetail = {
                                    titulo: dtTitleSnap.textContent.trim(),
                                    meta: dtMetaSnap ? dtMetaSnap.textContent.trim() : '',
                                    sinopse: dtSinSnap ? dtSinSnap.textContent.trim() : '',
                                    fundo: dtFundoSnap ? dtFundoSnap.style.backgroundImage : '',
                                    episodioHtml: dtEpSnap ? dtEpSnap.innerHTML : '',
                                    episodioHidden: dtEpSnap ? dtEpSnap.classList.contains('hidden') : true,
                                    relacionadosHtml: relSnap ? relSnap.innerHTML : ''
                                };
                            }
                            var dt = document.getElementById('detalhe-titulo') || document.getElementById('destaque-titulo') || document.getElementById('previa-titulo');
                            var t2 = (dt && dt.textContent ? dt.textContent.trim() : '') || window.__currentMovieTitle;
                            if (t2 && !isGenericTitle(t2)) {
                                window.__currentMovieTitle = t2;
                            }
                            window.__isUserInMovie = true;
                            window.__isUserInCatalog = false;
                            var id = window.__activeMovieMeta ? window.__activeMovieMeta.tmdb_id : '';
                            queryAndReportSavedProgress(window.__currentMovieTitle, id);
                            var savedSec = getBestSavedProgress(window.__currentMovieTitle, id);
                            if (window.AndroidBridge && window.AndroidBridge.onAssistirClicked) {
                                try {
                                    window.AndroidBridge.onAssistirClicked(window.__currentMovieTitle || '', savedSec);
                                } catch(err) {
                                    window.AndroidBridge.onAssistirClicked(window.__currentMovieTitle || '');
                                }
                            } else if (window.AndroidBridge && window.AndroidBridge.onMovieStateChanged) {
                                window.AndroidBridge.onMovieStateChanged(true, window.__currentMovieTitle || '');
                            }
                            autoSelectFirstServerAndPlay();
                        }
                    } catch(err) {}
                }, true);

                function autoSelectFirstServerAndPlay() {
                    var attempts = 0;
                    var maxAttempts = 60;
                    var timer = setInterval(function() {
                        attempts++;
                        if (attempts > maxAttempts) {
                            clearInterval(timer);
                            return;
                        }
                        try {
                            // 1. Auto click first server button if displayed in DOM
                            var serverButtons = document.querySelectorAll(
                                '#optionList .option, #optionList > div, .option, [onclick*="player"], ' +
                                '#detalhe-acoes button:not(#detalhe-assistir):not(#detalhe-voltar), ' +
                                '.server-btn, .btn-server, [data-server], .server-item'
                            );
                            if (serverButtons && serverButtons.length > 0) {
                                for (var i = 0; i < serverButtons.length; i++) {
                                    var s = serverButtons[i];
                                    if (s && s.offsetParent !== null) {
                                        s.click();
                                        break;
                                    }
                                }
                            }

                            // 2. Click achamosTocar if shown
                            var achamosTocar = document.getElementById('achamos-tocar');
                            if (achamosTocar && !achamosTocar.closest('.hidden')) {
                                achamosTocar.click();
                            }

                            // 3. Auto click play button if web player is visible
                            var playButtons = document.querySelectorAll(
                                '.play-btn, .vjs-big-play-button, .jw-display-icon-container, [aria-label*="Play" i], button.play, #np-play'
                            );
                            for (var j = 0; j < playButtons.length; j++) {
                                var p = playButtons[j];
                                if (p && p.offsetParent !== null) {
                                    p.click();
                                }
                            }

                            // 4. Auto play any paused video
                            var videos = document.querySelectorAll('video');
                            for (var k = 0; k < videos.length; k++) {
                                var v = videos[k];
                                if (v && v.paused && v.id !== 'detalhe-trailer-video') {
                                    v.muted = false;
                                    v.play().catch(function(){});
                                }
                            }
                        } catch(e) {}
                    }, 100);
                }

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

                // Sync catalog "Continuar assistindo" items from Supabase and bvd:progresso to native player DB
                function syncCatalogProgressToAndroid() {
                    try {
                        if (!window.AndroidBridge) return;
                        var raw = localStorage.getItem('bvd:progresso');
                        if (raw) {
                            var map = JSON.parse(raw);
                            for (var k in map) {
                                var item = map[k];
                                if (item && (item.titulo || item.title) && item.segundo && item.segundo > 10) {
                                    var titleName = (item.titulo || item.title || '').trim();
                                    if (window.AndroidBridge.onSyncLocalProgress) {
                                        window.AndroidBridge.onSyncLocalProgress(titleName, Math.round(item.segundo), Math.round(item.duracao || 0));
                                    }
                                }
                            }
                        }
                        if (window.bvdAuth && window.bvdAuth.cliente && window.bvdAuth.usuarioAtual) {
                            var usuario = window.bvdAuth.usuarioAtual();
                            if (usuario && usuario.id) {
                                window.bvdAuth.cliente.from('progresso')
                                    .select('titulo, segundo, duracao, tmdb_id, kind')
                                    .order('visto_em', { ascending: false })
                                    .limit(20)
                                    .then(function(res) {
                                        if (res && res.data && res.data.length > 0) {
                                            res.data.forEach(function(row) {
                                                if (row.titulo && row.segundo && row.segundo > 10) {
                                                    var tName = row.titulo.trim();
                                                    if (window.AndroidBridge.onSyncLocalProgress) {
                                                        window.AndroidBridge.onSyncLocalProgress(tName, Math.round(row.segundo), Math.round(row.duracao || 0));
                                                    }
                                                }
                                            });
                                        }
                                    }).catch(function(err){});
                            }
                        }
                    } catch(e) {}
                }
                setInterval(syncCatalogProgressToAndroid, 6000);
                setTimeout(syncCatalogProgressToAndroid, 1500);
            } catch(globalErr) {}
        })();
        """.trimIndent()
    }
}

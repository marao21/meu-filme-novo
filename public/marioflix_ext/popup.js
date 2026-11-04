document.addEventListener("DOMContentLoaded", () => {
  const elements = {
    btnRefresh: document.getElementById("btn-refresh"),
    inputManualUrl: document.getElementById("input-manual-url"),
    btnAddManual: document.getElementById("btn-add-manual"),
    stateLoading: document.getElementById("state-loading"),
    stateEmpty: document.getElementById("state-empty"),
    stateError: document.getElementById("state-error"),
    stateList: document.getElementById("state-list"),
    videoItemsList: document.getElementById("video-items-list"),
    errorMessage: document.getElementById("error-message"),
    previewPanel: document.getElementById("preview-panel"),
    previewPlayer: document.getElementById("preview-player"),
    previewTitle: document.getElementById("preview-title"),
    previewQuality: document.getElementById("preview-quality"),
    previewStatus: document.getElementById("preview-status")
  };

  let videoListStore = [];
  let activeVideoUrl = "";

  function showState(targetState) {
    [elements.stateLoading, elements.stateEmpty, elements.stateError, elements.stateList].forEach(el => {
      if (el) el.classList.add("state-hidden");
    });
    if (targetState) targetState.classList.remove("state-hidden");
  }

  function setPreviewStatus(message) {
    elements.previewStatus.textContent = message;
  }

  function stopPreview() {
    const player = elements.previewPlayer;
    if (!player) return;
    player.pause();
    player.removeAttribute("src");
    player.load();
    activeVideoUrl = "";
    elements.previewPanel.classList.add("state-hidden");
  }

  function copyToClipboard(text, buttonEl) {
    navigator.clipboard.writeText(text).then(() => {
      const originalText = buttonEl.textContent;
      buttonEl.textContent = "✓ Copiado!";
      setTimeout(() => { buttonEl.textContent = originalText; }, 1600);
    }).catch(() => { buttonEl.textContent = "Falhou"; });
  }

  function detectFormat(url) {
    const lower = url.toLowerCase();
    if (/\.m3u8(?:$|[?#])/.test(lower)) return "M3U8 (HLS)";
    if (/\.mp4(?:$|[?#])/.test(lower)) return "MP4";
    if (/\.webm(?:$|[?#])/.test(lower)) return "WEBM";
    return "LINK VÍDEO";
  }

  function isHlsUrl(url) {
    return /\.m3u8(?:$|[?#])/i.test(url);
  }

  function previewVideo(video) {
    const player = elements.previewPlayer;
    const url = video.url;
    const isHls = video.isHls || isHlsUrl(url);

    stopPreview();
    elements.previewPanel.classList.remove("state-hidden");
    elements.previewTitle.textContent = video.title || "Pré-visualização";
    elements.previewQuality.textContent = isHls ? "AUTO · HLS" : "ORIGINAL";
    activeVideoUrl = url;

    // Native HLS playback is adaptive when the browser exposes HLS support.
    // Chrome desktop usually needs an HLS.js bundle; remote scripts are not
    // permitted by Manifest V3 extension-page CSP, so do not load one remotely.
    if (isHls && !player.canPlayType("application/vnd.apple.mpegurl") && !player.canPlayType("application/x-mpegURL")) {
      setPreviewStatus("Este navegador não reproduz HLS nativamente. Para qualidade adaptativa neste Chrome, é necessário incluir o HLS.js localmente na extensão.");
      elements.previewQuality.textContent = "HLS";
      return;
    }

    player.preload = "none";
    player.src = url;
    player.load();
    setPreviewStatus(isHls
      ? "Qualidade automática: o navegador seleciona a variante conforme a conexão, se o manifesto oferecer várias qualidades."
      : "MP4 direto: reproduz a qualidade fornecida pelo servidor. A troca automática de qualidade exige um manifesto HLS/DASH.");

    // Do not autoplay: playback/network use starts only after the user's action.
  }

  function createVideoCard(video) {
    const card = document.createElement("div");
    card.className = "video-card";

    const header = document.createElement("div");
    header.className = "card-header";
    const title = document.createElement("p");
    title.className = "video-title";
    title.textContent = video.title || "Vídeo Encontrado";
    const badge = document.createElement("span");
    badge.className = video.isHls ? "badge-hls" : "badge-mp4";
    badge.textContent = video.format || detectFormat(video.url);
    header.append(title, badge);

    const urlBox = document.createElement("div");
    urlBox.className = "card-url-box";
    const urlInput = document.createElement("input");
    urlInput.className = "card-url-input";
    urlInput.type = "text";
    urlInput.readOnly = true;
    urlInput.value = video.url;
    const btnCopy = document.createElement("button");
    btnCopy.className = "btn-secondary";
    btnCopy.textContent = "📋 Copiar";
    btnCopy.addEventListener("click", () => copyToClipboard(video.url, btnCopy));
    urlBox.append(urlInput, btnCopy);

    const actions = document.createElement("div");
    actions.className = "card-actions";
    const btnPreview = document.createElement("button");
    btnPreview.className = "btn-primary";
    btnPreview.textContent = "▶ Pré-visualizar";
    btnPreview.addEventListener("click", () => previewVideo(video));

    const btnDownload = document.createElement("button");
    btnDownload.className = "btn-secondary";
    if (video.isHls || isHlsUrl(video.url)) {
      btnDownload.textContent = "⚡ Baixar HLS";
      btnDownload.addEventListener("click", () => {
        window.open(`https://m3u8-downloader.com/?url=${encodeURIComponent(video.url)}`, "_blank");
      });
    } else {
      btnDownload.textContent = "⬇ Baixar MP4";
      btnDownload.addEventListener("click", () => {
        chrome.runtime.sendMessage({ action: "downloadVideo", url: video.url, filename: "dorama_video.mp4" });
      });
    }
    actions.append(btnPreview, btnDownload);
    card.append(header, urlBox, actions);
    return card;
  }

  function renderVideoList(videos) {
    if (!elements.videoItemsList) return;
    elements.videoItemsList.textContent = "";
    if (!videos || videos.length === 0) {
      showState(elements.stateEmpty);
      return;
    }
    const fragment = document.createDocumentFragment();
    videos.forEach(video => fragment.appendChild(createVideoCard(video)));
    elements.videoItemsList.appendChild(fragment);
    showState(elements.stateList);
  }

  async function handleAddManualUrl() {
    const rawUrl = elements.inputManualUrl.value.trim();
    if (!rawUrl) return;
    let parsed;
    try { parsed = new URL(rawUrl); } catch (_) {
      elements.errorMessage.textContent = "Digite um endereço HTTP ou HTTPS válido.";
      showState(elements.stateError);
      return;
    }
    if (!/^https?:$/.test(parsed.protocol)) {
      elements.errorMessage.textContent = "Somente links HTTP ou HTTPS são aceitos.";
      showState(elements.stateError);
      return;
    }

    const directVideo = /\.(mp4|m3u8|webm)(?:$|[?#])/i.test(parsed.href);
    if (directVideo) {
      const isHls = isHlsUrl(parsed.href);
      const newVideo = {
        id: "manual_" + Date.now(), url: parsed.href,
        title: "Link de Vídeo Direto", format: detectFormat(parsed.href),
        isHls, timestamp: Date.now()
      };
      videoListStore = [newVideo, ...videoListStore.filter(v => v.url !== newVideo.url)];
      elements.inputManualUrl.value = "";
      renderVideoList(videoListStore);
      return;
    }

    showState(elements.stateLoading);
    try {
      const response = await fetch(parsed.href);
      if (!response.ok) throw new Error("Não foi possível acessar a página.");
      const htmlText = await response.text();
      const videoRegex = /https?:\/\/[^"'\s<>]+\.(?:mp4|m3u8|webm)(?:\?[^"'\s<>]*)?/gi;
      const uniqueLinks = [...new Set(htmlText.match(videoRegex) || [])];
      if (uniqueLinks.length) {
        const extractedVideos = uniqueLinks.map((url, idx) => ({
          id: "extracted_" + Date.now() + "_" + idx, url,
          title: `Vídeo Extraído (${idx + 1})`, format: detectFormat(url),
          isHls: isHlsUrl(url), timestamp: Date.now()
        }));
        videoListStore = [...extractedVideos, ...videoListStore];
        elements.inputManualUrl.value = "";
        renderVideoList(videoListStore);
      } else {
        chrome.tabs.create({ url: parsed.href, active: true });
        showState(elements.stateEmpty);
      }
    } catch (_) {
      chrome.tabs.create({ url: parsed.href, active: true });
      showState(elements.stateEmpty);
    }
  }

  function fetchVideos() {
    stopPreview();
    showState(elements.stateLoading);
    chrome.tabs.query({ active: true, currentWindow: true }, tabs => {
      if (!tabs || tabs.length === 0) {
        elements.errorMessage.textContent = "Não foi possível identificar a aba ativa.";
        showState(elements.stateError);
        return;
      }
      chrome.runtime.sendMessage({ action: "getVideos", tabId: tabs[0].id }, response => {
        if (chrome.runtime.lastError || !response) {
          elements.errorMessage.textContent = "Não foi possível ler os vídeos desta aba.";
          showState(elements.stateError);
          return;
        }
        videoListStore = response.videos || [];
        renderVideoList(videoListStore);
      });
    });
  }

  elements.previewPlayer.addEventListener("error", () => {
    if (activeVideoUrl) setPreviewStatus("Não foi possível reproduzir este link. Ele pode exigir autorização, cookies ou um formato compatível.");
  });
  elements.previewPlayer.addEventListener("waiting", () => setPreviewStatus("Carregando dados do vídeo…"));
  elements.previewPlayer.addEventListener("playing", () => setPreviewStatus("Reproduzindo. O navegador gerencia o buffer da reprodução."));

  elements.btnAddManual.addEventListener("click", handleAddManualUrl);
  elements.inputManualUrl.addEventListener("keydown", e => { if (e.key === "Enter") handleAddManualUrl(); });
  elements.btnRefresh.addEventListener("click", fetchVideos);
  window.addEventListener("unload", stopPreview);
  fetchVideos();
});

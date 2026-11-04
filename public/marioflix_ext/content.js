(function () {
  let popupContainer = null;
  let isDragging = false;
  let startX, startY, initialLeft, initialTop;

  function createPopupDOM() {
    if (document.getElementById("vd-inpage-popup")) return;

    popupContainer = document.createElement("div");
    popupContainer.id = "vd-inpage-popup";
    popupContainer.className = "vd-popup-hidden";

    popupContainer.innerHTML = `
      <div class="vd-popup-card">
        <div class="vd-popup-header" id="vd-drag-handle" title="Clique e arraste para mover">
          <div class="vd-popup-title-box">
            <span class="vd-popup-badge" id="vd-badge">MP4</span>
            <h3 class="vd-popup-title" id="vd-title">Vídeo Encontrado</h3>
          </div>
          <button id="vd-btn-close" class="vd-close-btn" title="Fechar">✕</button>
        </div>

        <div class="vd-popup-body">
          <div class="vd-player-wrapper">
            <video id="vd-preview-player" controls preload="metadata" poster="">
              Seu navegador não suporta a pré-visualização.
            </video>
          </div>

          <div class="vd-url-box">
            <input type="text" id="vd-url-input" readonly value="" />
            <button id="vd-btn-copy" title="Copiar Link">📋 Copiar</button>
          </div>
        </div>

        <div class="vd-popup-footer">
          <button id="vd-btn-open" class="vd-btn vd-btn-secondary">🔗 Abrir Link</button>
          <button id="vd-btn-download" class="vd-btn vd-btn-primary">⬇ Baixar Vídeo</button>
        </div>
      </div>
    `;

    document.body.appendChild(popupContainer);

    // Lógica para arrastar o pop-up pelo cabeçalho
    const headerHandle = document.getElementById("vd-drag-handle");
    
    headerHandle.addEventListener("mousedown", (e) => {
      // Se clicar no botão de fechar, não inicia o arraste
      if (e.target.id === "vd-btn-close") return;

      isDragging = true;
      startX = e.clientX;
      startY = e.clientY;

      const rect = popupContainer.getBoundingClientRect();
      initialLeft = rect.left;
      initialTop = rect.top;

      // Altera o posicionamento para 'absolute' baseado na janela para permitir movimento livre
      popupContainer.style.position = "fixed";
      popupContainer.style.bottom = "auto";
      popupContainer.style.right = "auto";
      popupContainer.style.left = initialLeft + "px";
      popupContainer.style.top = initialTop + "px";

      document.addEventListener("mousemove", onMouseMove);
      document.addEventListener("mouseup", onMouseUp);
      e.preventDefault();
    });

    function onMouseMove(e) {
      if (!isDragging) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;

      popupContainer.style.left = (initialLeft + dx) + "px";
      popupContainer.style.top = (initialTop + dy) + "px";
    }

    function onMouseUp() {
      isDragging = false;
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
    }

    // Eventos dos botões
    document.getElementById("vd-btn-close").addEventListener("click", hidePopup);

    document.getElementById("vd-btn-copy").addEventListener("click", () => {
      const urlInput = document.getElementById("vd-url-input");
      navigator.clipboard.writeText(urlInput.value).then(() => {
        const btnCopy = document.getElementById("vd-btn-copy");
        btnCopy.textContent = "✓ Copiado";
        setTimeout(() => (btnCopy.textContent = "📋 Copiar"), 2000);
      });
    });

    document.getElementById("vd-btn-open").addEventListener("click", () => {
      const url = document.getElementById("vd-url-input").value;
      if (url) window.open(url, "_blank");
    });

    document.getElementById("vd-btn-download").addEventListener("click", () => {
      const url = document.getElementById("vd-url-input").value;
      const isHls = url.toLowerCase().includes(".m3u8");

      if (isHls) {
        window.open(`https://m3u8-downloader.com/?url=${encodeURIComponent(url)}`, "_blank");
      } else {
        chrome.runtime.sendMessage({
          action: "downloadVideo",
          url: url,
          filename: "dorama_video.mp4"
        });
      }
    });
  }

  function showPopup(video) {
    createPopupDOM();

    const titleEl = document.getElementById("vd-title");
    const badgeEl = document.getElementById("vd-badge");
    const urlInput = document.getElementById("vd-url-input");
    const player = document.getElementById("vd-preview-player");
    const btnDownload = document.getElementById("vd-btn-download");

    titleEl.textContent = video.title || "Vídeo Detectado";
    badgeEl.textContent = video.format || "VÍDEO";
    urlInput.value = video.url;
    player.src = video.url;

    if (video.isHls) {
      btnDownload.textContent = "⚡ Baixar (HLS Web)";
      btnDownload.style.backgroundColor = "#0284c7";
    } else {
      btnDownload.textContent = "⬇ Baixar Vídeo";
      btnDownload.style.backgroundColor = "#16a34a";
    }

    popupContainer.classList.remove("vd-popup-hidden");
  }

  function hidePopup() {
    if (popupContainer) {
      const player = document.getElementById("vd-preview-player");
      if (player) {
        player.pause();
        player.src = "";
      }
      popupContainer.classList.add("vd-popup-hidden");
    }
  }

  chrome.runtime.onMessage.addListener((message) => {
    if (message.action === "showInPagePopup" && message.video) {
      showPopup(message.video);
    }
  });
})();
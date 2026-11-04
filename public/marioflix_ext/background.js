console.log("[background.js] Monitor de rede com aviso em tela (In-Page Popup) ativo.");

const DOMAIN_BLACKLIST = ["youtube.com", "googlevideo.com", "ytimg.com"];
const MEDIA_EXTENSIONS = [".mp4", ".m3u8", ".m4s", ".webm"];
const MEDIA_MIME_TYPES = [
  "video/mp4",
  "video/webm",
  "application/x-mpegurl",
  "application/vnd.apple.mpegurl"
];

function isMediaRequest(url, headers) {
  const lowerUrl = url.toLowerCase();

  if (DOMAIN_BLACKLIST.some(domain => lowerUrl.includes(domain))) {
    return false;
  }

  if (lowerUrl.includes(".ts") && !lowerUrl.includes(".m3u8")) {
    return false;
  }

  const hasMediaExt = MEDIA_EXTENSIONS.some(ext => lowerUrl.includes(ext));
  if (hasMediaExt) return true;

  if (headers && Array.isArray(headers)) {
    const contentType = headers.find(h => h.name.toLowerCase() === "content-type");
    if (contentType && contentType.value) {
      const mime = contentType.value.toLowerCase();
      if (MEDIA_MIME_TYPES.some(type => mime.includes(type))) {
        return true;
      }
    }
  }

  return false;
}

function detectFormat(url) {
  const lowerUrl = url.toLowerCase();
  if (lowerUrl.includes(".m3u8")) return "M3U8 (HLS)";
  if (lowerUrl.includes(".mp4")) return "MP4";
  return "VÍDEO";
}

function deriveTitle(url, pageTitle) {
  if (pageTitle && pageTitle.length > 2) {
    return pageTitle.replace(/[\/\\:*?"<>|]/g, "").trim();
  }
  try {
    const filename = new URL(url).pathname.split("/").pop();
    if (filename && filename.length > 3) {
      return decodeURIComponent(filename);
    }
  } catch (e) {}
  return "Vídeo Detectado";
}

chrome.webRequest.onHeadersReceived.addListener(
  (details) => {
    if (details.tabId < 0) return;

    const url = details.url;
    const responseHeaders = details.responseHeaders || [];

    if (isMediaRequest(url, responseHeaders)) {
      const tabId = details.tabId;
      const storageKey = `videos_${tabId}`;

      chrome.tabs.get(tabId, (tab) => {
        const pageTitle = tab ? tab.title : "";

        chrome.storage.session.get([storageKey], (result) => {
          const existingVideos = result[storageKey] || [];

          if (!existingVideos.some(v => v.url === url)) {
            const newVideo = {
              id: Date.now() + Math.random().toString(36).substr(2, 5),
              url: url,
              title: deriveTitle(url, pageTitle),
              format: detectFormat(url),
              isHls: url.toLowerCase().includes(".m3u8"),
              timestamp: Date.now()
            };

            const updatedList = [newVideo, ...existingVideos];
            chrome.storage.session.set({ [storageKey]: updatedList }, () => {
              chrome.tabs.sendMessage(tabId, {
                action: "showInPagePopup",
                video: newVideo
              }).catch(() => {});
            });
          }
        });
      });
    }
  },
  { urls: ["<all_urls>"] },
  ["responseHeaders"]
);

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || !message.action) return false;

  if (message.action === "getVideos") {
    const tabId = message.tabId || (sender.tab ? sender.tab.id : null);
    if (!tabId) return false;

    chrome.storage.session.get([`videos_${tabId}`], (result) => {
      sendResponse({ videos: result[`videos_${tabId}`] || [] });
    });
    return true;
  }

  if (message.action === "downloadVideo") {
    const { url, filename } = message;
    chrome.downloads.download({
      url: url,
      filename: filename || "dorama_video.mp4",
      conflictAction: "uniquify",
      saveAs: true
    }, (downloadId) => {
      if (chrome.runtime.lastError) {
        sendResponse({ success: false, error: chrome.runtime.lastError.message });
      } else {
        sendResponse({ success: true, downloadId });
      }
    });
    return true;
  }

  return false;
});
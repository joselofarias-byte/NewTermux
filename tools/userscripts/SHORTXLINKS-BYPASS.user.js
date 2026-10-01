// ==UserScript==
// @name         ShortXLinks Final URL Catcher
// @namespace    local.shortxlinks.bypass
// @version      1.2.0
// @description  Salta etapas ShortXLinks/MTC y captura el destino entregado por /links/go.
// @match        *://shortxlinks.in/*
// @match        *://*.shortxlinks.in/*
// @match        *://shortxlinks.com/*
// @match        *://*.shortxlinks.com/*
// @match        *://iti-result.in/*
// @match        *://*.iti-result.in/*
// @updateURL    https://raw.githubusercontent.com/joselofarias-byte/NewTermux/tools/shortxlinks-bypass-20260930/tools/userscripts/SHORTXLINKS-BYPASS.user.js
// @downloadURL  https://raw.githubusercontent.com/joselofarias-byte/NewTermux/tools/shortxlinks-bypass-20260930/tools/userscripts/SHORTXLINKS-BYPASS.user.js
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  const seen = new Set();

  function isMtcHost(hostname) {
    return /^mtc\d+\./i.test(hostname) && /(^|\.)iti-result\.in$/i.test(hostname);
  }

  function decodeB64Json(value) {
    try {
      const normalized = String(value).replace(/-/g, '+').replace(/_/g, '/');
      const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4);
      return JSON.parse(atob(padded));
    } catch {
      return null;
    }
  }

  function resolveMtcTarget(raw) {
    if (!raw) return null;
    let target = String(raw);
    try {
      const nested = new URL(target, location.href).searchParams.get('safelink_redirect');
      if (nested) {
        const decoded = decodeB64Json(nested);
        if (decoded && decoded.safelink) target = decoded.safelink;
      }
    } catch {}
    return target;
  }

  function handleMtcStage() {
    const direct = new URLSearchParams(location.search).get('safelink_redirect');
    if (direct) {
      const decoded = decodeB64Json(direct);
      if (decoded && decoded.safelink) {
        location.replace(decoded.safelink);
        return true;
      }
    }

    let tries = 0;
    const timer = setInterval(() => {
      tries++;
      const el = document.getElementById('value') ||
                 document.querySelector('input[name="newwpsafelink"]');
      const raw = el ? el.value : window.ad_mem;

      if (raw) {
        const decoded = decodeB64Json(raw);
        if (decoded && decoded.linkr) {
          clearInterval(timer);
          const target = resolveMtcTarget(decoded.linkr);
          if (target) location.replace(target);
          return;
        }
      }

      if (tries >= 120) clearInterval(timer);
    }, 250);

    return true;
  }

  if (isMtcHost(location.hostname)) {
    handleMtcStage();
    return;
  }

  function isHttpUrl(value) {
    try {
      const u = new URL(String(value), location.href);
      return /^https?:$/.test(u.protocol);
    } catch {
      return false;
    }
  }

  function normalizeUrl(value) {
    try {
      return new URL(String(value), location.href).href;
    } catch {
      return null;
    }
  }

  function showFinalUrl(value) {
    const url = normalizeUrl(value);
    if (!url || seen.has(url)) return;
    if (/shortxlinks\.(in|com)/i.test(new URL(url).hostname)) return;

    seen.add(url);

    const render = () => {
      if (!document.documentElement) return setTimeout(render, 50);

      let box = document.getElementById('sxl-final-url-box');
      if (!box) {
        box = document.createElement('div');
        box.id = 'sxl-final-url-box';
        box.style.cssText = [
          'position:fixed',
          'left:12px',
          'right:12px',
          'bottom:12px',
          'z-index:2147483647',
          'background:#111',
          'color:#fff',
          'border:2px solid #45d483',
          'border-radius:14px',
          'padding:14px',
          'font:14px/1.35 sans-serif',
          'box-shadow:0 8px 30px rgba(0,0,0,.45)'
        ].join(';');

        box.innerHTML = `
          <div style="font-weight:700;font-size:16px;margin-bottom:8px">URL FINAL ENCONTRADA</div>
          <div id="sxl-final-url-text" style="word-break:break-all;margin-bottom:10px"></div>
          <div style="display:flex;gap:8px;flex-wrap:wrap">
            <button id="sxl-copy" style="padding:9px 14px">COPIAR</button>
            <button id="sxl-open" style="padding:9px 14px">ABRIR</button>
            <button id="sxl-hide" style="padding:9px 14px">OCULTAR</button>
          </div>
        `;
        document.documentElement.appendChild(box);
      }

      box.querySelector('#sxl-final-url-text').textContent = url;
      box.style.display = 'block';

      box.querySelector('#sxl-copy').onclick = async () => {
        try {
          await navigator.clipboard.writeText(url);
          box.querySelector('#sxl-copy').textContent = 'COPIADO';
        } catch {
          prompt('Copiá esta URL:', url);
        }
      };

      box.querySelector('#sxl-open').onclick = () => {
        location.href = url;
      };

      box.querySelector('#sxl-hide').onclick = () => {
        box.style.display = 'none';
      };
    };

    render();
  }

  function inspectPayload(data) {
    if (!data) return;

    if (typeof data === 'string') {
      const trimmed = data.trim();

      if (isHttpUrl(trimmed)) showFinalUrl(trimmed);

      try {
        const parsed = JSON.parse(trimmed);
        inspectPayload(parsed);
      } catch {}

      const matches = trimmed.match(/https?:\/\/[^\s"'<>\\]+/g);
      if (matches) matches.forEach(showFinalUrl);
      return;
    }

    if (Array.isArray(data)) {
      data.forEach(inspectPayload);
      return;
    }

    if (typeof data === 'object') {
      for (const [key, value] of Object.entries(data)) {
        if (/^(url|link|destination|redirect|target|final_url|finalUrl)$/i.test(key)) {
          if (typeof value === 'string') showFinalUrl(value);
        }
        inspectPayload(value);
      }
    }
  }

  const originalFetch = window.fetch;
  if (originalFetch) {
    window.fetch = async function (...args) {
      const response = await originalFetch.apply(this, args);

      try {
        const reqUrl = typeof args[0] === 'string'
          ? args[0]
          : args[0] && args[0].url;

        if (reqUrl && /\/links\/go\b/i.test(reqUrl)) {
          const clone = response.clone();
          clone.text().then(inspectPayload).catch(() => {});
        }
      } catch {}

      return response;
    };
  }

  const xhrOpen = XMLHttpRequest.prototype.open;
  const xhrSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    this.__sxl_url = url;
    return xhrOpen.call(this, method, url, ...rest);
  };

  XMLHttpRequest.prototype.send = function (...args) {
    if (this.__sxl_url && /\/links\/go\b/i.test(this.__sxl_url)) {
      this.addEventListener('load', function () {
        try {
          inspectPayload(this.responseText || this.response);
        } catch {}
      });
    }
    return xhrSend.apply(this, args);
  };

  window.open = function (url, ...args) {
    if (url && isHttpUrl(url)) {
      const normalized = normalizeUrl(url);
      if (normalized && !/shortxlinks\.(in|com)/i.test(new URL(normalized).hostname)) {
        showFinalUrl(normalized);
      }
    }
    return null;
  };

  const scanLinks = () => {
    document.querySelectorAll('a[href]').forEach(a => {
      const href = a.href;
      if (!href) return;
      try {
        const host = new URL(href).hostname;
        if (host && !/shortxlinks\.(in|com)$/i.test(host) &&
            !/^(javascript:|#)/i.test(href)) {
          const text = (a.textContent || '').toLowerCase();
          if (/download|continue|destination|final|get link|go to link/.test(text)) {
            showFinalUrl(href);
          }
        }
      } catch {}
    });
  };

  document.addEventListener('DOMContentLoaded', () => {
    scanLinks();
    new MutationObserver(scanLinks).observe(document.documentElement, {
      childList: true,
      subtree: true
    });
  });
})();

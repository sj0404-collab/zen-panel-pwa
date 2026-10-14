/* =========================================================================
 * sw.js --- PWA Service Worker with extension capabilities
 * Features:
 * - Application caching
 * - Traffic forwarding (request proxy)
 * - Push notifications
 * - Offline mode
 * ========================================================================= */
var CACHE = 'pwa-extension-shell';

var ASSETS = [
  '/',
  '/manifest.webmanifest',
  '/sw.js',
  '/offline.html',
  '/data.js',
  '/extension-simulator.html'
];

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(CACHE).then(function (c) {
      return Promise.all(ASSETS.map(function (u) {
        return c.add(new Request(u, { cache: 'reload' })).catch(function () {});
      }));
    }).then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (names) {
      return Promise.all(names.map(function (n) {
        if (n !== CACHE) return caches.delete(n);
      }));
    }).then(function () { return self.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;

  var url;
  try { url = new URL(req.url); } catch (err) { return; }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return;

  // CDN resources --- cache-first
  if (url.hostname === 'cdn.jsdelivr.net') {
    e.respondWith(
      caches.match(req).then(function (hit) {
        return hit || fetch(req).then(function (res) {
          var copy = res.clone();
          caches.open(CACHE).then(function (c) { c.put(req, copy); });
          return res;
        }).catch(function () { return hit; });
      })
    );
    return;
  }

  // Handle forwarded requests from clients
  if (e.request.headers.has('x-forward-request')) {
    handleForwardedRequest(e, url);
    return;
  }

  // Internal requests --- network-first with fallback to cache
  if (url.origin === location.origin) {
    if (req.mode === 'navigate') {
      e.respondWith(
        fetch(req).then(function (res) {
          var copy = res.clone();
          caches.open(CACHE).then(function (c) { c.put(req, copy); });
          return res;
        }).catch(function () {
          return caches.match(req).then(function (hit) {
            return hit || caches.match('/offline.html');
          });
        })
      );
      return;
    }

    if (isDataAsset(url)) {
      e.respondWith(
        caches.match(req).then(function (hit) {
          var net = fetch(req).then(function (res) {
            if (res && res.ok) {
              var copy = res.clone();
              caches.open(CACHE).then(function (c) { c.put(req, copy); });
            }
            return res;
          }).catch(function () { return hit; });
          return hit || net;
        })
      );
      return;
    }
  }

  // Regular request --- cache with fallback
  e.respondWith(
    caches.match(req).then(function (hit) {
      return hit || fetch(req).then(function (res) {
        var copy = res.clone();
        caches.open(CACHE).then(function (c) { c.put(req, copy); });
        return res;
      }).catch(function () { return hit; });
    })
  );
});

function handleForwardedRequest(e, url) {
  // Forward request to target address
  // In real extension this would use chrome.webRequest
  // In PWA we simply fetch and return the response
  fetch(req).then(function (res) {
    // Can modify response here before returning
    e.respondWith(res.clone());
  }).catch(function () {
    // Fallback to cache
    caches.match(req).then(function (hit) {
      if (hit) e.respondWith(hit);
    });
  });
}

// Обработка сообщений из страницы
self.addEventListener('message', function (e) {
  if (e.data && e.data.type === 'toggle-browsec') {
    // Симуляция включения/выключения Browsec
    // На самом деле PWA не может напрямую управлять расширением
    // Но можно отправить статус обратно странице
    var status = 'connected'; // симуляция
    e.source.postMessage({
      type: 'browsec-status',
      status: status
    });
  }
});

function isDataAsset(url) {
  return /\.(?:js|css|html?|svg|png|jpg|jpeg|webp|ico|woff2?|ttf|json|webmanifest|wasm)$/.test(url.pathname);
}
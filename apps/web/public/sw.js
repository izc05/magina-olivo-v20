/* Mágina Olivo V20 service worker: Web Push only. Offline caching is intentionally handled separately. */

function safeTarget(path) {
  const scope = self.registration.scope;
  if (typeof path !== 'string') return scope;
  const clean = path.trim().replace(/^\/+/, '');
  if (!clean || clean.includes('..') || clean.includes('://')) return scope;
  try {
    const target = new URL(clean, scope);
    if (target.origin !== self.location.origin || !target.href.startsWith(scope)) return scope;
    return target.href;
  } catch {
    return scope;
  }
}

self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    payload = {};
  }

  const title = typeof payload.title === 'string' ? payload.title : 'Mágina Olivo';
  const body = typeof payload.body === 'string' ? payload.body : 'Tienes una nueva actualización.';
  const tag = typeof payload.tag === 'string' ? payload.tag : 'magina-update';
  const path = typeof payload.path === 'string' ? payload.path : '';
  const icon = new URL('assets/app-icon.svg', self.registration.scope).toString();

  event.waitUntil(self.registration.showNotification(title, {
    body,
    icon,
    tag,
    renotify: false,
    data: { path },
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const target = safeTarget(event.notification.data?.path);
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of windows) {
      if ('focus' in client && client.url.startsWith(self.registration.scope)) {
        if ('navigate' in client) await client.navigate(target);
        return client.focus();
      }
    }
    return self.clients.openWindow(target);
  })());
});

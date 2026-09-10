# V20 · Web Push notifications

## Goal

Deliver user-approved observational alerts (starting with radar) through standard Web Push without coupling farm-domain writes to an external push service.

```text
FarmRadarObservation
  -> radar_alert_rules
  -> notification_intents
  -> magina-notification-dispatch-v1
  -> push_deliveries
  -> PushSenderPort
  -> browser push service
  -> public/sw.js
  -> system notification
```

`notification_intents.status = dispatched` means the dispatcher completed delivery processing and at least one device accepted the push. It is not proof that a person read the notification.

## Consent and privacy

- Never request notification permission on install or first page load.
- Request permission only after the user presses **Activar avisos en este dispositivo**.
- A Push endpoint is a capability URL. Do not log it or expose it in analytics/public APIs.
- Each `push_subscription` is bound to a concrete `user_session`. A revoked/expired session is excluded from delivery even if the browser subscription still exists.
- A subscription already bound to another active account cannot silently move between users.
- The dispatcher re-checks active user + workspace membership before delivering.
- Radar intents also re-check `user_preferences.weather_alerts` at delivery time.

## Delivery semantics

- `2xx`: delivery accepted by push service.
- `404/410`: subscription is permanently revoked server-side.
- `429/5xx/network`: retryable with backoff, maximum five application attempts.
- No active subscription: intent becomes `suppressed`, not falsely `dispatched`.
- Per-device state is stored in `push_deliveries`.
- The notification worker queue uses pg-boss `singleton` policy and a one-minute scheduled pending scan. API test pushes also enqueue an immediate dispatch job.
- A process crash after the remote service accepts a push but before the DB acknowledgement can cause at-least-once transport. Stable notification `tag` values make browsers replace equivalent radar notifications instead of stacking them.

## VAPID configuration

API needs only:

```text
VAPID_PUBLIC_KEY=...
```

Notification worker needs:

```text
WORKER_MODULES=notifications
VAPID_SUBJECT=mailto:admin@your-domain.example
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
```

Generate the VAPID key pair once. Never commit the private key. Production secrets belong in the deployment secret store.

## PWA behavior

`apps/web/public/sw.js` handles only Push/notification click behavior in this phase. Offline caching remains a separate concern. Notification navigation accepts only relative paths inside the registered Mágina scope, including GitHub Pages base-path previews.

## Radar wording

Radar push content remains observational:

- allowed: `Eco de precipitación observado a unos 8 km al oeste.`
- forbidden until a validated nowcast exists: `Lloverá en 20 minutos.`
- do not automatically convert dBZ into mm/h.

## Next production hardening

- configure exact production origin/CORS/CSRF policy when web/API hostnames are finalized;
- add metrics using subscription/delivery IDs only, never raw endpoints or encryption keys;
- add retention cleanup for revoked subscriptions and old delivery rows;
- verify installed-PWA Web Push on Android Chrome and iOS Safari/Home Screen before release.

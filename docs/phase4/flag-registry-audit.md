# F-C-83 — Cross-Repo Feature Flag Registry Audit

**Status:** Findings-only (no code changes).
**Scope:** Phase 10 / t1-captain-contracts-consumer.
**Dependent on:** F-C-52 (event-name + enum codegen registry).
**Long-term owner:** Platform team — Unleash migration TODO.

---

## 1. Executive summary

Captain and backend maintain two independent feature-flag stores with **zero
overlap** and **zero sync mechanism** between them.

| Store           | Mechanism                     | Count (P9+P10) | Rollout control                              |
|-----------------|-------------------------------|----------------|----------------------------------------------|
| Captain         | `BuildConfig.FF_*` via Gradle | 23 flags       | Gradle property flip -> canary APK           |
| Backend         | `process.env.FF_*`            | 25 flags       | `.env` edit -> ECS task restart              |

A flag that needs simultaneous client + server behaviour (e.g., `FF_DISPATCH_ACK_HANDLER`,
`FF_ASSIGNMENT_STATUS_PAYLOAD_V2`, `FF_BOOKING_V2_PAYLOAD`) cannot be flipped atomically
across both sides today. Current mitigation is **backend-first rollout**: server
emits legacy AND v2 shapes in parallel, captain ships v2 consumers flag-OFF, then
Gradle canary flips the flag. Once captain DAU on v2 >= 90%, backend drops the
legacy emit. See the "Rollout plans" section below.

---

## 2. Captain BuildConfig flags (23 shipped, all default OFF unless noted)

Source of truth: `app/build.gradle.kts` `defaultConfig { buildConfigField(...) }` block.

### 2.1 Phase 9 — P9 t1 Coordinator refactor (4 flags)
Gradle property override via `-PFF_BROADCAST_COORDINATOR_REFACTOR=true` etc.

| Flag                                   | Default | Landmark       | Purpose                                                |
|----------------------------------------|---------|----------------|--------------------------------------------------------|
| `FF_BROADCAST_COORDINATOR_REFACTOR`    | OFF     | F-C-05         | Single-owner StateFlow coordinator                      |
| `FF_BROADCAST_SINGLE_OWNER_BUFFER`     | OFF     | F-C-10         | Single-owner buffer ingress                             |
| `FF_BROADCAST_PRIORITY_DRAIN`          | OFF     | F-C-11         | Priority drain ordering                                 |
| `FF_BROADCAST_SHARED_FLOW_INGRESS`     | OFF     | F-C-05 ext     | SharedFlow ingress path                                 |

### 2.2 Phase 9 — P9 t3 Overlay lifecycle (3 flags)

| Flag                                   | Default | Landmark       | Purpose                                                |
|----------------------------------------|---------|----------------|--------------------------------------------------------|
| `FF_BROADCAST_TRANSLUCENT_THEME`       | OFF     | F-C-04         | Translucent FSI theme via activity-alias               |
| `FF_BROADCAST_AUDIO_CONTROLLER`        | OFF     | F-C-06         | App-scoped BroadcastAudioController                     |
| `FF_BROADCAST_FLP_LOCATION`            | OFF     | F-C-16         | Async FLP location + 30s memoization cache             |

### 2.3 Phase 9 — P9 t4 WorkManager (2 flags)

| Flag                                   | Default | Landmark       | Purpose                                                |
|----------------------------------------|---------|----------------|--------------------------------------------------------|
| `FF_HOLD_RELEASE_WORKMANAGER`          | OFF     | F-C-22         | WorkManager-backed HoldReleaseWorker                   |
| `FF_FCM_DATA_ONLY_HANDLER`             | OFF     | F-C-60         | Data-only FCM wake worker                              |

### 2.4 Phase 9 — P9 t2 Driver Assignment bundle (7 flags)

| Flag                                   | Default | Landmark       | Purpose                                                |
|----------------------------------------|---------|----------------|--------------------------------------------------------|
| `FF_DRIVER_ASSIGNMENT_VM_MIGRATION`    | OFF     | F-C-29         | MVVM migration for DriverAssignmentScreen               |
| `FF_DRIVER_TOTAL_SECONDS_FROM_DTO`     | OFF     | F-C-31         | totalSeconds sourced from server DTO                    |
| `FF_DRIVER_TIMER_STABLE_KEY`           | OFF     | F-C-32         | Timer remember keyed on assignmentId                    |
| `FF_DRIVER_ALARM_LOOPING`              | OFF     | F-C-33         | Looping alarm sound until user action                   |
| `FF_PHASE_AWARE_TIMER`                 | OFF     | F-C-34         | Hold phase-aware countdown UI                           |
| `FF_DRIVER_IDEMPOTENT_DISMISS`         | OFF     | F-C-44         | Idempotent dismiss via dismissedAt filter               |
| `FF_PROACTIVE_DRIVER_EVICTION`         | OFF     | F-C-47         | Offline-driver eviction from driverAssignments map      |

### 2.5 Phase 10 — P10 t1 Captain contracts consumers (5 flags — added in this task)

| Flag                                   | Default | Landmark       | Purpose                                                |
|----------------------------------------|---------|----------------|--------------------------------------------------------|
| `FF_ASSIGNMENT_STATUS_ROUTER_V2`       | OFF     | F-C-51         | Route `assignment_status_changed` via typed v2 event    |
| `FF_DISPATCH_ACK_HANDLER`              | OFF     | F-C-53         | Consume backend `dispatch_ack_response` echo            |
| `FF_BOOKING_V2_PAYLOAD`                | OFF     | F-C-55         | Consume v2 booking broadcast w/ transporter context     |
| `FF_ASSIGNMENT_STATUS_PAYLOAD_V2`      | OFF     | F-C-63         | V2 payload (orderId + bookingId oneof)                  |
| `FF_ORDER_PROGRESS_V1`                 | OFF     | F-C-65         | Per-order truck progress event consumer                 |

### 2.6 BroadcastFeatureFlagsRegistry runtime flags (14 — NOT BuildConfig)

These live in `app/src/main/java/com/weelo/logistics/broadcast/BroadcastFeatureFlags.kt`
and use Android `SharedPreferences("weelo_prefs")` rather than Gradle. They can be
flipped at RUNTIME via a debug toggle activity or via ADB `am broadcast`. This is
separate from the BuildConfig flags above.

Flags (all default ON except where noted):
- `broadcastCoordinatorEnabled`
- `broadcastLocalDeltaApplyEnabled`
- `broadcastReconcileRateLimitEnabled`
- `broadcastStrictIdValidationEnabled`
- `broadcastOverlayInvariantEnforcementEnabled`
- `broadcastDisableLegacyWebsocketPath` (default OFF)
- `broadcastOverlayWatchdogEnabled`
- `captainCancelEventStrictDedupeEnabled`
- `captainCanonicalCancelAliasesEnabled`
- `broadcastOverlayMapEnabled`
- `captainReconcileSnapshotEnabled`
- `captainOverlaySafeRenderEnabled`
- `captainBurstQueueModeEnabled`
- `broadcastFreshnessMs` (Long, default 0)

---

## 3. Backend process.env flags (25 shipped)

Source of truth: `weelo-backend/.env.example` lines 179-245.

### 3.1 Hold & dispatch flags (12)

| Flag                                      | Default in `.env.example` | Landmark          |
|-------------------------------------------|---------------------------|--------------------|
| `FF_BROADCAST_STRICT_SENT_ACCOUNTING`     | `true`                   | Phase 3           |
| `FF_CANCELLED_ORDER_QUEUE_GUARD`          | `true`                   | Phase 3           |
| `FF_CANCELLED_ORDER_QUEUE_GUARD_FAIL_OPEN`| `false`                  | Phase 3           |
| `FF_LEGACY_BOOKING_PROXY_TO_ORDER`        | `true`                   | F-C-55 (long-term)|
| `FF_DB_STRICT_IDEMPOTENCY`                | `true`                   | Phase 3           |
| `FF_ORDER_DISPATCH_OUTBOX`                | `true`                   | Phase 3           |
| `FF_ORDER_DISPATCH_STATUS_EVENTS`         | `true`                   | Phase 3           |
| `FF_CANCEL_OUTBOX_ENABLED`                | `true`                   | Phase 3           |
| `FF_CANCEL_POLICY_TRUCK_V1`               | `true`                   | Phase 3           |
| `FF_CANCEL_EVENT_VERSION_ENFORCED`        | `true`                   | Phase 3           |
| `FF_CANCEL_REBOOK_CHURN_GUARD`            | `true`                   | Phase 3           |
| `FF_CANCEL_DEFERRED_SETTLEMENT`           | `true`                   | Phase 3           |

### 3.2 Additional matching + dispatch (8)

| Flag                                    | Default  |
|-----------------------------------------|----------|
| `FF_CANCEL_IDEMPOTENCY_REQUIRED`        | `true`   |
| `FF_MATCHING_CANONICAL_ENFORCED`        | `true`   |
| `FF_ORDER_CREATE_PENDING_QUEUE`         | `true`   |
| `FF_DUAL_CHANNEL_DELIVERY`              | `true`   |
| `FF_ORDER_JOIN_RACE_REEMIT`             | `true`   |
| `FF_H3_INDEX_ENABLED`                   | `false`  |
| `FF_CIRCUIT_BREAKER_ENABLED`            | `true`   |
| `FF_SEQUENCE_DELIVERY_ENABLED`          | `false`  |

### 3.3 Recent additions tracked for client sync (5)

| Flag                                    | Default  | Client counterpart                          |
|-----------------------------------------|----------|---------------------------------------------|
| `FF_DIRECTIONS_API_SCORING_ENABLED`     | `true`   | — server-only                               |
| `FF_HOLD_DB_ATOMIC_CLAIM`               | `true`   | — server-only                               |
| `FF_QUEUE_DEPTH_CAP`                    | `10000`  | — server-only (int, not boolean)            |
| `FF_DISPATCH_ACK_HANDLER`               | TBD      | Captain `FF_DISPATCH_ACK_HANDLER` (F-C-53)  |
| `FF_ASSIGNMENT_STATUS_PAYLOAD_V2`       | TBD      | Captain `FF_ASSIGNMENT_STATUS_PAYLOAD_V2` (F-C-63) |

Note: the "TBD" entries in the last two rows are landing in t5-backend-consumers
during P10. Captain already ships the matching client flag (this task).

---

## 4. Flag overlap / divergence findings

### 4.1 Zero name overlap today

Before this task, NO captain BuildConfig flag had a corresponding backend
`process.env` flag. This makes a two-phase rollout impossible at the contract
layer — you cannot flip a single logical switch and have it take effect on
both sides.

### 4.2 P10 introduces name-matched pairs (2)

| Logical feature              | Captain flag                       | Backend flag                       |
|-------------------------------|------------------------------------|------------------------------------|
| Dispatch ACK round-trip       | `FF_DISPATCH_ACK_HANDLER`          | `FF_DISPATCH_ACK_HANDLER` (t5)     |
| Assignment status v2 payload  | `FF_ASSIGNMENT_STATUS_PAYLOAD_V2`  | `FF_ASSIGNMENT_STATUS_PAYLOAD_V2`  |

These are coordinated by NAME only — there is no sync primitive, no single
control plane. Operators must remember to flip BOTH sides.

### 4.3 Logically-paired flags with DIFFERENT names (3)

| Logical feature                  | Captain                        | Backend                              |
|----------------------------------|--------------------------------|--------------------------------------|
| Booking personalized payload     | `FF_BOOKING_V2_PAYLOAD`        | `FF_LEGACY_BOOKING_PROXY_TO_ORDER`   |
| Order progress event             | `FF_ORDER_PROGRESS_V1`         | `FF_ORDER_DISPATCH_STATUS_EVENTS` (partial) |
| Assignment router v2             | `FF_ASSIGNMENT_STATUS_ROUTER_V2` | (no backend flag — always emits)    |

Divergent naming is a maintenance hazard. Recommendation: at F-C-83 Unleash
migration time, normalize to a single canonical name per logical feature.

### 4.4 Orphan flags (one side only)

**Captain-only** (no backend counterpart; these are purely client-side
concerns — translucent theme, WorkManager migration, timer keys, etc):
- All 14 Phase 9 Captain flags are client-only and correctly orphan.

**Backend-only** (no captain counterpart; server-internal concerns —
idempotency, circuit breakers, outbox patterns):
- Phase 3 hold/cancel/outbox flags are correctly server-only.

---

## 5. Rollout plans (captain <-> backend flag sync)

### 5.1 F-C-53 dispatch_ack round-trip

1. Backend ships `socket.on('dispatch_ack', ...)` handler flag-gated by
   `FF_DISPATCH_ACK_HANDLER=false`. No behaviour change.
2. Captain ships `handleDispatchAckResponse` flag-gated by
   `FF_DISPATCH_ACK_HANDLER=false` (this commit).
3. Backend flips `.env` -> `FF_DISPATCH_ACK_HANDLER=true`. Server now records
   acks AND emits `dispatch_ack_response` echo.
4. Captain canary Gradle build with `-PFF_DISPATCH_ACK_HANDLER=true` consumes
   the echo for telemetry. No business-logic side effect.
5. After 90%+ DAU on canary, fold into default-ON release.

### 5.2 F-C-63 assignment_status_payload_v2

1. Backend ships `emitAssignmentStatusChanged` factory behind
   `FF_ASSIGNMENT_STATUS_PAYLOAD_V2=false`. Factory emits legacy shape.
2. Captain ships typed parser behind `FF_ASSIGNMENT_STATUS_PAYLOAD_V2=false`
   (this commit). Legacy handler stays wired.
3. Backend flips `FF_ASSIGNMENT_STATUS_PAYLOAD_V2=true`. Factory emits
   10-field v2 shape. Captain legacy path still works (backward-compat).
4. Captain canary flips flag. Captain parses typed v2; legacy path becomes
   dead code.
5. After 90%+ DAU, backend drops legacy emit path.

### 5.3 F-C-55 booking broadcast v2 personalization

1. Backend extends `buildBroadcastPayload(booking, transporterContext?)` to
   populate 6 personalized fields when context provided. No flag needed
   (additive, backward-compatible).
2. Backend 3 call sites pass `transporterContext` -> 6 fields populated.
3. Captain `handleBookingBroadcastV2` under `FF_BOOKING_V2_PAYLOAD=false`
   consumes the v2 event for telemetry.
4. Canary flips captain flag. Captain UI shows "X of Y trucks" correctly.

---

## 6. Long-term recommendation — Unleash migration

### 6.1 Current pain

- **Atomic rollout impossible.** Backend env flip + captain Play Store release
  are 1-3 days apart; v2 events risk being consumed by pre-v2 captains.
- **No per-cohort canary.** Captain can't do "10% of transporters see v2"
  without a dual-APK Play Store split.
- **Drift detection is manual.** Flag naming drift (section 4.3) is caught
  only via audits like this one.
- **No kill switch.** Rolling back a bad flag on captain requires another
  Play Store release (3-7 days).

### 6.2 Proposed: self-hosted Unleash + dual-read fallback

Per INDEX F-C-83 proposed fix:

1. Deploy self-hosted Unleash (Docker Compose; isolated Postgres per the
   CLAUDE.md "no migrate deploy" rule on production DB).
2. Backend: new `unleash.service.ts::isEnabled(flag, userId)` with
   `process.env` fallback when Unleash unreachable.
3. Captain: new `UnleashFeatureFlagProvider` implementing existing
   `BroadcastFeatureFlagProvider` interface (section 2.6 above).
   `SharedPreferences` remains as fallback.
4. Migrate 34 flags (23 captain + 11 shared-name backend) incrementally.
5. Metric: `unleash_eval_total{flag,result}` on both sides + alert on high
   `unleash_fallback_total{flag,source=env|prefs}`.

### 6.3 Blast radius / risk

- **Low.** Unleash is read-only from both sides; worst case both systems fall
  back to env/SharedPreferences (current behaviour).
- **Privacy.** Unleash stores per-user flag evaluations — comply with
  existing PII policy for `transporterId`.

### 6.4 Ownership

- **This task (F-C-83):** findings-only audit. No code, no deploy.
- **Follow-up P11+:** Platform team owns Unleash provisioning + dual-read
  shim. Target: 1 quarter.

---

## 7. Audit checklist for future additions

Before adding ANY new `FF_*` flag on either side:

- [ ] Search this doc + `.env.example` for existing flag with same semantic.
- [ ] If flag requires coordinated client+server rollout, pick the SAME name
      on both sides (avoid section 4.3 drift).
- [ ] Default OFF until both sides ship.
- [ ] Document rollout plan (section 5 format) in commit message.
- [ ] Add to `rules/common` security review: verify flag name doesn't leak
      PII in log lines.

---

## 8. Artifacts

- Captain BuildConfig flags: `app/build.gradle.kts:58-93`
- Captain runtime flags: `app/src/main/java/com/weelo/logistics/broadcast/BroadcastFeatureFlags.kt`
- Backend env flags: `weelo-backend/.env.example:179-245`
- This audit: `docs/phase4/flag-registry-audit.md` (F-C-83)
- Parent index: `.planning/phase4/INDEX.md#F-C-83` (backend repo)

---

_Last updated: 2026-04-18 by t1-captain-contracts-consumer (P10 W5b)._

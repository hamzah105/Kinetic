# Phase 3A Native Android Capabilities

## Scope and invariant

Phase 3A adds exactly three Android-native, user-visible capabilities:

- `open_https_url(url)`
- `share_text(text)`
- `open_settings(destination)` where `destination` is `GENERAL` or `WIFI`

The governing invariant remains: **the model proposes intent; Kinetic controls execution**. A
provider can produce only a structured proposal. It cannot call Android APIs, authorize its own
proposal, select an arbitrary Intent, or infer approval from conversational text.

## Module and dependency boundary

```text
:app
  -> :data:android-capabilities -> :core:kernel
  -> :data:model                -> :core:kernel
  -> :data:persistence          -> :core:kernel
```

`:core:kernel` remains pure Kotlin/JVM. It owns provider-neutral typed inputs, registry contracts,
capability policy, approval, durable effect identity, and results. `:data:android-capabilities` is
the only Phase 3A module that imports `Activity`, `Intent`, `Uri`, or `Settings`. `:app` assembles
the Android tools into the existing `ToolRegistry`; there is no second Android dispatch pipeline.

## Capability metadata

The existing `ToolDefinition` and `CapabilityMetadata` model now describes stable ID, name,
description, risk, confirmation requirement, required permissions, distribution availability,
category, and whether the capability crosses the Kinetic application boundary. All three Phase 3A
tools are `CONFIRM`, require interactive approval, require no Android runtime permission, are
available in `PLAY_CORE` and `LAB`, and cross the application boundary.

## Foreground Intent adapter

`ForegroundActivityIntentLauncher` is the centralized Android boundary. `MainActivity` attaches
itself while started and detaches on stop. The launcher keeps only a weak reference and fails safely
when no usable foreground Activity exists. It intentionally does not use
`FLAG_ACTIVITY_NEW_TASK`, a service, a background component, shell, reflection, hidden APIs, or an
application-context launch.

The adapter maps sealed `AndroidActivityRequest` values to platform Intents and catches
`ActivityNotFoundException`, `SecurityException`, and malformed arguments as typed safe failures.
No model-supplied action, component, package, flags, extras, or MIME type reaches this adapter.

## Supported capabilities and validation

### `open_https_url`

The only input is `url`. Validation requires a lowercase `https://` prefix, a syntactically valid
absolute URI with a non-empty host, no user-info, no whitespace/control characters or backslashes,
a valid port, and a maximum length of 2,048 characters. HTTP, file, content, intent, JavaScript,
data, market, custom, missing, malformed, credential-bearing, and oversized URLs fail before an
Intent is built. Dispatch uses only `Intent.ACTION_VIEW` and the validated URI through Android's
normal resolver. A successful result means only: `HTTPS URL opened through Android.`

### `share_text`

The only input is non-blank text of at most 4,000 characters. Kinetic builds an untargeted
`ACTION_SEND` Intent with the fixed `text/plain` MIME type and the fixed `EXTRA_TEXT`, then wraps it
with `Intent.createChooser`. The model cannot select a package, component, recipient, phone number,
email address, network, flags, extras, or MIME type. Opening the chooser is not sending: the user
still selects a recipient or cancels. A successful result means only:
`Android share chooser opened.`

### `open_settings`

The only input is the closed enum `GENERAL | WIFI`. Kinetic maps those values internally to
`Settings.ACTION_SETTINGS` or `Settings.ACTION_WIFI_SETTINGS`. Arbitrary action strings and all
other destinations are rejected. In particular Phase 3A does not expose Accessibility, developer
options, unknown-sources/install, battery exemption, VPN, or device-admin screens. Opening Settings
does not change a setting. Results state only that Android accepted the general or Wi-Fi page
dispatch.

## Structured model schemas

`:data:model` exposes the three definitions only when structured tools are enabled. Each schema is
an object with one required property and `additionalProperties: false`; Settings uses the explicit
`GENERAL`/`WIFI` enum. The local adapter checks exact fields, JSON types, bounds, enum membership,
and the typed kernel contract before producing a `ToolCall`. Printed JSON or ordinary prose remains
non-executable. Provider continuations receive only the bounded `ToolResult`, never raw Android
implementation details.

## Policy, approval, and argument binding

The normal path is:

```text
structured provider proposal
  -> local schema and typed-input validation
  -> ToolRegistry lookup
  -> deterministic capability policy
  -> permission/precondition check
  -> exact interactive ApprovalGate
  -> durable EffectLedger EXECUTING state
  -> foreground Android adapter
  -> bounded ToolResult
  -> model continuation
```

Approval remains bound to approval, session, turn, provider call, tool, and exact typed input.
Every `ToolInput` supplies a canonical value whose SHA-256 digest is persisted as the effect's
`inputBinding`. `markExecuting` verifies that digest. Changing the URL, share text, Settings
destination, or call identity after approval therefore fails closed. Duplicate provider call IDs
remain rejected by the effect ledger. The approval UI uses a bounded safe summary: URL, truncated
single-line text preview, or Settings destination.

Room schema version 3 stores the capability category, cross-application-boundary flag, and effect
input binding. Migration 2-to-3 preserves existing records with a legacy marker; all newly prepared
effects are exactly bound. Approval payloads required for safe recovery retain their exact typed
value, while journal entries continue to store only bounded/redacted summaries.

## Effect durability and process death

The runtime persists `EXECUTING` and the exact input binding before `startActivity`. After Android
accepts dispatch, the effect is marked complete before provider continuation. If the process dies
in the uncertain interval after dispatch, recovery sees `EXECUTING`, fails the turn, and never
launches again. A restored `WAITING_FOR_APPROVAL` record displays the exact pending proposal but
does not execute it. `COMPLETED`, `REJECTED`, `CANCELLED`, and failed/uncertain effects are also never
re-dispatched automatically. The user must create a new turn for any retry.

## Permissions, Play, and privacy boundary

The application manifest still declares only the existing normal `android.permission.INTERNET`.
Phase 3A adds no dangerous permission, package enumeration, Accessibility service, storage access,
overlay, notification listener, MediaProjection, root, shell, hidden API, background service,
dynamic executable code, or package-visibility bypass. Intent construction tests inspect native
integration without repeatedly opening external applications during the automated suite.

## Deferred capabilities

App launching/enumeration, arbitrary Intents, notifications, clipboard, files/SAF, camera,
microphone, location, contacts, SMS/calls, alarms, autonomous background work, Accessibility/UI
clicking, MediaProjection, local models, MCP/A2A/AppFunctions, scripting/NDK work, distribution
flavors, and every Phase 3B capability remain unimplemented and unauthorized.

# Phase 3C: Android Capability Lifecycle and Execution Coordination

## Scope

Phase 3C strengthens the six Phase 3A/3B Android capabilities without adding a
new capability, permission, service, worker, receiver, or background execution
path. It introduces one foreground execution coordinator and one
provider-neutral availability contract. Model providers still only propose
typed tool calls; local code owns availability, approval, dispatch, and durable
effect state.

## Foreground owner

`MainActivity` registers itself with
`ForegroundAndroidCapabilityExecutionCoordinator` from `onResume` and removes
it from `onPause`. This follows Android's foreground lifetime: the resumed
Activity is interactive, while `onPause` is the boundary at which it may no
longer be in the foreground. The coordinator stores only a weak reference and
also rejects an Activity that is finishing or destroyed.

Replacement registration is identity-aware. A late pause/detach from an old
Activity cannot clear a newer resumed Activity. Registration itself never
executes or resumes a tool, so returning from Chrome, Settings, a chooser, a
dialer, or an email composer cannot repeat the prior handoff.

References:

- <https://developer.android.com/guide/components/activities/activity-lifecycle>
- <https://developer.android.com/training/package-visibility/use-cases>

## Availability and execution sequence

The kernel `Tool` contract exposes a local, side-effect-free
`checkAvailability` hook with bounded statuses. The Android adapter maps its
coordinator result into that provider-neutral contract. The sequence is:

1. The provider returns a typed proposal.
2. Kernel validation and policy produce the existing approval request.
3. The user accepts the exact canonical arguments.
4. Local availability is checked while the app has a resumed Activity.
5. An unavailable action fails the turn and effect without entering
   `EXECUTING` or calling Android.
6. An available action is atomically claimed in the durable ledger.
7. The coordinator rechecks the foreground owner at the invocation boundary,
   then performs the single bounded Android effect.

The second check closes the lifecycle race between preflight and dispatch.
Activity intents are constructed locally. Kinetic intentionally does not
enumerate installed applications or use package visibility queries; it invokes
the intent and safely maps `ActivityNotFoundException` when no handler exists.

## Concurrency and cancellation

The agent runtime already serializes turns. The coordinator adds a fail-fast
atomic dispatch guard at the Android boundary, so a second effect is rejected
rather than queued behind an in-flight effect. This is defense in depth for any
future caller outside the current turn path.

Availability is suspendable. Cancellation before the durable execution claim
propagates through it, marks the pending run cancelled/failed, and invokes no
Android API. Once the synchronous platform invocation has been accepted, the
existing exactly-once ledger treats that handoff as performed; cancellation or
lifecycle restoration cannot safely retract or replay it.

## Recreation, backgrounding, and process death

- Backgrounding clears the resumed owner and makes all six capabilities
  unavailable.
- Activity recreation replaces the weak owner without executing an action.
- A destroyed stale Activity is never used for dispatch.
- Returning to Kinetic registers a foreground owner but never redispatches.
- Process recovery may restore a pending approval for explicit user review.
  It never replays an effect already marked executing or completed.

No foreground service, background service, WorkManager job, notification
action, receiver, or retained Activity is introduced.

## Audit and privacy

Structured journal entries record only:

- the call ID, tool ID, and bounded availability status; and
- the call ID, tool ID, and bounded dispatch failure code.

They do not record URLs, shared text, clipboard text, phone numbers, email
addresses, subjects, bodies, credentials, intent URIs, or exception messages.
The existing sanitization, Keystore-backed provider credential storage, and
Room schema remain unchanged.

## Deferred work

Background capability execution, notification-triggered actions, services,
workers, retries, queues, direct calls or messages, contact access, attachments,
installed-app enumeration, and broader Android capabilities remain out of
scope. Phase 4 is not started.

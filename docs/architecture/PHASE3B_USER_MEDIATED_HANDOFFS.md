# Phase 3B: User-Mediated Android Handoffs

## Scope

Phase 3B adds three narrow Android capabilities to the existing proposal/approval/execution kernel:

- `copy_text_to_clipboard(text)`
- `open_dialer(phone_number)`
- `compose_email(recipient?, subject?, body?)`

Every capability has `CONFIRM` approval policy. Model output can only create a typed proposal; it cannot invoke Android APIs or cross an application boundary. Execution occurs only after local validation, an approval bound to the canonical arguments, and the existing exactly-once claim.

## Capability boundaries

### Clipboard write

`copy_text_to_clipboard` writes a single plain-text clip through `ClipboardManager.setPrimaryClip`. Kinetic does not expose clipboard reads, clipboard history, listeners, enumeration, or background monitoring. Text must be nonblank and no longer than 4,000 characters. Approval summaries are bounded so large or sensitive content is not duplicated indefinitely in UI state.

### Dialer handoff

`open_dialer` creates an internal `tel:` URI and launches `Intent.ACTION_DIAL`. It never uses `ACTION_CALL`, never places a call, and requests no phone permission. Phone input is normalized locally and limited to an optional leading `+`, digits, spaces, and dashes, with at most 15 digits. URI construction is internal rather than accepting a model-supplied URI, component, package, flags, or action.

### Email composition handoff

`compose_email` creates an internal RFC 6068 `mailto:` URI and launches `Intent.ACTION_SENDTO`. Recipient, subject, and body are optional, but at least one must be present and nonblank. Recipient syntax and field lengths are validated locally. For Gmail interoperability, Kinetic places the recipient in the URI address and the two fixed fields `subject` and `body` in the URI query, with each dynamic value independently UTF-8 percent-encoded. Standard Android email extras remain as a compatibility fallback. Body line breaks are normalized to CRLF before URI encoding. Kinetic does not send mail, attach files, grant URI permissions, select a target package, add arbitrary mail headers, or use broad `ACTION_SEND` MIME routing. The receiving email application remains responsible for user review and the final send action.

The model never supplies a URI or query key. Characters such as `&`, `=`, `?`, `#`, `%`, `+`, Unicode, and emoji are encoded as field data and cannot create new headers or alter the scheme. Actual control characters remain forbidden in recipients and subjects; percent-looking text is encoded again and is decoded only once by the receiving URI parser. Multiline content is allowed only in the body.

## Dispatch and result semantics

Phase 3C supersedes the original foreground adapter with a resumed-Activity execution coordinator. Activity handoffs and clipboard writes require a current resumed owner; loss of that owner fails closed. The coordinator rechecks lifecycle state at invocation and serializes the Android dispatch boundary. Dispatch returns a narrow result:

- `Dispatched` after the Android API accepted the handoff/write.
- `NoForegroundActivity` when an activity handoff has no foreground owner.
- `NoHandler` when Android has no application capable of resolving the intent.
- `SecurityFailure` or `UnexpectedFailure` for safely classified platform errors.
- `ConcurrentDispatch` when another Android effect is already crossing the platform boundary.

These results mean that Kinetic performed its bounded local effect. They do not claim that a user completed a call or sent an email.

## Approval and recovery

Canonical input serialization is included in the proposal binding hash. Changing clipboard text, phone number, or any email field creates a different binding and cannot reuse a prior approval. Rejecting a proposal records a terminal rejection and performs no Android effect. Claiming before execution preserves the existing exactly-once boundary: a completed or claimed proposal is not replayed after process restart. Pending proposals remain persisted in the existing Room ledger without a schema change.

## Security and privacy posture

Phase 3B adds no manifest permission. The app keeps only its existing network permission for model-provider access. It does not read contacts, call logs, clipboard contents, installed applications, messages, or email accounts. It performs no direct call, SMS, email send, background launch, attachment transfer, or security bypass. Provider credentials remain protected by the existing Android Keystore-backed storage and are not included in tool inputs, approval summaries, logs, or result text.

## Deferred work

Direct calling or messaging, contacts access, attachments, chooser customization, background dispatch, notification actions, installed-app enumeration, accessibility automation, and any broader native capability remain deferred. Phase 3C adds lifecycle coordination only; Phase 4 is not started.

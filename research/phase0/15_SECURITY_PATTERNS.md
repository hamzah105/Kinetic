# Security Patterns and Required Trust Boundaries

## Bottom line

Kinetic must treat the model as an **untrusted planner**, not an authority. A model-produced tool name, argument object, recovery plan, learned skill or confirmation claim is only a request to a deterministic policy layer. The reusable references contain several strong ingredients—OpenClaw's metadata-only audit lifecycle and surface-specific deny sets, Hermes' context-local secret scoping and argument hashing, OpenDroid's all-steps approval check, and AndyClaw's tool attenuation—but none is a complete Android security architecture. Several dangerous bypasses are explicitly available in the reference implementations.

The most important Phase 0 conclusion is therefore structural: authorization, Android permission state, user-presence confirmation, data-flow restrictions, background eligibility and audit recording must live outside prompts and outside provider-specific agent loops.

## Evidence classification

- **Observed** means the behavior is present in this checkout at the named path.
- **Risk inference** means Phase 0 derives a likely failure mode from that behavior; it is not a claim that the repository has been exploited.
- **Kinetic requirement** is a proposed requirement, not an implementation decision or final schema.

## Reference patterns worth preserving

### OpenClaw

Strong observed patterns:

- `E:\Projects\Eco_reference\openclaw\src\security\dangerous-tools.ts` centralizes high-risk tool identities. Gateway HTTP denies command execution, filesystem mutation, session injection, persistent automation, configuration, paired-node control and Android UI control by default. It separately identifies owner-only tools.
- `E:\Projects\Eco_reference\openclaw\src\security\external-content.ts` labels external content, gives each wrapper a random boundary, detects suspicious instruction patterns, and normalizes/removes model special-token spellings and marker-confusable characters. This is useful defense in depth against prompt injection.
- `E:\Projects\Eco_reference\openclaw\src\security\install-policy.ts` models install origin, authority, mutability and network use; validates the configured policy executable and its path permissions; bounds request/output sizes and time; and returns structured findings.
- `E:\Projects\Eco_reference\openclaw\src\audit\audit-event-types.ts` defines a versioned lifecycle for agent runs, tool actions and messages. It includes started/succeeded/failed/cancelled/timed-out/blocked/unknown outcomes and deliberately persists metadata-only records with references instead of raw identifiers.
- `E:\Projects\Eco_reference\openclaw\extensions\policy\src\policy-state-types.ts` collects evidence for tool risk/sensitivity/capabilities, tool posture, sandbox posture, ingress, gateway exposure, data handling, secret sources, execution approvals, routing and network posture.
- `E:\Projects\Eco_reference\openclaw\extensions\policy\src\tool-policy-conformance.ts` resolves named tools, groups and globs into a common policy view.

Limits and risk inferences:

- Random prompt boundaries and suspicious-pattern regexes do not establish authority; a model may still follow malicious content. The decisive control must be tool availability and an out-of-model authorization check.
- Most OpenClaw execution assumptions are desktop/server and Node-centric. Its shell, filesystem, package-install and dynamically loaded plugin surfaces cannot be transferred to a Play-distributed Android core.
- A deny list is a useful last line but a capability allowlist is the safer primary rule. New tools otherwise risk being unintentionally available until someone adds them to a deny list.
- The observed audit record intentionally omits arguments and content. Kinetic should preserve privacy while also retaining a keyed/canonical argument digest, policy version, confirmation reference and coarse sensitivity labels sufficient for incident reconstruction.

### Hermes

Strong observed patterns:

- `E:\Projects\Eco_reference\hermes-agent\agent\secret_scope.py` uses a context-local profile secret map. When multiplexing is active, an unscoped read raises `UnscopedSecretError` rather than falling back to another profile's process environment. Only a tight set of genuinely global configuration variables bypasses the scope.
- `E:\Projects\Eco_reference\hermes-agent\agent\redact.py` has default-on multi-format secret redaction, special strict egress behavior, and explicit forced-redaction boundaries that cannot be disabled by a general logging preference.
- `E:\Projects\Eco_reference\hermes-agent\agent\tool_guardrails.py` canonicalizes arguments, stores a non-reversible SHA-256 call identity, detects repeated exact failures and no-progress reads, and imposes hard per-turn ceilings on web search and delegation.
- `E:\Projects\Eco_reference\hermes-agent\tools\approval.py` keeps approval identity context-local, has per-session pending queues, a user deny floor that applies even in YOLO/off modes, permanent/session/once choices, and special protection for writes to the security configuration itself.

Limits and risk inferences:

- In `agent/tool_guardrails.py`, repeated-call hard stops default to disabled; warnings do not prevent execution. The fixed search/delegation caps still block, but mutation loop safety depends on an opt-in setting.
- Smart approval delegates a security judgment to another model. That may be a useful advisory classifier, but it must not be the sole authorization for an Android effect.
- Hermes ultimately exposes terminal/code execution and broad host tools. Those controls are useful reference semantics, not a safe Android execution substrate.
- Regex-based redaction is never complete. The primary rule must be that secret values and sensitive payloads are not put into prompts, general logs or analytics in the first place.

### OpenDroid

Strong observed patterns:

- `E:\Projects\Eco_reference\opendroid\app\src\main\java\com\opendroid\ai\core\agent\ActionSchema.kt` separates action name, parameters, category and a `neverAutoApprove` flag.
- `E:\Projects\Eco_reference\opendroid\app\src\main\java\com\opendroid\ai\core\agent\AutoApprovalPolicy.kt` checks every primary and fallback action in a plan. AUTO is all-or-nothing; unknown or `neverAutoApprove` actions cannot be granted. The decision code is pure and directly testable.
- `E:\Projects\Eco_reference\opendroid\app\src\main\java\com\opendroid\ai\core\permissions\PermissionModel.kt` represents permission groups and denied/permanently-denied states rather than assuming a permission request succeeds.
- `E:\Projects\Eco_reference\opendroid\app\src\main\java\com\opendroid\ai\core\security\SecurePrefs.kt` uses an Android keystore-backed master key and encrypted preferences, migrates legacy plaintext and fails without silently falling back to plaintext.
- `E:\Projects\Eco_reference\opendroid\app\src\main\java\com\opendroid\ai\core\crash\CrashLogRedactor.kt` performs capture-time redaction so later exporters do not have to remember it.
- `E:\Projects\Eco_reference\opendroid\app\src\main\java\com\opendroid\ai\core\llm\LLMProvider.kt` makes provider request credentials transient and redacts their string representation.

Limits and risk inferences:

- `AutoApprovalPolicy.kt` makes YOLO return `true` before either the grant list or `neverAutoApprove` protection is consulted. Kinetic Core must not have a mode that bypasses irreversible-action confirmation or policy hard floors.
- `PermissionModel.kt` assembles broad permission groups and a grant-all route. That is the opposite of Play-oriented, just-in-time least privilege even though denial modeling is good.
- `ActionSchema.kt` carries only a coarse boolean for irreversible actions. It lacks caller identity, Android scopes, data sensitivity, foreground/user-presence requirements, network destinations, rate/budget policy and audit semantics.
- Capture-time log regexes help but cannot prove that provider keys, user content, screenshots, contacts or notification bodies are absent from all crash paths.

### AndyClaw

Strong observed patterns:

- `E:\Projects\Eco_reference\AndyClaw\AndyClaw\src\main\java\org\ethereumphone\andyclaw\skills\ToolDefinition.kt` combines JSON input schema, approval requirement and Android permission names.
- `E:\Projects\Eco_reference\AndyClaw\app\src\main\java\org\ethereumphone\andyclaw\safety\ToolAttenuation.kt` gives downloaded skills a lower trust tier and removes non-read-only tools before the model sees them. It also prevents shadowing a set of protected tool names.
- `E:\Projects\Eco_reference\AndyClaw\AndyClaw\src\main\java\org\ethereumphone\andyclaw\extensions\security\ExtensionSecurityManager.kt` validates installed package/signing information, optionally pins a certificate digest, rejects a shared host UID, and checks declared permissions before a function call.
- `E:\Projects\Eco_reference\AndyClaw\AndyClaw\src\main\java\org\ethereumphone\andyclaw\extensions\security\ExtensionSecurityPolicy.kt` enables extension signature, permission and UID checks by default.
- `E:\Projects\Eco_reference\AndyClaw\app\src\main\java\org\ethereumphone\andyclaw\safety\SafetyLayer.kt` uses a per-session random wrapper nonce, output limits, leak checks, sanitization and rate limiting when enabled.

Limits and risk inferences:

- `SafetyConfig.enabled` is `false` by default, and `NodeApp.createSafetyLayer()` also disables safety while YOLO is active. Therefore prompt isolation, leak scanning, rate limiting and downloaded-skill attenuation can all be bypassed at once.
- The extension trust predicate accepts descriptor-provided `trusted`, a local trusted-ID list or developer mode. Trust elevation must not be self-asserted by untrusted package metadata.
- An APK merely having a signing certificate is not a trust decision. Without an expected digest, installer identity or user-reviewed publisher relationship, any normally signed APK passes that portion of validation.
- `executionTimeoutMs` appears in the policy data class, but the inspected security manager does not enforce it. The invocation boundary must independently apply timeout/cancellation.
- Isolating an extension into another Android UID is valuable but not a sandbox policy by itself. Exported Binder surfaces, signature permissions, URI grants, network access and payload validation remain separate boundaries.

### AnyClaw

Observed negative patterns that Kinetic must not reproduce:

- `E:\Projects\Eco_reference\anyclaw\internal\pkg\manifest.go` allows CLI, script, pipeline and OpenAPI adapters with only shallow parameter/auth metadata.
- `E:\Projects\Eco_reference\anyclaw\internal\adapter\cli.go` substitutes model-controlled arguments into a string and invokes `sh -c`. This is a command-injection/RCE shape even if the package directory itself is trusted.
- `E:\Projects\Eco_reference\anyclaw\internal\adapter\script.go` launches arbitrary Python or Node scripts.
- `E:\Projects\Eco_reference\anyclaw\internal\adapter\pipeline.go` performs package-directed fetches and browser navigation/evaluation. Without a destination policy this creates SSRF, credential-origin and arbitrary-page-script risks.
- Credential material is stored in a local YAML file protected mainly by filesystem mode. That is inadequate for Android; use Keystore-backed, provider-scoped storage.

Portable idea: package-defined typed commands exposed through MCP. Non-portable implementation: shell/script/browser execution as the adapter substrate.

### MobileRun

Observed architecture-level risks:

- The server and device adapter under `E:\Projects\Eco_reference\mobilerun-main\mobilerun\` control Android through desktop-side drivers and can collect screenshots, UI state and trajectories.
- This is useful for an external test harness and benchmark oracle, not an in-app privilege model. ADB authority, device pairing, host filesystem access and remote MCP clients are outside Kinetic Core's trust boundary.
- Trajectories and screenshots can contain notification text, messages, credentials, location and other third-party app data. Dataset capture needs explicit consent, minimization, encryption, retention and deletion controls.

### AirLLM

Observed architecture-level risks:

- `E:\Projects\Eco_reference\airllm\air_llm\airllm\airllm_base.py` and adjacent loaders dynamically consume very large model artifacts and exercise native/PyTorch/CUDA/MLX parsers. The useful concept is bounded layer streaming, not this execution stack.
- Model files are untrusted supply-chain inputs. Hash/signature verification, provenance, declared license, size limits, safe atomic install and parser isolation are security requirements even for an offline model.

## Kinetic trust-boundary model

```text
User / trusted system event
          |
          v
Intent + context builder ------ untrusted external content
          |                              |
          +---------- labelled data -----+
                         |
                         v
                  Model / planner
                 (untrusted proposal)
                         |
                  typed candidate call
                         v
   Capability broker + deterministic policy engine
   - caller identity       - installed profile
   - schema validation     - Android permission state
   - Kinetic scopes        - foreground/user presence
   - data/network policy   - quotas/rate/budget
   - confirmation token    - policy/version digest
                         |
             allow / deny / ask / unavailable
                         |
                         v
        Native executor or isolated extension IPC
                         |
          bounded/redacted result + audit lifecycle
```

No natural-language text crosses the broker as proof of permission. “The user already approved,” a remembered approval in model context, a skill instruction, or an external-page instruction has no security meaning.

## Proposed capability metadata—not a final schema

The following is a Phase 0 requirements sketch. Field names and wire format are explicitly **not final**.

| Field | Required semantics |
|---|---|
| `toolId`, `version` | Stable identity and compatibility version; external packages cannot shadow reserved IDs. |
| `description` | Model-facing intent only; never grants authority. |
| `inputSchema`, `outputSchema` | Closed, bounded typed schemas; reject unknown keys unless explicitly supported. |
| `requiredAndroidPermissions` | Exact runtime/special access prerequisites, evaluated at call time. |
| `requiredKineticScopes` | App-level least-privilege capabilities independent of Android's coarse permission. |
| `riskLevel` | At least read-only, sensitive read, reversible write, external communication, irreversible/destructive, financial/account-security. |
| `confirmationPolicy` | Never, once-per-call, bounded-session grant, always-user-presence, or prohibited; hard floors cannot be disabled. |
| `dataSensitivity` | Input/output classes such as public, personal, contacts, communications, location, authentication, financial, health, screen content. |
| `networkPolicy` | Offline/allowed destinations/redirect rules; no arbitrary URL by default. |
| `foregroundRequirement` | Whether activity visibility, foreground service, unlocked device or explicit user interaction is required. |
| `auditPolicy` | Metadata fields, retention class, redaction and whether content capture is forbidden. |
| `availabilityPredicate` | OS/API level, installed app integration, profile, hardware, policy flavor and current permission state. |
| `rateBudget` | Per-call and time-window limits for effects, network, tokens, battery and retries. |
| `executorKind` | Built-in native, AppFunction, isolated extension IPC, or research-only external harness. |
| `publisherIdentity` | Built-in Kinetic ID or pinned package/signing identity; never self-asserted text. |

The broker should compute an **effective call grant** from all fields plus current state. A tool is callable only if every required condition is satisfied; a broad user preference cannot silently weaken a hard floor.

## Confirmation and process-death semantics

Kinetic requirements:

1. Canonicalize the exact tool ID, arguments, target identity, policy version and sensitivity class before showing confirmation.
2. Persist a pending approval record only when needed, containing the canonical digest, expiry, originating user/session and a redacted display summary. Do not persist raw secrets merely to reconstruct a dialog.
3. A confirmation token is single-use, short-lived and bound to that digest. Editing an argument or resolving a package/contact/account invalidates it.
4. After process death, an operation with an external effect never auto-resumes from “approved” state. Revalidate identity, permissions, foreground/user presence, policy and current target; require fresh confirmation for irreversible effects.
5. If an executor dies after dispatch and outcome is unknown, record `unknown`; do not retry a non-idempotent operation automatically. Reconcile using an operation-specific idempotency key or provider status API.
6. Cancellation is propagated to the executor, but audit distinguishes “cancellation requested” from “effect definitely cancelled.”

This extends the lifecycle vocabulary observed in OpenClaw's `audit-event-types.ts` and the canonical argument hashing observed in Hermes' `tool_guardrails.py`.

## Secrets and sensitive-data handling

Kinetic requirements:

- Store provider credentials in Android Keystore-backed encrypted storage, scoped by profile/provider/account. Do not expose a generic “list all secrets” tool.
- Executors receive only the secret reference they need, resolve it at the last responsible moment, and never return it to the model.
- Treat prompt context, model caches, memory, tool results, screenshots, notifications and accessibility trees as separate data stores with explicit sensitivity/retention.
- Redact before persistence/export; forced redaction applies at crash, audit, provider-error and analytics boundaries.
- Disable or constrain Android backup for keys, credentials, pending approvals, sensitive memories and captured screen data; document restore behavior.
- Rotate/delete credentials on account removal. Invalidated keystore entries fail closed; recovery must not create a plaintext fallback.
- Provider calls use explicit destinations, TLS, redirect limits and certificate/platform defaults. URLs embedded in model output or web content are data until a network policy accepts them.
- Logs should prefer stable opaque references and coarse event metadata. Content logging is opt-in diagnostic state with prominent expiry and export controls.

## Extension and learned-skill security

Kinetic Core:

- Accept only declarative, validated Skill IR plus built-in executors. No dynamic DEX/JAR/SO, arbitrary shell, Python/Node process, `eval`, or remotely supplied native library.
- A learned skill is a proposed composition of existing capabilities. It can narrow authority but cannot introduce a new capability, permission, destination or confirmation bypass.
- Validate and simulate a learned skill before activation; record source provenance and a versioned digest; show the user the externally visible effects.
- Re-evaluate every step at execution time. Installing or approving a workflow is not blanket approval for all future instances.

Kinetic Advanced/Research:

- Compile as a distinct distribution/profile with visibly different policy and signing/update channel, as mapped in `10_ANDROID_CAPABILITY_POLICY_MATRIX.md`.
- Prefer Android package/UID isolation and explicit Binder/AppFunction-style typed IPC over embedded general-purpose runtimes.
- Pin publisher certificate and package identity, define exported-component permissions, bound request/response size and time, validate URI grants, and apply per-call scopes.
- Assume a compromised extension. It must not read host secrets, impersonate confirmations, register protected tool IDs or access a raw all-powerful broker.

## High-priority abuse cases for design and tests

| Abuse case | Required invariant |
|---|---|
| Prompt injection in email/web/page/notification | Content label cannot change tool policy; sensitive effects still require independently derived authorization. |
| Model invents tool/argument or smuggles unknown fields | Closed schema rejects before execution; no coercion into shell/URI text. |
| Learned skill asks for stronger authority | Compilation fails; a skill's effective authority is the intersection of referenced capabilities and current grant. |
| Replayed approval after arguments change | Digest/expiry/single-use check rejects. |
| Process dies during payment/message/delete | Outcome becomes unknown; no blind automatic retry. |
| Malicious extension self-labels trusted | Trust comes only from host policy and pinned installer/signing identity. |
| Redirect/URL reaches loopback, private IP or credential origin | Destination and redirect policy blocks; DNS/IP are revalidated safely. |
| Screenshot/accessibility result leaks into cloud prompt | Data policy requires explicit route; minimization/redaction/local processing where possible. |
| Tool loops or recovery repeatedly mutates state | Hard effect budgets and idempotency policy stop independent of model instruction. |
| Model artifact is swapped or truncated | Digest/signature, size, atomic install and load verification fail closed. |
| App restored onto a new device | Credentials/approval tokens are absent or invalid; user reauthenticates. |
| Advanced capability appears in Play flavor | Compile-time dependency/manifest/conformance test fails the build. |

## Security gates before implementation approval

1. A written Kinetic threat model names assets, actors, entry points, trust boundaries and abuse cases.
2. A typed capability registry and independent policy-decision interface exist on paper before tool implementation.
3. Core and Advanced capability sets are compile-time testable, not runtime feature flags around the same exported surface.
4. Confirmation lifecycle, process-death and unknown-outcome semantics are specified for every non-idempotent category.
5. Secret/data classes have storage, prompt-routing, network, logging, retention, backup and deletion rules.
6. Extension/skill provenance and signing rules are specified; unknown publisher means unavailable, not “warn and continue.”
7. Audit records are privacy-reviewed and sufficient to reconstruct policy decisions without retaining raw sensitive payloads.
8. Fuzz/property tests cover schemas, canonicalization, policy monotonicity, URL handling, IPC and redaction; device tests cover permissions and lifecycle.
9. Play Core has automated manifest/dependency/source scans for forbidden advanced surfaces and dynamic code.
10. A security review owns bypass modes. No YOLO/developer setting may defeat irreversible-action hard floors, user deny rules, package identity or secret isolation.

## Phase 0 decision

Reuse the **ideas and contracts**, not a complete security implementation. The strongest composite starting point is:

- OpenClaw for audit lifecycle, surface-specific posture and policy evidence;
- Hermes for context-local identity/secrets, canonical call digests and hard deny floors;
- OpenDroid for pure all-steps/fallback approval evaluation and Android failure-state modeling;
- AndyClaw for pre-model tool attenuation and typed isolated-extension concepts.

Kinetic must redesign these into one deny-by-default Android capability broker. Any architecture in which the provider loop directly calls Android actions, or in which safety is a prompt/preference that can be switched off, fails this Phase 0 requirement.

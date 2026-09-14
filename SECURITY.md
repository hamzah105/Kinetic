# Security Policy

Kinetic is an experimental Android agent framework with explicit security boundaries around model output, tool execution, credentials, native code, and Android capabilities.

## Supported versions

Kinetic does not yet have a stable production release. Security fixes currently target the `main` branch.

## Reporting a vulnerability

Please **do not** disclose security vulnerabilities, exploit payloads, private endpoints, API keys, tokens, device identifiers, or user data in a public issue.

Preferred reporting path:

1. Open the repository's **Security** tab.
2. Use **Report a vulnerability** / GitHub private vulnerability reporting if it is available.
3. Include the affected commit, component, reproduction conditions, impact, and a minimal proof of concept that does not contain real credentials or personal data.

If GitHub private reporting is unavailable, open a minimal public issue requesting a private reporting channel **without vulnerability details**.

## High-sensitivity areas

Reports are especially valuable when they involve:

- bypassing `ToolRegistry`, policy, approval, or effect-ledger controls;
- model text, memory, summaries, or external tool output becoming executable authority;
- replay or duplicate execution of side effects after cancellation/process death;
- credential leakage into prompts, logs, Room, diagnostics, or error messages;
- arbitrary Intent, shell, Accessibility, storage, or permission expansion;
- native/JNI memory-safety or model-file integrity issues;
- downloaded executable-code or plugin execution;
- provider-routing/fallback behavior that violates privacy or user choice;
- TLS, endpoint validation, or future MCP/network trust-boundary bypasses.

## Secrets

Never submit real API keys, OAuth tokens, keystores, signing passwords, private certificates, Room databases, or app-private data in issues or pull requests. Use clearly fake fixtures.

## Security invariants

Contributions should preserve these baseline rules:

- Models propose; Kinetic validates and authorizes.
- Ordinary model text is never executable.
- Memory and summaries are untrusted context.
- Risk-bearing actions require the Kinetic policy/approval/effect path.
- Interrupted uncertain effects are not silently replayed.
- Credentials remain outside model-visible and durable conversation content.
- Model files are data; executable native code ships through the signed app build.

This policy is for coordinated security reporting and does not constitute a claim that Kinetic is production-secure or fully audited.

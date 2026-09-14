# Phase 5A physical ARM64 engineering device gate — 2026-09-11

**PHYSICAL ARM64 DEVICE REQUIRED — GATE NOT RUN.**

## Authority and prerequisite result

The owner approved the physical-device engineering gate. Roadmap was read first,
followed by the required Phase5A verification/architecture, emulator attribution,
boot-pressure, cold-recovery, Chrome retry/root-cause, Astra owner acceptance and
development-environment documents. Historical setup/owner documents do not override
the current roadmap or authorize old flows.

Starting authoritative state: Phase4 COMPLETE; Phase4C ACCEPTED; Phase5 IN PROGRESS;
Phase5A IMPLEMENTED / AUTOMATED GATE PASSED / DEVICE GATE BLOCKED; Phase5B NOT STARTED.
Separate Astra/Stellar ENGINEERING DEVICE GATE PASSED / OWNER ACCEPTANCE PENDING.

At **2026-09-11 14:49:55 PKT**, ordinary `adb devices -l` returned exactly one
transport, identified as `sdk_gphone64_x86_64` / `emu64xa`, the emulator. **No USB or
wireless physical-device transport, authorized or unauthorized, was listed.**
No physical phone serial or personal identifiers are recorded in this report.

Under the explicit no-physical-ARM64-device stop condition, no device-specific
shell, installation, app launch or acceptance operation followed. The emulator
was not used as a substitute. This is a missing prerequisite, not a reproduced
Kinetic defect or a failed physical-device test.

## Requested result ledger

| Requested evidence | Result |
|---|---|
| Physical device detected / ARM64 confirmation | No physical device detected; architecture not verified |
| Phone model / Android version / API / ABI | Unavailable; no phone inspected |
| Existing Kinetic installation on phone | Not inspected |
| APK SHA256 / version / signer | Not freshly verified; stopped at device prerequisite before artifact-install stage |
| Permission/security audit | No new physical/artifact audit; historical permission findings remain historical |
| Install/update | NOT RUN; neither fresh install nor update occurred |
| Fresh data versus preserved phone data | Not applicable; no phone accessed |
| First launch / Provider / Memory / Context / Appearance / IME | NOT RUN |
| Local SIMULATED / TEST / streaming / network independence | NOT RUN on physical device; no new live proof |
| Protected Reject / pending background-return / rotation | NOT RUN |
| Kinetic HTTPS dispatch count | No dispatch initiated; no observed physical exactly-once result |
| Browser/chooser / return / Activity recreation / process-death no-replay | NOT RUN |
| Debug disclosures / Room health / provider final state | Not inspected on a physical device |
| New Kinetic ANRs / crashes / startup timeouts | NOT AUDITED on a physical device; do not report zero as a pass |
| Room/migration errors / credential leakage markers | NOT AUDITED; no personal-device logs collected |
| Source changes | None |
| Automated tests/build | Not rerun; no source change/build needed at this prerequisite stop |
| Real local model | None downloaded, installed or tested |
| Physical engineering classification | PHYSICAL ARM64 DEVICE REQUIRED — GATE NOT RUN |

Historical expected artifact, **not newly verified this turn**:

- `app/build/outputs/apk/debug/app-debug.apk`, version `0.4.4-astra-stellar`, appId `dev.kinetic.app`.
- APK SHA256 `D0438351DB6E9EBA22459C1466256999D4D4E49487E43A888AA8062DC4C0A466`.
- Signer SHA256 `42A08797D78E4B0C851A09155E2BF30027055AC1B614E9C7D636007BD7E446F2`.
- Automated baseline244 passed,0 failed,0 errors,0 skipped; lint0 errors/5 unchanged warnings; build previously passed.

These values must be verified again before any later installation; approval does
not waive artifact, device architecture, installed-signer or health checks.

## Safety and preservation

No source, Gradle, manifest, APK, signer, applicationId, Room or provider changes.
No build, install, uninstall, clear, root, model download, cloud request or app test.
No emulator start/restart/configuration/resolution change, database access or
provider access. No emulator data, key, memory or history copied. Device enumeration
alone is not a fresh data-preservation verification; prior evidence remains intact.

Only this report and the final roadmap checkpoint were edited. Roadmap is updated
last. Phase5 IN PROGRESS; Phase5A DEVICE GATE BLOCKED; Phase5B NOT STARTED. The prior
emulator failures remain historical and are not erased or superseded by a physical
pass that has not occurred. No owner acceptance granted.

## Exactly one owner acceptance flow — withheld until engineering passes

After a future physical engineering pass only: on the phone select Local SIMULATED /
TEST, send `hello`, confirm the visible simulated response; send `protected demo`
and Reject, confirming no external action; request `open https://example.com`,
background/return and rotate while pending, confirm no browser yet, then approve
once. Return, force-stop/relaunch only Kinetic, and confirm the result remains
without reopening the browser. Check Debug's NOT RUN/unavailable real-model metrics,
restore Cloud selection (key missing is acceptable on a fresh install), and report
PASS/FAIL. No real cloud key is required. **Do not run this flow yet.**

## Exactly one next task — not begun

Connect and manually authorize a suitable owner-provided physical ARM64 Android
phone, then resume this same Phase5A engineering gate. The owner should unlock the
phone and review/approve its USB debugging prompt personally. No security-setting
bypass, emulator fallback or Phase5B work is authorized.

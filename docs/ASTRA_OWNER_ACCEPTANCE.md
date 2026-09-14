# Owner acceptance — Astra / Stellar gate

Engineering results do not mark owner acceptance. Use the existing installation; do not
uninstall or clear app data. Do not paste any provider credential into Codex or a report.
Report only pass/fail, safe error type, and which step failed. These are foreground tests,
not permission for a later functional phase.

Prerequisite satisfied September 9, 2026: the installed-build startup/visual engineering
gate passed as documented in [the verification checkpoint](ASTRA_ENGINEERING_VERIFICATION.md).
Historical emulator ANRs remain recorded. None of these owner flows is automatically
accepted by that engineering result. The currently recommended single next owner flow
is Flow 3 below; do not use destructive recovery or begin Phase 5.

## 1. Astra provider flow

1. Tap the model status or Menu → Provider → OpenAI settings. Enter your real OpenAI key
   only in Kinetic. Select Fast, leave structured tools off, Save settings, then Use OpenAI.
   Account/model access is required; an authentication/quota/model-access failure is not a pass.
2. Start a new conversation. Send a short greeting containing a harmless unique word, then
   ask for that word. Confirm streamed text, continuity and truthful completion. Cancel one
   longer response and confirm it stops without a completed partial message.
3. Inspect Debug local metrics. Unknown usage/cost must say unavailable, not a fabricated zero.
4. Switch to Fake and back to OpenAI. Save with the key field blank, change profile, rotate,
   and restart the app. Confirm the stored-key indicator and a short real response still work.
   Check compatible/OpenRouter settings still show the previous configuration and stored key.
   Do not clear either real key merely to test deletion; selected-only Clear has synthetic
   Android coverage.

## 2. Tool-safety flow

1. In OpenAI settings enable Kinetic structured tools, Save, and select OpenAI. Ask for an
   echo of `KINETIC_ASTRA_OK`; confirm a genuine structured tool result and continuation.
2. Ask to open `https://example.com`, even if the prompt claims it was already approved.
   Confirm an approval card with exact URL and external-data/reversal disclosure. Reject;
   nothing should open.
3. Request it again. While approval is pending, background/foreground and rotate Kinetic.
   Nothing should execute. Approve once; the browser should open once. Return to Kinetic,
   recreate the Activity and force-stop/relaunch: there must be no duplicate dispatch or replay.
   Results must describe opening the browser, not completion of an external task.

## 3. Stellar / memory flow

1. In Menu → Appearance try Kinetic Light, Stellar Dark, Follow system and dynamic color.
   With system font scale at 200%, rotate and resize the window. Check that chat, composer,
   approval controls and scrollable sheets remain reachable. Use keyboard Tab and TalkBack
   to check meaningful labels/order, visible focus and no decorative-star announcements.
2. Open Memory, Context and Debug. Verify CURRENT/NEEDS ATTENTION/HISTORY labels, provenance,
   edit/replace/resolve/delete availability and the deterministic context explanation.
3. Finish the pending owner Phase 4C repair retest using the existing test preference:
   `Update my preferred Kinetic test database to SQLite.` Check that the legacy PostgreSQL
   row is now superseded, SQLite is current, and preserved history remains visible. In a new
   conversation ask for the preferred test database, inspect context, and restart/repeat.
   PostgreSQL must not be presented as another current value. Do not delete unrelated memories.
   The earlier Part B conflict/resolution acceptance remains recorded separately.

Only after these owner checks pass should the gate be marked owner accepted. Phase 5 remains
not started in this checkout; this document does not authorize it.

# Phase 4B Provider Credential Retention Audit

## Incident classification

The audit classifies the regression as **APPLICATION BUG — reproduced and fixed**. The historical
automation selected `Use Cloud` at `(520, 700)`, inside its recorded bounds
`[434,672][602,725]`; `Clear API key` was at `[364,2006][583,2059]`. The clear control was not
invoked. The provider preference hash changed on the mode-setting write and both encrypted fields
were absent afterward.

The source had two deletion paths. `clearApiKey()` was the intended explicit user path.
`apiKey()` was an unintended second path: any Base64, AES-GCM, or Android Keystore decryption
exception caused both the ciphertext and IV preferences to be removed. A pre-fix Android test
saved a synthetic key, made its ciphertext undecryptable, read the credential, and reproduced the
silent deletion of both fields.

## Retention semantics

`clearApiKey()` is now the only code path that removes either encrypted preference. Credential
reads require the existing Keystore alias. They never create a replacement key during decryption,
never remove encrypted material on failure, and return a bounded configuration error asking the
user to clear and replace the key explicitly. Raw crypto failures, ciphertext, IVs, and plaintext
are not logged or surfaced.

Non-secret provider writes explicitly retain and verify the existing ciphertext and IV. This
applies to Fake/Cloud mode changes, blank-key configuration saves, and automatic-compaction
settings. A nonblank key field atomically replaces the encrypted pair. A blank key field means
that the existing key is retained. Only the explicit Clear action removes the pair.

## UI and verification

The provider panel never redisplays plaintext. With a stored key it labels the field as
`Replace API key` and says that a blank field retains the stored key. Without one it says
`No key stored`. The separately enabled `Clear API key` action remains the only deletion action.

Android regression coverage uses only synthetic markers and verifies reopen/reconstruction,
Fake/Cloud round trips, blank base-URL/model saves, validation failure, compaction isolation,
replacement, explicit Clear, preference/Room/journal exclusion, and Room migration isolation.
The final device gate is designed to additionally cover Activity recreation, force-stop/relaunch,
and the actual Compose provider-settings flow without making a public provider request.

# Issue #10 → Issue #12 recipient confirmation boundary — 10 November 2026

- **Decision status:** Explicit requester-directed implementation-ownership allocation for the #10/#12 handoff; not a claim of full-team formal sign-off, and not a change to the MVP or Atlas requirement text
- **Scope:** MVP; self-send and direct account-to-account delivery remain in the MVP
- **Existing PR:** [#33](https://github.com/theguardianofthebed/Time-Capsule/pull/33) on `feature/10-accounts-recipient` (Ready for review at 11 November pre-merge inspection; check PR for the actual merged SHA)
- **Base:** `main` at `d60187503e45f665004dcc99c59cf71c108e6502`; **latest code head inspected:** `16c8038615500b96ae9590611b1e32973f58b574` and **student review documentation head:** `82858400a8f0e6ba44be6606543c7e572eb8fef1` (read the current branch SHA on resume)
- **Implementation contributions:** User G's Issue #10 work and User J's emulator/stabilization contributions are recorded in their weekly logs. The requester reports a different peer has reviewed and approved in conversation; no submitted GitHub review was present at inspection. Do not claim a named or formal GitHub approval. Issue #12 acting owner and reviewer are not yet established here.
- **Prerequisites:** Issue #9 foundation merged through PR #29 at `c60d866cdc9794af97db129c946b66711270f6b9`. Issue #12 still depends on #9 and the **merged, verified bounded #10**. #11 and #12 may proceed concurrently only after their respective readiness checks. No dependency edge is changed.
- **Traceability:** FR-01, FR-02, FR-03; DD-03, DD-19, DD-26, DD-27; ARC-01, ARC-06; AC-01, AC-03; T-01, T-03, T-39. Shared AC/test requirements are not considered fully passed by this split.
- **Workspace:** Remote branch inspected; local worktree and uncommitted files were not inspected. Do not overwrite other contributors' local work.

## Exact handoff contract

**Issue #10 delivers only the recipient lookup/confirmation *interface*, not the user-facing S-05 confirmation screen.** Keep its other bounded account responsibilities: email/password registration, login/logout, profile creation/repair, own UID display/copy, small `recipientDirectory/{uid}` record and owner-only writes, authenticated single-UID reads (no directory listing), typed errors, and session safety.

Existing source interface, subject to final review:
- `AccountSessionManager.lookupRecipient(uid)` trims an exact UID and returns typed `OperationResult<RecipientConfirmation>` with a `RecipientProfile(uid, displayName)` and `isConfirmed = false` on success.
- `AccountSessionManager.confirmRecipient(confirmation, confirm)` returns a confirmation state; the interface does **not** replace an explicit user action.
- `DefaultCapsuleRepository.getRecipient(uid)` and `DefaultFirestoreAdapter.getRecipientProfile(uid)` provide the exact lookup boundary. Keep a single known UID, not prefix/substring search or a directory list.
- Absent/invalid UID, network unavailability and denied access must not be presented as interchangeable. Do not treat cached/offline absence as authoritative without proof.
- Never carry a confirmation or in-flight lookup from a different session into the active draft.

**Issue #12 owns the S-05 View/XML/ViewModel UI**, alongside the S-04 content and S-06 review flow. For the recipient section in S-05, the implementer must:
1. Provide `Me` (self) or an exact other-user UID entry.
2. Show the resolved **UID and display name** before enabling explicit confirmation. A successful lookup is *not* automatically a confirmation.
3. Require the user to confirm that displayed identity. Reset confirmation when the UID text changes, account/session changes, or a later lookup fails. Do not let a stale asynchronous lookup re-confirm an edited target.
4. Block progression/send review on blank/absent/denied/unavailable identity or unconfirmed recipient. Explain failure and offer Retry for unavailable lookups; do not assert nonexistence on network failure.
5. Pass an explicitly confirmed UID/name into the mutable draft and S-06 review. Frozen publication is #14; no fake send success in #12.
6. Keep recipient and Time/Location/Both lock sections together within S-05; keep Android Views/XML + ViewBinding and the approved eight main screens.

## Current evidence and known limitations

- At inspected implementation head `16c8038615500b96ae9590611b1e32973f58b574`, the account/recipient interface and S-01/S-02/S-08 work exist; the S-05 screen remains a placeholder. This is intentional under the newly approved ownership split.
- The User J weekly log reports local Debug build, lint, **47 JVM unit tests** and **9 Android 17 Firebase Emulator instrumentation tests** passing. On **11 November 2026**, the requester additionally reported a passing **9/9** instrumentation run on the new Windows laptop after resolving an Android 17 AVD UWB-service issue. The exact local SHA was not printed; use the source commit SHA and report as distinct evidence. These documentation edits did **not** rerun Android tests, and no GitHub CI run or formal GitHub review was observed at inspection.
- **Updated concurrency assessment:** PR #33 implementation commit `16c8038` serializes state-mutating sign-in/sign-up operations through a coroutine `Mutex` and adds delayed Auth/SDK-side-effect and logout-in-flight unit tests. This mitigates the previously identified overlapping sign-in race in the same `AccountSessionManager`. `signOut()`, profile repair and manager recreation are not covered by the same mutex; the passing bounded tests do not prove all multi-manager concurrency cases. Reviewer should treat any reproducible account-isolation failure as a blocker and document narrower residual concerns as follow-up; no such new failing regression was supplied for this merge.
- The #10 recipient API existing as code does not constitute passing the entire end-to-end T-03/AC-03: S-05 explicit confirmation UI is owned and verified with #12, full send/integration with #14/#23.
- **Review and merge checkpoint:** PR #33 was marked Ready for review; peers' go-ahead and the explicit merge instruction were reported by the requester. This is not a formal recorded GitHub approval. Actual merged SHA must be retrieved from the live PR and reflected in Issue #10 after merging.

## Next action

**Issue #10:** User G's bounded account implementation with User J's stabilization evidence is ready for the requester-authorized PR #33 merge, conditional on GitHub's merge checks. Record the actual merge SHA and close the issue only after the bounded result is verified; do not fabricate GitHub approval or broader MVP acceptance. Work period: current 11 November handoff/merge checkpoint, not a newly promised deadline.

**Issue #12:** A team member must explicitly claim ownership and a different reviewer. After #10 is merged and its bounded evidence is verified, implement S-05 recipient confirmation UI using the interface above, then test UID change/lookup failure/account switch before integrating S-06. This work is MVP, not an optional enhancement.

This document records a requester-directed implementation boundary only; independent teammate review remains required. It neither edits the live Atlas nor waives any course or product acceptance requirement.

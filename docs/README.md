# TimeCapsule documentation

2026-10-13 Notion handoff / 노션 설계 초안 연결.
These are working snapshots, not team-approved final specifications. Keep live review threads in Notion; update snapshots through reviewed PRs after changes.

## Requirements and review

- [Requirements](requirements.md) — current Option B working baseline, with open follow-up questions.
- [Acceptance criteria](acceptance-criteria.md) — criteria, not test passes.
- [Decision log](decisions.md) — includes historical questions and the latest reconciliation.
- [Test ideas](test-ideas.md) — planned checks and discussion dependencies.

## Design inputs

- [User flow](user-flow.md)
- [Screen list](screen-list.md) — wireframe handoff for Junwon.
- [Architecture](architecture.md)
- [Data model](data-model.md)
- [Test outline](test-outline.md)

## Proposal preparation

- [Requirements freeze preparation](requirements-freeze.md) — not frozen.
- [WBS draft](wbs.md) — not finalized implementation assignments.
- [Proposal review checklist](proposal-review.md) — slides/wireframes not yet reviewed.

## Open joint decisions / 공동 결정 대기

Self-send preview vs recipient locking; post-send editing; stable-ID retries vs identical-content checks; creation-time-zone metadata; backend/media provider and billing. Preserve original discussions until everyone involved responds. Numerical photo/location limits are initial draft values, not validated device guarantees.

Account-based self/registered-user delivery is current MVP. Voice, public feed/comments/reactions, advanced social features, external link/QR sharing, self-destruct, new-receipt FCM/periodic sync and GPS anti-spoofing are excluded from the working MVP. Firebase and Room are not final selections.

The repository has no production Android/backend implementation on main at this handoff. Isolated SpikeLab E1 evidence is not integrated app E2 or repeated verification E3.

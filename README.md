# TimeCapsule

Mobile Programming team project: create text/photo capsules for yourself or another registered user, then open received content after selected time/location conditions are satisfied.

## Project status / 현재 상태

The current working MVP is Option B: basic accounts, self/registered-user send/receive, text plus optional photo, time/location locks and current-time/current-location AND. Once unlocked, received content remains viewable. New receipt sync happens on app open; notifications are non-authoritative and exact-second arrival is not guaranteed.

This repository is at the documentation/bootstrap stage. It does not yet contain a buildable production Android app or verified backend integration. Firebase, media storage/billing and local-cache technology remain unresolved. Several product choices still need joint review; these documents are not unanimous approvals.

- [Documentation and open decisions](docs/README.md)
- [Contribution workflow](CONTRIBUTING.md)
- [Agent safety and scope rules](AGENTS.md)
- [Team Notion workspace](https://www.notion.so/c32f1c1dcaa38204b40a817b528e0cf8)

## Collaboration

Requirement → Acceptance Criteria → Design → Issue → Implementation → PR → Review → Test → Merge.

Geonwoo prepares requirements/design/review; Flora leads product/presentation; Junwon leads feasibility/implementation/integration. All members review and eventually implement; later assignments are agreed by the team. Preserve joint discussions until all requested opinions are given.

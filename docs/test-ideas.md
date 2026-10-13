<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3eff1c1dcaa38332bb54018efd2f4e91 -->
> Working snapshot from [Notion](https://www.notion.so/3eff1c1dcaa38332bb54018efd2f4e91), exported 2026-10-13. Review is pending; comments and approvals remain in Notion. This export is not a scope freeze or executed test evidence. The latest review addendum distinguishes historical questions from current decisions.

# Define Test Ideas
## 목적과 범위 / Purpose & Scope
FR/NFR v0.1과 acceptance criteria를 검증할 테스트 아이디어다. 아직 수행 결과나 통과 판정은 없다. 학기 프로젝트 규모에 맞춰 상태 로직은 작은 반복 테스트, 계정 전달·권한·복구는 통합/수동 검증, 위치·백그라운드는 feasibility spike로 나눈다.
[Notion source](https://app.notion.com/p/a2cf1c1dcaa382fea69301a4867e6315)
[Notion source](https://app.notion.com/p/3d1f1c1dcaa3824bbff58133eb53130c)
<details>
<summary>English Version</summary>
These ideas validate FR/NFR v0.1 and acceptance criteria; no tests have been executed or marked passed here. Use focused repeatable logic tests, integration/manual checks for delivery/permissions/recovery, and feasibility spikes for location/background behavior.
</details>
## 1. 초기 Vertical Slice / First Priority
**T-01 / AC-02, 04:** 텍스트 캡슐 생성 → 미래 `unlockAt` 저장 → 시간 전 내용 숨김 → 시간 도달/경과 후 열람. 알림 없이도 상태 판정이 맞는지 확인한다.
**T-02 / AC-04:** 통제된 시각으로 `unlockAt` 직전/같음/직후를 평가한다. 시간 조건 충족은 각각 거짓/참/참이어야 한다.
**T-03 / AC-07:** 앱 종료 후 백그라운드 이벤트를 누락시킨다. 기한이 지난 뒤 재실행하면 저장된 조건을 읽어 열 수 있어야 한다. 기한 전 재실행 대조군은 잠김이어야 한다.
**T-04 / AC-09:** 잠금 해제 후 반복 평가·재실행해도 다시 잠기지 않는다. 이후 위치 통합 단계에서 위치 이탈도 확인한다.
<details>
<summary>English Version</summary>
**T-01 / AC-02, 04:** Create a text capsule → persist future `unlockAt` → hide content before expiry → allow viewing at/after expiry. Verify state without notification delivery.
**T-02 / AC-04:** Evaluate immediately before, exactly at and after `unlockAt` using controlled time; expected time eligibility is false/true/true.
**T-03 / AC-07:** Close the app and suppress the background event. Relaunch after expiry must load persisted conditions and allow viewing. A pre-expiry control remains locked.
**T-04 / AC-09:** Repeated checks/relaunch after unlock must not relock. Test leaving the location after location integration.
</details>
## 2. 계정·콘텐츠·직접 전달 / Account, Content & Delivery
**T-05 / AC-01, 03:** 테스트 계정 A에서 A로 전송, A에서 B로 전송한다. B의 별도 로그인 세션에서 수신 목록을 확인하고 관계없는 C 계정에서 접근할 수 없는지 확인한다. 목록과 상세 열람 모두 점검한다.
**T-06 / AC-02, 09:** 텍스트·사진을 저장하고 재실행/잠금 해제 후 값과 사진을 확인한다. 입력 오류와 사진 읽기 실패에서 완료 오표시·비정상 종료가 없는지 점검한다. 제한 수치 확정 후 최대/초과·빈 입력 사례를 추가한다.
**T-07 / AC-03, 10:** 네트워크 연결 실패/저장 실패를 유도하고 성공으로 표시하지 않는지 확인한다. 백엔드 설계 후 재시도 시 중복 캡슐·잘못된 수신 계정 문제가 없는지 통합 검증한다.
<details>
<summary>English Version</summary>
**T-05 / AC-01, 03:** Send A→A and A→B. Verify receipt in B's separate login session and denied access for unrelated C. Check both lists and detail access.
**T-06 / AC-02, 09:** Save text/photos and verify values/images after relaunch/unlock. Check invalid input and photo-read failures for false completion or crashes. Add maximum/excess/empty boundaries once limits are defined.
**T-07 / AC-03, 10:** Induce network/storage failure; verify it is not shown as success. After backend design, test retries for duplicate capsules and incorrect recipients.
</details>
## 3. 위치·AND·재열람 / Location, AND & Continued Access
**T-08 / AC-05:** 합의된 위치 판정의 안/밖·경계를 통제된 위치 입력으로 테스트한다. 반경·정확도 확정 전 수치를 합격 기준으로 넣지 않는다. 에뮬레이터 결과와 가능하면 실기기 관찰을 구분한다.
**T-09 / AC-06:** 최초 해제 전 시간/위치 4조합을 테스트한다. 거짓/거짓, 참/거짓, 거짓/참은 잠김, 참/참만 열람 가능해야 한다. 기한 전 방문 후 이탈 사례는 위치 이력 정책 확정 후 기대 결과를 추가한다.
**T-10 / AC-05, 10:** 위치 권한 거부/재허용, 서비스 꺼짐/복구, 위치 조회 실패를 검증한다. 실패만으로 내용이 노출되지 않고 안내가 나와야 한다.
**T-11 / AC-09:** 위치·복합 잠금을 한 번 해제한 후 장소를 벗어나고 앱을 재실행한다. 해제 상태와 재열람이 유지되어야 한다.
<details>
<summary>English Version</summary>
**T-08 / AC-05:** Test inside/outside/boundary cases using controlled locations against the selected rule. Do not invent numeric pass limits before radius/accuracy decisions. Distinguish emulator results from real-device observations where available.
**T-09 / AC-06:** Test all four time/location combinations before initial unlock; only true/true permits viewing. Add visit-before-time-and-leave expectations after the location-history policy is decided.
**T-10 / AC-05, 10:** Deny/regrant location permission, disable/restore services and induce query failure. Failures must not expose content and must show guidance.
**T-11 / AC-09:** After initial location/combined unlock, leave the place and relaunch; unlocked state and viewing remain available.
</details>
## 4. 알림·백그라운드·복구 / Notification & Recovery
**T-12 / AC-08:** 알림 권한 허용/거부 각각에서 동일한 시간 조건을 평가한다. 알림 유무와 무관하게 열람 가능 상태가 같아야 하며 거부 시 비정상 종료하지 않아야 한다.
**T-13 / AC-07, 08:** foreground, background, 화면 꺼짐, 기한 이후 재실행을 나눠 조건 충족·저장 상태·알림 발생을 각각 기록한다. 알림 지연이 상태 판단 오류로 취급되지 않도록 구분하고 정확한 초 도착을 요구하지 않는다.
**T-14 / AC-08:** 알림 클릭 시 대상 캡슐과 실제 상태를 다시 확인한다. 중복 이벤트/반복 평가가 불필요한 중복 알림이나 상태 역전을 만들지 않는지 확인한다.
**T-15 / NFR-01, 06:** 선택한 구현에 따라 재부팅 후 저장 데이터와 관련 작업 재등록을 spike로 확인한다. 자동 알림 복구 결과와 앱 재실행 시 상태 복구 결과를 따로 기록한다. 검증 전 모든 종료/강제 중지 상황에서의 동작을 보장하지 않는다.
<details>
<summary>English Version</summary>
**T-12 / AC-08:** Evaluate the same time condition with notification permission granted and denied. Eligibility must match regardless of notifications; denial must not crash.
**T-13 / AC-07, 08:** Separate foreground, background, screen-off and post-expiry relaunch cases. Record eligibility, persisted state and notification occurrence independently; exact-second arrival is not required.
**T-14 / AC-08:** Notification navigation checks the actual capsule/state. Test repeated events/evaluation for unnecessary duplicate notifications or state reversal.
**T-15 / NFR-01, 06:** Spike persistence and relevant work re-registration after reboot for the chosen design. Record automatic notification recovery separately from state recovery on relaunch. Do not guarantee all termination/force-stop cases before validation.
</details>
## 5. 결과 기록과 설계 의존성 / Evidence & Dependencies
각 테스트에 요구사항/AC ID, 기기·앱 버전, 계정, 권한/서비스 상태, 입력 조건, 기대 결과, 실제 결과, 저장 상태, 알림 시각/지연, 통과·실패·보류 및 증거를 기록한다.
초기 시간 slice → 사진·계정 전달 → 위치·AND → 백그라운드 복구 순으로 검증 범위를 늘린다. 구체적인 저장소·위치·수신자 정책이 미정인 사례는 보류 사유를 남기고 설계 결정 후 기대 결과를 확정한다. 준원의 기존 반복 횟수·성공률 제안은 spike 검토 기준이며 제품의 보장 수치로 전환하지 않는다.
<details>
<summary>English Version</summary>
For each case record requirement/AC IDs, device/app version, accounts, permission/service state, inputs, expected/actual outcome, persisted state, notification time/latency, pass/fail/blocked status and evidence.
Expand validation from the time slice to photos/account delivery, location/AND and background recovery. Mark design-dependent cases blocked with a reason and finalize expectations after decisions. Junwon's proposed trial counts/success rates are spike review gates, not product guarantees.
</details>
## 10/13 실행 계획 보완 / Current execution-plan addendum
아래는 위 초기 테스트의 현재 FR/AC 대응을 보완한다. 기존 원문/댓글은 보존하며 실제 테스트는 NOT RUN이다.
- T-01~03: 온라인 서버 확인으로 최초 해제. 시간 전/같음/이후, 현재·과거 입력 거부, 기기 시각 변경·시간대 이동, 오프라인 실패 후 온라인 복구를 포함.
- T-06: 텍스트만/사진만/둘 다/둘 다 없음, 최대 1장 JPEG/PNG 5 MiB 허용/초과/지원외, 부분 업로드 실패 확인.
- T-07: FR-03/AC-03에는 stable capsuleId 재시도가 이미 추가됨. Flora의 동일 내용 판정 제안과 차이를 답변하고 공동 논의 유지. 합의 전 이를 ‘팀 승인된 최종 테스트 계약’으로 표시하지 않음.
- T-08: 현재 초안의 150m/샘플 60초/정확도100m 안·밖·경계·오래된/부정확한 샘플. 숫자는 초기 작업값이고 실기기 보장 아님.
- T-09: 서버 시간/현재 위치 4조합, 시간 전 방문 후 이탈은 이후 시간만 지나도 잠김. 시간 후 위치 진입·신선 위치 확인 시 해제. Flora 요청대로 화면 시간 대기/위치 대기/해제 확인.
- T-14: 알림 클릭이 계정/실제 상태를 우회하지 않음은 AC-08 근거 있음. 중복 알림 억제는 설계 권고/검증 관찰로 기록하고 제품 FR/AC 추가 여부는 팀 검토 전 확정하지 않음. 상태 역전 금지는 FR-12/NFR-04 근거 있음.
[Notion source](https://app.notion.com/p/3edf1c1dcaa38031b6b8ef106057d537)
<details>
<summary>English Version</summary>
Current addendum: online server-confirmed initial unlock and offline recovery; supported one-photo boundaries; stable-ID retry is already present in FR/AC but the latest alternative needs joint agreement; initial 150m/60s/100m location cases; current AND with visit-before-time-and-leave remaining locked and stage UI. Notification navigation/account checks are AC-08 requirements; duplicate-alert suppression remains a design observation/recommendation unless promoted by review. No test pass is claimed.
</details>

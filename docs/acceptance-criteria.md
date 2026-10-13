<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3d1f1c1dcaa3824bbff58133eb53130c -->
> Working snapshot from [Notion](https://www.notion.so/3d1f1c1dcaa3824bbff58133eb53130c), exported 2026-10-13. Review is pending; comments and approvals remain in Notion. This export is not a scope freeze or executed test evidence. The latest review addendum distinguishes historical questions from current decisions.

# Define Acceptance Criteria
## 목적 / Purpose
FR/NFR v0.1의 각 주요 동작을 검토·테스트 가능한 성공 조건으로 연결한다. 이는 실행 결과가 아니라 검토용 기준이다.
[Notion source](https://app.notion.com/p/a2cf1c1dcaa382fea69301a4867e6315)
<details>
<summary>English Version</summary>
Map the major FR/NFR behaviors to reviewable, testable success conditions. These are review criteria, not executed test results.
</details>
## Acceptance Criteria / 기능별 성공 기준
**AC-01 / FR-01 — 계정 경계:** 본인 계정으로 보낸/받은 목록에 접근할 수 있다. 관계없는 다른 계정에서는 해당 수신 캡슐을 열 수 없다.
**AC-02 / FR-02, 04 — 생성·콘텐츠:** 텍스트만·사진만·둘 다 각각 유효한 수신자 및 시간 또는 위치 중 최소 하나의 조건으로 생성할 수 있고 저장된 값이 일치한다. 시간만·위치만·둘 다를 각각 확인하며 둘 다면 AND이다. 내용 둘 다 비어 있음/잠금 조건 없음은 거부한다. 필수 입력 오류나 저장 실패 시 완료로 표시하지 않고 수정/재시도 안내를 제공한다. 사진 1장/JPEG·PNG/최대 5 MiB의 허용 경계와 초과·지원 불가 파일의 안내/재선택을 확인한다.
**AC-03 / FR-03, 11 — 직접 전달:** 자기 자신과 다른 테스트 계정에 각각 전송하면 올바른 수신 계정의 목록에 나타난다. 다른 계정 전송은 별도 계정 세션에서 확인한다. 네트워크 실패는 전송 성공으로 표시하지 않는다. 필요한 사진 업로드와 메타데이터의 서버 확인 및 수신 목록 조회 가능 상태 이후에만 전송 성공이다. 상대 앱 실행은 필요 없고 로컬 대기 쓰기는 성공이 아니다. 잘못된/확인 불가 수신 코드는 거부하며 같은 전송 재시도는 동일 capsuleId로 중복을 만들지 않는다.
**AC-04 / FR-05, 08, 09 — 시간 조건:** 아직 잠긴 시간 전용 캡슐은 `unlockAt` 직전에는 내용이 숨겨지고, 해당 시각 또는 이후 온라인 서버 확인이 성공한 평가에서는 열람 가능하다. 현재/과거 시각 입력은 거부하고 기기 시계를 앞당겨도 조기 해제되지 않는다. 오프라인 최초 해제는 잠김+안내/재시도이며 시간대 변경은 저장된 시점을 바꾸지 않는다. 이 판정에는 알림 수신이 필요하지 않다.
**AC-05 / FR-06, 08 — 위치 조건:** 아직 잠긴 위치 전용 캡슐은 합의된 판정 기준 밖에서는 내용이 숨겨지고, 유효한 위치 정보로 기준 충족이 확인되면 열 수 있다. 위치 권한 거부/서비스 꺼짐/확인 실패만으로 잠금 해제하지 않는다. 초기 반경 150m/ENTER/체류 없음, 현재 위치 샘플 60초 이내·정확도 100m 이하 기준의 안/밖/부정확/오래된 샘플을 검증한다. 수치는 실기기 후 조정한다. 최초 해제는 온라인 확인이 필요하다.
**AC-06 / FR-07 — AND:** 최초 잠금 해제 전 시간/위치 조합이 거짓/거짓, 참/거짓, 거짓/참이면 잠김이고 참/참이면 열람 가능하다. 최초 평가 시 서버 시각과 현재 위치가 동시에 충족되어야 한다. 시간 전에 방문했다가 떠난 경우 나중에 시간만 지나도 해제하지 않는다. 해제 후 이탈은 다시 잠그지 않는다.
**AC-07 / FR-09, NFR-01 — 복구:** 저장된 시간 전용 캡슐에서 백그라운드 이벤트/알림을 의도적으로 누락한 후 기한이 지나 앱을 재실행하고 온라인 서버 확인이 성공하면 조건 재평가로 열람 가능하다. 오프라인이면 안내 후 잠김을 유지하고 온라인 재시도 시 복구한다. 기한 전에는 잠김을 유지한다. 복합 잠금은 시간 경과만으로 열리지 않는다.
**AC-08 / FR-10, NFR-02 — 알림:** 권한과 지원 조건이 충족되고 트리거 경로가 실행되면 수신자에게 관련 캡슐 알림을 시도한다(자기 자신 수신이면 같은 사용자). 알림 거부/지연/누락 시에도 앱에서 조건을 평가하여 올바른 상태를 표시하고 비정상 종료하지 않는다. 정확한 초 단위 도착은 합격 기준이 아니다. 알림 클릭도 상태 확인을 우회하지 않는다.
**AC-09 / FR-12 — 지속 열람:** 한 번 해제된 캡슐은 화면 재진입·앱 재실행·위치 이탈 후에도 텍스트·사진을 다시 볼 수 있다.
**AC-10 / NFR-03–05 — 실패 처리:** 위치·저장·네트워크·사진 읽기 실패에서 잠긴 내용이 노출되지 않고 이해 가능한 안내가 나온다. 복구 가능한 조건이 회복되면 재시도할 수 있다.
<details>
<summary>English Version</summary>
**AC-01 / FR-01:** Own-account sent/received lists are accessible; an unrelated account cannot open the recipient's capsule.
**AC-02 / FR-02, 04:** Allow text-only/photo-only/both with a valid recipient and at least one time or location lock; both use AND. Test each lock combination; empty content/no lock is rejected. Persist values correctly and explain invalid input/failures. Test one JPEG/PNG photo up to 5 MiB and oversized/unsupported-file reselection.
**AC-03 / FR-03, 11:** Sends to self and another test account appear in the correct recipient list; verify cross-user delivery in a separate account session. Network failures are not reported as success. Sent requires backend-acknowledged optional media and metadata plus recipient-list availability; recipient opening is not required, and local pending writes are not success. Invalid/unresolved recipient codes are rejected. Same-send retries reuse capsuleId without duplicates.
**AC-04 / FR-05, 08, 09:** A locked time-only capsule hides content before `unlockAt`; evaluation at or after it allows viewing after online server confirmation, independently of notifications. Reject now/past input. Device-clock changes cannot unlock early; time-zone changes preserve the instant. Offline initial unlock stays locked with guidance/retry.
**AC-05 / FR-06, 08:** A locked location-only capsule remains hidden outside the agreed criterion and opens on a valid qualifying location check. Denied permission, disabled services or failed checks cannot unlock it. Test initial 150m ENTER/no dwell, current samples ≤60s old with reported accuracy ≤100m, inside/outside/stale/inaccurate paths. Tune after physical-device testing; initial unlock requires online confirmation.
**AC-06 / FR-07:** Before first unlock, time/location combinations false/false, true/false and false/true remain locked; true/true is available. Initial unlock requires server time and currently valid location simultaneously. An earlier visit followed by leaving cannot count when time later passes. Leaving after unlock does not relock.
**AC-07 / FR-09, NFR-01:** Suppress background/notification delivery for a persisted time-only capsule. Relaunch after expiry with successful online server confirmation must re-evaluate and allow viewing. Offline stays locked with guidance, then recovers on online retry; before expiry it stays locked. Combined locks still require location.
**AC-08 / FR-10, NFR-02:** Under granted permissions and supported conditions, an executed trigger path attempts the relevant notification for the recipient (same user for self-send). Denied/delayed/missed delivery must not crash or affect correct in-app eligibility. Exact-second arrival is not a pass criterion. Notification navigation must still check state.
Define FR/NFR v0.1
**AC-09 / FR-12:** Once unlocked, text/photos remain viewable after revisiting, relaunching or leaving the location.
**AC-10 / NFR-03–05:** Location/storage/network/photo-read failures do not expose locked content and show understandable guidance; retry is available when supported conditions recover.
</details>
## 검토 완료 조건 / Review Gate
- FR/NFR와 AC 간 범위·번호를 확인한다.
- 수신자 식별·저장/전송 완료·위치 판정·시간 기준의 미정 항목은 설계 결정으로 보완한다.
- 초기 vertical slice는 AC-02, 04, 07, 09부터 검증하고, 이후 위치 AND 및 계정 전달을 통합한다.
- 알림 지연과 실제 잠금 해제 정확성은 별도로 기록한다.
<details>
<summary>English Version</summary>
- Check FR/NFR-to-AC scope and IDs.
- Resolve recipient lookup, persistence/delivery completion, location semantics and time authority in design.
- Verify AC-02, 04, 07 and 09 in the early vertical slice, then integrate location AND and account delivery.
- Record notification latency separately from unlock correctness.
</details>
## 10/13 리뷰 반영 연결 / Review alignment
AC-02의 text-only 허용과 AC-04의 현재/과거 시각 거부는 본문에 이미 반영되어 있다. 반복 수정하지 않고 댓글에 해당 근거를 답변한다.
AC-06은 시간 전 방문 이력을 latch하지 않는 현재 AND로 이미 갱신되어 있다. Junwon의 제안과 Flora의 동의는 이 동작에서 일치한다. 추가 화면 검증: combined 시간 전 ‘시간 대기’, 시간 후 현재 위치 미충족 ‘위치 대기’, 전체 온라인 확인 후 ‘해제’. 확인 불명은 ‘확인 필요’로 구분한다.
[Notion source](https://app.notion.com/p/3edf1c1dcaa38031b6b8ef106057d537)
[Notion source](https://app.notion.com/p/3edf1c1dcaa3806bb895e37f0dc6c7ca)
<details>
<summary>English Version</summary>
Text-only validity and now/past rejection are already reflected in AC-02/04. AC-06 already uses current AND/no historical latch, consistent with Junwon and Flora. Add UI checks for time-wait, location-wait after time, unlocked after complete online confirmation, and verification-required when unknown. These are planned checks, not passes.
</details>

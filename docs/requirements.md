<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/a2cf1c1dcaa382fea69301a4867e6315 -->
> Working snapshot from [Notion](https://www.notion.so/a2cf1c1dcaa382fea69301a4867e6315), exported 2026-10-13. Review is pending; comments and approvals remain in Notion. This export is not a scope freeze or executed test evidence. The latest review addendum distinguishes historical questions from current decisions.

# Define FR/NFR v0.1
## 문서 기준 / Baseline
이번 대화에서 확인된 Option B(계정 기반 자기 자신/다른 사용자에게 보내기·받기)를 MVP 기준으로 작성한다. 기존 메인 페이지와 feasibility 자료의 ‘전달은 stretch’ 설명은 이전 범위이므로 문서 간 범위 정합성 검토가 필요하다. 아래 미정 사항은 확정된 기술 선택이나 검증 결과가 아니다.
<details>
<summary>English Version</summary>
This draft uses the confirmed Option B MVP: accounts and sending/receiving capsules for self or another user. Older home-page and feasibility wording that treats delivery as stretch needs scope alignment. Open items below are not finalized technology choices or verified results.
</details>
## Functional Requirements (FR)
**FR-01. User Account** — 사용자는 회원가입·로그인·로그아웃을 할 수 있어야 한다. 잘못된 인증정보 또는 만료/무효 세션으로 보호된 데이터에 접근할 수 없어야 하며, 계정 전환 시 해당 계정의 데이터만 보여야 한다. 다른 계정의 수신 캡슐에 접근할 수 없어야 한다.
**FR-02. Capsule Creation** — 사용자는 캡슐을 생성하고 내용, 수신 대상, 시간 또는 위치 중 최소 하나의 잠금 조건을 설정할 수 있어야 한다. 두 조건을 모두 선택할 수 있으며 이 경우 AND로 평가한다. 내용/수신자/조건의 필수 입력이 유효하지 않으면 저장·전송 성공으로 처리하지 않는다.
**FR-03. Recipient Selection & Delivery** — 수신 대상은 자기 자신 또는 다른 등록 사용자 계정이다. 계정 기반 직접 전송·수신은 MVP이다. 서버에 캡슐이 저장되어 수신자의 받은 목록에서 조회 가능한 상태이면 전송 성공이며, 상대가 앱을 열어야 할 필요는 없다. 존재하지 않거나 확인할 수 없는 수신자는 전송 성공으로 처리하지 않는다. 수신자가 앱을 열 때 새 캡슐 메타데이터를 동기화하고 위치 조건을 등록/평가한다. 최초 동기화 전 백그라운드 도착 알림은 보장하지 않으며 새 캡슐 발견을 위한 FCM·주기적 백그라운드 동기화는 MVP 필수가 아니다. 다른 사용자는 공유받은 공개 수신 코드(계정 UID)를 정확히 입력하여 확인하고 표시 이름을 확인한 뒤 선택한다. 전체 사용자 목록/이메일 검색은 MVP 필수가 아니다. 같은 전송을 재시도할 때 동일 capsuleId를 재사용하여 중복을 방지한다. 사진이 있으면 업로드와 메타데이터 저장 모두 서버 확인 후에만 전송 성공으로 표시한다.
**FR-04. Capsule Content** — MVP는 텍스트와 캡슐당 최대 1장의 사진을 지원한다. 텍스트만·사진만·텍스트+사진을 허용하며 둘 다 비어 있으면 생성할 수 없다. 사진은 JPEG/PNG 파일 최대 5 MiB로 제한한다. 자동 압축은 MVP 필수가 아니며 용량 초과/지원하지 않는 형식은 안내 후 재선택한다.
FR-05. Time-Based Lock — 생성 시 현재 또는 과거 시각은 허용하지 않고 미래 날짜·시간을 unlockAt 조건으로 저장한다. 최초 잠금 해제는 인터넷 연결 후 서버 시각이 unlockAt 이상인지 확인하여 판정한다. 기기 시각을 바꾸어도 조기 해제되지 않아야 한다. 화면에는 사용자의 현지 시간으로 표시하며, 시간대 변경이 해제 시점 자체를 바꾸지 않아야 한다.
**FR-06. Location-Based Lock** — 지정 지점의 초기 반경 150m 안에 들어오면 도착으로 판정하며 별도 체류 시간은 요구하지 않는다. 실제 휴대폰 검증 후 반경을 조정한다. 위치 권한·위치 서비스 및 유효한 현재 위치 확인이 필요하며 확인 실패만으로 해제하지 않는다. 초기 위치 샘플 기준은 60초 이내·보고 정확도 100m 이하이며 현재 거리 150m 이하로 평가한다. 불확실/경계 밖이면 해제하지 않고 재시도를 안내한다. 위치 권한은 기능 이용 시 요청하며 백그라운드 위치 권한은 앱 내 해제의 필수 조건이 아니다. 장소 입력은 MVP에서는 위도·경도 직접 입력(위도 -90~90, 경도 -180~180)과 선택 가능한 현재 위치 사용으로 단순화한다. 지도 검색/주소 변환은 필수가 아니다. GPS 위조·수정 클라이언트 방어는 MVP 범위 밖이다.
**FR-07. Combined Time + Location** — 최초 해제 평가 시 서버 시각이 unlockAt 이상이고 현재 위치 조건도 충족되어야 한다(AND). 시간 전에 지정 장소를 방문했다가 떠난 기록만으로 나중에 해제되지 않는다. 한 번 해제된 뒤에는 장소를 벗어나도 계속 열람할 수 있다.
FR-08. Locked Capsule Visibility — 수신자는 잠긴 캡슐의 발신자·잠금 종류·해제 조건과 잠금 상태를 확인할 수 있지만, 최초 잠금 해제 전에는 텍스트·사진 내용이 노출되지 않아야 한다. 발신자는 전송 후에도 자신이 보낸 내용을 계속 열람할 수 있다.
FR-09. Condition Evaluation & Recovery — 알림 도착 여부와 관계없이 저장된 조건과 상태를 다시 확인하여 해제 가능 여부를 판정한다. 최초 잠금 해제에는 인터넷 연결과 서버 확인이 필요하다. 연결 또는 서버 확인 실패 시 잠김을 유지하고 안내·재시도를 제공한다. 앱 재실행 시 저장된 조건을 서버와 재평가하여 놓친 알림 때문에 영구 잠김 상태로 남지 않게 한다. 복합 조건은 위치 조건도 충족해야 한다.
**FR-10. Unlock Notification** — 필요한 권한과 지원되는 실행 조건에서 수신자에게 잠금 해제 가능 상태를 알리는 Android 알림을 시도한다. 자기 자신에게 보낸 경우에는 해당 사용자에게 알린다. 알림 권한은 기능 이용 시 설명 후 요청한다. 알림은 별도 전달 기능이며 정각·정확한 초 단위 도착을 보장하지 않는다. 알림 거부/누락이 잠금 해제를 막지 않아야 한다.
**FR-11. Capsule List** — 사용자는 자신이 보낸 캡슐과 받은 캡슐 및 각 잠금 상태를 확인할 수 있어야 한다.
**FR-12. Persistent Access After Unlock** — 한 번 잠금 해제된 캡슐은 이후에도 계속 열람할 수 있어야 한다. 시간이 변하거나 지정 장소를 벗어났다는 이유로 다시 잠그지 않는다.
<details>
<summary>English Version</summary>
**FR-01. User Account** — Users can register, sign in and sign out. Invalid credentials or expired/invalid sessions cannot access protected data. Account switches show only the active account's data; another account's received capsules must remain inaccessible.
**FR-02. Capsule Creation** — Users create capsules with content, a recipient and at least one time or location lock. Both may be selected and use AND. Invalid required content/recipient/condition input must not be reported as successful save/send.
**FR-03. Recipient Selection & Delivery** — Recipients are self or another registered user. Direct account-based send/receive is MVP. Sending succeeds when the capsule is stored on the server and available in the recipient’s received list; the recipient need not open the app. Nonexistent or unresolved recipients must not be reported as successful sends. New capsule metadata is synced on app open, then location conditions are registered/evaluated. Background arrival alerts before first sync are not guaranteed. FCM/periodic background sync for new-capsule discovery is not an MVP requirement. Confirm another user by exact public recipient code (account UID) and display name; full directory/email search is not required. Retries reuse the same capsuleId. Optional photo upload and final metadata must both be backend-acknowledged before Sent.
**FR-04. Capsule Content** — MVP supports text and at most one photo per capsule. Text-only, photo-only and text+photo are allowed; both empty is invalid. Photos are JPEG/PNG up to 5 MiB. Automatic compression is optional; explain unsupported/oversized files and allow reselection.
FR-05. Time-Based Lock — Only future dates/times are allowed at creation; reject now or past times and persist the selected unlockAt. Initial unlock requires an internet connection and server time at or after unlockAt. Device-clock changes must not allow early unlock. Display local time to the user; changing the time zone must not change the unlock instant.
**FR-06. Location-Based Lock** — Start with a 150 m radius around the target point; entry counts as arrival with no dwell duration. Adjust the radius after physical-device validation. Required location permission/services and a valid current-location check are needed; failed checks do not unlock. Initial samples must be no older than 60 seconds with reported accuracy ≤100 m; current distance must be ≤150 m. Uncertain/outside checks stay locked with retry. Request location permission contextually; background permission is not required for in-app unlock. Use direct latitude/longitude input (-90..90, -180..180) and optional current location; map search/geocoding is not required. GPS spoofing/modified-client resistance is outside MVP.
**FR-07. Combined Time + Location** — At initial unlock evaluation, server time must be at/after unlockAt AND the current location condition must be satisfied. An earlier visit followed by leaving does not qualify when the time later passes. Once unlocked, the capsule remains viewable after leaving.
FR-08. Locked Capsule Visibility — While locked, recipients can see the sender, lock type, unlock conditions and lock status, but text/photo content remains hidden until initial unlock. Senders can continue viewing their own sent content after delivery.
FR-09. Condition Evaluation & Recovery — Recheck persisted conditions/state to determine eligibility regardless of notification arrival. Initial unlock requires an internet connection and server confirmation. Connection or verification failures keep the capsule locked and provide guidance/retry. On relaunch, re-evaluate persisted conditions with the server so missed notifications do not cause permanent locking. Combined locks must also satisfy the location condition.
**FR-10. Unlock Notification** — Attempt an Android unlock notification for the recipient under required permissions and supported execution conditions; self-send notifies the same user. Request notification permission contextually with explanation. Notifications are separate delivery behavior; exact-second arrival is not guaranteed. Denied/missed notifications must not block unlock.
**FR-11. Capsule List** — Users can view sent and received capsules and their lock states.
**FR-12. Persistent Access After Unlock** — Once unlocked, capsules remain viewable; a later time change or leaving the target location must not relock them.
</details>
---
## Non-Functional Requirements (NFR)
NFR-01. Data Persistence — 서버가 캡슐 원본·소유권·수신자·전달 및 잠금 해제 상태를 관리하고, 로컬 저장은 캐시와 재실행 복구를 담당한다. 캡슐 내용·조건·상태는 정상 종료/재실행 후 유지되어야 하며 로컬 상태가 서버의 접근 권한이나 최초 해제 확인을 우회해서는 안 된다. Room은 로컬 저장 후보이며 백엔드·클라우드·사진 저장소 기술은 아직 최종 선정되지 않았다.
**NFR-02. Permission Handling** — 위치·사진 접근·알림 권한 거부 시 비정상 종료하지 않고 필요한 안내를 제공한다. 알림 권한과 잠금 해제 권한을 구분한다.
**NFR-03. Clear Status** — 잠김/열람 가능 상태 및 확인 불가 사유를 이해할 수 있게 표시한다. 전송 실패를 완료로 표시하지 않는다.
**NFR-04. Reliable Condition Handling** — 조건 확인 실패나 권한 부족은 잠긴 내용을 노출시키지 않는다. 반복 평가가 이미 해제된 상태를 되돌리지 않아야 한다.
**NFR-05. Error Handling** — 네트워크·사진 로딩·저장·위치 오류는 실패 이유와 가능한 재시도/설정 안내를 제공한다. 이미 로컬에 받아둔 목록과 잠금 해제된 내용은 오프라인에서도 열람할 수 있다. 생성·전송·새 수신과 최초 잠금 해제에는 인터넷 연결이 필요하며 오프라인 전송이나 무조건적 백그라운드 동작은 보장하지 않는다.
**NFR-06. Semester Scope & Testability** — 시간 조건 평가를 알림과 독립적으로 검증할 수 있어야 한다. 시간·위치·복합 조건과 오류/재실행 복구를 각각 검증할 수 있어야 한다. 수치 성능 목표는 근거 없이 확정하지 않는다.
<details>
<summary>English Version</summary>
**NFR-01. Persistence** — The server manages capsule originals, ownership, recipients, delivery and unlock state. Local storage supports caching and relaunch recovery; it must not bypass server authorization or initial-unlock confirmation. Content, conditions and state survive normal close/relaunch. Room remains a local candidate; backend, cloud and photo-storage technologies are not finalized.
**NFR-02. Permissions** — Handle denied location/photo/notification access without crashes and with guidance. Notification permission is separate from unlock eligibility.
**NFR-03. Clear Status** — Explain locked/available states and unavailable checks. Do not show failed sends as completed.
**NFR-04. Reliable Conditions** — Failed checks or missing permissions must not reveal locked content. Repeated evaluation must not revert an unlocked state.
**NFR-05. Errors** — Provide reasons and available retry/settings guidance for network, photo-loading, storage and location failures. Previously downloaded lists and unlocked content can be viewed offline. Creation, sending, new receipt and initial unlock require internet. Offline delivery and unconditional background execution are not guaranteed.
**NFR-06. Scope & Testability** — Test time evaluation independently of notifications. Time/location/combined conditions and failure/relaunch recovery must be independently testable. Do not invent quantitative performance guarantees.
</details>
---
## Out of MVP Scope / 미정 사항
MVP 제외: 음성 메시지, 공개 피드·댓글·반응·고급 소셜, 외부 링크/QR 공유, 한 번 열람 후 자동 삭제, 새 캡슐 수신 FCM/주기적 동기화, GPS 위조 방어.
설계/구현 메모: 첫 vertical slice는 create → time lock → unlock으로 진행하고 위치·전달을 단계적으로 통합한다. unlockAt·조건 평가·영속 상태·알림 스케줄링을 분리하며 알림/부팅 이벤트는 평가 힌트로만 사용한다. 최초 해제는 온라인 서버 확인, 복합 조건은 현재 위치 AND이며 이미 해제된 상태를 되돌리지 않는다. 서버 시간 보호 + 앱 위치 평가이며 GPS 위조 방어는 제외한다. Firebase를 선택하면 Firestore 캐시 + 필요한 소규모 DataStore부터 시작하고 같은 데이터를 위한 Room 중복 저장은 넣지 않는다. 미정/후속: 백엔드·사진 저장 서비스/비용/프로젝트 사용 가능 여부 최종 확인, 실제 A/B/C 권한·두 계정 E2E·위치 경계/재실행 테스트. 결정값은 구현 후 검증 완료를 뜻하지 않는다.
<details>
<summary>English Version</summary>
Out of MVP: voice, public feed/comments/reactions/advanced social, external link/QR sharing, self-destruction, new-receipt FCM/periodic sync and GPS anti-spoofing.
Design/implementation notes: begin with create → time lock → unlock, then integrate location/delivery. Separate unlockAt, condition evaluation, persisted state and notification scheduling; alarms/boot are hints. Initial unlock requires online server confirmation; combined locks require current-location AND never revert UNLOCKED. Server-enforced time plus app-evaluated location; no GPS anti-spoofing. If Firebase is selected, start with Firestore cache and small DataStore settings, without duplicate Room records. Still pending: final backend/photo-storage provider, cost and project availability; actual A/B/C rules, two-account E2E, physical-location boundaries and relaunch tests. Decisions are not implementation/test passes.
</details>

## 10/13 리뷰 후속 / Review follow-up
현재 문서는 수정된 작업 기준이며 모든 팀원 최종 승인이 아니다. Flora의 10/11 시간대/self-send/편집과 10/12 재시도 관련 질문은 별도로 답변하고 합의 전 열어둔다. 이미 답변·반영된 10/6 댓글은 다시 미해결로 처리하지 않는다.
복합 캡슐 화면은 시간 대기 → 시간 충족 후 현재 위치 대기 → 전체 조건 확인 후 해제로 구분한다. 이는 FR-07의 current AND를 변경하지 않는다. 위치-only에는 시간 gate가 없다.
[Notion source](https://app.notion.com/p/3edf1c1dcaa38046b4c5f17e1f1efc49)
[Notion source](https://app.notion.com/p/3edf1c1dcaa3806bb895e37f0dc6c7ca)
<details>
<summary>English Version</summary>
This is the updated working baseline, not unanimous approval. Keep Flora's timezone, self-send, editing and retry follow-ups open; do not reopen already-addressed old feedback as a new issue. Combined-lock UI distinguishes waiting for time, waiting for current location after time, and fully unlocked; location-only has no time gate.
</details>

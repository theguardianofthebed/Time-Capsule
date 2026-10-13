<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/53ef1c1dcaa382cc85d80179abc26fc3 -->
> Working snapshot from [Notion](https://www.notion.so/53ef1c1dcaa382cc85d80179abc26fc3), exported 2026-10-13. Review is pending; comments and approvals remain in Notion. This export is not a scope freeze or executed test evidence. The latest review addendum distinguishes historical questions from current decisions.

# Resolve Open Design Questions
## 문서 기준 / Baseline
이번 대화에서 확인된 Option B(계정 기반 자기 자신/다른 사용자에게 보내기·받기)를 MVP 기준으로 작성한다. 기존 메인 페이지와 feasibility 자료의 ‘전달은 stretch’ 설명은 이전 범위이므로 문서 간 범위 정합성 검토가 필요하다. 아래 미정 사항은 확정된 기술 선택이나 검증 결과가 아니다.
<details>
<summary>English Version</summary>
This draft uses the confirmed Option B MVP: accounts and sending/receiving capsules for self or another user. Older home-page and feasibility wording that treats delivery as stretch needs scope alignment. Open items below are not finalized technology choices or verified results.
</details>
## 확인된 결정 / Confirmed Decisions
- **D-01. MVP 방향:** Option B. 사용자 계정과 자기 자신/다른 사용자에게 직접 보내기·받기를 포함한다.
- **D-02. 콘텐츠:** 텍스트·사진만 MVP. 음성은 제외한다.
- **D-03. 잠금:** 시간, 위치, 시간+위치를 지원한다. 복합 조건은 AND이다.
- **D-04. 재열람:** 한 번 해제되면 계속 볼 수 있다.
- **D-05. 공유:** 직접 계정 전달은 MVP. 외부 링크/QR 공유는 추후 별도 추가 전까지 제외한다.
- **D-06. 판단 기준:** 저장된 조건·상태가 source of truth. `unlockAt`, eligibility/state, notification scheduling은 별도 책임이다.
- **D-07. 알림 계약:** 정확한 초 단위 도착은 약속하지 않는다. 알림 누락 시 앱 재실행에서 조건을 재평가한다.
- **D-08. 구현 순서:** create → time lock → unlock의 작은 vertical slice를 먼저 검증한 후 위치와 전달을 통합한다. 이는 전달을 MVP에서 제외하는 의미가 아니다.
<details>
<summary>English Version</summary>
- **D-01:** Option B MVP includes accounts and direct send/receive for self or another user.
- **D-02:** Text/photos only; voice excluded.
- **D-03:** Time, location and combined locks; combined logic is AND.
- **D-04:** Unlocked capsules remain viewable.
- **D-05:** Direct account delivery is MVP; external link/QR sharing requires a later scope addition.
- **D-06:** Persisted conditions/state are the source of truth. Separate `unlockAt`, eligibility/state and notification scheduling.
- **D-07:** No exact-second notification promise. Re-evaluate conditions on relaunch after missed delivery.
- **D-08:** Validate create → time lock → unlock first, then integrate location and delivery. This sequencing does not remove delivery from MVP.
</details>
---
## 아직 해결해야 할 설계 질문 / Open Design Questions
**Q-01. 수신자·인증:** 계정 인증 방식과 다른 사용자 검색/식별 방법은? 유효하지 않은 계정과 본인 전송을 어떻게 구분할 것인가?
**Q-02. 저장·백엔드:** 계정·캡슐·사진·상태의 원격 저장과 로컬 캐시 책임은? Room은 유력한 로컬 후보지만 Option B 전체 구조를 결정한 뒤 역할과 채택을 확정한다. Room만으로 사용자 간 전달을 해결할 수 있는 것으로 취급하지 않는다.
**Q-03. 전달 완료:** 어떤 저장/응답 시점에 ‘전송 완료’로 표시하는가? 실패·재시도·중복 전송과 수신 목록 갱신은 어떻게 처리하는가? 오프라인 전달 보장은 아직 없다.
**Q-04. 위치 판정:** 장소 입력, 반경, 정확도, foreground/background 확인 방식은? 복합 잠금에서 과거 방문을 저장해 인정할지, 시간 충족 이후 현재 도달을 요구할지 결정해야 한다. AND 결정만으로 이 의미가 확정되지는 않는다.
**Q-05. 시간·상태:** 시간대/저장 형식, 기기 시각 변경, Option B에서 시간과 상태의 권위 있는 평가 위치는? 저장 조건과 해제 상태의 동기화 및 재평가 규칙을 정의한다.
**Q-06. 백그라운드·알림:** 스케줄링 방식과 재부팅/재등록 지원 범위는? 알림 누락과 해제 상태 복구를 별도로 검증한다. 재부팅 후 항상 즉시 동작한다는 보장은 검증 전 넣지 않는다.
**Q-07. 권한·사진:** 선택한 사진 입력·위치 방식에 실제 필요한 권한은? 사진 개수/용량/빈 내용 기준과 거부 시 안내를 정한다. 음성 제외이므로 음성을 위한 마이크 권한은 요구하지 않는다.
<details>
<summary>English Version</summary>
**Q-01. Recipient/auth:** Select authentication and account lookup. Define invalid-recipient and self-send handling.
**Q-02. Storage/backend:** Define remote storage and local cache responsibilities for accounts, capsules, photos and state. Room is a strong local candidate; finalize its role only after Option B architecture. Do not treat Room alone as cross-user delivery.
**Q-03. Delivery completion:** Define the save/response point for successful sending, failure/retry/duplicate handling and recipient-list refresh. Offline delivery is not guaranteed yet.
**Q-04. Location:** Decide location input, radius, accuracy and foreground/background checks. Determine whether a prior visit is latched or arrival after time eligibility is required. AND alone does not settle this semantic choice.
**Q-05. Time/state:** Define time zones/storage format, clock changes and authoritative evaluation for Option B. Define synchronization and re-evaluation of persisted conditions/unlocked state.
**Q-06. Background/notifications:** Choose scheduling and reboot/re-registration support. Validate notification delivery separately from state recovery. Do not promise immediate post-reboot behavior before evidence.
**Q-07. Permissions/photos:** Determine permissions required by selected photo/location methods, photo count/size/empty-content rules and denial guidance. Voice is excluded, so do not require microphone access for voice.
</details>
## 설계 검토로 넘길 작업 / Design Handoff
- 김건우: 결정과 미정 사항을 FR/NFR·AC·데이터 모델에 일관되게 연결한다.
- 준원의 feasibility/spike 결과를 바탕으로 팀이 저장·위치·백그라운드 선택을 검토한다. 기술 후보를 검증 완료로 표시하지 않는다.
- 수신자·저장·전달은 기존 Oct 9 검토 항목, 위치·백그라운드·권한은 기존 Oct 15 검토 항목과 연결한다. 새 마감일을 임의로 추가하지 않는다.
- 메인 페이지와 이전 feasibility 문서의 오래된 MVP 범위 문구는 정합성 검토 대상이며, 이번 작업에서는 이 네 페이지에 최신 기준을 기록한다.
<details>
<summary>English Version</summary>
- Geonwoo connects decisions/open items consistently to FR/NFR, AC and the data model.
- The team reviews storage/location/background choices using Junwon's feasibility/spike evidence; candidates are not marked verified.
- Link recipient/storage/delivery to existing Oct 9 review items and location/background/permissions to existing Oct 15 items; no new deadlines are assigned.
- Older MVP wording in the home/feasibility pages needs alignment; this task records the current baseline in these four pages.
</details>
## 10/13 정합성 점검 / Current decision reconciliation
위 Q-01~07은 초기 질문 기록으로 보존한다. 답변이 FR/AC/보고서에 이미 반영된 항목을 다시 미해결로 간주하지 않는다. 아래는 현재 문서와 최신 댓글을 대조한 상태이며 건우의 작업 기준과 팀 승인 여부를 구분한다.
- Q-01: 계정 기반 self/registered recipient. 초안은 UID 수신 코드 정확 조회+이름 확인. 인증 공급자와 코드 UX는 backend/design 리뷰 대기.
- Q-02: 원격 원본/계정 관계/해제 상태 + 로컬 캐시/복구. Firebase 채택 시 중복 Room 없이 cache/DataStore 초안. 실제 공급자·사진 저장/비용은 아직 미정.
- Q-03: 서버 확인+수신 목록 조회 가능이면 Sent, 로컬 pending이나 상대 앱 실행은 성공 기준 아님. 같은 ID 재시도는 FR/AC에 반영됐지만 Flora의 10/12 동일 내용 검사 제안으로 공동 논의가 다시 열려 있음.
- Q-04: 초기 150m ENTER/체류 없음, 현재 샘플 60초·100m 기준. 현재 AND/no latch는 Junwon 제안과 Flora의 10/11 동의가 일치. 시간 후 위치 확인 및 단계 표시를 새 설계에 반영. 장소 좌표/현재 위치 초안과 수치는 실기기/UX 검토 대상.
- Q-05: 미래만·온라인 서버 시각·UTC instant·시간대 이동이 해제 시점 안 바꿈. Flora 시간대 저장 댓글에는 원래 시간대 보조 metadata 제안/설명을 답변. self-send 잠금 전 보기와 post-send 편집은 별도 공동 결정 필요.
- Q-06: 앱 실행 동기화/재평가, 알림과 해제 독립. 수신 FCM/주기적 새 수신 sync/상시 서비스 제외. 실제 reboot/registration은 선택 구현별 검증.
- Q-07: photo 1장 JPEG/PNG 5 MiB, text-only/photo-only/both, neither invalid. 직접 촬영/마이크는 필수 아님; 위치/알림 권한은 맥락에서 안내. 음성 제외.
[Notion source](https://app.notion.com/p/a2cf1c1dcaa382fea69301a4867e6315)
[Notion source](https://app.notion.com/p/3d1f1c1dcaa3824bbff58133eb53130c)
[Notion source](https://app.notion.com/p/3edf1c1dcaa38046b4c5f17e1f1efc49)
[Notion source](https://app.notion.com/p/3edf1c1dcaa3806bb895e37f0dc6c7ca)
<details>
<summary>English Version</summary>
Preserve Q-01~07 as the original question log; already-reflected answers are not new blockers. Current baseline is account delivery, remote authority/local cache, server-confirmed Sent, current-location AND, server time, app-open sync and optional supported photo. Backend/media provider and cost remain pending. Flora agrees with current AND and requests stage display; the new design reflects it. Her latest self-send preview, timezone metadata, editing and retry comments remain visible for team confirmation. Earlier FR/AC changes do not imply unanimous approval.
</details>

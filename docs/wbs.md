<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3edf1c1dcaa380da8e6df2212985e9a1 -->
> [Notion source](https://www.notion.so/3edf1c1dcaa380da8e6df2212985e9a1). Draft snapshot, not unanimous approval or implementation evidence. Latest shared discussions remain authoritative for unresolved choices.

# Final WBS — Review Draft
## 상태
2026-10-13 설계 초안에 맞춘 작업 분해안. 최종 설계 리뷰 전이므로 Final WBS 작업 자체는 진행 중이다. 아래 작업 묶음은 완료된 구현이 아니다. 기존 초기 WBS를 보존한다.
[Notion source](https://app.notion.com/p/3edf1c1dcaa38097a46df224d6104dd6)
[Notion source](https://app.notion.com/p/3edf1c1dcaa38024aeb9dff4d0cc1ccf)
[Notion source](https://app.notion.com/p/3edf1c1dcaa38062aeb9c8bdb57c2cff)
[Notion source](https://app.notion.com/p/3edf1c1dcaa3809ba11ff3b676c91d2d)
<table header-row="true">
<tr>
<td>WBS</td>
<td>산출물/완료 기준</td>
<td>의존성·근거</td>
<td>현재/역할</td>
</tr>
<tr>
<td>1. Setup & collaboration</td>
<td>buildable Android project; branches/issues/PR rules, reviewed CONTRIBUTING</td>
<td>기존 repo + CONTRIBUTING, Junwon 초기화</td>
<td>초기 repo 기록 있음; 앱 build 증거 별도 확인</td>
</tr>
<tr>
<td>2. Requirements & design</td>
<td>FR/AC, flow, screen list, architecture, model, test outline reviewed</td>
<td>기존 FR/AC, 현재 설계 문서</td>
<td>건우 초안; Flora 제품 리뷰/준원 기술 리뷰</td>
</tr>
<tr>
<td>3. Accounts & recipient</td>
<td>register/sign-in/out, self/valid recipient, A/B/C isolation</td>
<td>backend/auth/lookup spike, FR-01/03</td>
<td>공급자 검토 대기; 구현 분담은 팀 결정</td>
</tr>
<tr>
<td>4. Compose & media</td>
<td>text/photo input, validation, protected object upload/download and partial failure</td>
<td>FR-02/04, AC-02, media provider/billing</td>
<td>사진 1장; 직접 촬영/음성은 필수 아님</td>
</tr>
<tr>
<td>5. Delivery & lists</td>
<td>remote acknowledged send, correct received/sent lists, app-open sync, failure-safe status</td>
<td>FR-03/11, AC-03, retry 공동 결정</td>
<td>계정 간 전달은 MVP</td>
</tr>
<tr>
<td>6. Locks & access</td>
<td>server time, current location, AND, metadata-only locked view, monotonic unlock</td>
<td>FR-05~09/12, server access rules, self-send 질문</td>
<td>time slice 먼저; 위치/AND 후 통합</td>
</tr>
<tr>
<td>7. Notifications & recovery</td>
<td>contextual permission, state-independent local alert, relaunch recovery, conditional reboot registration</td>
<td>FR-09/10, NFR-01/02, platform spike</td>
<td>수신 FCM/상시 서비스 제외</td>
</tr>
<tr>
<td>8. Verification & stabilization</td>
<td>V1~V5 증거, actual two-account E2E, real-device location boundaries, regression</td>
<td>Test outline; test/commit/device evidence</td>
<td>생산 앱 테스트 미실행</td>
</tr>
<tr>
<td>9. Proposal & handoff</td>
<td>screen/wireframes/slides accuracy, rehearsal feedback, consistent final scope/WBS</td>
<td>10/15 rehearsal, 10/20 proposal</td>
<td>Flora PPT/대본; 준원 wireframes; 건우 화면/정합성 리뷰</td>
</tr>
</table>
## 순서와 완료 기준
텍스트 create→time lock→unlock → 실제 A/B 전달 → 사진 → 위치/현재 AND → 복구/회귀. 단계 순서는 최종 MVP 제외를 뜻하지 않는다. 각 기능은 요구사항/AC→설계→Issue→구현/PR→다른 팀원 리뷰→테스트→Merge로 연결한다. 미래 단계 담당/추가 기한은 임의 배정하지 않는다.
<details>
<summary>English Version</summary>
Review draft of final work packages, not completed implementation: setup/collaboration; requirements/design; accounts/recipient; compose/media; acknowledged delivery/lists; server-time/current-location locks/access; local notifications/recovery; verification; proposal/handoff. Geonwoo supplies design and accuracy review, Flora product/slides, Junwon feasibility/wireframes/initial integration; later implementation assignments stay a team decision. Sequence time slice, real A/B delivery, media, location/AND, recovery/regression without dropping any final MVP feature. Finalize WBS only after design review and all LGTM.
</details>

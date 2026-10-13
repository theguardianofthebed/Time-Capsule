<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3edf1c1dcaa38046b4c5f17e1f1efc49 -->
> [Notion source](https://www.notion.so/3edf1c1dcaa38046b4c5f17e1f1efc49). Draft snapshot, not unanimous approval or implementation evidence. Latest shared discussions remain authoritative for unresolved choices.

# User Flow v0.1
## 문서 상태 / Status
2026-10-13 작성한 건우의 설계 초안이다. 구현/테스트 완료 또는 팀 전체 승인 기록이 아니다. 기존 FR/NFR를 기준으로 진행하고, 새 공동 의견은 원문/댓글을 보존하여 팀 리뷰로 확정한다. 준원의 백엔드 spike가 끝나기 전에도 화면/흐름 설계를 진행하며 이후 백엔드 부분은 준원과 보완한다.
<details>
<summary>English Version</summary>
Geonwoo's design draft dated 13 Oct; not implemented, tested or approved by the whole team. Preserve source discussions. UI/flow work proceeds before the backend spike; Junwon will help refine backend assumptions afterward.
</details>
## 기준 문서 / Sources
[Notion source](https://app.notion.com/p/a2cf1c1dcaa382fea69301a4867e6315)
[Notion source](https://app.notion.com/p/3d1f1c1dcaa3824bbff58133eb53130c)
[Notion source](https://app.notion.com/p/3f4f1c1dcaa3801aa07ff830bb17000a)
## 공통 진입과 계정
1. 앱 시작 → 유효한 세션이면 캡슐 목록, 없으면 로그인.
2. 회원가입 → 로그인 → 본인 계정 목록. 인증 오류는 같은 화면에서 설명/수정.
3. 앱 실행/목록 재진입 시 온라인 수신 동기화. 네트워크 실패이면 캐시 목록과 마지막 확인 상태를 구분한다.
4. 로그아웃/계정 변경 시 이전 계정의 캐시 표시·알림·위치 등록을 정리한다.
## 작성 → 전송
1. 목록의 새 캡슐 → 텍스트/사진 입력. 하나 이상 필수, 사진은 최대 1장 JPEG/PNG 5 MiB.
2. 수신자: 자기 자신 또는 등록 사용자. 다른 사용자는 수신 코드 정확 조회와 표시 이름 확인. 검색 구현은 백엔드 검토 대상.
3. 시간/위치 중 최소 하나 선택. 시간은 미래만, 위치는 좌표/현재 위치 초기안. 둘 다면 AND.
4. 최종 확인 → 전송. 사진이 있으면 업로드 → 메타데이터 저장 → 서버 확인 후 완료.
5. 실패/결과 불명은 Sent로 표시하지 않는다. 같은 전송 ID를 유지하는 명시적 재시도는 건우 의견이며 Flora의 중복 판정 제안과 함께 검토한다.
## 수신 → 해제 → 재열람
1. 받은 목록 → 잠긴 상세: 발신자·잠금 종류·조건·상태만 표시.
2. 시간 전: ‘시간 대기’. 복합 조건에서 서버 시각 충족 후: ‘위치 대기’. 이때 현재 위치를 확인한다. 서버 확인 불가는 ‘확인 필요’로 표시하고 해제하지 않는다.
3. 최초 해제는 온라인 서버 확인 및 필요한 현재 위치 조건 충족 후 저장한다. 이전 방문 이력만으로 해제하지 않는다.
4. 해제 후 내용 화면. 이탈/재실행으로 다시 잠그지 않는다. 로컬 다운로드된 해제 내용은 오프라인 재열람.
5. 알림 클릭 → 계정/캡슐 접근 및 현재 상태 재확인 → 적절한 상세 화면. 알림 거부·누락은 앱 내 해제와 독립.
## 아직 팀 확인이 필요한 분기
- 자기 자신 전송: 기존 FR-08의 발신자 재열람과 수신자 잠금이 겹친다. 초안은 기존 문구를 유지하고 self-send 잠금 전 열람 정책은 공동 결정 전 확정하지 않는다.
- 전송 후 편집/삭제: MVP 필수 기능으로 추가하지 않는다. Flora 질문에 ‘전송 전 수정 가능, 전송 후 변경 제외’ 의견을 남기고 합의를 기다린다.
- 서버/사진 저장 제공자·비용, 수신 코드/좌표 입력 UX는 팀 리뷰 대상.
<details>
<summary>English Version</summary>
Entry: validate session, sign in/register, then account-scoped sent/received lists. Sync on app open; distinguish cached data from verified server state and clear previous-account state on switch.
Create: enter text and/or one supported photo → self or confirmed registered recipient → at least one future-time/location condition → confirm → optional upload → metadata commit → server ACK. Pending/failed is not Sent; stable-ID retry remains subject to the new team discussion.
Receive: metadata-only locked detail → waiting for time → waiting for current location for combined locks → online confirmed unlock → persistent content view. Permission/network failures remain locked with guidance; no historical-location latch. Notification navigation rechecks account/state.
Self-send preview, post-send editing/deletion, backend/media provider and input UX remain explicit review questions.
</details>
[Notion source](https://app.notion.com/p/3edf1c1dcaa3806bb895e37f0dc6c7ca)
## 리뷰 요청 / Review gate
준원: 기술 실현 가능성과 백엔드 경계를 확인. Flora: 사용자 흐름/표현과 MVP 범위를 확인. 수정 요청과 공동 질문을 모두 검토한 뒤 각 팀원의 LGTM을 기록한다. 그 전까지 In Review를 유지한다.
<details>
<summary>English Version</summary>
Junwon reviews feasibility/backend boundaries; Flora reviews UX and scope. Keep In Review until all reviewers give LGTM and substantive questions are settled.
</details>

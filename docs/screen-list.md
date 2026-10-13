<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3edf1c1dcaa3806bb895e37f0dc6c7ca -->
> [Notion source](https://www.notion.so/3edf1c1dcaa3806bb895e37f0dc6c7ca). Draft snapshot, not unanimous approval or implementation evidence. Latest shared discussions remain authoritative for unresolved choices.

# Screen List v0.1
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
## 와이어프레임용 화면 목록
아래 8개는 논리적 화면이며 Android Activity 8개를 의미하지 않는다. 목록 탭·입력 하위 화면은 구현 시 합칠 수 있다.
<table header-row="true">
<tr>
<td>ID / 화면</td>
<td>주요 내용·행동</td>
<td>오류/잠김 상태</td>
<td>근거</td>
</tr>
<tr>
<td>S-01 로그인 / Sign in</td>
<td>계정 인증, 회원가입 이동</td>
<td>잘못된 인증/만료 세션/네트워크 오류</td>
<td>FR-01, AC-01</td>
</tr>
<tr>
<td>S-02 회원가입 / Register</td>
<td>기본 계정 생성, 로그인 이동</td>
<td>필수 입력/중복 또는 인증 실패; 인증 제공자 미정</td>
<td>FR-01</td>
</tr>
<tr>
<td>S-03 캡슐 목록 / Capsule lists</td>
<td>받은/보낸 탭, 새 캡슐, 새로 확인, 계정 메뉴</td>
<td>빈 목록/로딩/캐시·오프라인/시간 대기·위치 대기·열람 가능</td>
<td>FR-03/11, AC-03</td>
</tr>
<tr>
<td>S-04 내용 작성 / Compose</td>
<td>텍스트, 사진 1장 선택/제거, 다음</td>
<td>둘 다 비어 있음, 5 MiB 초과, JPEG/PNG 외, 사진 읽기 실패</td>
<td>FR-02/04, AC-02</td>
</tr>
<tr>
<td>S-05 수신자·조건 / Recipient and locks</td>
<td>자기 자신/수신 코드·이름 확인, 시간/위치 선택, 미래 시각, 좌표 또는 현재 위치</td>
<td>수신자 불명, 조건 없음, 현재/과거 시각, 좌표 범위 오류, 위치 권한 거부</td>
<td>FR-03/05/06/07</td>
</tr>
<tr>
<td>S-06 전송 확인 / Confirm send</td>
<td>내용·수신자·조건 요약, 전송/돌아가서 수정, 성공 후 보낸 목록</td>
<td>전송 중 중복 누름 차단, 업로드 실패, 서버 결과 불명; 재시도 문구는 팀 검토</td>
<td>AC-02/03/10</td>
</tr>
<tr>
<td>S-07 캡슐 상세 / Capsule detail</td>
<td>잠김은 메타데이터만, 해제 후 텍스트/사진, 현재 조건 확인</td>
<td>시간/위치 대기, 권한·인터넷·위치 확인 필요, 접근 불가/사진 로딩 실패</td>
<td>FR-08/09/12, AC-04~10</td>
</tr>
<tr>
<td>S-08 계정·권한 / Account and permissions</td>
<td>본인 수신 코드, 로그아웃, 권한/서비스 안내</td>
<td>알림 거부는 열람 제한 없음; 위치 거부는 위치 해제 대기</td>
<td>FR-01/10, NFR-02/05</td>
</tr>
</table>
## 공통 UI 규칙
- Flora의 기존 soft brown 테마(#f9f8f4 / #865b4b / #ece8df)를 참고한다. 실제 대비·잠김 구분은 와이어프레임 리뷰에서 검증.
- 시간 잠금 표시에는 날짜·시간·시간대를 함께 보여 입력 의미를 확인한다. 저장은 절대 시각이며 시간대 이동이 목표 시점을 바꾸지 않는다.
- 복합 상세는 ‘시간 대기 → 위치 대기 → 해제’로 상태를 설명한다. 실제 UNLOCKED는 최종 AND 충족 후 한 번만 기록한다.
- 권한 안내/전송 성공/실패는 독립 화면을 늘리기보다 같은 화면 상태·대화상자로 표현한다.
- 잠긴 썸네일/텍스트 미리보기는 수신 화면에 표시하지 않는다. self-send 발신자 예외는 팀 확인 전 별도 주석으로 표시.
## 준원에게 전달할 산출물
S-03 → S-04 → S-05 → S-06 → S-07의 핵심 5화면과 S-07 잠김/해제/오류 변형부터 와이어프레임을 만들 수 있다. 로그인/계정은 보조 흐름. 발표용 A→B 텍스트 시간 잠금 예시를 우선 사용한다.
회의: 2026-10-15 17:00 Asia/Seoul 프론티어관 302호. 기존 카톡의 ‘12일’ 마감은 ‘15일’로 정정되었다.
<details>
<summary>English Version</summary>
Eight logical screens: sign in, register, account-scoped sent/received lists, compose, recipient/locks, confirm send, locked/unlocked detail, and account/permission settings. These are navigation destinations, not eight Activities. Use the table's FR/AC traceability and failure states. Reuse Flora's warm brown theme subject to contrast review. Show time zone, time-wait/location-wait/unlocked stages, and no received-content preview before unlock. Self-send is explicitly unresolved. Junwon can wireframe the five core capsule screens plus detail-state variants before the 15 Oct 17:00 rehearsal, Frontier Room 302.
</details>
[Notion source](https://app.notion.com/p/3edf1c1dcaa38046b4c5f17e1f1efc49)
## 리뷰 요청 / Review gate
준원: 기술 실현 가능성과 백엔드 경계를 확인. Flora: 사용자 흐름/표현과 MVP 범위를 확인. 수정 요청과 공동 질문을 모두 검토한 뒤 각 팀원의 LGTM을 기록한다. 그 전까지 In Review를 유지한다.
<details>
<summary>English Version</summary>
Junwon reviews feasibility/backend boundaries; Flora reviews UX and scope. Keep In Review until all reviewers give LGTM and substantive questions are settled.
</details>

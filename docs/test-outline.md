<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3edf1c1dcaa38031b6b8ef106057d537 -->
> [Notion source](https://www.notion.so/3edf1c1dcaa38031b6b8ef106057d537). Draft snapshot, not unanimous approval or implementation evidence. Latest shared discussions remain authoritative for unresolved choices.

# Test Outline v0.1
## 상태와 근거 / Status and sources
2026-10-13 건우 초안. 구현/테스트 통과 및 공급자 확정이 아니다. 백엔드 spike 후 준원이 검토·보완한다. 원문·기존 댓글은 유지하고 합의가 필요한 변경은 별도로 표시한다.
[Notion source](https://app.notion.com/p/a2cf1c1dcaa382fea69301a4867e6315)
[Notion source](https://app.notion.com/p/3d1f1c1dcaa3824bbff58133eb53130c)
[Notion source](https://app.notion.com/p/3f4f1c1dcaa3801aa07ff830bb17000a)
<details>
<summary>English Version</summary>
Geonwoo draft; not implementation, test evidence or a finalized provider. Junwon reviews/refines backend design after the spike. Preserve discussions and identify unresolved choices.
</details>
## 실행 상태
이 문서는 검증 계획이다. 생산 앱 또는 백엔드 테스트를 이번 작업에서 실행하지 않았다. SpikeLab E1 결과는 보고서의 해당 환경에만 적용되며 제품 통합 E2/반복 검증 E3가 아니다.
## 추적 가능한 검증 묶음
<table header-row="true">
<tr>
<td>묶음</td>
<td>연결</td>
<td>검증/기대 결과</td>
<td>방법/현 상태</td>
</tr>
<tr>
<td>V1 시간 slice</td>
<td>FR-02/05/09/12, AC-02/04/07/09, T-01~04</td>
<td>시간 직전/같음/이후; 온라인 서버 확인; 기기 시각 조작은 조기 해제 불가; 알림 누락 후 재실행 복구; 해제 후 유지</td>
<td>순수 로직 + 실제 서버/앱 통합. 계획</td>
</tr>
<tr>
<td>V2 계정·직접 전달</td>
<td>FR-01/03/08/11, AC-01/03, T-05/07</td>
<td>A→A/A→B, B 목록/잠김, C/로그아웃 거부, 계정 전환 캐시 정리, 미확인 수신자 거부, pending≠Sent</td>
<td>선택 backend emulator/두 앱 인스턴스. 아직 미실행</td>
</tr>
<tr>
<td>V3 사진·입력</td>
<td>FR-02/04, AC-02/10, T-06</td>
<td>텍스트만/사진만/둘 다/둘 다 없음; 1장/5 MiB 경계·초과/지원 외 MIME; 업로드 실패/메타데이터 실패; URI 재실행·원격 B 표시</td>
<td>입력 검증 + 업로드/권한 통합. 원격 미실행</td>
</tr>
<tr>
<td>V4 위치·AND</td>
<td>FR-06/07, AC-05/06, T-08~11</td>
<td>150m 안/밖/경계, 샘플 60초/100m 기준, 거부/서비스 꺼짐; 4조합 AND; 시간 전 방문 후 이탈은 잠김; 시간 후 현재 진입은 해제</td>
<td>통제 위치 로직 + 실기기. 수치 신뢰도 검증 대기</td>
</tr>
<tr>
<td>V5 알림·복구</td>
<td>FR-09/10, NFR-01/02/04, AC-07/08/09, T-12~15</td>
<td>권한 허용/거부 상태 동일; 클릭 접근 재확인; 반복 이벤트 상태 역전 없음; 정상 재실행/부팅 등록 복구; 정확한 초 알림 합격 기준 제외</td>
<td>실기기 + 실제 앱 통합. 자동 복구와 앱 실행 복구 분리</td>
</tr>
</table>
## 발표용 최소 시나리오
A 로그인 → 텍스트 캡슐 → B/미래 시간 지정 → 서버 전송 확인 → 별도 B 세션에서 잠긴 메타데이터 → 시간 전 거부 → 서버 시간 후 확인/열람 → 재진입 유지. 사진과 위치는 별도 검증 증거를 붙인다. 실제 앱이 미완성인 발표에서는 와이어프레임 시나리오라고 명시한다.
## 결과 기록 양식
케이스 ID, 요구사항/AC, commit/app 버전, 실제 기기/API 또는 emulator, 계정/인터넷/권한, 입력과 서버 시각, 기대/실제 결과, 상태 변화, 알림 시각, PASS/FAIL/BLOCKED/NOT RUN, 로그/화면 증거. 백엔드 미선정과 self-send/편집/재시도 공동 의견은 BLOCKED 사유로 구분.
## 논의 중인 기대 결과
T-07: stable ID 재시도와 Flora의 동일 내용 검사 안은 합의 전 별도 논의. T-14: 불필요한 중복 알림 억제는 현재 설계 권고이며 아직 FR/AC 계약 추가 여부를 확인한다. 자동 알림 지연과 조건 판단 오류를 혼동하지 않는다.
<details>
<summary>English Version</summary>
This is a plan, not test results. V1 time/recovery, V2 A/B/C accounts and backend-confirmed delivery, V3 content/photo/partial failure, V4 fresh location/current AND, V5 notification/relaunch/boot. Link FR/AC/T IDs as above. Minimum demo is A→B text/time lock with separate sessions; label wireframes as wireframes if no app exists. Record version, device/API, accounts/network/permissions, controlled inputs/server time, expected/actual state, PASS/FAIL/BLOCKED/NOT RUN and evidence. Stable-ID retry vs identical-content checks, duplicate-notification contract and self-send remain explicit review dependencies. SpikeLab E1 is not production E2/E3.
</details>
[Notion source](https://app.notion.com/p/3eff1c1dcaa38332bb54018efd2f4e91)
## 남은 리뷰 / Remaining review
준원의 기술/백엔드 검토와 Flora의 제품 흐름 확인이 필요하다. 모든 리뷰 LGTM 및 질문 처리 전 In Review 유지.
<details>
<summary>English Version</summary>
Keep In Review until technical/product reviews give LGTM and substantive questions are settled.
</details>

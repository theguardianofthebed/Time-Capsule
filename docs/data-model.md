<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3edf1c1dcaa3809ba11ff3b676c91d2d -->
> [Notion source](https://www.notion.so/3edf1c1dcaa3809ba11ff3b676c91d2d). Draft snapshot, not unanimous approval or implementation evidence. Latest shared discussions remain authoritative for unresolved choices.

# Data Model v0.1
## 상태와 근거 / Status and sources
2026-10-13 건우 초안. 구현/테스트 통과 및 공급자 확정이 아니다. 백엔드 spike 후 준원이 검토·보완한다. 원문·기존 댓글은 유지하고 합의가 필요한 변경은 별도로 표시한다.
[Notion source](https://app.notion.com/p/a2cf1c1dcaa382fea69301a4867e6315)
[Notion source](https://app.notion.com/p/3d1f1c1dcaa3824bbff58133eb53130c)
[Notion source](https://app.notion.com/p/3f4f1c1dcaa3801aa07ff830bb17000a)
<details>
<summary>English Version</summary>
Geonwoo draft; not implementation, test evidence or a finalized provider. Junwon reviews/refines backend design after the spike. Preserve discussions and identify unresolved choices.
</details>
## 공급자 독립 논리 모델
아래는 논리 엔티티다. Firestore collection/Room table/API 경로를 확정하지 않는다.
<table header-row="true">
<tr>
<td>엔티티</td>
<td>필드</td>
<td>불변조건/접근</td>
</tr>
<tr>
<td>User / PublicRecipient</td>
<td>uid, displayName, recipientCode(초안: uid)</td>
<td>공개 조회는 최소 식별정보만. 비밀번호·세션 토큰 저장 금지. 코드 방식은 UX 리뷰 대상.</td>
</tr>
<tr>
<td>CapsuleMetadata</td>
<td>capsuleId, senderId, recipientId, createdAt, lockType, unlockAt?, targetLat?, targetLng?, radiusM?, unlockState, unlockedAt?</td>
<td>발신/수신 관계만 조회. 잠김 목록은 텍스트·사진/다운로드 URL을 포함하지 않음.</td>
</tr>
<tr>
<td>CapsuleContent</td>
<td>capsuleId, text?, photoObjectRef?</td>
<td>text 또는 photo 중 하나 이상. 원격 접근은 선택된 보안 계약. sender/self 예외는 공동 결정 대기.</td>
</tr>
<tr>
<td>PhotoObject</td>
<td>objectRef, capsuleId, senderId, mimeType, sizeBytes</td>
<td>최대 1장 JPEG/PNG, 5 MiB 이하. 로컬 URI는 원격 사진 식별자로 사용하지 않음.</td>
</tr>
<tr>
<td>SendOperation(로컬 작업)</td>
<td>capsuleId, operationState, lastError?, photoUploadConfirmed, metadataConfirmed</td>
<td>PREPARING/SENDING/FAILED/UNKNOWN/CONFIRMED. 서버 두 단계 확인 전 CONFIRMED 불가. 같은 전송 ID 정책은 토론 유지.</td>
</tr>
<tr>
<td>LocationSample(일시적)</td>
<td>latitude, longitude, accuracyM, observedAt</td>
<td>현재 판단용; 과거 방문을 영구 참으로 저장하지 않음. 초기 60초/100m 기준은 실기기 후 조정.</td>
</tr>
<tr>
<td>LocalBookkeeping</td>
<td>accountUid, capsuleId, lastSyncAt, cachedUnlockState, notificationAttemptedForUnlock?, registrationInfo?</td>
<td>계정별 복구/알림 중복 억제. notificationPosted는 UNLOCKED 권한이 아님.</td>
</tr>
</table>
## 잠금/화면 상태
- lockType: TIME / LOCATION / TIME_AND_LOCATION. TIME은 unlockAt 필수, LOCATION은 좌표/반경 필수, combined는 둘 다 필수. 둘 다 없는 조건은 거부.
- 권위 있는 unlockState: LOCKED → UNLOCKED만 허용. unlockAt은 서버 시각과 비교할 UTC instant. 표시/입력 의미를 위해 creationTimeZoneId를 보조 필드로 보관하는 제안은 Flora 댓글과 함께 검토.
- 화면 상태 WAITING_TIME / WAITING_LOCATION / VERIFICATION_REQUIRED / AVAILABLE은 저장된 조건·현재 확인 결과에서 파생한다. 표시 단계가 별도 영구 해제 권한이 아니다.
- 시간 전 방문·이탈은 latchedLocationSatisfied로 저장하지 않는다. 초기 해제 때 온라인 서버 확인과 필요한 현재 위치가 모두 성공해야 한다.
- 사진 objectRef는 보호된 참조이며 누구나 읽는 공개 URL로 잠금/계정 경계를 우회하지 않는다.
## 전송 불변조건
업로드(사진 있을 때) → 메타데이터 커밋 → 서버 확인. 응답 유실이면 상태 UNKNOWN이며 Sent 오표시 금지. 같은 capsuleId 존재/서버 결과 확인 후 동일 작업만 재시도하는 안을 추천한다. 서로 다른 정상 전송의 내용이 같다는 이유만으로 자동 삭제/거부하지 않는다(팀 결정 필요).
## 아직 확정하지 않는 필드/기능
전송 후 편집·삭제, 공개 피드/댓글/반응, 음성, 반복/정기 캡슐을 추가하지 않는다. self-send 발신자 열람 예외, 실제 provider/schema/rules, 서버 확정 API와 비용은 열린 항목.
<details>
<summary>English Version</summary>
Provider-neutral entities: minimal public recipient identity; metadata with IDs/lock conditions/monotonic unlock state; separately protected content and one JPEG/PNG ≤5 MiB object; local send-operation status and account-scoped notification/cache bookkeeping. UTC instant is authoritative; creation time-zone metadata is a review proposal. Derive time-wait/location-wait/verification-required UI states without confusing them with UNLOCKED. Do not persist historical location as permanently true. Confirm optional upload plus metadata before Sent; unknown responses remain unknown. Post-send editing, self-send exception, provider schema/rules and billing are not finalized.
</details>
[Notion source](https://app.notion.com/p/3edf1c1dcaa38062aeb9c8bdb57c2cff)
## 남은 리뷰 / Remaining review
준원의 기술/백엔드 검토와 Flora의 제품 흐름 확인이 필요하다. 모든 리뷰 LGTM 및 질문 처리 전 In Review 유지.
<details>
<summary>English Version</summary>
Keep In Review until technical/product reviews give LGTM and substantive questions are settled.
</details>

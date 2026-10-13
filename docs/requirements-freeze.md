<!-- Notion snapshot: 2026-10-13; source: https://www.notion.so/3edf1c1dcaa38024aeb9dff4d0cc1ccf -->
> [Notion source](https://www.notion.so/3edf1c1dcaa38024aeb9dff4d0cc1ccf). Draft snapshot, not unanimous approval or implementation evidence. Latest shared discussions remain authoritative for unresolved choices.

# Requirements v0.1 Freeze Preparation
## 진행 상태
2026-10-13 최종 확정을 위한 준비 문서. 현재 FR/NFR를 복제해 별도 진실을 만들지 않고 원본을 연결한다. 10/15 설계 리뷰와 남은 공동 질문·백엔드 검토 후 범위를 동결한다. 아직 ‘최종 확정’이 아니다.
[Notion source](https://app.notion.com/p/a2cf1c1dcaa382fea69301a4867e6315)
[Notion source](https://app.notion.com/p/3d1f1c1dcaa3824bbff58133eb53130c)
[Notion source](https://app.notion.com/p/53ef1c1dcaa382cc85d80179abc26fc3)
[Notion source](https://app.notion.com/p/3edf1c1dcaa38046b4c5f17e1f1efc49)
[Notion source](https://app.notion.com/p/3edf1c1dcaa3806bb895e37f0dc6c7ca)
## 유지할 MVP 기준
기본 계정·자기 자신/등록 사용자 전송·수신, 텍스트/사진, 시간/위치/현재 AND, 잠긴 수신 메타데이터, 지속 열람, 온라인 최초 해제와 서버 시간, 비권위적 로컬 알림, 앱 실행 동기화. 사진 초기 1장 JPEG/PNG 5 MiB, 위치 초기 150m/60초/100m는 실기기 후 조정할 작업값이다.
## Freeze 전에 확인할 항목
- [ ] Flora/준원 최신 리뷰의 자기 자신 잠금 전 보기·시간대·전송 후 편집·재시도 의견 합의.
- [ ] AC-06 현재 AND 및 시간 대기/위치 대기 표시를 세 문서와 화면에서 확인.
- [ ] 서버/사진 저장 공급자·비용·실제 접근 규칙은 spike 결과를 통해 명시 또는 미정으로 유지.
- [ ] 본 문서/FR/AC의 최종 버전에 모든 검토자의 LGTM 기록.
- [ ] 확정 후 WBS/발표 자료에 같은 범위를 반영하고 변경 이유 기록.
## 발표용 범위 설명
음성·공개 피드/댓글/반응·고급 소셜·외부 링크/QR·자동 삭제·수신 FCM·주기적 새 수신 동기화·GPS 위조 방어는 포함하지 않는다. 계정 간 전달은 현재 MVP이며 과거 stretch 문구와 구분한다. 아직 실제 백엔드 E2E 검증을 통과했다고 발표하지 않는다.
<details>
<summary>English Version</summary>
Freeze preparation, not a frozen specification. Link the source FR/AC instead of duplicating them. Preserve the Option B account/self/registered-user delivery MVP, text/photo, time/location/current AND, online initial unlock, non-authoritative notifications and app-open sync. Resolve self-send, timezone metadata, editing and retry discussions; review backend/media cost and rules after the spike; collect all LGTM before freeze. Numeric photo/location defaults remain subject to real-device evidence. Do not present research or isolated spikes as integrated backend validation.
</details>

# Gates: 온디바이스 소리·영상 생성 구조 설계

OWNS: docs/superpowers/specs/2026-10-03-ondevice-av-generation-design.md, docs/qa/ondevice-av-generation/GATES.md

Scope: 최대 20초 온디바이스 분석·생성의 검토 가능한 설계를 만들고 승인 상태를 기록한다. 제품 구현·QA 완료를 나타내는 ledger가 아니다. 실행·통합·회귀 검증은 설계의 8장에 남아 있다.

- [x] G1: 사용자 확정 요구와 현 구현의 차이를 설계에 기록했다.
  EVIDENCE: 2026-10-03 사용자 최대20초 생성/외부전송금지 요청과 0.5.9 ClipEvidence.kt, SoundAnalyzer.kt, VideoImport.kt를 대조했다. 설계 1·2·5장에 유한후보 선택 제거, 원음·시간순 영상 결합, 로컬 생성 조건을 적었다.

- [x] G2: 첫 후보의 공식 실행 API와 공개 배포 메타데이터를 확인했다.
  EVIDENCE: 2026-10-03 공식 모델 카드, SDK v0.17.1 원천, HF 공개 API 및 hf models card를 조회했다. 설계 4·10장에 revision/선언해시/2,588,147,712bytes와 한 메시지 소리+이미지 입력을 기록했다. 모델 다운로드·실행 성공은 아직 확인하지 않았다.

- [x] G3: 제품 교체 전에 실제 입력·지원 기기·품질을 검증할 기준을 제시했다.
  EVIDENCE: 설계 6·8장에 첫 다운로드, 32K 배포문맥 제한, 시간순서/불일치/무음 대조군, Android ARM 지연·메모리, 비행기 모드, 기록·접근 회귀와 Play 교체 전 검증 조건을 적었다. 텍스트 벤치마크를 영상 성능으로 쓰지 않는다.

- [ ] G4: 사용자가 이 작성된 설계안을 검토했다.
  EVIDENCE: pending

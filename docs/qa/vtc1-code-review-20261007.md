# VTC1 연구 — 독립 두 축 코드 검토

2026-10-07. 고정 시작 `ab1446135af6e9ededa9b1cf90d27f5c5eada498`, 최초 검토 `6c5a52585b21f54ababb74d30aea6e35cd6fe7d0`.
기존 GPT-6.1 Sol/high Spec/Standards 검토자를 별도로 재사용했다.
issue tracker 설정은 없어 local VTC1 계약·연구 QA·현재 사용자 요구를 spec으로 사용한다.
원음·개별 점수·private 이벤트·추론·수정·삭제는 검토자 범위에서 제외했다.

## Standards

문서 규칙 위반0, 비차단 휴리스틱1이다. possible Primitive Obsession:
replay가 manifest의 원시 pcm_samples로 배열을 할당하고 scores/manifest의 JSON 크기나 Job을 제한하지 않았다.
기존 정상 자료를 무효로 본 것은 아니며, 입력 domain/schema와 자원 제한을 보완하도록 제안했다.
source AST·허용 어댑터·strict44key·원음/제품/권리 경계·상태와 정리 증거는 문서 기준에 맞았다.

## Spec

최초 P2 하나: 사전계약의 Python3.13.14가 pin/runner 검사와 기록에 빠지고 torch/NumPy만 검사했다.
다른 Python interpreter가 동일 torch/NumPy로 수용될 수 있는 재현 계약 누락이다.
실제 기존 결과가 틀렸다는 증거는 아니다. 모델 구조·시간축·Binarize·결과/제한 보고의 다른 필수지적은 없었다.

## 수정과 재검증

Python을 pin/runtime 검사에 추가했다. replay는 현재 Python+NumPy를 검증하고 원래 recorded backend runtime도 비교한다.
기존 두 run은 Python 필드가 **미기록**이므로 덮어쓰거나 소급해 검증됐다고 표시하지 않는다.
새 replay에 `historical_python_recorded=false`와 현재 replay runtime을 별도로 남긴다.
validation05는 새 pin의 실제 검사/기록, strict load·합성forward1/private0을 통과했다.

replay에 1~20초 sample domain/manifest1~100case·131,072B/score128MiB/duplicate JSON key 검사,
stale output/evidence 거부와 Job60초/1GiB를 추가했다.
새고유 domain검사를 포함한 temporal/native15, replay실제 hostileCLI5/5를 통과했고
기존3+33case의사건재생변화0/추가추론0을 확인했다.
다른작업의삭제차단대상을유지하며이번metadatafixture만개별제거했다.

수정 고정 `6c5a525...62e7de4`를 두 축에 재검토했다.
Standards는 이전제안해소/새규칙위반0/새휴리스틱0, Spec은 P2해소/새필수0이었다.
두검토자는현재pin/runtime검사·과거Python미기록분리·JSON/domain/Job·QA거부/정리metadata를확인했다.
개별사건을열거나독립재계산하지않았으며delta는허용된집계metadata와고정code에서확인한범위다.
Standards 최초필수0/제안1→수정후0, Spec 최초필수1→수정후미해결0이다.
[실제QA/실패이력](vtc1-20261007.md)을 따른다. 전체 앱 개선·제품배포 완료로 닫지 않는다.

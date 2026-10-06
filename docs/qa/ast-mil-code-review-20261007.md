# AST MIL / 공개 음원 취득 — 독립 두 축 리뷰

기준 `989a721aaaa8afcba80ccabe8eaa544025340bcc`, 최초 로컬 snapshot
`51a31869a1739fa95e6f662b21d434a10592f633`, 수정 뒤 검토 commit
`137d899eb4ffd555ec20991c87256cb031cbb9c1`.
원격 push 전에 별도 Spec/Standards agent가 코드·명세·안전한 실행 metadata/log를 검토했다.
원음/특징 body·사용자 자료를 읽거나 새 추론·학습·빌드·ADB·Git변경·삭제를 하지 않았다.

## Spec

최초 **P2 1건**: 직접 `--worker`는 fresh mkdir/Job 부모를 거치지 않아 기존 fit head/terminal 자료를 덮어쓸 수 있었다.
실제 덮어쓰기를 실행하지 않고 코드에서 경로를 확인했다. fresh launch token·mode/output/code pin,
terminal 거부·exclusive claim을 추가했다. 입력 feature 접근 전에 worker claim을 확인한다.

수정 뒤 **기존 P2 해소 / 새 필수 지적0**. 실제 두 worker CLI는 exit1/head 불변,
고유MIL unit12/1.534초/OK, fit02 exit0/8.799423초/자손0·retained02 exit0/5.843096초/자손0이다.
fit01/02 head SHA가 동일하고 현재8code pin은 검토 commit blob과 일치한다.
원래2cycle QA의 누락을 정정했고 최종3cycle·고유unit13/CLI7·state exists=false를 확인했다.
새 caption wholebag 문서는 사전계약/미실행이며 이번 개선 성과로 세지 않았다.

## Standards

최초 **P3 1건**: '리뷰 뒤 commit/push' 문구가 먼저 로컬 snapshot을 고정하는 실제 스킬 절차와 달랐다.
이전 리뷰/원격 push가 있었다고 주장하지 않고 계약을 '로컬 snapshot 고정→두 축 리뷰/수정→검증 후 원격 push'로 정정했다.

수정 뒤 **기존 P3 해소 / 새 필수 규칙 위반0 / 새 Fowler smell 권고0**.
AST 정상 실행 source8 pin과 취득 source8 pin, unit/CLI/timeout/recovery/dirty/storage/정리 보고가 실제 metadata와 일치한다.
공개 MP3는 한세트2개/667,032B, blocked/null/미검수 그대로다. 512MiB 초기 설정 실패는 timeout 성공으로 세지 않았다.
후속 snapshot71files/301,138B와 scoped state 부재, 과거/수정 뒤 실행 pin 분리를 확인했다.

최종 집계: **Spec 새 필수0, Standards 새 필수0**. 인식 개선·제품/ARM/Play 완료를 승인하는 리뷰가 아니다.
공개자료 추가 계약은 아직 특징 추출/학습/평가0이며 전체 goal은 active다.

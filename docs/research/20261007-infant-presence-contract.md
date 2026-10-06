# 성인 비언어 반례 + 아기 발성 존재 여부 — 실행 전 계약

2026-10-07. [작은head 첫실측](20261007-infant-head-first-results.md)과
[sourceCV 성인웃음오인](20261007-infant-head-source-cv-results.md)에 이어
[새12source 권리감사](20261007-adult-nonverbal-source-audit.md)를 실제 비교로 연결한다.
원음청취/strong 사건라벨·아기욕구정답은없고 caption-weak 개발실험이다.

## 자료와 비교 후보

기존23+새성인12=35publicpreview. Train21(아기7/성인10/기타4), validation14(아기4/성인8/기타2).
새ID/역할은코드의정확한35허용목록으로고정하며임의확장은거부한다. source/currentalias/기존public33
제외·CC0/BY snapshot·원문/preview SHA를그대로유지한다. 기존23media는hash확인해재사용,
새12만취득한다. 파일2MiB/첫20초decode/Job180초2GiB/offlinefeature·fit·평가다.
기존validation과사용자자료를학습·calibration·augmentation으로옮기지않는다.

새과제는 **아기 소리가 포함됐는가**다. 성인과아기소리가동시에날수있으므로
3class softmax의승자만있어야하는가정을제거한1024→1sigmoid(1025param/FP324100B)를비교한다.
기존encoder/필터/975ms시간창·score동등성gate는유지한다. p≥.8·binarymargin≥.4,
고정p는calibrated정답확률아니다. 단일975ms 단서와인접2창·support시간을계속별도로보고한다.

훈련은public21의weakcliplabel만사용한다. 아기1/성인·기타0의binarypresence이며
이는동일clip에서실제창마다아기가발성했다는stronglabel이아니다.
class/source/clip균등가중·clipL2normalize·zero초기화/Adam.03/300step/L2.0001,.001,.01를고정한다.
18train source의leave-one-source-out에서binaryNLL source평균최소(동률강한L2)를선택한후
21train전체로단하나fit,선택/훈련ID/weightSHA동결후validation을읽는다.
이번단계에합성mix·반복crop/augmentation은없다. 단순sigmoid변경만으로겹침분리를해결했다고보고하지않는다.
기존multiclass CV/weight는비교증거로보존하고새값의기존소스소급검증을하지않는다.

## 판단·다음

새14validation·기존public33·retained3/2원본을비교한다. 별도18sourceCV를일반정확도로보고하지않는다.
실제child+adult중첩은binary설계상동시존재가가능하다는가설이며아직분리성능입증이아니다.
사용자첫누락/희소발성·성인웃음2+신규source6·음악/잡음/무음오인회귀를함께본다.
정답/사용권·오인방지·AndroidARM/10·20초/중단·메모리·독립검토가남으면제품에넣지않는다.
threshold조정이나같은private원음을학습해성공을만들지않는다. 종료실험대용량중복은정리한다.

## QA matrix

| ID | 정상·공격모델 | 입력/명령 | 기대/실제·수정·증거·정리 |
|---|---|---|---|
| P01 | 연구자정상 | acquire35/features35/presencefit/eval | source/SHA/score0·21/14분리·최종보고 |
| P02 | source침입 | exact23/35밖·partialextra·duplicate·role/허락 | pure거부·원본보존 |
| P03 | 선택누출 | validationrole·sourcefold중복·class소실 | pure거부·훈련source18만 |
| P04 | 잘못된숫자/무음 | NaN/shape/empty/quiet·NLL | 거부/모델성공없음 |
| P05 | CLI오류·낡은결과 | wrongSHA/stale/outside/missing | 실제CLI nonzero·기존head불변·신규fixture0 |
| P06 | 종료/거짓성공 | Job자손/시간/메모리·completion | actualexit+SHA·잔여0·부분성공거부 |
| P07 | 지시삽입/dirty/정리 | description데이터·state·Git | 실행없음·다른변경보존·이번stateclear |

최대5cycle/같은3실패종료. 품질실패와도구QA완료를분리하며전체앱개선goal은active다.

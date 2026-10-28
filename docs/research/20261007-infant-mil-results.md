# 시간창 weak MIL 비교 — 실제 미회복 결과

2026-10-07. [실행 전 계약](20261007-infant-mil-contract.md)의 실제 실행이다.
**약국 첫누락 회복없음·성인비명오인잔존. 제품 미탑재.** 학습 pooling을 바꾸는 것만으로
현재목표를해결하지못했다. 기존beta0.5.18/code32이후새배포없고전체goal은active다.

## 고정 비교

동일public35/249창의고정학습특징재사용,21train/18source만CV·fit,14validation/11source는
head/selection 저장 후접근. windowL2/actualsample 가중logmeanexp/온도1.0/300step/.03,
3개L2의train source macroNLL은 `.0001=.3116299452, .001=.2631616947, .01=.3868753177`.
사전규칙대로`.001` 하나선택. 저장head2662B/1025param/FP324100B, SHA
`d7cc434ef8175b74ccef6ce5310c32130cde2bfbfdc9405f189ede840567077a`.
sigmoid support .8/margin .4를변경하지않았다. score는정답확률아니다.

| 조건 | binary35 support | MIL35 support | 인접2창(MIL) |
|---|---:|---:|---|
| 약국 첫0–10초 | 0 | 0 | 없음 |
| 같은 원본7.173–17.173초 | .975 | .975 | 없음 |
| 다른 울음 대조 | 2.925 | 1.95 | 있음 |
| validation영아18274/18277/18273 | 모두0 | 모두0 | 없음 |
| validation영아18276 | 0 | .975 | 없음 |
| 신규성인웃음42841 | .975 | .975 | 없음 |
| 신규성인비명384213 | 3.9 | 2.925 | 있음: **오인잔존** |
| 기존성인웃음cchenail/ripc | 모두0 | 모두0 | 없음 |
| public babble 짧은sample | .89775 | 0 | 없음 |
| public babble_crib | 7.8 | 7.8 | 있음 |
| 성인대화/피아노/기계음/무음·tone·noise·DC·rumble | 모두0 | 모두0 | 없음 |

한clip/창의검출시간과source정확도는다르다. 처음영아held4는한15개월source이며caption-weak다.
private3는2원본이고두누락은같은원본에서겹친다. public33은합성과중복source가있는개발/회귀조건이다.
지속회복·반례보존을충족하지않아Android/앱/번역에추가하지않는다.

## 실행과 다음

| run | exit | 초 | peak committed bytes | 자손 |
|---|---:|---:|---:|---:|
| mil35-01 fit | 0 |12.782|704,606,208|0|
| retained-mil01 평가 |0|.922|700,682,240|0|
| public-mil01 평가 |0|4.875|703,037,440|0|

각Job180초/2GiB/offline/termination없음. runnerSHA
`87bbd6fd47e07a2f4da6f4087195a407ccc67cba4445e6466f97ca688e43ea55`,
feature `467fb305d9555de6012a6b75988f042aa3d836675d4cac95b94004c097742e32`,
presence `8fb04de499c59c65cafa9cbb47988b59aeb636257627c92bafb72e7fd7856d83`.
학습공개특징은재사용했다. 평가3+33은실제로encoder를다시실행했으며,
계약초기‘encoder추론없음’문구를학습특징단계의범위로명확히했다. 실제추론을0으로보고하지않는다.
raw평가자료/출처·head·process/completion은ignored폴더에남기고외부전송하지않는다.

[새representation 원문조사](20261007-next-infant-representation-evidence.md)에서 실제0.81MB옹알이전문head는찾았지만
feature/normalizer·openSMILE상업조건·weights허락을확인하지못했다. ready제품후보로승인하지않는다.
현재영아train4source가작다는한계가구체화됐으며source를검수·확장하는것을우선한다.
새source/representation을조사하고조건을만족한이득만Android통합/QA/비공개업데이트한다.
[도구QA](../qa/infant-mil-20261007.md)완료는제품품질완료와별개다.

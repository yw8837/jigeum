# 새 source2의 whole-recording caption bag — 별도 미검수 연구 계약

2026-10-07. [경계 조사](20261007-weak-presence-training-boundary.md)와 원문/source 검토에 따라
**이번 exact 실험만** 미검수 caption의 noisy bag loss를 허용한다. 기존 사건 학습 승인과 별도다.
기존 `training_blocked=true`, `approved_training_label=null`, `audio_review_completed=false`,
`event_ground_truth=false`를 보존하고 일반 loader를 수정하거나 우회하지 않는다.
제품에 새 모델을 넣는 승인도 아니다. 실제 사건/화자 판독은 수행되지 않았다.

## Allowlist와 입력

실험 ID `ast-caption-wholebag-20261007`. [취득 증거](20261007-new-infant-audio-review-acquisition.md)의
정확 두 CC0 CDN preview만 새 연구 train bag으로 받는다.

|ID/uploader|음원 SHA|실제16k mono sample수|caption weak bag target|
|---|---|---:|---|
|9437/Rodcencko|61e4354ad089ecd676b2a5d1e9dfde32a4695563087420a8f0e46bb67b1e86c0|21,456|파일 어딘가 영아발성이 있다는 noisy 주장1|
|244237/Sauron974|05aa0e243efcbb3e66265f2966f9dd279a7d254ccf5193cea7dbcf6dd7349ac9|1,560,295|동일 noisy 주장1|

- acquisition SHA `0894e5ff8d6446afda67b9ad1721f8901f7c473484bff2721a06abdbc2c55cf1`,
  levels SHA `0c8e9718c6c42980034499a05f291c52fbe98350f0d9b16a4b9b7d5b027cf364`,
  validated-completion SHA `cee1c0ca602145a62d9b2f98c97e79340caa117b2f99255d6a907c4fb39b932c`.
- exact ID/sourcegroup/role·CC0·권리 snapshot·blocked/null·실험 ID를 모두 검사한다.
  허용 역할은 `future_train_review_candidate`; 별도 experimental-use로만 train을 기록한다.
- full FFmpeg9 SHA를 고정하고 전체 decode actual sample수 일치/NaN/길이/원음종료SHA를 검사한다.
  mono16k s16le→float,10초 비중첩 전체 coverage; 마지막tail<1초만1초까지0padding한다.
  원음/WAV/PCM 사본을 새로 저장하지 않고 약37KB 공개embedding만 저장한다.
- 9437의1span,244237의10span을 모두 추출한다. first20초·선택된 일부span에whole-file target을 붙이지 않는다.
  RMS<.003 span을loss에서제외하되 coverage/제외span을 기록한다. 실제sample로가중합1을 만든다.

## 학습·선택·비교

- 기존 exactpublic35 train21/18source + 새2bag/2source =23train/20uploader source.
  기존 validation14/11source는 유지한다. 가족독립/alias부재는 검증되지 않았다.
  알려진 sourcegroups와 새두 uploader 이름의 겹침을 거부한다.
- 기존 encoder/model/43span archive를 재사용한다. 새2bag의11span만 원527logits exact parity를 검사하며 추출한다.
- 768→1/769param, spanL2·actualsample 가중 log-mean-exp/온도1, bag당BCE하나.
  span을 개별 positive row로 학습하거나attention/highscore를사건정답/pseudolabel로재학습하지 않는다.
- class/source/clip 균등 loss,zero/Adam .03/300step, L2 .0001/.001/.01.
  train source leave-one-out macroNLL 최소/동률강한L2; 각fold와class/source손실을함께보존한다.
  private/validation feature를 사용하지 않고 선택/head SHA를동결한다. p≥.8/margin≥.4 유지.
- 동결후validation14/사용자retained3/public33을기존평균head와비교한다.
  모두 반복노출된개발/회귀자료다. 일반정확도나새안전성확인용독립test로보고하지않는다.
- 기존약국2support상실/새성인·음악·무음support/기존승인회귀가있으면후보미채택.
  새지원만생겨도인간사건검수/화자·감정충돌/Android/ARM gate전제품승격0이다.
  사용자음원·feature학습/증강/외부전송0, 욕구/번역texttarget0.

## 실행·QA matrix

출력은 ignored `work/ast-caption-wholebag-20261007/`의새run만 허용한다.
network금지/Job180초·feature/eval3GiB/fit2GiB,새encoder/LLM/modeldownload0.
추출은1회·fit는사전grid/CV1회·기존평가동결후1회로제한하며실패는분류/보존한다.

|ID|의도/설정|기대·증거|정리|
|---|---|---|---|
|W1|권리·ID·사용계약·blocked/null/pin변조|학습 전 거부; 새연구예외exact2만|원본 불변|
|W2|whole coverage·tail·padding·quiet·NaN/길이|actualsample합/가중합1·잘못된bag거부|메모리fixture|
|W3|CV·family과장·validation leak|train-only20fold·caption-only·loss보고·headfreeze|smallhead한개|
|W4|outside/stale/missing/forged-head CLI|nonzero·원본보존·잘못된output0|ownedfixture정리|
|W5|정상feature/fit/eval·falsecompletion|process/pin/runtime/자손0·complete일치|실제JSON/log|
|W6|dirty/중단·timeout·recovery/state|범위보존·scopedstateclear·bounded중단|fixture/process정리|
|W7|용량전후/새copy|실제snapshot·새raw파일0·이전삭제차단우회0|필요한head/features/log만|

최대5cycle/같은실패3회stop. 로컬검토snapshot 고정→두축독립review·필수수정→검증뒤원격push한다.
이 계약 작성 당시에는 새2source 특징 추출/학습/평가가0이었다.
이후 실행·단독 회귀와 보완 비교는 [실측 결과](20261007-caption-wholebag-results.md)를 따른다. 전체goal active를유지한다.
이계약의weak-only허용은새자료사건학습/제품품질승인으로전파하지않는다.

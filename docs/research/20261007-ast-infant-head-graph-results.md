# AST tiny head ONNX 연결 — 첫 수치·구조 검사 통과

2026-10-07 KST. [실행 전 계약](20261007-ast-infant-head-graph-contract.md)을 처음 실행했다.
**원 AST의 모든527 logits 차이0, 동결 head와 새 graph score 차이≤6.7647e-8**이다.
기존 약국10초 두 구간의 support도 유지됐다. Android·일반정확도·욕구 해석 승인이나 제품 적용은 아니다.

## 모델과 입력

기존모델91,134,123B에 768 L2→MatMul→Add→Sigmoid8개 node와769개 FLOAT parameter를 연결했다.
새파일91,138,597B, **추가4,474B**. SHA
`4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a`.
원 AST asset·852개 node·모든 기존initializer/input/logits는 변경하지 않았다.
단위 검사는 각 serialized node/initializer/input/logits byte동일성도 확인했다.
기존BSD3/원천 고지를 유지한다. head SHA는 사전 고정 `81d7357c…bdfd510`이며 새 fit·threshold 선택은 없다.

| 실제 입력 | span 초 | graph score | support |
|---|---|---:|---|
| 합성silence1 |0–1|.0433673|보류 |
| 합성tone10 |0–10|.2204099|보류 |
| 합성sweep2 |0–2|.1117315|보류 |
| public113221 |0–1.73275|.9720060|통과 |
| public184432 |0–10|.0097081|보류 |
| public256684 |0–9.7443125|.0085858|보류 |
| 약국첫누락 clip_b_0_base |0–10|.8197847|통과 |
| 같은원본후속 clip_b_1_base |0–10|.9400272|통과 |
| 다른울음 clip_c_0_base |0–8.7893125|.6419100|보류 |
| 합성tone20 |0–10/5–15/10–20|각.2204099|모두보류 |

10조건/12span, 원본·파생각1회/총24model forwards. NumPy reference는 새graph가 반환한
768표현에 동결FP32 head를 적용한 독립 연산이다. 모든원527 logits를 기존모델과 직접 비교했다.
이 연산동등성과 별도로 원음/정답/제품경로의 승인범위는 확장하지 않는다.
20초 중첩은 **합성tone의 시간축 검사**이며 실제아기20초나 Android에서 실행한 결과가 아니다.
quiet graph의 낮은 score는 수치검사이며 제품의 기존quiet 거부를 없애는 근거가 아니다.

## 실행·검사·용량

- PC 고정runtime Python3.13.14/NumPy2.4.6/ONNX1.20.1/ORT1.24.4/CPU4/inter1/sequential.
- Job120초3GiB: exit0,22.781594초, peak committed2,279,014,400B, termination없음/cleanuptrue/자손0.
  두 session 연구 비용이며 휴대폰 한 session의 속도·메모리·발열 수치가 아니다.
- runner SHA `896ec462cf2840e8482805c9500c91cd5c4f2c23a69376ca8a8bf4862aaf6587`.
  completion의 head-runner/interface/frontend SHA와 실제 실행코드가 일치한다.
  시간창 helper는 기존 `ondevice_audio_candidate.window_ranges`를 사용했다. 그 helper SHA는
  이번completion의별도필드에없으며 Git 고정 코드/명시된세중첩span으로 추적한다. 완전한실행의존성pin으로 과장하지 않는다.
- 단위2/exit0: 원node/weight/input/logits 유지, 분리한작은head의 유한NumPy 동등성·norm floor.
  합성operator 테스트는 영아정답검사가 아니다.
- 실제 CLI4거부/4통과/exit0: wrongsourceSHA/stale/outside/missing, 기존head불변·새output/fixture0.
  이번 검사는 **graph interface preflight**이며 새 전체UltraQA/Android QA 완료가 아니다.

원음 취득·새weight 다운로드·사용자 학습/전송0. 후속Android 후보에 필요한 정확한모델파일한개만
ignored `work/infant-head-20261007/ast-head-graph01/ast-infant-presence-candidate.onnx`에 보존했다.
이는 기존AST의91MB를복사한후4.4KB를추가한파일이며 새로운큰LLM을받은것이아니다.
앱제품asset·최신/직전배포본·다른작업자파일을 바꾸지 않았다. 종료후RUNBOOK정리대상이다.

전후 `storage-before-ast-graph01.json`/`storage-after-ast-graph01.json`:
작업폴더7,201,805→98,352,939B/285→297files, 증가91,151,134B.
C여유76,577,284,096→76,459,495,424B. 폴더증가와 C전체변화를 구분한다.
원음·특징·candidate model은 Git에 커밋하지 않는다.

## 다음 실행

[별도9clip/3uploader 평가 계약](20261007-extra-babble-evaluation-contract.md)의 허락과
학습차단·unknown age/naturalness를 유지하며 동결head를 평가한다. 추가gibberish 반례와 TV공백도 확인한다.
이어 실제 Android의원음·기존승인보존·20초중첩/전체AV·취소/메모리·ARM을 검사한다.
현재 앱0.5.18/code32 유지·새beta없음·전체goal active다.

# 현재 AST 표현 노출 — 실행 전 계약

2026-10-07. [정적 감사](20261007-existing-ast-embedding-interface-audit.md)의 다음 단계다.
제품모델 변경·학습·private자료 접근·새다운로드 없이 기존encoder의 표현 노출만 실행한다.

- 현재AST91,134,123B/SHA`3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`.
  기존logits/node/weights/input을유지하고 classifier layernorm FLOAT768output만메모리에서추가한다.
  파생model파일을저장하지않고앱기존hash를대체하지않는다. BSD3원고지는유지한다.
- runtime Python3.13.14/NumPy2.4.6/ONNX1.20.1/ORT1.24.4/CPU4thread/inter1/
  sequential/memorypatternoff/기존default optimizer. 원본·파생session모두같은설정.
- 먼저합성1초silence/10초tone/2초sweep. 이어이미취득한publicID113221/184432/256684만
  exactsource35snapshot/acquisition bytesSHA를검사해첫10초mono16kPCM16으로decode.
  1초미만이면거부하며waveformpadding/gain/DCfilter를추가하지않는다.
- 기존frontendextract/padding-before-normalize를사용한다. 527logits모두**exact equality**와
  안정sigmoid동일성을요구하며불일치시gate완화없음/결과보존. FLOAT[1,768]/finite도요구한다.
  전체AST를원본·파생각각1회씩실행하는것은interface parity연구용이며제품추론2회구조가아니다.
- 실제각case모델시간·inputSHA·원모델전후SHA·runner/frontendSHA·메모리측정.
  두session연구메모리를한session제품비용으로보고하지않는다.
- offline/Job120초3GiB/자손0/stale/outside/missingargs거부/소량publicfeature만ignored저장.
  사용자자료학습/전송금지. 성공이영아구별·희소사건위치·욕구번역·Android검증을뜻하지않는다.

통과후에는public출처별768head학습과반례평가계약을별도로고정한다.
실패해도encoder를제품에교체하지않고원인을진단한다. 전체개선goal은active로유지한다.
시작/종료용량·소스/실행기록을보존하고종료임시모델파일없음을확인한다.

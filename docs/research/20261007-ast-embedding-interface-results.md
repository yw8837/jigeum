# 기존 AST 768 표현 — 첫 실제 인터페이스 결과

2026-10-07. [계약](20261007-ast-embedding-interface-contract.md)/[정적 감사](20261007-existing-ast-embedding-interface-audit.md).
**6입력의기존527logits가모두exact동일(차이0)이고FLOAT[1,768]표현이반환됐다.**
학습/영아구별/의도정답/Android/제품변경/새베타배포는없다. 전체개선goal은active다.

실제입력은합성silence1초/tone10초/sweep2초와기존public113221(27724sample),184432(160000),
256684(155909)다. 첫10초PCM16/16k/mono를기존frontend에넣었고padding/gain/DCfilter를추가하지않았다.
public원음SHA·source35snapshot·원자산전후SHA를검사했다. 사용자자료접근/전송0.

원모델91134123B/SHA`3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`.
메모리파생graph91134200B/SHA`52689ae027bf52aaa5a2e2fa850dcbdfdacd6faa591a3fadc42e87e5761faa95`.
기존node/weight/input/logits그대로,layernorm768output선언77B추가다. 모델file복사/새다운로드없음.
public/synthetic featurearchive18546B/SHA`554786740d44fdb1fca99d31d3e5cbc374b0a428c33ad5208efd9fe7b199c2f1`만
ignored `work/infant-head-20261007/ast-interface01/`에보존했다. Git/앱에미포함이다.

실제Job120초/3GiB: exit0/11.303375초/peakcommitted2368811008B/잔여자손0/cleanuptrue/termination없음.
두세션동시연구의끝RSS925339648B이며제품한세션메모리측정이아니다.
1회씩실행한원본/파생시간은각각.513/.498,.623/.827,.914/.816,.865/.863,.904/.880,.831/.814초다.
warmup/반복통계/모바일비용없으므로속도개선/동등성보증으로보고하지않는다.

runtime Python3.13.14/NumPy2.4.6/ONNX1.20.1/ORT1.24.4/CPU4thread/inter1/sequential/memorypatternoff.
runnerSHA`95a3c34aaf4d2e654c01ccab4679835d5cd32fdc2e931d54f3f44f2b24a4041e`,
frontendSHA`ec9c7ec815508733a8a150a36b81a5052daa0d019b618ef6f2d185ff12fab018`.
unit2/exit0(미세logit차이·NaN·dtype/shape·wrongsource 선행거부),실제CLI4/4nonzero/기존출력불변/새fixture0.
이는 **첫인터페이스preflight**이며전체UltraQA/모바일품질승인이아니다. baseline모델기존검증도새head검증을대신하지않는다.

시작/종료storage snapshot: 연구폴더6890476→6918732B/236→249files,
C여유76407824384→75664732160B. 전역C변화와폴더증가28256B를구분한다.
모델세션·worker는종료됐고새모델file없음/기존차단fixture·개인원본·제품assets보존이다.
증거는ast-interface01/process,completion,interface및guards-ast-interface01/report,unit-ast-interface01.log.

다음은public source별768head를고정학습하고본래누락/새반례와비교한다.
10초표현을975ms발성위치로치환하지않으며개별구간과실제시간coverage를유지한다.

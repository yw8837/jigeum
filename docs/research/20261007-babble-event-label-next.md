# 옹알이 사건 정답의 다음 경로: ACLEW와 BabbleCor

확인일 **2026-10-07 KST**. 기존 연구를 먼저 읽고 미해결 **ACLEW/BabbleCor**만 좁혀 확인했다. 이 문서만 소유·작성하며 음원/weight 취득·청취·훈련·추론·개인 자료·외부 메시지·기기·키·Git·빌드는 모두0이다. 모든 후속 후보는 `training_blocked=true`, `approved_training_label=null`이다.

## 1. 결론

**실제 자연 장면의 who/when 및 canonical/noncanonical 발성 라벨 경로는 ACLEW에 있다. 그러나 이번에 합법적으로 실행 가능한 6–18개월 babble/adult/TV/laugh/cry의 완성 event test bundle을 확보하지 못했다.** BabbleCor는 발성5종 구분에 더 가깝지만 공개 API storage가 비어 있으며 정확한 span/원음/상업 허락을 읽을 수 없었다.

새로 확인한 것은 ACLEW의 target-age 녹음 ID/공식 세분 라벨 설명/실제 annotation의 불일치와 BabbleCor의 현재 공개 API 상태다. 새 corpus 발견·완성 정답 확보·모델 개선으로 표현하지 않는다. bvaudio475589는 caption-only 평가 source이며 이 corpus의 가족/사건 정답으로 사용하지 않는다.

기존 [10-04 vocal 후보](20261004-additional-vocal-candidates.md)의 SMD/BabbleCor schema·접근 차단, [10-01 cry 근거](20261001-cry-evidence.md)의 ACLEW 제한, [10-06 specialist](20261006-specialist-actionable-next.md)의 ComParE2019 발성5-way/CRIED4–16주 범위는 재사용했다. CRIED는 target 월령 밖이고 기존 모델/weight 조사는 반복하지 않았다.

## 2. 후보 비교: 확인한 사실과 미확인

| 항목 | ACLEW/AAS | BabbleCor |
|---|---|---|
| first-party schema |[공식 annotation 페이지](https://sites.google.com/view/aclewdid/annotation)와 [Soderstrom2021 원문](https://talkbank.org/homebank/access/0docs/soderstrom2021.pdf): ELAN waveform segment의 speaker tier, target child(CHI) vocal maturity subtier, 다른 화자의 addressee subtier, minCHAT 전사 |[현재 공식 OSF node](https://api.osf.io/v2/nodes/rz4tx/): 각 clip unique ID, speaker metadata 연결. canonical/noncanonical/cry/laugh/junk5-way는 기존 공식 논문/recipe 조사 근거이며 이번 actual label 파일은 없음 |
| event/time granularity |who speaks when을 segment로 annotation. canonical/noncanonical syllables 및 recognizable words/multiword 단계. 실제 EAF의 time slot/unit/정확 tag enum은 **미열람** |기존 ComParE2019는36–500ms chunks(modal400ms)와 majority labels. 잘린 clip label이며 원장면 onset/offset/성인·TV 라벨 정답으로 확장 불가. original span mapping 미확인 |
| 실제6–18개월 연결 |[공식 ACLEW.csv](https://talkbank.org/homebank/access/Public/ACLEW.csv)에 age_mo_rou6/7/8/9/10/11/12/13/14/17 등 기록. **recording target child age**, 순간 화자 age/정확 생후 개월·독립 가족 검증이 아님 |기존2–36개월 범위에 포함될 수 있으나 이번 node에 age별 파일 표본/분할 없음. 특정6–18개월 ID 미확정 |
| adult/TV/laugh/cry |adult/child speaker 및 electronic-media speech annotation 설명 있음. target-child maturity는 babble 관련. **laugh/cry 독립 enum·각 event 실제 표본은 이번 확인하지 못함** |cry/laugh는5-way에 있음. junk를 모든adult/TV의 깨끗한 negative로 간주할 수 없음 |
| 공개 접근 |공식 소개/CSV/논문/annotation 설명은 읽힘. 실제 corpus 링크는 Secure/Password 아래이며 EAF/audio는 corpus별 접근 제한 적용 |node `public=true`, `current_user_permissions=[read]`. provider osfstorage는 보이지만 **root data=[]/total0**. 공개 node와 실제 자료 공개는 다름 |
| 원음/labels 상업권 |[공식 Usage Restrictions](https://talkbank.org/homebank/access/Public/ACLEW.html): 각 corpus 및 HomeBank 제한이 EAF에도 적용. raw audio/EAF 공개 재배포·발표는 participant별 explicit 허용 필요. 기존 HomeBank CC BY-NC-SA3 및 추가 corpus 조건으로 상업 허락 미확보 |node `node_license=null`. 기존 접근 문의/DUA 경계 유지. 공개5-way 논문/README가 원음·labels의 상업권을 대신하지 않음 |
| weights 상업권 |이 annotation collection은 새 classifier weight grant가 아님. 이번 weight/card 미조회·미취득 |이번 공개 node에 weight grant 미확인. 이전 classifier/code license를 dataset/파생weight 상업권으로 전파하지 않음 |

## 3. 실패 근거와 작은 예시의 정확한 범위

**ACLEW 공식 원문 §Annotation/Tutorials/Reliability**는 CHI/FA1/MA1 speaker와 maturity/addressee를 가진 Figure2 예시를 설명한다. 이는 schematic 예시이며 실제 실패 음원 파일 ID/초 단위 span을 고정한 것은 아니다. `0.`은 미전사 발화의 placeholder다. **`0.`을 비언어 발성·negative label로 바꾸지 않는다.**

같은 원문은 DiViMe가 speech라고 잡은 **TV·crying·heartbeats**를 사람이 선별 제외했다고 명시한다. 논문에는 live/recorded speech 구분의 기술적 어려움도 있다. **실패 종류는 확인했지만 해당 실패 clip의 exact 파일·time span은 미확보**다. TV가 선별 단계에서 제외된 subset을 TV event 평가 set으로 사용할 수 있다고 가정하지 않는다.

원문 Reliability는 speaker-type cross-corpus κ0.61(range0.55–0.68), vocal maturity κ0.57(0.27–0.72), addressee κ0.48(0.33–0.65)를 보고한다. 이는 인간 annotation 신뢰도이고 우리 모델 정확도가 아니다. 임의 파일1분 재판독에서도 불일치가 있다는 근거이므로 모든 label을 무오류 참값으로 취급하지 않는다.

이번 실제로 읽힌 **작은 공개 항목**은 ACLEW.csv **6,731B**다. 사건 정답 파일이 아니라 recording metadata다. 부모 생년 등 불필요한 개인/가구 필드는 후속 문서/평가에 가져오지 않는다. target-age 연결 확인 예:

| homebank_ID /ACLEW_ID | age_mo_rou·corpus | 확인/미확인 |
|---|---|---|
|0848_000907 /1130|9개월·Warlaumont|recording metadata만 확인. EAF/audio/event·권리 허용 미확인 |
|0300_000607 /3528|6개월·Warlaumont|위와 같음 |
|0107_001128 /8768|12개월·McDivitt|위와 같음. 추가 접근 제한 적용 |
|0103_010502 /9854|17개월·McDivitt|위와 같음 |

다음 작은 원문 경로는 공식 annotation 페이지와 논문이 연결한 **[ACLEW tutorial OSF b2jep](https://osf.io/b2jep/)** 및 **[GoldStandard repository](https://github.com/aclew/ACLEW-GoldStandard)**다. 논문은 tutorial에 ELAN template·gold-standard comparison training 자료가 있고 repository가 segmentation overlap/tag accuracy/error report를 계산한다고 설명한다. **이번 두 경로의 파일 목록/README/template/body/license는 미조회**이며 즉시 audio/EAF 취득 가능하다고 보고하지 않는다. 다음은 공개 metadata/README 또는 template1개만 읽어 정확 enum·time unit·예제 basename·license를 고정하는 작업이다. 접근/동의가 요구되면 우회하지 않는다.

## 4. 다음 실제 평가 전 필요한 계약

1. ACLEW의 exact EAF/sample와 lawful 연결 음원을 함께 고정하고 허용 participant/corpus 범위를 확인한다. metadata만 있으면 age-linked event 정답으로 세지 않는다. BabbleCor가 gated/empty면 기존 차단을 유지한다.
2. actual annotation의 CHI/adult/electronic media와 babble/laugh/cry/uncertain enum·overlap·time unit·unlabeled gap을 확인한다. unlabeled 구간을 무발성/negative로 자동 채우지 않는다. child maturity만으로 sound identity와 TV/playback을 동시에 입증하지 않는다.
3. 6–18개월 target recording의 실제 화자 age/source 관계와 split 중복을 확인한다. 기존 train35/extra9/public 평가 source, bvaudio475589와 별개임을 label/lineage에서 검증하기 전 새 독립 가족/test로 부르지 않는다.
4. 모델 score 비공개 인간 검수와 dataset의 기존 label을 분리한다. 현재 head/gate 동결, trainingblocked/approvednull 유지. 실제 오류 clip/음원/event span이 없으면 **schema-only/metadata-only** 결과로 남긴다.
5. corpus의 원음·labels 및 weight 권리를 별도로 확인한다. 논문/코드의 허락을 상업 학습·배포·증류 허락으로 치환하지 않는다. external inquiry·동의/가입·자료 요청은 이번에 실행하지 않았다.

## 5. 이번 조회와 오류: 상한8 소진

| # | 실제 GET·결과 |
|---:|---|
|1|[ACLEW 공식 소개](https://talkbank.org/homebank/access/Public/ACLEW.html)200/7,411B |
|2|[BabbleCor node API](https://api.osf.io/v2/nodes/rz4tx/)200/4,700B |
|3|[ACLEW.csv](https://talkbank.org/homebank/access/Public/ACLEW.csv)200/6,731B |
|4|[BabbleCor provider API](https://api.osf.io/v2/nodes/rz4tx/files/)200/947B |
|5|[BabbleCor osfstorage API](https://api.osf.io/v2/nodes/rz4tx/files/osfstorage/)200/128B/**empty1** |
|6|[ACLEW project home](https://sites.google.com/view/aclewdid/home)200/126,559B |
|7|[ACLEW annotation](https://sites.google.com/view/aclewdid/annotation)200/123,189B |
|8|Jina Reader→[공식 Soderstrom2021 PDF](https://talkbank.org/homebank/access/0docs/soderstrom2021.pdf)200/text67,691B. PDF binary 저장0 |

**외부 조회8/8, HTTP 오류0, empty listing1, 검색 query0**. 로컬 rg wildcard 경로 오류1은 수정했으며 외부 조회를 발생시키지 않았다. calls는 발행한 GET 수이며 Jina 내부/redirect 수가 아니다. corpus 음원·label/EAF·weight body 취득0, 유저/기기 접근0, 실행0. 기존 반복 metadata를 신규 음원/정답 확보로 보고하지 않는다.

확인 snapshot SHA256: ACLEW.csv `e0a7bccd098d9fcb367def9e635a930dd43bebc20f08496b3759cc6de5d09ded`; BabbleCor node `c257242c820b44920767327df415ee002f5e550eb47176d542f246047b3c4d3a`; 논문 Jina text `ec0a452e8ced957a88420cb44ba82aa87a27b94d5d2633b61836e189bb1aff2d`. 모두 text/metadata이며 음원 hash가 아니다.

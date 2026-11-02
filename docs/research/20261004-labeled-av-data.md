# 영아·보호자 공개 AV corpus: 발화 주체/경계 검증 적합성

조사일: 2026-10-04 · 범위: TalkBank/CHILDES의 Providence와 Rollins 공식 corpus 설명, 접근·사용 규칙. 사용자 원본과 공개 media 파일은 다루지 않았다.

## 요약

**Rollins가 6–18개월 검증 표본 후보로 가장 직접적이다.** 공식 설명은 6, 9, 12, 18개월 등에서 보호자-영아 상호작용을 비디오로 녹화하고, 각 dyad의 10분 놀이를 CHAT으로 전사했다고 한다. Providence도 실제 audio/video와 보호자·아동 전사가 있으나 자료는 첫 단어 시작 무렵인 약 11개월 이후이며, 6명 중 Ethan의 비디오는 없다.

다만 공개 corpus 설명만으로는 두 자료 모두 **babble 전용 표기, 각 발성의 정밀한 onset/offset timecode, 영상 frame에 정렬된 주체 라벨**이 있다고 확인할 수 없다. CHAT 전사와 발화 경계 설명은 있지만 ASD 검증용 frame-level 정답으로 볼 근거는 부족하다. 파일 열람은 계정 등록을 요구하므로 이 조사에서는 CHAT 파일을 직접 읽지 않았다.

| Corpus | 연령·실제 AV | 화자·경계 표기 근거 | 발성 주체/옹알이 라벨 적합성 | 접근·사용 |
|---|---|---|---|---|
| **Rollins (CHILDES English Rollins)** | 54명. 6, 9, 12, 18, 22, 30개월 방문. 실제 비디오, split-screen 영상. 각 dyad의 10분 놀이 전사. | 공식 설명에 CHAT 형식, mother-infant dyad, utterance boundary는 주로 억양 윤곽·보조적으로 pause duration이라 기재. CHAT으로 전사했다는 사실은 화자 tier 기반 대화 전사와 맞지만, 공개 설명에서 실제 tier 이름·단어별 timestamp 형식은 확인 못함. | 영아와 어머니 상호작용이라 주체 비교에는 적합성이 높다. babble-specific tag, 비언어 발성 전사 일관성, audiovisual frame-level 정답은 설명에 없음. | corpus 페이지의 Browsable transcripts/Download transcripts/Link to media folder가 실제 링크. CHILDES 정책상 데이터 열람·다운로드에 이메일 등록/로그인이 필요. TalkBank 기본 CC BY-NC-SA 3.0은 상업 제품 포함을 금지. |
| **Providence (PhonBank English Providence)** | 6명, 표기 연령 0;11~4;00. 실제 video와 audio; Ethan만 video 없음. 집에서 보호자(대개 어머니)와 상호작용. | 공식 설명은 adult·child utterance를 orthographic CHAT으로 전사하고, child utterance는 broad phonemic transcription했다고 명시. 발화/영상 연결. 정확한 시작·끝 timestamp, speaker diarization 정답, babble 전용 코드 여부는 corpus 설명에서 확인 못함. | 11~18개월을 포함하지만 시작은 첫 단어 무렵이고 6개월 표본은 없음. 아동 음성 전사는 강점이나 babble을 일관되게 표기한다고 말할 근거 없음. 한 참여자의 video 부재도 주의. | Browsable transcript, Phon/CHAT zip, media-folder 링크 표시. CHILDES 등록/로그인 정책 적용. 기본 TalkBank CC BY-NC-SA 3.0, 비상업 조건. |

## 접근 및 권리 해석

- CHILDES [Data Access Levels](https://talkbank.org/childes/access.html)는 corpus/media 데이터 대부분이 **registration required**라고 설명한다. email과 password를 만들고 이메일 확인을 해야 browser/download로 접근할 수 있다. 페이지의 403 차단 안내도 확인했으나 가입·로그인하지 않았다.
- TalkBank [Ground Rules](https://talkbank.org/0share/rules.html)는 별도 명시가 없는 데이터에 CC BY-NC-SA 3.0 적용, 상업 제품 포함 금지, 연구·교육·임상 목적의 비상업 사용을 설명한다. 따라서 여기서의 연구 표본 접근과 앱 상업 모델 학습/배포 권리는 다른 문제다. 원본 데이터 재배포도 이 조사로 허가되지 않는다.
- 이 corpus들은 공개문서상 연구 전사와 AV 관찰 후보일 뿐, TalkNet/YAMNet/AST의 평가용 고품질 정답셋으로 인증되지 않았다. 실제 transcript/video를 사용하기 전에는 계정 접근 조건과 각 corpus별 고지/동의 범위를 확인하고, 해당 데이터는 로컬에 두며 외부 시스템으로 보내지 않는다.

## 정확한 공식 출처와 필드

1. [Rollins corpus 설명](https://talkbank.org/childes/access/Eng-NA/Rollins.html): `Participants: 54`, `Media type: video`; “brought into the lab within 1 week of their 6, 9, 12, 18, 22, and 30-month birthdays”; “split-screen images were videotaped”; “10 min ... transcribed”; CHAT 및 utterance boundary 근거. Browsable transcripts, download, media folder 링크가 페이지에 있다.
2. [Providence corpus 설명](https://talkbank.org/phon/access/Eng-NA/Providence.html): 참여자별 age-range 표; `Media type: video`; 오디오 모두, Ethan 제외 비디오; “Both adult and child utterances ... orthographically transcribed”; 아동 utterance phonemic transcription; linked acoustic files/video.
3. [CHILDES 데이터 접근 단계](https://talkbank.org/childes/access.html): registration-required 절차와 별도 controlled-access corpus/video 예외 목록.
4. [TalkBank Ground Rules](https://talkbank.org/0share/rules.html): 기본 CC BY-NC-SA 3.0, 상업 제품 금지, 비상업 연구·교육·임상 목적, 웹 처리 및 식별 정보 로컬 보관 관련 조건.
5. [Providence의 개별 transcript 기록 (MPI/TLA)](https://archive.mpi.nl/tla/islandora/object/tla%3A_corpora_childes_data_orig_Eng_NA_Providence_Naima_nai44_cha): 무인증 열람 시 archive가 403을 반환해 CHAT 본문을 직접 검증하지 못했다. 따라서 이 보고서는 sample의 구체적인 timestamp tier나 `yyy` 등 표기를 확정하지 않는다.

## 판단

좁은 후보 검색에서 **연령대·실제 AV·보호자/영아 상호작용까지 모두 확인되는 후보는 Rollins**, 아동 phonetic annotation 및 linked audio/video 후보는 **Providence**다. 그러나 둘 다 현재 공개 설명만으로 babble 정답 라벨과 시간 정밀도를 보증하지 않는다. 다음 판단은 corpora 공개 documentation 수준에 한정한다. 새 모델 후보 검색이나 media 다운로드는 하지 않았다.

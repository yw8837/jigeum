# 트레일러 재현 자료 — 과거 제작 이력

2026-10-28: 이 폴더는 확정 트레일러 제작 당시 소스 보관용이다. 아래0.3/0.4 기능 설명과 캡처는 과거 상태다.
현재 홍보는 [최신 소재](../../outputs/Marketing/README.md)를 사용한다. 확정 MP4는 이번에 변경하지 않았다.

# 확정 트레일러 재편집 소스

최종 사용 파일은 [jigeum-trailer-b-family.mp4](../../outputs/Marketing/jigeum-trailer-b-family.mp4)다. 이 폴더에는 선택한 원본 자산·효과음·당시 앱 화면과 편집 스크립트를 보존했다. 다른 환경에서 재편집할 수 있도록 정리한 것이며 폐기한 시안은 포함하지 않았다.

| 위치 | 내용 |
|---|---|
| `assets/parents-confused.png`, `assets/parents-happy.png` | Imagegen으로 만든 선택된 부모 표정 원본 |
| `assets/music.m4a` | 기존에 합성한 배경음악 |
| `audio/baby-fuss.wav`, `audio/baby-giggle.wav` | ElevenLabs에서 텍스트로 생성한 효과음. 실제 아이의 녹음이 아님 |
| `audio/generation.json` | 당시 프롬프트·설정·출처 메타데이터. API 키 없음 |
| `audio/mix-verification.json` | 승인본의 기존 음량·디코딩·스트림 검사 결과 |
| `screenshots/` | 영상에 사용한 제작 당시 앱 화면. 0.4 최신 화면이 아님 |
| `render_marketing.py` | Pillow/NumPy 프레임과 ffmpeg를 이용한 영상 합성 |
| `mix_marketing_audio.py` | 영상을 재인코딩하지 않고 효과음·ducking·normalization을 믹싱 |

## 환경과 실행

Python, Pillow, NumPy와 PATH에서 실행 가능한 ffmpeg/ffprobe가 필요하다. 기본 글꼴은 Windows 맑은 고딕이다. 다른 환경에서는 `JIGEUM_MARKETING_FONT`, `JIGEUM_MARKETING_FONT_BOLD`에 **사용권이 있는 한글 글꼴 경로**를 지정한다. 글꼴 파일은 저장소에 복사하지 않았다.

저장소 루트에서 실행한다.

```powershell
python tools/marketing/render_marketing.py
# 영상 그림은 그대로 두고 오디오만 재생성
python tools/marketing/mix_marketing_audio.py
```

**출력 위치는 항상 `work/marketing-rebuild`다. 승인된 outputs 파일을 자동으로 덮어쓰지 않는다.** 결과를 검토한 뒤 선택한 파일만 승인본에 반영한다.

스크립트에서 기존 work 입력 경로 의존성을 제거했고 구문 검사만 수행했다. 이번 인수인계에서는 전체 영상을 다시 렌더링하거나 청취 평가를 하지 않았다.

## 재현 범위와 출처

보존한 렌더러의 프레임 설정은 18fps이며 승인된 최종 MP4의 메타데이터는 30fps다. 실행 결과가 승인 파일과 바이트·프레임 단위로 동일하다고 가정하지 않는다. 필요하면 타임라인과 프레임 설정을 비교한다.

효과음 구간은 0.22~3.22초와 19.62~22.62초이며 영상 길이는 26.5초다. 최종 자산과 원본의 무결성은 [출처 목록](../../docs/references/source-manifest.json)을 참고한다.

## 유지할 콘셉트와 화면의 시점

응애·옹알이 → 당황한 부모 → 앱 사용 → 아이 시점 문장 → 깨달음 → 부모 반응·아기 웃음 → 서비스 이름 순서로 진행한다. 아기자기한 가족, 세 식구의 의상, 배경판 없는 큰 자막을 유지하며 MVP 문구는 없다. 현재 승인 영상에는 TTS 내레이션이 없다.

현재 0.3 앱은 저장 영상의 첫 최대 10초 소리를 기기에서 추출해 일반 소리 후보를 찾는 기능을 지원한다. **이 설명을 갱신했다고 트레일러의 기존 앱 캡처까지 새로 촬영한 것은 아니다.** 캡처는 제작 당시 화면을 보존한 것이며 0.4 전체 이용권의 구현·QA는 아직 완료되지 않았다.

출시 전에는 효과음의 자연스러움을 청취하고, 실제 0.4 화면과 과금 범위가 홍보 내용에 맞는지 다시 확인한다.

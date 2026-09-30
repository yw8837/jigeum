# 확정 트레일러 재편집 소스

최종사용파일은 [jigeum-trailer-b-family.mp4](../../outputs/Marketing/jigeum-trailer-b-family.mp4)다.여기는선택된원본자산/효과음/당시앱화면과편집스크립트를다른환경에전달하기위한폴더다.폐기된시안은포함하지않았다.

| 위치 | 내용 |
|---|---|
| `assets/parents-confused.png`, `parents-happy.png` | Imagegen으로만든선택부모표정원본 |
| `assets/music.m4a` | 기존합성배경음악 |
| `audio/baby-fuss.wav`, `baby-giggle.wav` | ElevenLabs텍스트생성효과음,실제아이녹음아님 |
| `audio/generation.json` | 당시프롬프트/설정/출처메타데이터,API키없음 |
| `audio/mix-verification.json` | 승인본의기존음량/디코딩/스트림검사 |
| `screenshots/` | 영상에쓰인당시앱화면,0.4최신화면아님 |
| `render_marketing.py` | Pillow/NumPy프레임과ffmpeg영상합성 |
| `mix_marketing_audio.py` | 영상재인코딩없이효과음·ducking·normalization믹싱 |

Python/Pillow/NumPy와PATH의ffmpeg/ffprobe필요.글꼴기본은Windows맑은고딕이며다른환경은`JIGEUM_MARKETING_FONT`, `JIGEUM_MARKETING_FONT_BOLD`로**사용권이있는한글글꼴경로**를설정한다.폰트파일은저장소에복사하지않았다.

```powershell
python tools/marketing/render_marketing.py
# 영상그림은그대로두고오디오만재생성
python tools/marketing/mix_marketing_audio.py
```

**출력은항상`work/marketing-rebuild`이다.승인된outputs파일을자동덮어쓰지않는다.**검토후선택결과만승인파일에반영한다.스크립트경로는기존work의존성을제거했고구문검사만수행했다.이번인수인계에서전체재렌더나청취평가를하지않았다.

보존렌더러의프레임설정은18fps이고승인된최종MP4메타데이터는30fps다.따라서실행출력이승인파일과바이트/프레임단위로동일하다고가정하지말고필요시타임라인/프레임설정을비교한다.효과음은0.22~3.22초/19.62~22.62초,길이26.5초.최종자산과원본무결성은[출처목록](../../docs/references/source-manifest.json)참조.

콘셉트:응애/옹알이→당황한부모→앱사용→아이문장→깨달음→부모반응/아기웃음→서비스명.아기자기한가족,세식구의상,배경판없는큰자막,MVP문구없음.내레이션TTS는현재승인영상에없다.출시전효과음의자연스러움청취와앱0.4화면/과금범위홍보일치는다시확인한다.

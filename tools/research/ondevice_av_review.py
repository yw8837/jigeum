"""Build a local-only review page. Never upload this page or its media/responses."""
import argparse
import html
import json
from pathlib import Path
from ondevice_av_probe import validate_output_path


def build(manifest_path: Path, runs: Path, output: Path) -> int:
    validate_output_path(output)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    sections = []
    count = 0
    for item in manifest:
        case = item["id"]
        clip = output.parent / f"{case}.mp4"
        blocks = []
        for mode, label in [("audio", "음성만"), ("video", "영상만"), ("av", "소리 + 영상")]:
            run = runs / f"{case}-{mode}"
            if mode != "video" and not item["has_audio"]:
                blocks.append(f"<article><h3>{label}</h3><p>원본에 음성 트랙이 없어 실행하지 않았습니다.</p></article>")
                continue
            summary = run / "summary.json"
            response = run / "response-private.txt"
            if not summary.exists():
                blocks.append(f"<article><h3>{label}</h3><p>아직 결과가 없습니다.</p></article>")
                continue
            data = json.loads(summary.read_text(encoding="utf-8"))
            raw = response.read_text(encoding="utf-8") if response.exists() else "응답 생성 실패"
            count += 1
            blocks.append(f"<article><h3>{label}</h3><p>상태: {html.escape(str(data.get('status', '실패')))} · "
                          f"추론 {html.escape(str(data.get('inference_seconds', '?')))}초</p>"
                          f"<pre>{html.escape(raw)}</pre></article>")
        sections.append(f"<section><h2>{html.escape(case)} · {item['start_ms']/1000:g}–{item['end_ms']/1000:g}초</h2>"
                        f"<p>원본: {html.escape(Path(item['source']).name)}</p>"
                        f"<video controls preload='metadata' src='{html.escape(clip.resolve().as_uri(), quote=True)}'></video>"
                        f"<div class='results'>{''.join(blocks)}</div>"
                        "<p>확인할 점: 실제 소리 종류와 행동이 맞는가? 처음과 끝의 변화가 맞는가? "
                        "없는 소리·행동을 만들었는가? 화면 자막을 실제 발성으로 착각했는가?</p></section>")
    page = """<!doctype html><html lang='ko'><meta charset='utf-8'>
<meta http-equiv='Content-Security-Policy' content="default-src 'none'; style-src 'unsafe-inline'; media-src file:; img-src file:">
<meta name='viewport' content='width=device-width, initial-scale=1'>
<title>아가톡 · 로컬 관찰 비교</title><style>
body{max-width:1200px;margin:32px auto;padding:0 20px;background:#fbf8f1;color:#27251f;font:16px/1.65 system-ui,sans-serif}
h1,h2,h3{line-height:1.3} section{margin:32px 0;padding:24px;background:white;border:1px solid #ddd8cc}
video{display:block;width:min(100%,480px);max-height:540px;margin:20px 0;background:#111}
.results{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px}
article{border-top:2px solid #786b51;padding-top:8px;min-width:0}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.6 monospace}
</style><h1>소리·영상을 어떻게 읽었는지 비교</h1>
<p>이 자료는 이 컴퓨터에만 있습니다. 영어 관찰문은 로컬 모델의 원문이며 정답·아기말이 아닙니다.
형식이 통과해도 장면을 틀리게 읽을 수 있습니다. 각 원본의 첫10초를 보고 대조해 주세요.
원본은 수정하지 않았고, 재생용 사본만 H.264로 변환했습니다. 외부 연결과 스크립트는 사용하지 않습니다.</p>
""" + "".join(sections) + "</html>"
    output.write_text(page, encoding="utf-8")
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--runs", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps({"local_result_count": build(args.manifest, args.runs, args.output)}))

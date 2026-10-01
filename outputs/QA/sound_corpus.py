"""Prepare the attributed 0.5.3 local QA corpus. Never package the media with the app.
Source and PCM hashes are pinned in the manifest. Requires ffmpeg on PATH.
"""
from pathlib import Path
import argparse
import array
import hashlib
import json
import math
import random
import subprocess
import urllib.request
from qa_config import ROOT


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory',type=Path,required=True)
    args=parser.parse_args(); target=args.directory.resolve(); target.mkdir(parents=True,exist_ok=True)
    manifest=ROOT/'docs/qa/sound-quality-0.5.3/audio-manifest.json'
    entries=json.loads(manifest.read_text(encoding='utf-8'))
    samples={}
    for entry in entries:
        if entry['split']=='derived': continue
        name=entry['id']; source=target/(name+'.mp3'); pcm=target/(name+'.f32')
        if not source.exists():
            request=urllib.request.Request(entry['url'],headers={'User-Agent':'Agattok local QA (attribution retained)'})
            with urllib.request.urlopen(request,timeout=40) as response:
                data=response.read(15*1024*1024+1)
            if len(data)>15*1024*1024: raise ValueError('Source too large')
            source.write_bytes(data)
        if hashlib.sha256(source.read_bytes()).hexdigest()!=entry['source_sha256']:
            raise ValueError('Source changed; do not silently replace baseline: '+name)
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(source),'-t','15','-vn','-ac','1','-ar','16000',
                        '-f','f32le',str(pcm)],check=True,timeout=30)
        data=array.array('f'); data.frombytes(pcm.read_bytes())
        samples[name]=[max(-1,min(1,x)) for x in data]
    cry=samples['cry'][32000:96000]; laugh=samples['laugh']; rng=random.Random(53)
    samples.update({
        'laugh_quiet':[v*.25 for v in laugh],
        'laugh_then_cry':laugh+cry, 'cry_then_laugh':cry+laugh,
        'overlap':[laugh[i%len(laugh)]*.5+cry[i]*.5 for i in range(len(cry))],
        'silence':[0.]*48000,
        'tone':[.1*math.sin(i*2*math.pi*440/16000) for i in range(48000)],
        'noise':[rng.uniform(-.1,.1) for _ in range(48000)],
        'laugh_with_rumble':[v*.6+.15*math.sin(2*math.pi*40*i/16000) for i,v in enumerate(laugh)],
        'cry_with_dc':[v*.5+.2 for v in cry],
        'rumble':[.2*math.sin(2*math.pi*40*i/16000) for i in range(48000)], 'dc':[.3]*48000})
    for entry in entries:
        name=entry['id']
        data=array.array('f',(max(-1,min(1,x)) for x in samples[name][:240000])).tobytes()
        if hashlib.sha256(data).hexdigest()!=entry['pcm_sha256']:
            raise ValueError('PCM differs; inspect decoder/version before changing baseline: '+name)
        (target/(name+'.f32')).write_bytes(data)
    (target/'manifest.json').write_bytes(manifest.read_bytes())
    print(f'Prepared {len(entries)} hash-verified local cases; no media uploaded')


if __name__=='__main__': main()

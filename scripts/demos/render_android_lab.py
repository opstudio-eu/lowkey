#!/usr/bin/env python3
"""Add editable captions and measured interaction highlights to continuous Android footage."""
import argparse
import json
import subprocess
import textwrap
from pathlib import Path


def render(folder):
    raw=folder/'raw.mp4'; events=json.loads((folder/'timeline.json').read_text())
    duration=float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=nw=1:nk=1',str(raw)],text=True))
    font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    texts=folder/'captions';texts.mkdir(exist_ok=True)
    def text(name, value, x, y, size, color='white', enable=None):
        path=texts/(name+'.txt');path.write_text(value)
        f=f"drawtext=fontfile='{font}':textfile='{path.resolve()}':fontsize={size}:fontcolor={color}:x={x}:y={y}:line_spacing=16"
        return f+(f":enable='{enable}'" if enable else '')
    filters=[f'color=c=0x0a111c:s=1920x1080:r=30:d={duration}[bg]',
             '[0:v]scale=432:960,setpts=PTS-STARTPTS[phone]',
             '[bg][phone]overlay=100:60:shortest=1[stage]']
    overlays=[
        'drawbox=x=645:y=265:w=1155:h=560:color=0x142333:t=fill',
        text('brand','LOWKEY KEYBOARD  /  0.4.5',650,90,26,'0xa9c7fa'),
        text('title','Keyboard lab +\nmodel retention',645,142,48),
        text('foot','Actual Android recording  |  Pixel API 36\nVoice uses bundled debug audio with local recognition.',645,920,23,'0x9eacba'),
    ]
    for event in events:
        start=event['startSeconds'];end=min(event['endSeconds'],duration)
        enable=f'between(t,{start:.3f},{end:.3f})'
        if event['caption']:
            overlays.append(text(event['id']+'-chapter',event['chapter'].upper(),690,308,23,'0x5cd193',enable))
            overlays.append(text(event['id'],textwrap.fill(event['caption'],43),690,373,38,enable=enable))
        for i,action in enumerate(event['actions']):
            r=action['rect']; at=action['time']
            x=100+r['x']*.4;y=60+r['y']*.4;w=r['width']*.4;h=r['height']*.4
            stable_key = 'Long-press for' in action['label'] or action['label'].startswith('Insert ')
            # Modal/menu/navigation targets vanish during dispatch. Clear their outline as
            # dispatch begins; native button ripples supply the pressed feedback. Letter-key
            # bounds stay fixed, so a held key can retain its outline throughout the gesture.
            until = at + (1.25 if stable_key else 0)
            overlays.append(f"drawbox=x={x:.1f}:y={y:.1f}:w={w:.1f}:h={h:.1f}:color=0x5cd193:t=4:enable='between(t,{at-.6:.3f},{until:.3f})'")
        if event['id'].endswith('chapter'):
            overlays.append(f"drawbox=x=0:y=0:w=1920:h=1080:color=0x0a111c:t=fill:enable='{enable}'")
            overlays.append(text(event['id']+'-full',event['chapter'],150,340,64,enable=enable))
            overlays.append(text(event['id']+'-purpose',textwrap.fill(event['caption'],60),150,470,36,'0xa9c7fa',enable))
    filter_path=folder/'filters.txt';filter_path.write_text(';\n'.join(filters)+';\n[stage]'+','.join(overlays)+'[out]')
    output=folder/'keyboard-lab-0.4.5.mp4'
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','warning','-y','-i',str(raw),'-filter_complex_script',str(filter_path),'-map','[out]','-c:v','libx264','-preset','fast','-crf','22','-pix_fmt','yuv420p','-movflags','+faststart','-threads','2',str(output)],check=True)
    print(output)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('folder',type=Path);render(parser.parse_args().folder)

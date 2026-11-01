#!/usr/bin/env python3
"""Drive the owned Android emulator, preserving real input and an editable video timeline."""
import argparse
import json
import os
import re
import signal
import subprocess
import time
from pathlib import Path

class Device:
    def __init__(self, args):
        self.cli = [args.agent_cli]
        self.flags = ['--platform','android','--serial',args.serial,'--config',args.config,'--session',args.session]
        self.adb = ['adb','-s',args.serial]
        self.output = Path(args.output); self.output.mkdir(parents=True, exist_ok=True)
        self.timeline = []
        self.zero = time.monotonic()
        self.current = None

    def run(self, *args):
        result = subprocess.run(self.cli + list(args) + self.flags, text=True, capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout

    def tree(self, interactive=False):
        flags = ['snapshot', '--json']
        if interactive: flags.append('-i')
        return json.loads(self.run(*flags))['data']['nodes']

    def contains(self, text):
        return any(text in n.get('label','') for n in self.tree())

    def draft(self):
        return next((n.get('value','') for n in self.tree() if n.get('editable')), '')

    def clear(self):
        self.tap('Clear')
        until = time.monotonic() + 5
        while time.monotonic() < until:
            if self.draft() == '': return
            time.sleep(.1)
        raise AssertionError('Test field did not clear')

    def wait(self, text, seconds=20):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            if self.contains(text): return
            time.sleep(.4)
        raise AssertionError(f'Missing visible text: {text}')

    def target(self, label):
        found = [n for n in self.tree(interactive=True) if re.fullmatch(label,n.get('label',''),re.I) and n.get('enabled',True)]
        if not found: raise AssertionError(f'No target: {label}')
        return next((n for n in found if n.get('hittable')),found[0])

    def tap(self, label, hold=None, key=False):
        n=self.target(label); r=n['rect']
        if self.current:
            self.current.setdefault('actions',[]).append({'time':time.monotonic()-self.zero,'label':label,'rect':r,'holdMs':hold})
        if hold:
            self.run('longpress',str(round(r['x']+r['width']/2)),str(round(r['y']+r['height']/2)),str(hold))
        elif key:
            # The CLI mistakes native IME refs for editor controls covered by the IME.
            self.run('click',str(round(r['x']+r['width']/2)),str(round(r['y']+r['height']/2)))
        else:
            self.run('click','@'+n['ref'])

    def back(self):
        self.run('back')

    def scroll(self, direction='down', amount='500'):
        self.run('scroll',direction,'--pixels',amount,'--settle')

    def scroll_to(self, text):
        for _ in range(6):
            if self.contains(text): return
            self.scroll('down','1000')
        raise AssertionError(f'Could not scroll to {text}')

    def shot(self, name):
        self.run('screenshot',str(self.output/(name+'.png')))
        (self.output/(name+'.json')).write_text(json.dumps(self.tree(),indent=2))

    def note(self, step, chapter, caption, hold=3):
        if self.current:
            self.current['endSeconds']=time.monotonic()-self.zero
        self.current={'id':step,'chapter':chapter,'caption':caption,'startSeconds':time.monotonic()-self.zero,'actions':[]}
        self.timeline.append(self.current)
        time.sleep(hold)

    def finish_step(self, hold=2):
        time.sleep(hold)
        self.shot(self.current['id'])
        self.current['endSeconds']=time.monotonic()-self.zero
        (self.output/'timeline.json').write_text(json.dumps(self.timeline,indent=2))


def record(d):
    # Prerequisites are prepared through the actual UI before the continuous recording.
    d.tap('Models'); d.wait('Speech models')
    proc=subprocess.Popen(d.adb+['shell','screenrecord','--bit-rate','6000000','--time-limit','180','/sdcard/keyboard-lab-raw.mp4'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    d.zero=time.monotonic()
    try:
        d.note('overview','Keyboard lab','',4); d.finish_step(0)
        d.note('lab-chapter','Test from any page','Test your keyboard without leaving the page you are on.',4); d.finish_step(0)
        d.note('open-lab','Test from any page','Tap Test keyboard on Models. A small lab opens over your page.',3)
        d.tap('Test keyboard'); d.wait('Test input'); d.wait('Switch to voice typing'); d.finish_step(3)
        d.note('type','A shared test draft','Use the real keyboard. Insert T3 Code, tap q, then hold w for 2.',3)
        d.tap('Insert T3 Code, saved term.*',key=True)
        d.tap('q\\. Long-press for 1',key=True)
        d.tap('w\\. Long-press for 2',hold=800,key=True)
        d.wait('T3 Code q2'); d.finish_step(3)
        d.note('return-models','Keep your place','Back to Models closes the lab. Your model list stays where it was.',3)
        d.tap('Back to Models'); d.wait('Speech models'); d.finish_step(2)
        d.note('dictionary','A shared test draft','Switch to Dictionary and open Test keyboard. The same draft comes with you.',3)
        d.tap('Dictionary'); d.tap('Test keyboard'); d.wait('T3 Code q2'); d.finish_step(3)
        d.note('return-dictionary','Keep your place','Close the lab to return to Dictionary.',2)
        d.tap('Back to Dictionary'); d.finish_step(2)
        d.note('retention-chapter','Choose model retention','Keep a model ready for faster dictation, or release it to save memory.',4); d.finish_step(0)
        d.note('retention','Choose model retention','In Settings, open Voice. The default is 2 minutes after recording and transcription end.',3)
        d.tap('Settings'); d.scroll('down','1200'); d.wait('Keep model loaded')
        d.tap('Keep model loaded.*'); d.finish_step(3)
        d.note('choose-five','Choose model retention','Choose 5 minutes. Recording and final transcription never expire.',3)
        d.tap('5 minutes'); d.wait('5 minutes'); d.finish_step(2)
        d.note('dictate','Test voice in place','Open Test keyboard here. This emulator uses a bundled debug audio clip with real local recognition.',4)
        d.tap('Test keyboard'); d.clear(); d.wait('Switch to voice typing')
        d.tap('Switch to voice typing',key=True)
        d.wait('Stop',40)
        # Hold enough real audio to contain the full short clip and visible live text.
        time.sleep(8)
        d.finish_step(0)
        d.note('stop','Finish dictation','Tap Stop to commit the transcript. The loaded model stays ready for reuse.',3)
        d.tap('Stop',key=True); d.wait('Ready for your next dictation.',45); d.finish_step(3)
        assert d.draft().strip(), 'Final transcript is empty'
        d.note('return-settings','Keep your place','Close the lab. Voice settings stay at the same scroll position.',3)
        d.tap('Back to Settings'); d.wait('Keep model loaded'); d.wait('loaded in memory'); d.finish_step(3)
        d.note('release','Release idle memory','Choose Release after dictation. Because the model is idle, it releases now.',3)
        d.tap('Keep model loaded.*'); d.tap('Release after dictation')
        d.wait('No model loaded in memory'); d.finish_step(4)
        d.note('closing','Ready to experiment','Test from any page. Keep your place. Choose how long your model stays loaded. Downloads stay installed.',4); d.finish_step(2)
    finally:
        # Signal the device recorder itself; terminating only the local adb can truncate MP4.
        subprocess.run(d.adb+['shell','pkill','-2','screenrecord'],check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try: proc.communicate(timeout=15)
        except subprocess.TimeoutExpired: proc.terminate(); proc.communicate(timeout=5)
        subprocess.run(d.adb+['pull','/sdcard/keyboard-lab-raw.mp4',str(d.output/'raw.mp4')],check=True)
        if d.current: d.current['endSeconds']=time.monotonic()-d.zero
        (d.output/'timeline.json').write_text(json.dumps(d.timeline,indent=2))


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--agent-cli',default=os.environ.get('AGENT_DEVICE_CLI','agent-device'))
    p.add_argument('--serial',required=True); p.add_argument('--config',required=True); p.add_argument('--session',required=True)
    p.add_argument('--output',required=True); p.add_argument('--record',action='store_true')
    args=p.parse_args(); d=Device(args)
    if args.record: record(d)
    else: print(json.dumps(d.tree(),indent=2))

if __name__=='__main__': main()

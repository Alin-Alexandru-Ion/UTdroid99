#!/usr/bin/env python3
"""Extract selected direct pthread PLT call sites from the pinned Core ELF."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

EXPECTED = 'f0ef4d6bd3b2bf13c4afb8e0ad0c3ea6be6a63182b9d64a67554136e03c3837d'
TARGETS = ('FEventUnix::Create(int)', 'FEventUnix::Trigger()',
           'FEventUnix::WaitForEventHelper(FEventUnix*, unsigned int)',
           'FCriticalSectionUnix::FCriticalSectionUnix()')
def main():
    p = argparse.ArgumentParser()
    p.add_argument('--core', type=Path, required=True)
    p.add_argument('--objdump', required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    digest = hashlib.sha256(args.core.read_bytes()).hexdigest()
    if digest != EXPECTED:
        raise SystemExit('Unexpected Core.so hash; refusing to attribute observations')
    run = subprocess.run([args.objdump, '-d', '--demangle', str(args.core)], check=True,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', errors='replace')
    header = re.compile(r'^([0-9a-f]+) <(.+)>:$')
    call = re.compile(r'\b(?:bl|b)\s+0x[0-9a-f]+ <(pthread_[^>]+)@plt>')
    functions = {}; current = None
    for line in run.stdout.splitlines():
        match = header.match(line)
        if match:
            current = match.group(2) if match.group(2) in TARGETS else None
            if current: functions[current] = {'address': '0x' + match.group(1), 'instructions': [], 'pthread_calls': []}
        elif current and re.match(r'^\s*[0-9a-f]+:', line):
            instructions = functions[current]['instructions']
            instructions.append(line.strip())
            found = call.search(line)
            if found:
                functions[current]['pthread_calls'].append({'name': found.group(1), 'context': instructions[-6:]})
    missing = [name for name in TARGETS if name not in functions]
    if missing: raise SystemExit('Expected disassembly symbols missing: ' + ', '.join(missing))
    report = {'core_sha256': digest, 'objdump': args.objdump, 'functions': functions,
              'limitations': ['Static disassembly only; indirect calls, dataflow and runtime behavior are not established.',
                              'Call contexts show preceding instructions and do not prove control-flow reachability or input values.']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    for name, f in functions.items(): print(f"{name}: {', '.join(c['name'] for c in f['pthread_calls'])}")

if __name__ == '__main__': main()

import json
from collections import defaultdict

with open('sonar_issues_full.json','r',encoding='utf-8') as f:
    issues = json.load(f)

# Category breakdown
cats = {'test': 0, 'frontend': 0, 'backend_src': 0, 'config_docker': 0, 'other': 0}
for i in issues:
    fp = i.get('component','').replace('autoassist-call-management:','')
    if '/test/' in fp or 'test_' in fp:
        cats['test'] += 1
    elif fp.startswith('frontend/'):
        cats['frontend'] += 1
    elif 'Dockerfile' in fp or fp.endswith('.yml') or fp.endswith('.html') or fp.endswith('.sql'):
        cats['config_docker'] += 1
    elif fp.startswith('services/'):
        cats['backend_src'] += 1
    else:
        cats['other'] += 1

print('=== BY CATEGORY ===')
for cat, count in sorted(cats.items(), key=lambda x: -x[1]):
    print(f'  {cat}: {count}')

# SAFE fixes categories
print()
print('=== SAFE FIXES: String constants (CRITICAL) ===')
for i in issues:
    fp = i.get('component','').replace('autoassist-call-management:','')
    msg = i.get('message','')
    sev = i.get('severity','')
    if 'Define a constant' in msg and sev == 'CRITICAL':
        line = i.get('line','?')
        print(f'  {fp} L{line} : {msg[:80]}')

print()
print('=== SAFE FIXES: Collectors.toList() -> Stream.toList() (MAJOR) ===')
for i in issues:
    msg = i.get('message','')
    if 'Stream.toList' in msg:
        fp = i.get('component','').replace('autoassist-call-management:','')
        line = i.get('line','?')
        print(f'  {fp} L{line}')

print()
print('=== Readonly props (MINOR) ===')
for i in issues:
    msg = i.get('message','')
    if 'read-only' in msg.lower() or 'readonly' in msg.lower():
        fp = i.get('component','').replace('autoassist-call-management:','')
        line = i.get('line','?')
        print(f'  {fp} L{line}')

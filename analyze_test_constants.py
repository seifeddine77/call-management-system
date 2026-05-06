import json, re
from collections import defaultdict

with open('sonar_issues_full.json','r',encoding='utf-8') as f:
    issues = json.load(f)

test_constants = defaultdict(list)
for i in issues:
    msg = i.get('message','')
    fp = i.get('component','').replace('autoassist-call-management:','')
    if 'Define a constant' in msg and '/test/' in fp:
        m = re.search(r'"(.+?)"', msg)
        literal = m.group(1) if m else '?'
        test_constants[fp].append(literal)

print(f'=== TEST FILES WITH CONSTANT ISSUES ({sum(len(v) for v in test_constants.values())} total) ===')
for fp, literals in sorted(test_constants.items(), key=lambda x: -len(x[1])):
    print(f'\n[{len(literals)}] {fp}')
    for lit in literals:
        print(f'    "{lit}"')

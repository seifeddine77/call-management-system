"""
Safe automated constant extraction for SonarCloud 'Define a constant' issues.
Only touches test files. Reads sonar_issues_full.json, modifies each file.
FIXED: Insert constants AFTER doing replacements to avoid self-reference.
"""
import json, re, os
from collections import defaultdict

BASE = r"c:\Users\ZBOOK\.gemini\antigravity\scratch\call-management-system"

with open(os.path.join(BASE, 'sonar_issues_full.json'), 'r', encoding='utf-8') as f:
    issues = json.load(f)

# Group 'Define a constant' issues by file
file_literals = defaultdict(set)
for i in issues:
    msg = i.get('message', '')
    fp = i.get('component', '').replace('autoassist-call-management:', '')
    if 'Define a constant' in msg and '/test/' in fp:
        m = re.search(r'"(.+?)"', msg)
        if m:
            file_literals[fp].add(m.group(1))

def make_const_name(literal):
    """Generate a valid Java constant name from a string literal."""
    if literal.startswith('/api/'):
        name = literal.replace('/api/', 'API_').replace('/', '_').replace('{', '').replace('}', '')
    elif literal.startswith('$.'):
        name = literal.replace('$.', 'JSON_').replace('[0].', '_FIRST_').replace('.', '_')
    elif literal.startswith('/'):
        name = literal.lstrip('/').replace('/', '_')
    else:
        name = literal
    name = re.sub(r'[^a-zA-Z0-9]', '_', name)
    name = re.sub(r'_+', '_', name).strip('_')
    if name and name[0].isdigit():
        name = 'VAL_' + name
    name = name.upper()
    if len(name) > 40:
        name = name[:40]
    return name

def process_file(rel_path, literals):
    """Process a single test file: add constants and replace literals."""
    abs_path = os.path.join(BASE, rel_path.replace('/', os.sep))
    if not os.path.exists(abs_path):
        print(f"  SKIP (not found): {abs_path}")
        return 0
    
    with open(abs_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Generate constant declarations
    const_lines = []
    replacements = {}  # literal -> const_name
    used_names = set()
    
    for literal in sorted(literals):
        const_name = make_const_name(literal)
        base_name = const_name
        counter = 2
        while const_name in used_names:
            const_name = f"{base_name}_{counter}"
            counter += 1
        
        count = content.count(f'"{literal}"')
        if count < 2:
            print(f"    SKIP '{literal}' (only {count} occurrence)")
            continue
        
        used_names.add(const_name)
        replacements[literal] = const_name
        const_lines.append(f'    private static final String {const_name} = "{literal}";')
        print(f"    {const_name} = \"{literal}\" ({count} occurrences)")
    
    if not const_lines:
        print(f"  SKIP (no valid replacements)")
        return 0
    
    # Find class declaration
    class_match = re.search(r'((?:public|class)\s+class\s+\w+[^{]*\{)', content, re.DOTALL)
    if not class_match:
        class_match = re.search(r'(class\s+\w+[^{]*\{)', content, re.DOTALL)
    if not class_match:
        print(f"  SKIP (no class declaration found)")
        return 0
    
    # STEP 1: Do all replacements FIRST (before inserting constants)
    for literal, const_name in replacements.items():
        content = content.replace(f'"{literal}"', const_name)
    
    # STEP 2: Now insert the constant declarations block
    # Re-find class declaration in modified content
    class_match = re.search(r'((?:public|class)\s+class\s+\w+[^{]*\{)', content, re.DOTALL)
    if not class_match:
        class_match = re.search(r'(class\s+\w+[^{]*\{)', content, re.DOTALL)
    if not class_match:
        print(f"  SKIP (class lost after replacement)")
        return 0
    
    insert_pos = class_match.end()
    const_block = "\n\n    // --- SonarCloud: extracted string constants ---\n"
    const_block += "\n".join(const_lines)
    const_block += "\n"
    
    content = content[:insert_pos] + const_block + content[insert_pos:]
    
    if content != original:
        with open(abs_path, 'w', encoding='utf-8') as f:
            f.write(content)
        return len(replacements)
    return 0

# Process all files
total_changes = 0
files_changed = 0
for fp, literals in sorted(file_literals.items(), key=lambda x: -len(x[1])):
    print(f"\n=== {fp} ({len(literals)} constants) ===")
    changes = process_file(fp, literals)
    total_changes += changes
    if changes > 0:
        files_changed += 1

print(f"\n{'='*60}")
print(f"TOTAL: {total_changes} constant extractions across {files_changed} files")

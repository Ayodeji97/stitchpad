"""Validate French resource coverage and positional format contracts."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1] / 'composeApp/src/commonMain/composeResources'
def read(locale):
    nodes = list(ET.parse(ROOT / locale / 'strings.xml').getroot())
    strings = [n for n in nodes if n.tag == 'string']
    result = {n.attrib['name']: ''.join(n.itertext()) for n in strings}
    assert len(strings) == len(result), f'Duplicate keys: {locale}'
    return result
def formats(text):
    return sorted(re.findall(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]', text.replace('%%', '')))
en, fr = read('values'), read('values-fr')
assert en.keys() == fr.keys(), f'Missing: {en.keys()-fr.keys()}; extra: {fr.keys()-en.keys()}'
for key in en:
    assert fr[key].strip() or not en[key].strip(), f'Empty: {key}'
    assert formats(en[key]) == formats(fr[key]), f'Format mismatch: {key}'
print(f'French coverage and formats verified: {len(en)} strings')

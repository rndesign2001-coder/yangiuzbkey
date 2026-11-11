"""Build compact word lists (one word per line, most frequent first) for app assets."""
import re, sys, os
src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

def clean(words, pat, limit):
    seen, res = set(), []
    for w in words:
        w = w.strip().lower()
        if not w or w in seen or not re.fullmatch(pat, w) or len(w) < 2 and w not in ('a','i','u','o','e','y','и','в','с','к','у','я','о','а'):
            continue
        seen.add(w); res.append(w)
        if len(res) >= limit: break
    return res

def hermit(lang):
    with open(f'{src}/{lang}.txt', encoding='utf-8') as f:
        return [l.split(' ')[0] for l in f]

specs = {
    'en': (r"[a-z']+", 20000),
    'ru': (r"[а-яё]+", 25000),
    'tr': (r"[a-zçğıöşü]+", 25000),
    'de': (r"[a-zäöüß]+", 25000),
    'fr': (r"[a-zàâæçéèêëîïôœùûüÿ']+", 20000),
    'es': (r"[a-záéíñóúü]+", 20000),
}
for lang, (pat, lim) in specs.items():
    ws = clean(hermit(lang), pat, lim)
    open(f'{out}/{lang}.txt', 'w', encoding='utf-8').write('\n'.join(ws) + '\n')
    print(lang, len(ws))

# Kazakh from multilingual list
kk = clean(open(f'{src}/w_kk.txt', encoding='utf-8').read().split(), r"[а-яёәіңғүұқөһ]+", 6000)
open(f'{out}/kk.txt', 'w', encoding='utf-8').write('\n'.join(kk) + '\n'); print('kk', len(kk))

# Uzbek: curated base + multilingual seed, normalised apostrophes
APOS = "'`ʻʼ‘’"
def norm_uz(w):
    w = w.lower()
    for a in APOS: w = w.replace(a, 'ʻ')
    w = re.sub(r"([og])ʻ", r"\1ʻ", w)
    # remaining ʻ not after o/g is the tutuq belgisi ʼ
    w = re.sub(r"(?<![og])ʻ", "ʼ", w)
    return w
base = open(os.path.join(os.path.dirname(__file__), 'uz_base.txt'), encoding='utf-8').read().split()
seed = open(f'{src}/w_uz.txt', encoding='utf-8').read().split()
uz = clean([norm_uz(w) for w in base + seed], r"[a-zʻʼ]+", 8000)
uz = [w for w in uz if w not in ('the', 'jssv')]
open(f'{out}/uz_latn.txt', 'w', encoding='utf-8').write('\n'.join(uz) + '\n'); print('uz_latn', len(uz))

def to_new(w):
    w = w.replace('oʻ', 'ō').replace('gʻ', 'ḡ').replace('sh', 'ş').replace('ch', 'ç')
    return w
uzn = []
seen = set()
for w in uz:
    n = to_new(w)
    if n not in seen: seen.add(n); uzn.append(n)
open(f'{out}/uz_new.txt', 'w', encoding='utf-8').write('\n'.join(uzn) + '\n'); print('uz_new', len(uzn))

LAT2CYR = [('yoʻ','йў'),('ye','е'),('oʻ','ў'),('gʻ','ғ'),('sh','ш'),('ch','ч'),('yo','ё'),('yu','ю'),('ya','я'),('ʼ','ъ'),
  ('a','а'),('b','б'),('d','д'),('e','е'),('f','ф'),('g','г'),('h','ҳ'),('i','и'),('j','ж'),('k','к'),('l','л'),
  ('m','м'),('n','н'),('o','о'),('p','п'),('q','қ'),('r','р'),('s','с'),('t','т'),('u','у'),('v','в'),('x','х'),('y','й'),('z','з')]
def to_cyr(w):
    r, i = '', 0
    while i < len(w):
        for a, b in LAT2CYR:
            if w.startswith(a, i):
                if a == 'e' and i == 0: b = 'э'
                r += b; i += len(a); break
        else:
            r += w[i]; i += 1
    return r
uzc = []
seen = set()
for w in uz:
    c = to_cyr(w)
    if c not in seen: seen.add(c); uzc.append(c)
open(f'{out}/uz_cyrl.txt', 'w', encoding='utf-8').write('\n'.join(uzc) + '\n'); print('uz_cyrl', len(uzc))

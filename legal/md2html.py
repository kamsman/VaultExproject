# Convertit les deux documents legaux (Markdown) en pages HTML du site public.
# Relancer apres toute modification de legal/*.md pour garder les deux en phase.
import re, html, sys

def inline(t):
    t = html.escape(t)
    t = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', t)
    t = re.sub(r'(?<!\*)\*([^*]+)\*(?!\*)', r'<em>\1</em>', t)
    t = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2" rel="noopener">\1</a>', t)
    t = re.sub(r'(?<!["=>])(https?://[^\s<)]+)', r'<a href="\1" rel="noopener">\1</a>', t)
    return t

def convert(md):
    out, lines, i = [], md.split('\n'), 0
    ul = False
    def close_ul():
        nonlocal ul
        if ul: out.append('</ul>'); ul = False
    while i < len(lines):
        l = lines[i].rstrip()
        if l.startswith('|'):                       # tableau
            close_ul()
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                rows.append([c.strip() for c in lines[i].strip('|').split('|')])
                i += 1
            rows = [r for r in rows if not all(set(c) <= set('-: ') for c in r)]
            out.append('<table>')
            for n, r in enumerate(rows):
                tag = 'th' if n == 0 else 'td'
                out.append('<tr>' + ''.join(f'<{tag}>{inline(c)}</{tag}>' for c in r) + '</tr>')
            out.append('</table>')
            continue
        if re.match(r'^#{1,4} ', l):
            close_ul()
            lvl = len(l) - len(l.lstrip('#'))
            out.append(f'<h{lvl}>{inline(l[lvl+1:])}</h{lvl}>')
        elif l.strip() == '---':
            close_ul(); out.append('<hr>')
        elif l.startswith('- '):
            if not ul: out.append('<ul class="plain">'); ul = True
            out.append(f'<li>{inline(l[2:])}</li>')
        elif l.strip() == '':
            close_ul()
        else:
            close_ul(); out.append(f'<p>{inline(l)}</p>')
        i += 1
    close_ul()
    return '\n'.join(out)

TPL = """<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} — VaultEx</title>
<meta name="description" content="{desc}">
<link rel="stylesheet" href="style.css">
</head>
<body>
<div class="wrap">
  <header>
    <nav class="nav">
      <a class="brand" href="index.html"><span class="diamond"></span> VaultEx</a>
      <span class="spacer"></span>
      <a class="link" href="conditions-generales.html">CGU</a>
      <a class="link" href="confidentialite.html">Confidentialité</a>
    </nav>
  </header>
  <div class="doc">
    <a class="back" href="index.html">← Retour à l'accueil</a>
{body}
  </div>
</div>
</body>
</html>
"""

for src, dst, title, desc in [
    ('legal/conditions-generales.md', 'docs/conditions-generales.html',
     "Conditions Générales d'Utilisation",
     "Conditions Générales d'Utilisation de l'application VaultEx."),
    ('legal/politique-confidentialite.md', 'docs/confidentialite.html',
     'Politique de Confidentialité',
     "Politique de confidentialité de VaultEx : quelles données sont traitées, comment et pourquoi."),
]:
    body = convert(open(src, encoding='utf-8').read())
    open(dst, 'w', encoding='utf-8').write(TPL.format(title=title, desc=desc, body=body))
    print('écrit', dst)

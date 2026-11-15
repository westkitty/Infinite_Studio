#!/usr/bin/env python3
"""Compile a self-contained release from the supplied source plus local additions.
Run from this project directory. Requires local tailwindcss 4 and FontAwesome SVG npm packages.
The resulting HTML itself has no runtime CDNs or dependency on build.py.
"""
import json
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parent
source = (root/'original_source.html').read_text()
css = (root/'laboratory.css').read_text()
markup=(root/'laboratory.ui.html').read_text()
js=(root/'laboratory.js').read_text()
# Gather static and dynamically referenced utility candidates from the supplied source.
# Extra candidate strings are harmless: the Tailwind compiler rejects non-utilities.
tokens=set()
for m in re.finditer(r'(?<![\w-])[-\w!@:\[\]#%./]+(?:/[-\w!@:\[\]#%./]+)?',source):
    token=m.group(0)
    if len(token)<72 and not token.startswith(('/','http')):
        tokens.add(token)
for m in re.finditer(r'class(?:Name)?\s*=\s*[\"\'`]([^\"\'`]+)',source):
    tokens.update(m.group(1).split())
# Include explicit utilities introduced by UI names if present.
tokens.update(['hidden','block','flex','grid','absolute','fixed','relative','pointer-events-none','opacity-0','opacity-100'])
node_js='''const tw=require('tailwindcss');const fs=require('fs');const path=require('path');\nconst root=path.dirname(require.resolve('tailwindcss/package.json'));\nconst src=fs.readFileSync(path.join(root,'preflight.css'),'utf8')+'\\n'+fs.readFileSync(path.join(root,'theme.css'),'utf8')+'\\n@tailwind utilities;';\n(async()=>{const result=await tw.compile(src);const out=result.build(JSON.parse(fs.readFileSync(process.argv[2],'utf8')));fs.writeFileSync(process.argv[3],out);})().catch(e=>{console.error(e);process.exit(1)});'''
(root/'_compile.cjs').write_text(node_js)
(root/'_candidates.json').write_text(json.dumps(sorted(tokens)))
subprocess.run(['node',str(root/'_compile.cjs'),str(root/'_candidates.json'),str(root/'_tw.css')],check=True,env={**__import__('os').environ,'NODE_PATH':subprocess.check_output(['npm','root','-g'],text=True).strip()})
tw=(root/'_tw.css').read_text()
# Icon paths rather than an external font resource; Font Awesome Free attribution in README.
icon_names=set(re.findall(r'fa-([a-z][a-z0-9-]+)',source))
icon_names.difference_update(['solid','regular','xs','sm','lg','xl','2x'])
# Free icon fallbacks for names found in the source but absent in Free SVG set.
icon_names.update(['draw-polygon','wand-magic-sparkles','square'])
(root/'_icons.txt').write_text(json.dumps(sorted(icon_names)))
icon_cmd='''const fs=require('fs');const solids=require('@fortawesome/free-solid-svg-icons');const regulars=require('@fortawesome/free-regular-svg-icons');const wanted=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));const icons={};for(const [name,icon] of Object.entries({...solids,...regulars})){const d=icon.icon;if(!d)continue;const ico=icon.iconName;if(wanted.includes(ico)&&!icons[ico])icons[ico]=[d[0],d[1],d[4]];}fs.writeFileSync(process.argv[3],JSON.stringify(icons));'''
(root/'_compile_icons.cjs').write_text(icon_cmd)
subprocess.run(['node',str(root/'_compile_icons.cjs'),str(root/'_icons.txt'),str(root/'_icons.json')],check=True,env={**__import__('os').environ,'NODE_PATH':subprocess.check_output(['npm','root','-g'],text=True).strip()})
defs=json.loads((root/'_icons.json').read_text())
for missing,replacement in {'lasso':'draw-polygon','sparkles':'wand-magic-sparkles','square-dashed':'square'}.items():
    if missing not in defs and replacement in defs: defs[missing]=defs[replacement]
icons=json.dumps(defs,separators=(',',':'))
icon_script='''(()=>{const defs=ICON_DEFS;
const ns='http://www.w3.org/2000/svg';
function refresh(i){const found=[...i.classList].find(k=>k.startsWith('fa-')&&!['fa-solid','fa-regular'].includes(k));if(!found)return;
const name=found.slice(3);if(i.dataset.iconName===name)return;i.dataset.iconName=name;i.textContent='';const def=defs[name];
if(!def){i.textContent='◆';return;}const svg=document.createElementNS(ns,'svg');svg.setAttribute('viewBox',`0 0 ${def[0]} ${def[1]}`);svg.setAttribute('aria-hidden','true');
const paths=Array.isArray(def[2])?def[2]:[def[2]];paths.forEach(d=>{const path=document.createElementNS(ns,'path');path.setAttribute('d',d);svg.appendChild(path)});i.append(svg);}
const scan=(node)=>{if(node.nodeType!==1)return;if(node.matches?.('i.fa-solid,i.fa-regular'))refresh(node);node.querySelectorAll?.('i.fa-solid,i.fa-regular').forEach(refresh)};
scan(document.documentElement);const observer=new MutationObserver(list=>{for(const m of list){if(m.type==='attributes'){if(m.target.matches?.('i.fa-solid,i.fa-regular'))refresh(m.target)}else for(const n of m.addedNodes)scan(n)}});observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['class']});
})();'''.replace('ICON_DEFS',icons)
# Remove external dependency references. Keep web-app content as the user's original source.
source=re.sub(r'^\s*<script src="https://cdn.tailwindcss.com"></script>\s*\n','\n',source,flags=re.M)
source=re.sub(r'^\s*<link href="https://fonts.googleapis.com[^\n]+\n','',source,flags=re.M)
source=re.sub(r'^\s*<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome[^\n]+\n','',source,flags=re.M)
source=source.replace('</head>',f'<style id="compiled-utilities">\n{tw}\n</style>\n<style id="studio-lab-styles">\n{css}\n</style>\n</head>',1)
source=source.replace('</body>',markup+'\n<script id="studio-lab-code">\n'+js+'\n</script>\n<script id="studio-inline-icons">\n'+icon_script+'\n</script>\n</body>',1)
# Retitle without provenance language.
source=source.replace('Infinite AI Studio - Tab S9 Ultra Edition','Infinite Studio · The Futures Table')
# Narrow a false claim in the legacy script comment and replace stale provider-specific loading copy.
source=source.replace('True Infinite Canvas Engine + Samsung Galaxy Tab S9 Ultra S-Pen & Multi-Touch','Bounded raster canvas (up to 8000 px/side) + Samsung S-Pen and multi-touch')
source=source.replace('Gemini 3.1 Flash Image is painting your selection onto a new layer...','One possible result is being prepared for your review...')
source=source.replace('NATURAL MEDIA BRUSH ENGINE (S-PEN PRESSURE & TILT RESPONSIVE)','NATURAL MEDIA BRUSH ENGINE (S-PEN PRESSURE RESPONSIVE)')
# The original fill tool incorrectly blocked transparent interiors of closed outlines.
source=source.replace("if (targetA === 0) {\n                showToast('Draw a closed outline first before filling');\n                return;\n            }","// Transparent regions are valid flood-fill targets.")
# Delete the prior direct-to-provider integration entirely; this avoids dead insecure code paths.
start=source.index('async function runAIGeneration() {')
end=source.index('async function fetchWithBackoff(',start)
source=source[:start]+"function runAIGeneration() { throw new Error('Studio extension not initialized'); }\n\n        "+source[end:]
# Build with a standalone inline editor; the local backend handles all credentials.
(root/'index.html').write_text(source)
print('Built index.html:',len(source),'bytes; CSS',len(tw),'bytes; SVG icons',len(icons),'bytes')
print('Icon coverage',len(json.loads(icons)),'/',len(icon_names))

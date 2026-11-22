from playwright.sync_api import sync_playwright
from pathlib import Path
import hashlib

URL='file:///mnt/data/infinite_studio_laboratory/index.html'
OUT=Path(__file__).resolve().parent.parent

with sync_playwright() as pw:
  browser=pw.chromium.launch(headless=True, executable_path='/usr/bin/chromium',args=['--no-sandbox','--disable-dev-shm-usage'])
  page=browser.new_page(viewport={'width':1450,'height':900},accept_downloads=True)
  errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.set_content((OUT/'index.html').read_text(), wait_until='domcontentloaded', timeout=30000)
  page.wait_for_timeout(600)
  print('title',page.title())
  print('body',page.locator('body').is_visible())
  print('error',errors[:4])
  print('tailwind hidden',page.evaluate("getComputedStyle(document.getElementById('panel-layers')).display"))
  print('initial layers',page.evaluate('state.layers.length'))
  page.screenshot(path=str(OUT/'studio_home.png'),full_page=True)
  page.locator('[data-tool="ink"]').click()
  canvas=page.locator('#viewport').bounding_box()
  print('viewport',canvas)
  x=canvas['x']+canvas['width']/2;y=canvas['y']+canvas['height']/2
  page.mouse.move(x-90,y)
  page.mouse.down()
  for dx,dy in [(30,0),(55,-20),(80,-50),(110,-20),(170,0)]:page.mouse.move(x-90+dx,y+dy,steps=5)
  page.mouse.up()
  print('after stroke history',page.evaluate('window.__studioLabTest.history'))
  before=page.evaluate("Object.fromEntries(state.layers.map(l=>[l.id,l.canvas.toDataURL('image/png')]))")
  page.locator('[data-tool="rect-select"]').click()
  page.mouse.move(x-140,y-90)
  page.mouse.down();page.mouse.move(x+140,y+90,steps=7);page.mouse.up()
  print('selection',page.evaluate('state.selection.type'),page.evaluate('state.selection.points.length'))
  alphas=page.evaluate("""() => { const s=window.__studioLabTest.selectedRegion(); const r=window.__studioLabTest.captureRegion(s); const a=r.mask.getContext('2d'); return [a.getImageData(0,0,1,1).data[3],a.getImageData(r.mask.width/2|0,r.mask.height/2|0,1,1).data[3]]; }""")
  assert alphas==[255,0],f'Mask selection alpha direction incorrect: {alphas}'
  print('mask outside/inside alpha',alphas)
  page.locator('#lab-futures-open').click()
  page.wait_for_timeout(300)
  print('modal visible',page.locator('#lab-modal').is_visible())
  print('branches',page.locator('.lab-branch').count())
  page.screenshot(path=str(OUT/'studio_futures.png'),full_page=True)
  src1=page.locator('#lab-preview-after').get_attribute('src')
  page.locator('.lab-branch').nth(1).click()
  src2=page.locator('#lab-preview-after').get_attribute('src')
  print('distinct previews',src1!=src2)
  page.locator('#lab-split').fill('30')
  print('slider',page.locator('#lab-preview-after').evaluate('(e)=>e.style.clipPath'))
  page.locator('#lab-accept').click()
  page.wait_for_timeout(100)
  after=page.evaluate("(before) => Object.fromEntries(state.layers.filter(l=>Object.keys(before).includes(l.id)).map(l=>[l.id,l.canvas.toDataURL('image/png')]))",before)
  assert before==after,'Existing layer pixels changed after accepting future'
  assert page.evaluate('state.layers.length')==3
  print('accept layers',page.evaluate('state.layers.length'),'original layers same',before==after)
  page.locator('#btn-undo').click()
  page.wait_for_timeout(600)
  assert page.evaluate('state.layers.length')==2
  print('after undo',page.evaluate('state.layers.length'))
  page.locator('#btn-redo').click()
  page.wait_for_timeout(600)
  assert page.evaluate('state.layers.length')==3
  print('after redo',page.evaluate('state.layers.length'))
  page.locator('#lab-project-open').click()
  with page.expect_download() as dl:
    page.locator('#lab-save-project').click()
  d=dl.value
  dest=OUT/'roundtrip_export.iastudio'
  d.save_as(str(dest))
  print('saved bytes',dest.stat().st_size)
  page.once('dialog',lambda dialog:dialog.accept())
  page.locator('#lab-new-project').click()
  # dialog handler may need be assigned before click; if blocked by native confirm, this fails.
  page.wait_for_timeout(300)
  print('after new',page.evaluate('state.layers.length'))
  page.on('dialog',lambda dialog:dialog.accept())
  page.set_input_files('#lab-project-file',str(dest))
  page.wait_for_timeout(1000)
  assert page.evaluate('state.layers.length')==3
  print('after load',page.evaluate('state.layers.length'))
  print('project status',page.locator('#lab-project-status').inner_text())
  assert not errors,errors
  print('final errors',errors)
  browser.close()

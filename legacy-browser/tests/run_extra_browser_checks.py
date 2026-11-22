from pathlib import Path
from playwright.sync_api import sync_playwright
SRC=Path(__file__).resolve().parents[1]/'index.html'
html=SRC.read_text()
with sync_playwright() as pw:
  browser=pw.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox','--disable-dev-shm-usage'])
  for width,height in [(800,1280),(390,844)]:
    p=browser.new_page(viewport={'width':width,'height':height})
    errors=[];p.on('pageerror',lambda e:errors.append(str(e)))
    p.set_content(html,wait_until='domcontentloaded')
    p.locator('#lab-futures-open').click()
    p.wait_for_timeout(150)
    assert p.locator('#lab-modal').is_visible(),(width,'modal not visible')
    assert p.locator('#lab-discard').is_visible(),(width,'discard not visible')
    print('responsive modal',width,height,'scroll width',p.evaluate('document.documentElement.scrollWidth'),'dialog height',p.locator('#lab-modal .lab-dialog').evaluate('(e)=>e.scrollHeight'),'errors',errors)
    if width==800:p.screenshot(path=str(SRC.parent/'studio_tablet_portrait.png'))
    else:p.screenshot(path=str(SRC.parent/'studio_phone_preview.png'))
    assert not errors
    p.close()
  p=browser.new_page(viewport={'width':1300,'height':800})
  p.set_content(html,wait_until='domcontentloaded')
  p.locator('#btn-shape-guide').click()
  p.locator('#btn-shape-guide').click()
  assert p.evaluate('state.shapeGuide')=='rect'
  p.mouse.move(520,310);p.mouse.down();p.mouse.move(780,500,steps=12);p.mouse.up()
  p.wait_for_timeout(50)
  def center_alpha():return p.evaluate("() => { const a=getActiveLayer(),q=worldToLocal(0,0);return a.ctx.getImageData(Math.floor(q.x),Math.floor(q.y),1,1).data[3]}")
  print('center alpha before fill',center_alpha())
  p.locator('[data-tool="fill"]').click()
  p.mouse.click(650,400)
  p.wait_for_timeout(80)
  print('center alpha after fill',center_alpha())
  assert center_alpha()>0,'Transparent outline interior was not filled'
  p.locator('#btn-undo').click()
  p.wait_for_timeout(700)
  print('center alpha after undo',center_alpha())
  assert center_alpha()==0,'Undo of fill did not restore prior transparent center'
  p.close();browser.close()

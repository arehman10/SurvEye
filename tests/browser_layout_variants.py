#!/usr/bin/env python3
"""Optional real-browser layout smoke for English themes and generated Arabic/Urdu UI.
Requires playwright and Chromium. Inputs are existing generated synthetic HTML.
"""
from pathlib import Path
import argparse,json,shutil
from playwright.sync_api import sync_playwright
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
p.add_argument('--out',type=Path,default=Path('build/layout-variants'))
p.add_argument('--chromium',default=shutil.which('chromium'))
a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
checks=[];errors=[]
with sync_playwright() as pw:
 b=pw.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 for language,path in [('english','examples/surveye_admin2_review.html'),('arabic','build/review-arabic.html'),('urdu','build/review-urdu.html')]:
  page=b.new_page(viewport={'width':1440,'height':1050},reduced_motion='reduce')
  page.route('**/*',lambda r:r.abort());page.on('pageerror',lambda e:errors.append(str(e)))
  page.set_content((a.root/path).read_text(encoding='utf-8'),wait_until='load')
  assert page.locator('html').get_attribute('dir')==('ltr' if language=='english' else 'rtl')
  checks.append(f'{language}: correct document direction')
  for theme in (['worldbank','dark','clean','forest'] if language=='english' else ['worldbank']):
   page.locator('body').evaluate('(e,theme)=>e.dataset.theme=theme',theme)
   for width in [390,768,1440]:
    page.set_viewport_size({'width':width,'height':1050});page.wait_for_timeout(100)
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),f'{language} {theme} {width} overflow'
    page.locator('#controls-toggle').click();page.wait_for_timeout(60)
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),f'{language} {theme} expanded {width} overflow'
    page.locator('#controls-toggle').click()
    checks.append(f'{language}/{theme}: {width}px, filters closed and open, no page overflow')
   page.screenshot(path=str(a.out/f'{language}-{theme}.png'))
  page.close()
 assert not errors,errors
 b.close()
result={'result':'PASS','checks':len(checks),'assertions':checks,'page_errors':errors,'method':'Real Chromium set_content; current generated HTML, production CSS and libraries; no live tile requests.'}
(a.out/'layout-results.json').write_text(json.dumps(result,indent=2)+'\n')
print(f'PASS {len(checks)} layout checks; no page errors')

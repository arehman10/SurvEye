#!/usr/bin/env python3
"""Opt-in real Chromium QA. Uses actual embedded Chart.js/Leaflet, never stubs.

Usage: python tests/browser_review.py examples/surveye_admin2_review.html --out build/browser-qa
Requires the development-only Python playwright package and a Chromium binary.
HTML is loaded with set_content; no browser policy or file-access policy is changed.
HTTP(S) requests are aborted deliberately to exercise offline boundary rendering.
"""
from __future__ import annotations
import argparse, csv, io, json, math, shutil, statistics, time
from pathlib import Path
from playwright.sync_api import sync_playwright

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('html', type=Path)
parser.add_argument('--out', type=Path, default=Path('build/browser-qa'))
parser.add_argument('--chromium', default=shutil.which('chromium') or shutil.which('google-chrome'))
parser.add_argument('--diagnostics-html', type=Path)
args = parser.parse_args()
if not args.chromium:
    raise SystemExit('Specify an installed Chromium executable with --chromium.')
args.out.mkdir(parents=True, exist_ok=True)
checks: list[str] = []
errors: list[str] = []
started = time.monotonic()
def check(value, label):
    if not value:
        raise AssertionError(label)
    checks.append(label)
    print("PASS "+label, flush=True)
def near(a, b):
    return math.isclose(a, b, rel_tol=1e-10, abs_tol=1e-8)
def stats_cells(panel):
    return panel.locator('.stats-table th').evaluate_all('(rs)=>Object.fromEntries(rs.map(r=>[r.textContent.trim(),r.nextElementSibling.textContent.trim()]))')
def open_map(page):
    page.evaluate('() => {L.Map.addInitHook(function(){window.__qaMap=this});}')
    page.locator('#spatial-map').evaluate('(e)=>e.open=true')
    # Poll through CDP; wait_for_function uses eval, forbidden by the report CSP.
    for _ in range(25):
        if page.evaluate('!!window.__qaMap && document.querySelector("#leaflet-map").dataset.boundaryReady === "true"'):
            return
        page.wait_for_timeout(80)
    raise AssertionError("Map did not initialize")
def set_filter(page, values):
    page.evaluate('(f)=>SurvEyeDashboard.setFilters(f)', values)
    page.wait_for_timeout(60)
def scroll_anchor(page, locator, offset=215):
    locator.evaluate('(e,offset)=>window.scrollTo(0,window.scrollY+e.getBoundingClientRect().top-offset)', offset)
    page.wait_for_timeout(120)

with sync_playwright() as pw:
    browser = pw.chromium.launch(executable_path=args.chromium, headless=True, args=['--no-sandbox','--disable-dev-shm-usage'])
    context = browser.new_context(viewport={'width':1440,'height':1050}, device_scale_factor=1, accept_downloads=True)
    context.route('**/*', lambda route: route.abort() if route.request.url.startswith(('http://','https://')) else route.continue_())
    page = context.new_page()
    page.set_default_timeout(10000)
    page.on('pageerror', lambda err: errors.append(str(err)))
    page.set_content(args.html.read_text(encoding='utf-8'), wait_until='load')
    page.wait_for_timeout(900)
    data = page.evaluate('DATA')
    source_root=Path(__file__).resolve().parents[1]
    actual_html=args.html.read_text(encoding='utf-8')
    check((source_root/'src/resources/dashboard.js').read_text(encoding='utf-8').strip() in actual_html, 'Preview embeds current production JavaScript byte for byte')
    check((source_root/'src/resources/dashboard.css').read_text(encoding='utf-8').strip() in actual_html, 'Preview embeds current production CSS byte for byte')
    check(len(data)==240, 'Fixture contains 240 explicitly synthetic interviews')
    check(page.locator('canvas.chart').count()==117, 'All 117 original panels and their canvas nodes retained')
    check(page.locator('details.story').count()==6, 'All six original sections retained')
    check(page.evaluate('Chart.defaults.animation.duration')==800, 'Default animation duration remains 800ms')
    check(page.evaluate('document.querySelectorAll("[id]").length===new Set([...document.querySelectorAll("[id]")].map(e=>e.id)).size'), 'Generated DOM ids are unique')
    # Independently compute the affirmative weighted share, not the modal response.
    yes = sum(r['_w'] for r in data if str(r.get('female_owner'))=='1')
    valid = sum(r['_w'] for r in data if str(r.get('female_owner')) in ['1','0'])
    h = page.evaluate('SurvEyeDashboard.highlight("female_owner")')
    check('Yes' in h['detail'] and h['value']==f'{yes/valid*100:.1f}%', 'Binary highlight stays affirmative even when No is the modal response')
    page.screenshot(path=str(args.out/'overview-light.png'))
    open_map(page)
    check(page.evaluate('CONFIG.map.boundary.length===25 && CONFIG.map.boundaryLabels.length===25'), 'Real archive embeds 25 named Sri Lankan district features')
    check(page.evaluate('Object.values(__qaMap._layers).filter(l=>l instanceof L.Polygon).length')==25, 'Leaflet renders 25 actual interactive district polygons')
    check(page.locator('.surveye-point').count()==240, 'Points mode renders 240 separate accessible SVG markers, not clusters')
    check(page.locator('#map-missing').inner_text()=='0' and page.locator('#map-outside').inner_text()=='0', 'Synthetic preview has no missing or outside-boundary points')
    page.wait_for_timeout(400)
    check(page.locator('#map-tile-status').is_visible(), 'Failed external tiles expose notice while embedded map remains visible')
    legend = page.locator('#map-legend').evaluate('(e)=>[...e.children].map(n=>[n.textContent,getComputedStyle(n.querySelector("i")).backgroundColor])')
    page.locator('#map-fit-country').click()
    country_bounds = page.evaluate('__qaMap.getBounds().toBBoxString()')
    scroll_anchor(page,page.locator('#spatial-map'))
    page.screenshot(path=str(args.out/'admin2-map-light.png'))
    page.evaluate('() => {const p=Object.values(__qaMap._layers).find(l=>l instanceof L.Polygon);p.openTooltip(p.getBounds().getCenter());}')
    check(page.locator('.boundary-tooltip').count()==1 and len(page.locator('.boundary-tooltip').inner_text())>3, 'District name tooltip renders safely as text')
    page.evaluate('() => {__qaMap.eachLayer(l=>{if(l instanceof L.Polygon)l.closeTooltip()});}')
    point = page.locator('.surveye-point').first
    check(point.get_attribute('role')=='button' and point.get_attribute('tabindex')=='0', 'Interview point has keyboard button semantics')
    point.focus(); point.press('Enter')
    check(page.locator('.leaflet-popup').is_visible(), 'Enter on interview point opens coordinate popup')
    page.evaluate('() => {__qaMap.closePopup();}')
    page.locator('#controls-toggle').click()
    page.locator('.chip[data-filter="region"][data-value="01"]').click()
    western=[r for r in data if r['region']=='01']
    check(page.evaluate('SurvEyeDashboard.rows().length')==len(western), 'Filter 01 selects Western interviews only')
    check(page.locator('#map-count').inner_text()==str(len(western)), 'Map count follows clicked filter')
    check(page.locator('.surveye-point').count()==len(western), 'Map rendered markers follow clicked filter')
    check(page.locator('#map-legend').evaluate('(e)=>[...e.children].map(n=>[n.textContent,getComputedStyle(n.querySelector("i")).backgroundColor])')==legend, 'Region colors and legend stay stable under filtering')
    set_filter(page,{'region':['1']})
    central=[r for r in data if r['region']=='1']
    check(page.evaluate('SurvEyeDashboard.rows().length')==len(central), 'Code 1 remains distinct from code 01')
    check(page.evaluate('SurvEyeDashboard.rows().every(r=>r.region==="1")'), 'Exact category identity preserved, not numeric coercion')
    panel=page.locator('.panel').filter(has=page.locator('canvas[data-variable="sales"]'))
    panel.evaluate('(e)=>e.closest("details.story").open=true')
    scroll_anchor(page,panel,350)
    panel.locator('[data-panel-view="stats"]').click()
    sales=[r['sales'] for r in central if isinstance(r.get('sales'),(int,float))]
    check(stats_cells(panel)['Valid raw n']==str(len(sales)), 'Visible Stats raw n agrees with independent filtered values')
    page.locator('#weight-toggle').uncheck()
    median=statistics.median(sales)
    check(near(page.evaluate('SurvEyeDashboard.summary("sales").number'),median), 'Unweighted median matches Python independent calculation')
    fmt=page.evaluate('(v)=>SurvEyeDashboard.metricFmt("sales",v,2)',median)
    check(stats_cells(panel)['Median']==fmt, 'Open Stats updates after weight toggle')
    sd=statistics.stdev(sales)
    check(stats_cells(panel)['Mean + 3 SD']==page.evaluate('(v)=>SurvEyeDashboard.metricFmt("sales",v,2)',statistics.mean(sales)+3*sd), 'Mean + 3 sample SD agrees with independent calculation')
    page.locator('#usd-toggle').check()
    check(near(page.evaluate('SurvEyeDashboard.summary("sales").number'),median/300), 'USD presentation divides once by explicit demo rate')
    check(stats_cells(panel)['Median']==page.evaluate('(v)=>SurvEyeDashboard.metricFmt("sales",v,2)',median/300), 'Open Stats updates after currency toggle')
    page.locator('#weight-toggle').check()
    check(panel.locator('.stats-note').count()==1, 'Weighted Stats explanation restored')
    set_filter(page,{'region':['01']})
    check(stats_cells(panel)['Valid raw n']==str(sum(isinstance(r.get('sales'),(float,int)) for r in western)), 'Already-open Stats refreshes immediately on another filter')
    panel.locator('[data-panel-view="distribution"]').click()
    set_filter(page,{'region':['1']})
    page.evaluate('() => {const c=document.querySelector("canvas[data-variable=sales]");SurvEyeDashboard.ensureBuilt(c);}')
    counts=page.evaluate('() => {const c=Chart.getChart(document.querySelector("canvas[data-variable=sales]"));return c.data.datasets[0].data}')
    check(sum(b['raw'] for b in counts)==len(sales), 'Actual Chart.js histogram raw-bin counts equal valid filtered sample')
    check(near(sum(b['y'] for b in counts),sum(r['_w'] for r in central if isinstance(r.get('sales'),(int,float)))), 'Actual histogram weighted-bin total agrees with independent calculation')
    # Browser downloads, not an anchor stub. Keep underscore survey columns and raw local currency.
    page.evaluate('() => {META._income={label:"Income",kind:"continuous"};DATA.forEach((r,i)=>r._income=1000+i);}')
    with page.expect_download() as event:
        page.locator('#download-data').click()
    download=event.value; dest=args.out/'filtered-data.csv'; download.save_as(str(dest))
    csv_rows=list(csv.DictReader(io.StringIO(dest.read_text(encoding='utf-8-sig'))))
    check(len(csv_rows)==len(central), 'Browser CSV download contains exactly filtered interviews')
    check('_income' in csv_rows[0] and '_w' not in csv_rows[0] and '_lat' not in csv_rows[0], 'CSV keeps ordinary underscore variables but removes runtime helpers')
    check(all(r['region']=='Central' for r in csv_rows), 'CSV uses correct exact-code category label')
    check(near(float(csv_rows[0]['sales']),central[0]['sales']), 'USD display does not alter raw CSV currency values')
    # Search is a view operation; it must not change the analytic sample.
    page.locator('#indicator-search').fill('sales');page.wait_for_timeout(200)
    check(page.evaluate('SurvEyeDashboard.rows().length')==len(central), 'Indicator search does not change selected interviews')
    check(page.evaluate('[...document.querySelectorAll("details.story .panel")].every(e=>e.classList.contains("hidden")!==e.dataset.search.includes("sales"))'), 'Search hides only nonmatching panels in original section grids')
    view=page.evaluate('SurvEyeDashboard.viewStateObject()')
    check(view['f']['region']==['1'] and view['q']=='sales' and view['u']==1, 'Shareable view preserves exact filter, search, and currency mode')
    page.locator('#indicator-search').fill('not-a-real-variable');page.wait_for_timeout(100)
    check(page.locator('#no-results').is_visible(), 'Empty indicator search has a visible explanation')
    page.locator('#reset').click()
    check(page.evaluate('SurvEyeDashboard.rows().length')==240 and page.locator('.panel.hidden').count()==0, 'Reset restores all observations and chart panels')
    set_filter(page,{'region':['unobserved-code']})
    check(page.locator('#map-count').inner_text()=='0' and page.locator('.surveye-point').count()==0, 'Empty subset removes stale map markers')
    check(page.evaluate('__qaMap.getBounds().toBBoxString()')==country_bounds, 'Empty map returns to country extent, not old sample extent')
    check(page.locator('.highlight[data-variable="female_owner"] .highlight-value').inner_text()=='n/a', 'Empty binary highlight reports n/a, not zero percent')
    page.locator('#reset').click()
    page.locator('#usd-toggle').uncheck()
    page.locator('#controls-toggle').click()
    # Repeated beforeprint events should not overwrite the saved disclosure state.
    before=page.evaluate('() => {const ds=[...document.querySelectorAll("details.story")];ds.forEach((e,i)=>e.open=i%2===0);document.querySelector("#spatial-map").open=false;return ds.map(e=>e.open)}')
    page.evaluate('() => {dispatchEvent(new Event("beforeprint"));dispatchEvent(new Event("beforeprint"));}')
    page.wait_for_timeout(300)
    check(page.evaluate('Object.keys(Chart.instances).length')==117, 'All 117 panels instantiate actual Chart.js charts for print')
    check(page.evaluate('[...document.querySelectorAll("canvas.chart")].every(c=>c.width>0&&c.height>0)'), 'Every chart has positive rendered canvas dimensions')
    check(page.evaluate('Object.values(Chart.instances).every(c=>Number.isFinite(c.width)&&Number.isFinite(c.height))'), 'No nonfinite Chart.js rendering dimensions')
    page.evaluate('() => {dispatchEvent(new Event("afterprint"));}')
    check(page.evaluate('[...document.querySelectorAll("details.story")].map(e=>e.open)')==before, 'Repeated print events restore previous section disclosures')
    check(not page.locator('#spatial-map').evaluate('(e)=>e.open'), 'Print restoration closes previously closed map')
    page.locator('#expand-all').click()
    first=page.locator('details.story').first
    scroll_anchor(page,first)
    page.wait_for_timeout(900)
    page.screenshot(path=str(args.out/'charts-light.png'))
    # Chart image export includes a real raster, tested by signature/dimensions below.
    female=page.locator('.panel').filter(has=page.locator('canvas[data-variable="registered"]'))
    scroll_anchor(page,female,255)
    with page.expect_download() as event:
        female.locator('[data-export-chart]').click()
    png=args.out/'exported-chart.png';event.value.save_as(str(png))
    import struct
    header=png.read_bytes()[:24]
    width,height=struct.unpack('>II',header[16:24])
    check(header[:8]==b'\x89PNG\r\n\x1a\n' and width>=560 and height>100, 'Chart PNG export is a nonempty raster with title/legend area')
    female.locator('.panel-customize').click()
    page.locator('.chart-popover .popover-text').fill('Registration status — review title')
    page.locator('.chart-popover .popover-text').press('Enter')
    check(female.locator('.panel-title').inner_text()=='Registration status — review title', 'Per-chart title customization still works')
    page.keyboard.press('Escape')
    page.locator('#theme-toggle').click()
    scroll_anchor(page,first)
    page.wait_for_timeout(900)
    page.screenshot(path=str(args.out/'charts-dark.png'))
    check(page.evaluate('document.body.dataset.theme')=='dark', 'Theme control switches to dark rendering')
    page.locator('#theme-toggle').click()
    # Actual responsive layout, not jsdom. Tables and nav may scroll inside their own containers.
    for w in [320,390,600,768,1024,1440,1920]:
        page.set_viewport_size({'width':w,'height':900});page.evaluate('window.scrollTo(0,0)');page.wait_for_timeout(140)
        check(page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'), f'No page-level horizontal overflow at {w}px')
        page.locator('#controls-toggle').click()
        page.wait_for_timeout(60)
        check(page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'), f'Expanded filters fit within page at {w}px')
        page.locator('#controls-toggle').click()
        if w==390:
            page.screenshot(path=str(args.out/'mobile-light.png'))
            scroll_anchor(page,first,175);page.wait_for_timeout(250)
            page.screenshot(path=str(args.out/'mobile-charts.png'))
    page.emulate_media(reduced_motion='reduce')
    page.set_content(args.html.read_text(encoding='utf-8'), wait_until='load')
    check(page.evaluate('Chart.defaults.animation.duration')==0, 'Reduced-motion preference disables animations')
    if args.diagnostics_html:
        page.set_content(args.diagnostics_html.read_text(encoding='utf-8'), wait_until='load');open_map(page)
        check(page.locator('#map-missing').inner_text()=='2' and page.locator('#map-outside').inner_text()=='2', 'Separate edge-case fixture exposes two missing and two outside coordinates')
        for code, missing, outside in [('01','1','0'),('1','1','0'),('03','0','1'),('04','0','1')]:
            set_filter(page,{'region':[code]})
            check(page.locator('#map-missing').inner_text()==missing and page.locator('#map-outside').inner_text()==outside, f'Missing/outside footer follows region {code}, not full-sample totals')
    check(not errors, f'No uncaught browser JavaScript exceptions ({len(errors)} recorded)')
    browser.close()
report={'result':'PASS','checks':len(checks),'duration_seconds':round(time.monotonic()-started,2),'assertions':checks,'page_errors':errors,'browser':args.chromium,'method':'Real Chromium, page.set_content, embedded production Chart.js and Leaflet; HTTP(S) blocked deliberately.','limits':['No licensed Stata runtime or Windows network drive test.','Live basemap tile service availability not tested.','No Safari/Firefox/Edge browser test.','LocalStorage persistence across real file openings not tested on the opaque in-memory origin.','No claim that every country or every real Survey Solutions instrument has been tested.']}
(args.out/'browser-results.json').write_text(json.dumps(report,indent=2)+'\n')
print(f'PASS real Chromium: {len(checks)} assertions in {report["duration_seconds"]} seconds; zero page errors.')

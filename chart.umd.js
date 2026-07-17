:root{
  --navy:#002244;
  --blue:#0071bc;
  --cyan:#00a6d2;
  --gold:#f7b32b;
  --green:#14866d;
  --coral:#cf4b32;
  --purple:#6955a1;
  --paper:#fbfaf6;
  --card:#ffffff;
  --ink:#16202e;
  --muted:#5a6473;
  --line:#e6e1d5;
  --soft:#f0f4f5;
  --shadow:0 1px 2px rgba(0,34,68,.04),0 14px 36px rgba(0,34,68,.07);
  --radius:16px;
  /* Chart colors are separate from interface colors.  The brighter World
     Bank accents are excellent data marks but some are too light for small
     interface text on white. */
  --chart-blue:#009fda;
  --chart-gold:#fdb714;
  --chart-green:#00a887;
  --chart-coral:#e5552b;
  --chart-purple:#7c5ba6;
  --chart-teal:#1c8c9b;
  --chart-navy:#002244;
  --chart-stone:#b7b09c;
  --chart-sky:#5bc0eb;
  --chart-amber:#d58e00;
  --chart-leaf:#6e9f3c;
  --chart-rose:#b85c8a;
  --chart-special:#9aa0a6;
  --chart-binary-no:#d9d3c5;
  --chart-on-accent:#16202e;
  --chart-grid:rgba(90,100,115,.13);
}

body[data-theme="clean"]{
  --navy:#1d2939;--blue:#3867d6;--cyan:#4b7bec;--gold:#f6b93b;--green:#18856f;
  --paper:#f7f7f8;--soft:#f0f2f5;
}

body[data-theme="forest"]{
  --navy:#183a33;--blue:#297d6a;--cyan:#3d9d84;--gold:#d9a928;--green:#14765f;
  --paper:#f4f7f3;--soft:#eaf2ed;
}

body[data-theme="dark"]{
  --navy:#0b1320;--blue:#58b7e8;--cyan:#65d5e7;--gold:#f4c35b;--green:#58c2a7;
  --coral:#ef806a;--purple:#a993df;--paper:#0f1723;--card:#172231;--ink:#eff5fb;
  --muted:#aebac7;--line:#2c3a4a;--soft:#1d2b3a;--shadow:0 14px 38px rgba(0,0,0,.26);
  --chart-navy:#9bd5ee;--chart-special:#b5bbc2;--chart-grid:rgba(174,186,199,.16);
}

*{box-sizing:border-box}
html{scroll-behavior:smooth;scroll-padding-top:136px}
body{
  margin:0;background:var(--paper);color:var(--ink);
  font-family:Inter,"Segoe UI",Roboto,"Noto Sans Arabic","Noto Naskh Arabic",Tahoma,Helvetica,Arial,"Noto Sans",sans-serif;
  font-size:15px;line-height:1.52;-webkit-font-smoothing:antialiased;
}
button,input,select{font:inherit}
.sr-only{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}
button:focus-visible,input:focus-visible,select:focus-visible,a:focus-visible,summary:focus-visible{
  outline:3px solid color-mix(in srgb,var(--cyan) 45%,transparent);outline-offset:2px
}
a{color:inherit}
.wrap{max-width:1380px;margin:0 auto;padding:0 24px}
.topline{height:5px;background:linear-gradient(90deg,var(--cyan) 0 38%,var(--gold) 38% 52%,var(--green) 52% 77%,var(--navy) 77%)}
.brandbar{background:var(--card);border-bottom:1px solid var(--line)}
.brandbar .wrap{min-height:44px;display:flex;align-items:center;gap:16px}
.brandbar img{display:block;max-height:31px;max-width:240px}
.brandtext{font-size:12px;font-weight:800;letter-spacing:.075em;text-transform:uppercase;color:var(--navy)}
.brandtext span{font-weight:500;color:var(--muted);letter-spacing:.02em;text-transform:none;margin-left:8px}
body[data-theme="dark"] .brandtext{color:var(--ink)}
.privacy{margin-left:auto;font-size:11px;color:var(--muted)}
.topbar{position:sticky;top:0;z-index:60;background:var(--navy);color:#fff;box-shadow:0 2px 12px rgba(0,34,68,.16)}
.topbar .wrap{display:flex;align-items:center;min-height:62px;gap:18px}
.product{font-weight:750;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:58vw}
.product small{display:block;color:#b8d7e9;font-size:10px;text-transform:uppercase;letter-spacing:.11em;margin-bottom:1px}
.top-spacer{flex:1}
.badge{font-size:10px;font-weight:800;letter-spacing:.08em;text-transform:uppercase;padding:6px 10px;border-radius:999px;background:var(--gold);color:#3b2a00;white-space:nowrap}
.badge.sim{background:#ffe2dc;color:#7d2415}
.section-nav{position:sticky;top:62px;z-index:55;background:color-mix(in srgb,var(--paper) 94%,transparent);backdrop-filter:blur(10px);border-bottom:1px solid var(--line)}
.section-nav .wrap{height:48px;display:flex;align-items:center;gap:5px;overflow-x:auto;scrollbar-width:thin}
.section-nav a{font-size:12px;font-weight:650;color:var(--muted);text-decoration:none;white-space:nowrap;padding:6px 11px;border-radius:8px}
.section-nav a:hover{background:var(--soft);color:var(--navy)}
.section-nav a.active{background:var(--navy);color:#fff}
body[data-theme="dark"] .section-nav a:hover{color:var(--ink)}
.hero{padding:52px 0 18px}
.eyebrow{font-size:11px;text-transform:uppercase;letter-spacing:.15em;font-weight:800;color:var(--blue);margin-bottom:13px}
.hero h1{font-family:Georgia,"Noto Serif",serif;font-size:clamp(34px,5vw,58px);line-height:1.06;letter-spacing:-.025em;margin:0;max-width:22ch;color:var(--navy)}
body[data-theme="dark"] .hero h1{color:var(--ink)}
.hero .subtitle{font-size:18px;color:var(--muted);max-width:72ch;margin:15px 0 0}
.notice{display:flex;gap:10px;align-items:flex-start;margin-top:20px;max-width:78ch;background:color-mix(in srgb,var(--gold) 15%,var(--card));border:1px solid color-mix(in srgb,var(--gold) 45%,var(--line));padding:11px 13px;border-radius:11px;font-size:12px;color:var(--muted)}
.notice b{color:var(--ink)}
.controls{position:sticky;top:110px;z-index:50;margin:24px 0 18px;background:var(--card);border:1px solid var(--line);border-radius:var(--radius);box-shadow:var(--shadow);padding:15px 16px}
.control-row{display:flex;align-items:center;gap:13px;flex-wrap:wrap}
.searchbox{position:relative;min-width:260px;flex:1 1 300px;max-width:440px}
.searchbox input{width:100%;border:1px solid var(--line);border-radius:10px;background:var(--paper);color:var(--ink);padding:9px 34px 9px 12px}
.searchbox .icon{position:absolute;right:11px;top:8px;color:var(--muted);font-size:17px}
.filters{display:flex;align-items:center;gap:13px;flex-wrap:wrap;flex:2 1 500px}
.filter{display:flex;align-items:center;gap:7px;flex-wrap:wrap}
.filter-label{font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:.07em;color:var(--muted)}
.chips{display:flex;gap:5px;flex-wrap:wrap}
.chip{appearance:none;border:1px solid var(--line);background:var(--card);color:var(--muted);border-radius:999px;padding:5px 10px;font-size:11px;font-weight:650;cursor:pointer;max-width:180px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.chip:hover{border-color:var(--blue);color:var(--navy)}
.chip[aria-pressed="true"]{background:var(--navy);border-color:var(--navy);color:#fff}
body[data-theme="dark"] .chip:hover{color:var(--ink)}
.reset{appearance:none;border:0;background:transparent;color:var(--coral);font-size:12px;font-weight:750;cursor:pointer;padding:7px}
.matched{font-size:12px;color:var(--muted);margin-left:auto;white-space:nowrap}.matched b{font-size:17px;color:var(--navy)}
body[data-theme="dark"] .matched b{color:var(--ink)}
.kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:13px;margin:19px 0}
.kpi,.highlight{background:var(--card);border:1px solid var(--line);border-radius:14px;box-shadow:var(--shadow);padding:16px;position:relative;overflow:hidden}
.kpi:before,.highlight:before{content:"";position:absolute;left:0;top:0;bottom:0;width:4px;background:var(--blue)}
.kpi:nth-child(2):before{background:var(--green)}.kpi:nth-child(3):before{background:var(--gold)}.kpi:nth-child(4):before{background:var(--purple)}
.kpi-value,.highlight-value{font-family:Georgia,"Noto Serif",serif;font-size:30px;line-height:1.06;font-weight:700;color:var(--navy);font-variant-numeric:tabular-nums}
body[data-theme="dark"] .kpi-value,body[data-theme="dark"] .highlight-value{color:var(--ink)}
.kpi-label,.highlight-label{font-size:12px;color:var(--muted);margin-top:7px;line-height:1.35}
.highlight-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:13px;margin:18px 0}
.highlight:before{background:var(--green)}
.highlight-value{font-size:25px}.highlight-detail{font-size:10px;color:var(--muted);margin-top:4px}
.messages{margin:25px 0}.messages h2,.map-head h2{font:700 24px/1.2 Georgia,"Noto Serif",serif;color:var(--navy);margin:0 0 13px}
body[data-theme="dark"] .messages h2,body[data-theme="dark"] .map-head h2{color:var(--ink)}
.message-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.message{border:1px solid var(--line);border-left:4px solid var(--gold);background:var(--card);border-radius:12px;padding:14px 15px}
.message h3{font-size:13px;margin:0 0 5px;color:var(--navy)}.message p{font-size:12px;color:var(--muted);margin:0;white-space:pre-wrap}
body[data-theme="dark"] .message h3{color:var(--ink)}
.flagbox{margin:18px 0;background:color-mix(in srgb,var(--gold) 10%,var(--card));border:1px solid color-mix(in srgb,var(--gold) 40%,var(--line));border-radius:12px;padding:10px 14px}
.flagbox summary{cursor:pointer;font-size:12px;font-weight:750;color:var(--ink)}.flagbox ul{margin:9px 0 2px;padding-left:19px;color:var(--muted);font-size:11px}
.map-card{margin:30px 0;background:var(--card);border:1px solid var(--line);border-radius:18px;box-shadow:var(--shadow);padding:18px}
.map-head{display:flex;align-items:flex-start;gap:20px;margin:0;list-style:none;cursor:pointer}.map-head::-webkit-details-marker{display:none}.map-card[open] .map-head{margin-bottom:12px}.map-head h2{margin:0}.map-meta{font-size:11px;color:var(--muted);margin-top:4px}.map-count{margin-left:auto;text-align:right}.map-count b{display:block;color:var(--navy);font:700 26px/1 Georgia,"Noto Serif",serif}.map-count span{font-size:10px;color:var(--muted)}
.map-action{align-self:center;display:inline-flex;align-items:center;gap:6px;color:var(--blue);font-size:10px;font-weight:750;white-space:nowrap}.map-hide{display:none}.map-chevron{font-size:18px;line-height:1;transition:transform .18s}.map-card[open] .map-show{display:none}.map-card[open] .map-hide{display:inline}.map-card[open] .map-chevron{transform:rotate(180deg)}
body[data-theme="dark"] .map-count b{color:var(--ink)}
.map-stage{position:relative;width:100%;height:clamp(360px,48vw,620px);border-radius:14px;overflow:hidden;background:linear-gradient(180deg,color-mix(in srgb,var(--cyan) 8%,var(--paper)),var(--paper));border:1px solid var(--line)}
.leaflet-map{position:absolute;inset:0;width:100%;height:100%;z-index:1;background:var(--soft)}
.map-boundary-template{display:none}
.map-loading{position:absolute;z-index:2;inset:0;display:flex;align-items:center;justify-content:center;background:var(--soft);color:var(--muted);font-size:12px;font-weight:700;pointer-events:none}.map-loading.is-hidden{display:none}
.leaflet-container{font-family:Inter,"Segoe UI",Roboto,"Noto Sans Arabic","Noto Naskh Arabic",Tahoma,Helvetica,Arial,"Noto Sans",sans-serif;color:var(--ink)}
.leaflet-interactive[role="button"]:focus{outline:none;stroke:var(--gold)!important;stroke-width:3px!important;filter:drop-shadow(0 0 2px #fff)}
.leaflet-control-layers,.leaflet-bar{border:1px solid var(--line)!important;border-radius:9px!important;box-shadow:0 5px 18px rgba(0,34,68,.16)!important}.leaflet-control-layers-expanded{padding:8px 10px;font-size:11px}.leaflet-control-layers-toggle{background-image:none!important;display:grid!important;place-items:center}.leaflet-control-layers-toggle:after{content:"▧";font-size:18px;font-weight:800;color:#24445f}.leaflet-control-attribution{font-size:9px!important}.leaflet-tooltip{font-size:10px;font-weight:700;border-radius:7px}.leaflet-popup-content{font-size:11px;line-height:1.45;margin:11px 13px}.map-popup-title{font-weight:800;color:#002244;margin-bottom:4px}.map-popup-row{color:#4b5f72}.map-popup-note{margin-top:5px;color:#607182;font-size:9px}
.map-land{fill:color-mix(in srgb,var(--cyan) 12%,var(--card));stroke:color-mix(in srgb,var(--navy) 42%,var(--line));stroke-width:1.15;vector-effect:non-scaling-stroke;fill-rule:evenodd}
.map-admin{fill:color-mix(in srgb,var(--cyan) 9%,var(--card));stroke:color-mix(in srgb,var(--navy) 25%,var(--line));stroke-width:.65;vector-effect:non-scaling-stroke;fill-rule:evenodd}
.map-foot{display:flex;justify-content:space-between;gap:15px;flex-wrap:wrap;margin-top:9px;font-size:10px;color:var(--muted)}
.map-legend{display:flex;flex-wrap:wrap;gap:7px 14px;margin-top:10px;font-size:11px;color:var(--ink)}.map-legend span{display:inline-flex;align-items:center;gap:6px}.map-legend i{width:10px;height:10px;border-radius:50%;display:inline-block;box-shadow:0 0 0 1px rgba(255,255,255,.9),0 0 0 2px rgba(22,32,46,.34)}
.surveye-point{filter:drop-shadow(0 0 1px rgba(0,34,68,.9))}
.story{margin:26px 0 0;scroll-margin-top:174px;border-top:2px solid var(--navy);padding-top:0}
.story>summary{list-style:none;cursor:pointer;display:flex;align-items:flex-start;gap:12px;padding:11px 2px 10px}
.story>summary::-webkit-details-marker{display:none}
.sec-number{font:700 18px/1 Georgia,"Noto Serif",serif;color:var(--blue);min-width:29px}
.sec-title{font:700 clamp(20px,2.4vw,27px)/1.14 Georgia,"Noto Serif",serif;color:var(--navy);max-width:38ch}
body[data-theme="dark"] .sec-title{color:var(--ink)}
.sec-meta{display:block;font-size:10.5px;color:var(--muted);margin:4px 0 0}.chevron{margin-left:auto;color:var(--muted);font-size:20px;transition:transform .18s}.story[open] .chevron{transform:rotate(180deg)}
.grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px;padding:4px 0 8px;align-items:stretch}
.panel{grid-column:span var(--panel-span,2);height:100%;background:var(--card);border:1px solid var(--line);border-radius:13px;box-shadow:0 1px 2px rgba(0,34,68,.04),0 8px 22px rgba(0,34,68,.055);padding:13px 14px 12px;min-width:0;display:flex;flex-direction:column;overflow:hidden;content-visibility:auto;contain-intrinsic-size:auto 250px}
.panel.hidden{display:none}.panel-head{display:flex;gap:9px;align-items:flex-start}.panel-title{font-size:13.5px;font-weight:750;color:var(--navy);margin:0;line-height:1.27;min-height:34px;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden}.varbadge{margin-left:auto;background:var(--soft);color:var(--muted);font:600 9.5px/1.2 ui-monospace,SFMono-Regular,Consolas,monospace;border-radius:6px;padding:4px 6px;white-space:nowrap;max-width:88px;overflow:hidden;text-overflow:ellipsis;flex:0 0 auto}
body[data-theme="dark"] .panel-title{color:var(--ink)}
.panel-meta{display:flex;align-items:center;gap:0;min-height:20px;margin:2px 0 3px;white-space:nowrap;overflow:hidden}.panel-sub{font-size:10px;color:var(--muted);overflow:hidden;text-overflow:ellipsis}.panel-stat{font-size:10px;color:var(--green);font-weight:700;overflow:hidden;text-overflow:ellipsis}.panel-stat:not(:empty):before{content:"·";color:var(--line);margin:0 6px}.panel-stat:empty{display:none}
.panel-tabs{margin-left:auto;display:inline-flex;gap:1px;padding:2px;border:1px solid var(--line);border-radius:7px;background:var(--soft);flex:0 0 auto}.panel-tabs button{appearance:none;border:0;background:transparent;color:var(--muted);border-radius:5px;padding:2px 6px;font-size:8.5px;font-weight:750;line-height:1.2;cursor:pointer}.panel-tabs button[aria-selected="true"]{background:var(--card);color:var(--navy);box-shadow:0 1px 3px rgba(0,34,68,.12)}
body[data-theme="dark"] .panel-tabs button[aria-selected="true"]{background:color-mix(in srgb,var(--blue) 18%,var(--card));color:var(--ink)}
.chart-summary{position:relative;min-height:0}.panel--yesno .chart-summary,.panel--completion .chart-summary{margin:auto 0 3px}.summary-lead{display:flex;align-items:baseline;gap:6px;color:var(--navy);min-width:0}.summary-lead strong{font:700 25px/1 Georgia,"Noto Serif",serif;font-variant-numeric:tabular-nums}.summary-lead span{font-size:11px;font-weight:750;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.summary-parts{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:3px;color:var(--muted);font-size:9.5px}.summary-part{display:inline-flex;align-items:center;gap:4px;min-width:0}.summary-part i{width:7px;height:7px;border-radius:2px;flex:0 0 auto;box-shadow:0 0 0 1px rgba(22,32,46,.22)}.summary-part b{color:var(--ink);font-variant-numeric:tabular-nums}.summary-accessible{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}
body[data-theme="dark"] .summary-lead{color:var(--ink)}
.chartwrap{height:158px;position:relative;min-width:0}.chartwrap--bar,.chartwrap--multibar{height:var(--chart-height,158px);flex:1 1 auto;max-height:236px}.chartwrap--yesno,.chartwrap--completion{height:42px;flex:0 0 42px}.chartwrap--hist,.chartwrap--date{height:178px}.chartwrap--donut{height:185px;flex:1 1 auto;max-height:185px}.panel canvas{max-width:100%}
.panel-view-stack{height:178px;position:relative;min-width:0}.panel-view{position:absolute;inset:0;visibility:hidden;pointer-events:none}.panel-view.is-active{visibility:visible;pointer-events:auto}.panel-view--stats{overflow:auto;border-top:1px solid var(--line);padding-top:5px}.numeric-stats{height:100%;font-variant-numeric:tabular-nums}.stats-table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:9.5px}.stats-table th,.stats-table td{padding:4px 4px;border-bottom:1px solid color-mix(in srgb,var(--line) 70%,transparent);vertical-align:middle}.stats-table th{text-align:left;color:var(--muted);font-weight:650;width:27%}.stats-table td{text-align:right;color:var(--ink);font-weight:750;width:23%}.stats-note{font-size:8.5px;color:var(--muted);line-height:1.35;margin:5px 4px 0}.stats-empty{height:100%;display:flex;align-items:center;justify-content:center;color:var(--muted);font-size:11px;text-align:center;padding:14px}
.empty{position:absolute;inset:0;display:none;align-items:center;justify-content:center;flex-direction:column;text-align:center;color:var(--muted);font-size:12px;gap:5px}.empty.show{display:flex}.empty strong{font-size:22px;color:var(--line)}
.no-results{display:none;background:var(--card);border:1px dashed var(--line);border-radius:13px;padding:20px;text-align:center;color:var(--muted);margin:18px 0}.no-results.show{display:block}
.footer{margin:55px 0 35px;border-top:1px solid var(--line);padding-top:19px;display:grid;grid-template-columns:2fr 1fr;gap:20px;font-size:10.5px;color:var(--muted)}
.footer b{color:var(--ink)}.footer-right{text-align:right}

@media(max-width:900px){
  .kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.highlight-grid,.message-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
  .controls{position:relative;top:auto}.section-nav{top:62px}
}

@media(max-width:1120px){.panel{--panel-span:3}}

@media(max-width:680px){
  .panel{--panel-span:6;height:auto;padding:13px}.panel-title{-webkit-line-clamp:3;min-height:0}.chartwrap--hist,.chartwrap--date{height:168px}.panel-view-stack{height:168px}.chartwrap--donut{height:175px}
}

@media(max-width:600px){
  .wrap{padding:0 16px}.privacy,.product small{display:none}.product{max-width:62vw;font-size:12px}.badge{font-size:8px;padding:5px 7px}
  .hero{padding-top:34px}.hero h1{font-size:34px}.hero .subtitle{font-size:15px}.kpis{gap:9px}.kpi{padding:13px}.kpi-value{font-size:25px}
  .highlight-grid,.message-grid{grid-template-columns:1fr}.searchbox{min-width:100%;max-width:none}.matched{margin-left:0}.filters{flex-basis:100%}
  .map-card{padding:12px}.map-head{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:7px 12px}.map-count{grid-column:1;text-align:left;margin:0}.map-action{grid-column:2;grid-row:1/3;align-self:center}.map-stage{min-height:360px;aspect-ratio:auto}.footer{grid-template-columns:1fr}.footer-right{text-align:left}
}

body[data-density="comfortable"] .wrap{max-width:1240px;padding-left:28px;padding-right:28px}
body[data-density="comfortable"] .grid{gap:16px}
body[data-density="comfortable"] .panel{--panel-span:3}
body[data-density="comfortable"] .panel{padding:17px;border-radius:15px;contain-intrinsic-size:auto 340px}
body[data-density="comfortable"] .panel-title{font-size:14px;line-height:1.32}
body[data-density="comfortable"] .panel-meta{margin-bottom:7px}
body[data-density="comfortable"] .chartwrap{height:245px}
body[data-density="comfortable"] .chartwrap--bar,body[data-density="comfortable"] .chartwrap--multibar{height:var(--chart-height,255px);max-height:620px}
body[data-density="comfortable"] .chartwrap--hist,body[data-density="comfortable"] .chartwrap--date{height:285px}
body[data-density="comfortable"] .panel-view-stack{height:285px}
body[data-density="comfortable"] .chartwrap--donut{height:255px;max-height:255px}
body[data-density="comfortable"] .chartwrap--yesno,body[data-density="comfortable"] .chartwrap--completion{height:72px;flex-basis:72px}
@media(max-width:680px){body[data-density="comfortable"] .wrap{padding-left:16px;padding-right:16px}body[data-density="comfortable"] .panel{--panel-span:6;height:auto}body[data-density="comfortable"] .chartwrap--hist,body[data-density="comfortable"] .chartwrap--date{height:225px}body[data-density="comfortable"] .panel-view-stack{height:225px}}

@media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}.chevron,.map-chevron{transition:none!important}}

/* Arabic-script typography follows the interface language, independently of
   layout direction. This matters when an author explicitly requests LTR. */
body[data-ui-language="arabic"],body[data-ui-language="urdu"]{
  font-family:"Noto Sans Arabic","Noto Naskh Arabic","DejaVu Sans",Tahoma,Arial,sans-serif
}
body[data-ui-language="arabic"] .hero h1,body[data-ui-language="urdu"] .hero h1,
body[data-ui-language="arabic"] .messages h2,body[data-ui-language="urdu"] .messages h2,
body[data-ui-language="arabic"] .map-head h2,body[data-ui-language="urdu"] .map-head h2,
body[data-ui-language="arabic"] .sec-title,body[data-ui-language="urdu"] .sec-title,
body[data-ui-language="arabic"] .kpi-value,body[data-ui-language="urdu"] .kpi-value,
body[data-ui-language="arabic"] .highlight-value,body[data-ui-language="urdu"] .highlight-value{
  font-family:"Noto Naskh Arabic","Noto Sans Arabic","DejaVu Sans",Tahoma,Arial,sans-serif;
  letter-spacing:0
}
body[data-ui-language="arabic"] .brandtext,body[data-ui-language="urdu"] .brandtext,
body[data-ui-language="arabic"] .brandtext span,body[data-ui-language="urdu"] .brandtext span,
body[data-ui-language="arabic"] .product small,body[data-ui-language="urdu"] .product small,
body[data-ui-language="arabic"] .badge,body[data-ui-language="urdu"] .badge,
body[data-ui-language="arabic"] .eyebrow,body[data-ui-language="urdu"] .eyebrow,
body[data-ui-language="arabic"] .filter-label,body[data-ui-language="urdu"] .filter-label{
  letter-spacing:0;text-transform:none
}

/* Bidirectional layout. Leaflet's map pane stays LTR so its coordinate math and
   control anchoring remain stable; its textual controls are switched back to RTL. */
html[dir="rtl"] body{text-align:start}
html[dir="rtl"] .topline{background:linear-gradient(270deg,var(--cyan) 0 38%,var(--gold) 38% 52%,var(--green) 52% 77%,var(--navy) 77%)}
html[dir="rtl"] .brandtext span{margin-left:0;margin-right:8px}
html[dir="rtl"] .privacy{margin-left:0;margin-right:auto}
html[dir="rtl"] .searchbox input{padding:9px 12px 9px 34px}
html[dir="rtl"] .searchbox .icon{right:auto;left:11px}
html[dir="rtl"] .matched{margin-left:0;margin-right:auto}
html[dir="rtl"] .kpi:before,html[dir="rtl"] .highlight:before{left:auto;right:0}
html[dir="rtl"] .message{border-left:1px solid var(--line);border-right:4px solid var(--gold)}
html[dir="rtl"] .flagbox ul{padding-left:0;padding-right:19px}
html[dir="rtl"] .map-count{margin-left:0;margin-right:auto;text-align:left}
html[dir="rtl"] .chevron{margin-left:0;margin-right:auto}
html[dir="rtl"] .varbadge{margin-left:0;margin-right:auto;direction:ltr;unicode-bidi:isolate}
html[dir="rtl"] .panel-tabs{margin-left:0;margin-right:auto}
html[dir="rtl"] .stats-table th{text-align:right}
html[dir="rtl"] .stats-table td{text-align:left;direction:ltr;unicode-bidi:isolate}
html[dir="rtl"] .footer-right{text-align:left}
html[dir="rtl"] .product,html[dir="rtl"] .hero h1,html[dir="rtl"] .subtitle,
html[dir="rtl"] .sec-title,html[dir="rtl"] .panel-title,html[dir="rtl"] .panel-sub,
html[dir="rtl"] .filter-label,html[dir="rtl"] .chip,html[dir="rtl"] .message,
html[dir="rtl"] .map-meta{unicode-bidi:plaintext}
html[dir="rtl"] .leaflet-map{direction:ltr}
html[dir="rtl"] .leaflet-control-layers,html[dir="rtl"] .leaflet-popup-content,
html[dir="rtl"] .leaflet-tooltip{direction:rtl;text-align:right}
html[dir="rtl"] .leaflet-control-attribution{direction:ltr!important;text-align:left!important;unicode-bidi:isolate}
html[dir="rtl"] .map-popup-value,html[dir="rtl"] .map-tooltip-value,
html[dir="rtl"] .map-legend b{unicode-bidi:plaintext}

@media(max-width:600px){
  html[dir="rtl"] .matched{margin-right:0}
  html[dir="rtl"] .map-count{text-align:right}
  html[dir="rtl"] .footer-right{text-align:right}
}

@media print{
  :root,body[data-theme]{--paper:#fff;--card:#fff;--ink:#000;--muted:#444;--line:#bbb;--shadow:none;--chart-navy:#002244;--chart-special:#9aa0a6;--chart-grid:rgba(68,68,68,.15)}
  .topbar,.section-nav,.controls,.topline,.brandbar .privacy{display:none!important}
  .wrap{max-width:none;padding:0 14mm}.hero{padding-top:10mm}.hero h1{font-size:28pt}.notice{break-inside:avoid}
  .kpis,.highlight-grid,.message-grid{break-inside:avoid}.story{break-before:page}.story>summary .chevron{display:none}.story:not([open])>.grid{display:grid!important}
  .grid{grid-template-columns:repeat(6,minmax(0,1fr))}.panel{break-inside:avoid;box-shadow:none;content-visibility:visible}.chartwrap,.chartwrap--bar,.chartwrap--multibar,.chartwrap--hist,.chartwrap--date,.chartwrap--donut{height:190px!important}.chartwrap--yesno,.chartwrap--completion{height:60px!important;flex-basis:60px}.map-card{break-inside:avoid;box-shadow:none}
  .panel-tabs{display:none!important}.panel-view-stack{height:190px!important}.panel-view[data-panel-view-pane="distribution"]{visibility:visible!important}.panel-view[data-panel-view-pane="stats"]{visibility:hidden!important}
  .footer{break-before:auto}.chip,.reset,.searchbox{display:none!important}
}

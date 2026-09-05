(function(){
  "use strict";
  var SPEC=JSON.parse(document.getElementById("surveye-spec").textContent);
  var Q=SPEC.questions||[],DATAVARS=SPEC.datavars||{},HASDATA=Object.keys(DATAVARS).length>0;
  var byVar={};Q.forEach(function(q){byVar[q.v]=q;});
  var THEMES=["worldbank","editorial","clean","forest","dark"];
  var LANGS=["auto","english","arabic","urdu"];
  var WTYPES=["pweight","aweight","fweight","iweight"];

  function slug(s){return (""+s).toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-+|-+$/g,"")||"dashboard";}
  var state={sel:[],kind:{},groups:[],cmp:{members:[],by:"",levels:[],title:""},
    filters:[],weightVar:"",weightType:"pweight",usdVars:[],usdRate:"",currency:"",
    title:SPEC.title||"",subtitle:"",byline:"",saving:slug(SPEC.title)+".html",
    lang:"auto",theme:"worldbank",tab:"stata"};
  var STORE="surveye:cfg:"+(SPEC.questionnairePath||SPEC.title);
  try{var saved=JSON.parse(window.localStorage.getItem(STORE)||"null");
    if(saved&&saved.sel)Object.keys(state).forEach(function(k){if(k in saved)state[k]=saved[k];});}catch(e){}

  function persist(){try{window.localStorage.setItem(STORE,JSON.stringify(state));}catch(e){}}
  function el(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;
    if(text!==undefined)n.textContent=text;return n;}
  function selected(){return Q.filter(function(q){return state.sel.indexOf(q.v)>=0;});}
  function isSel(v){return state.sel.indexOf(v)>=0;}
  function chartableType(q){return !q.repeat&&["single","multi","numeric","date"].indexOf(q.type)>=0;}
  function optSig(q){return JSON.stringify((q.opts||[]).map(function(o){return [o[0],(o[1]||"").toLowerCase()];}));}
  function grouped(v){return state.groups.some(function(g){return g.members.indexOf(v)>=0;});}
  function toggleIn(list,v){var i=list.indexOf(v);if(i>=0)list.splice(i,1);else list.push(v);}

  // ---------------------------------------------------------------- layout
  var app=document.getElementById("app");
  var head=el("header","cfg-head");
  head.appendChild(el("div","cfg-brand","SurvEye \u00b7 Configurator"));
  head.appendChild(el("div","cfg-title",SPEC.title||"Questionnaire"));
  var meta=el("div","cfg-meta");
  meta.appendChild(el("span","cfg-badge",SPEC.source||"questionnaire"));
  meta.appendChild(el("span","",SPEC.sections.length+" section"+(SPEC.sections.length===1?"":"s")+
    " \u00b7 "+Q.length+" items \u00b7 "+Q.filter(chartableType).length+" chartable"));
  if(HASDATA)meta.appendChild(el("span","cfg-badge","data in memory"));
  meta.appendChild(el("span","","engine "+SPEC.engineVersion));
  head.appendChild(meta);
  app.appendChild(head);

  var main=el("div","cfg-main");
  var left=el("div","card vars-card");
  var rail=el("aside","rail");
  main.appendChild(left);main.appendChild(rail);
  app.appendChild(main);

  // ------------------------------------------------------- variable browser
  var toolbar=el("div","vars-toolbar");
  var search=el("input","vars-search");search.type="search";
  search.placeholder="Search variables or question text\u2026";
  var btnAll=el("button","ghost","Select all chartable");
  var btnClear=el("button","ghost","Clear selection");
  toolbar.appendChild(search);toolbar.appendChild(btnAll);toolbar.appendChild(btnClear);
  left.appendChild(toolbar);

  var sectionsHost=el("div");left.appendChild(sectionsHost);
  var rowIndex={};

  function kindOptions(q){
    if(q.type==="single")return ["auto","bars","donut"];
    if(q.type==="numeric")return ["auto","hist","discrete","continuous"];
    if(q.type==="multi")return ["auto","bars"];
    return null;
  }
  function buildRows(){
    sectionsHost.textContent="";rowIndex={};
    var bySec={};
    Q.forEach(function(q){(bySec[q.section]=bySec[q.section]||[]).push(q);});
    SPEC.sections.forEach(function(s){
      var items=bySec[s.n]||[];if(!items.length)return;
      var sec=el("section","sec open");
      var headRow=el("div","sec-head");
      var master=el("input");master.type="checkbox";
      master.addEventListener("click",function(ev){ev.stopPropagation();
        var eligible=items.filter(chartableType).map(function(q){return q.v;});
        var allOn=eligible.every(isSel);
        eligible.forEach(function(v){var i=state.sel.indexOf(v);
          if(allOn&&i>=0)state.sel.splice(i,1);
          if(!allOn&&i<0)state.sel.push(v);});
        refresh();});
      headRow.appendChild(master);
      headRow.appendChild(el("h3","",s.title));
      headRow.appendChild(el("span","sec-count",items.length+" items"));
      headRow.appendChild(el("span","sec-toggle","\u25b8"));
      headRow.addEventListener("click",function(){sec.classList.toggle("open");});
      sec.appendChild(headRow);
      var body=el("div","sec-body");
      items.forEach(function(q){body.appendChild(row(q));});
      sec.appendChild(body);
      sec.dataset.master="1";sec._master=master;sec._items=items;
      sectionsHost.appendChild(sec);
    });
  }
  function row(q){
    var r=el("div","qrow"+(q.repeat?" repeat":""));
    var cb=el("input");cb.type="checkbox";cb.checked=isSel(q.v);
    cb.disabled=!!q.repeat;
    cb.addEventListener("change",function(){toggleIn(state.sel,q.v);refresh();});
    r.appendChild(cb);
    var mainCol=el("div","qmain");
    var top=el("div","qtop");
    top.appendChild(el("span","qvar",q.v));
    top.appendChild(el("span","qtype "+q.type,q.type));
    if(q.repeat){var f=el("span","qflag","repeat");f.title="Repeat-group field: exported to companion files, not chartable here.";top.appendChild(f);}
    if(HASDATA&&DATAVARS[q.v]){var d=el("span","qdot");d.title="Present in the data in memory";top.appendChild(d);}
    mainCol.appendChild(top);
    mainCol.appendChild(el("div","qlabel",q.label));
    if(q.sub)mainCol.appendChild(el("div","qsub",q.sub+" \u00b7 "+(q.rawType||"")));
    var kinds=kindOptions(q);
    if(kinds){
      var kwrap=el("div","qkind");
      var ks=el("select");
      kinds.forEach(function(k){var o=el("option","",k==="auto"?"chart: auto":"chart: "+k);o.value=k;ks.appendChild(o);});
      ks.value=state.kind[q.v]||"auto";
      ks.addEventListener("change",function(){
        if(ks.value==="auto")delete state.kind[q.v];else state.kind[q.v]=ks.value;
        refresh();});
      kwrap.appendChild(ks);kwrap.style.display=isSel(q.v)?"":"none";
      mainCol.appendChild(kwrap);
      r._kind=kwrap;
    }
    r.appendChild(mainCol);
    rowIndex[q.v]=r;r._cb=cb;
    return r;
  }
  function syncRows(){
    var needle=search.value.trim().toLowerCase();
    Q.forEach(function(q){var r=rowIndex[q.v];if(!r)return;
      r._cb.checked=isSel(q.v);
      if(r._kind)r._kind.style.display=isSel(q.v)?"":"none";
      var hit=!needle||q.v.toLowerCase().indexOf(needle)>=0||q.label.toLowerCase().indexOf(needle)>=0;
      r.style.display=hit?"":"none";});
    Array.prototype.forEach.call(sectionsHost.children,function(sec){
      var eligible=sec._items.filter(chartableType);
      var on=eligible.filter(function(q){return isSel(q.v);}).length;
      sec._master.checked=eligible.length>0&&on===eligible.length;
      sec._master.indeterminate=on>0&&on<eligible.length;});
  }
  search.addEventListener("input",syncRows);
  btnAll.addEventListener("click",function(){Q.filter(chartableType).forEach(function(q){if(!isSel(q.v))state.sel.push(q.v);});refresh();});
  btnClear.addEventListener("click",function(){state.sel=[];state.kind={};refresh();});

  // ---------------------------------------------------------------- rail
  function panel(title,hint){var p=el("div","card panel");p.appendChild(el("h4","",title));
    if(hint)p.appendChild(el("p","hint",hint));return p;}
  function chipRow(items,active,onToggle,numbered){
    var wrap=el("div","chips");
    items.forEach(function(it){
      var on=active.indexOf(it.v)>=0;
      var c=el("button","chip"+(on?" on":"")+(numbered?" num":""),it.label);
      c.title=it.title||it.v;
      if(numbered&&on){var o=el("span","ord",""+(active.indexOf(it.v)+1));c.appendChild(o);}
      c.addEventListener("click",function(){onToggle(it.v);});
      wrap.appendChild(c);});
    return wrap;
  }
  function shortLabel(q){return q.label.length>26?q.label.slice(0,25)+"\u2026":q.label;}

  function buildRail(){
    rail.textContent="";

    // Output essentials -------------------------------------------------
    var pOut=panel("Dashboard","Titles, output file, look.");
    [["Title","title"],["Subtitle","subtitle"],["Byline (Label|Name|Role|Email)","byline"],["Save as","saving"]]
      .forEach(function(pair){
        var f=el("div","field");f.appendChild(el("label","",pair[0]));
        var input=el("input");input.type="text";input.value=state[pair[1]];
        input.addEventListener("input",function(){state[pair[1]]=input.value;refreshCommandOnly();});
        f.appendChild(input);pOut.appendChild(f);});
    var fTheme=el("div","field");fTheme.appendChild(el("label","","Theme"));
    var sTheme=el("select");THEMES.forEach(function(t){var o=el("option","",t);o.value=t;sTheme.appendChild(o);});
    sTheme.value=state.theme;sTheme.addEventListener("change",function(){state.theme=sTheme.value;refreshCommandOnly();});
    fTheme.appendChild(sTheme);pOut.appendChild(fTheme);
    var fLang=el("div","field");fLang.appendChild(el("label","","Interface language"));
    var sLang=el("select");LANGS.forEach(function(l){var o=el("option","",l);o.value=l;sLang.appendChild(o);});
    sLang.value=state.lang;sLang.addEventListener("change",function(){state.lang=sLang.value;refreshCommandOnly();});
    fLang.appendChild(sLang);pOut.appendChild(fLang);
    rail.appendChild(pOut);

    // Filters ------------------------------------------------------------
    var pFil=panel("Filters","Reader-side filter chips; also power per-chart Compare by.");
    var filterable=selected().filter(function(q){return q.type==="single";});
    if(filterable.length){
      pFil.appendChild(chipRow(filterable.map(function(q){return{v:q.v,label:q.v,title:q.label};}),
        state.filters,function(v){toggleIn(state.filters,v);refresh();}));
    }else pFil.appendChild(el("p","hint","Select at least one single-select variable."));
    rail.appendChild(pFil);

    // Variable groups ----------------------------------------------------
    var pGrp=panel("Variable groups","One stacked card from compatible single-selects (same options).");
    var glist=el("div","glist");
    state.groups.forEach(function(g,gi){
      var item=el("div","gitem");
      var top=el("div","gtop");
      var name=el("input");name.type="text";name.placeholder="Group label";name.value=g.label;
      name.addEventListener("input",function(){g.label=name.value;refreshCommandOnly();});
      var del=el("button","gdel","\u00d7");del.title="Remove group";
      del.addEventListener("click",function(){state.groups.splice(gi,1);refresh();});
      top.appendChild(name);top.appendChild(del);item.appendChild(top);
      var sig=g.members.length?optSig(byVar[g.members[0]]):null;
      var eligible=selected().filter(function(q){
        return q.type==="single"&&(q.opts||[]).length&&
          state.cmp.members.indexOf(q.v)<0&&
          (!grouped(q.v)||g.members.indexOf(q.v)>=0)&&
          (!sig||optSig(q)===sig);});
      var mem=el("div","gmembers");
      mem.appendChild(chipRow(eligible.map(function(q){return{v:q.v,label:q.v,title:q.label};}),
        g.members,function(v){toggleIn(g.members,v);refresh();}));
      item.appendChild(mem);
      if(g.members.length===1)item.appendChild(el("div","err","Pick at least two members."));
      if(!g.label.trim()&&g.members.length)item.appendChild(el("div","err","Give the group a label."));
      glist.appendChild(item);
    });
    pGrp.appendChild(glist);
    var addG=el("button","addbtn","+ New group");
    addG.addEventListener("click",function(){state.groups.push({label:"",members:[]});refresh();});
    pGrp.appendChild(addG);
    rail.appendChild(pGrp);

    // Comparison ---------------------------------------------------------
    var pCmp=panel("Comparison","Members\u2019 affirmative shares, side by side across a grouping variable.");
    var cmpEligible=selected().filter(function(q){return q.type==="single"&&!grouped(q.v);});
    pCmp.appendChild(el("label","field","Members").firstChild||el("span"));
    var fm=el("div","field");fm.appendChild(el("label","","Members"));
    fm.appendChild(chipRow(cmpEligible.map(function(q){return{v:q.v,label:q.v,title:q.label};}),
      state.cmp.members,function(v){toggleIn(state.cmp.members,v);refresh();}));
    pCmp.appendChild(fm);
    var fb=el("div","field");fb.appendChild(el("label","","Compare by"));
    var sBy=el("select");
    var noneOpt=el("option","","\u2014");noneOpt.value="";sBy.appendChild(noneOpt);
    selected().filter(function(q){return q.type==="single"&&(q.opts||[]).length>=2;})
      .forEach(function(q){var o=el("option","",q.v+" ("+(q.opts||[]).length+" levels)");o.value=q.v;sBy.appendChild(o);});
    sBy.value=state.cmp.by;
    sBy.addEventListener("change",function(){state.cmp.by=sBy.value;state.cmp.levels=[];refresh();});
    fb.appendChild(sBy);pCmp.appendChild(fb);
    if(state.cmp.by){
      var byQ=byVar[state.cmp.by],opts=(byQ&&byQ.opts)||[];
      var needsLevels=opts.length>5;
      if(needsLevels||state.cmp.levels.length){
        var fl=el("div","field");fl.appendChild(el("label","","Levels (2\u20135, in order)"));
        fl.appendChild(chipRow(opts.map(function(o){return{v:o[1]||o[0],label:o[1]||o[0]};}),
          state.cmp.levels,function(v){
            if(state.cmp.levels.indexOf(v)<0&&state.cmp.levels.length>=5)return;
            toggleIn(state.cmp.levels,v);refresh();},true));
        pCmp.appendChild(fl);
        if(needsLevels&&(state.cmp.levels.length<2))
          pCmp.appendChild(el("div","err",opts.length+" levels: pick 2\u20135 with comparelevels."));
      }
      var ft=el("div","field");ft.appendChild(el("label","","Comparison title (optional)"));
      var ti=el("input");ti.type="text";ti.value=state.cmp.title;
      ti.addEventListener("input",function(){state.cmp.title=ti.value;refreshCommandOnly();});
      ft.appendChild(ti);pCmp.appendChild(ft);
    }
    if(state.cmp.members.length&&!state.cmp.by)
      pCmp.appendChild(el("div","err","Choose the compare-by variable."));
    rail.appendChild(pCmp);

    // Weights & USD ------------------------------------------------------
    var pW=panel("Weights & currency","");
    var numericPool=HASDATA
      ?Object.keys(DATAVARS).filter(function(v){return DATAVARS[v]==="numeric";})
      :Q.filter(function(q){return q.type==="numeric";}).map(function(q){return q.v;});
    var fw=el("div","field");fw.appendChild(el("label","","Weight variable"));
    var sw=el("select");var wnone=el("option","","\u2014 unweighted");wnone.value="";sw.appendChild(wnone);
    numericPool.forEach(function(v){var o=el("option","",v);o.value=v;sw.appendChild(o);});
    if(state.weightVar&&numericPool.indexOf(state.weightVar)<0){
      var keep=el("option","",state.weightVar);keep.value=state.weightVar;sw.appendChild(keep);}
    sw.value=state.weightVar;
    sw.addEventListener("change",function(){state.weightVar=sw.value;refresh();});
    fw.appendChild(sw);pW.appendChild(fw);
    if(state.weightVar){
      var fwt=el("div","field");fwt.appendChild(el("label","","Weight type"));
      var swt=el("select");WTYPES.forEach(function(w){var o=el("option","",w);o.value=w;swt.appendChild(o);});
      swt.value=state.weightType;
      swt.addEventListener("change",function(){state.weightType=swt.value;refreshCommandOnly();});
      fwt.appendChild(swt);pW.appendChild(fwt);
    }
    var usdEligible=selected().filter(function(q){return q.type==="numeric";});
    if(usdEligible.length){
      var fu=el("div","field");fu.appendChild(el("label","","USD conversion for"));
      fu.appendChild(chipRow(usdEligible.map(function(q){return{v:q.v,label:q.v,title:q.label};}),
        state.usdVars,function(v){toggleIn(state.usdVars,v);refresh();}));
      pW.appendChild(fu);
      if(state.usdVars.length){
        var fr=el("div","field");fr.appendChild(el("label","","Units per USD (rate)"));
        var ri=el("input");ri.type="number";ri.step="any";ri.value=state.usdRate;
        ri.addEventListener("input",function(){state.usdRate=ri.value;refreshCommandOnly();});
        fr.appendChild(ri);pW.appendChild(fr);
        var fc=el("div","field");fc.appendChild(el("label","","Local currency code"));
        var ci=el("input");ci.type="text";ci.placeholder="LKR";ci.value=state.currency;
        ci.addEventListener("input",function(){state.currency=ci.value;refreshCommandOnly();});
        fc.appendChild(ci);pW.appendChild(fc);
        if(!state.usdRate)pW.appendChild(el("div","err","Set the exchange rate."));
      }
    }
    rail.appendChild(pW);
  }

  // ----------------------------------------------------------- composers
  function sq(s){s=""+s;return s.indexOf("\"")>=0?"`\""+s+"\"'":"\""+s+"\"";}
  function wrapCmd(head,parts){
    var lines=[],line=head;
    parts.forEach(function(p){
      if((line+" "+p).length>78){lines.push(line+" ///");line="    "+p;}
      else line+=" "+p;});
    lines.push(line);return lines.join("\n");
  }
  function kindBucket(k){return Object.keys(state.kind).filter(function(v){return state.kind[v]===k&&isSel(v);});}
  function validation(){
    var msgs=[];
    if(!state.sel.length)msgs.push("Select at least one variable.");
    state.groups.forEach(function(g){
      if(g.members.length===1)msgs.push("Group needs two members.");
      if(g.members.length&&!g.label.trim())msgs.push("Group needs a label.");});
    if(state.cmp.members.length&&!state.cmp.by)msgs.push("Comparison needs compare-by.");
    if(state.cmp.by){var n=((byVar[state.cmp.by]||{}).opts||[]).length;
      if(n>5&&(state.cmp.levels.length<2||state.cmp.levels.length>5))
        msgs.push("Pick 2\u20135 comparison levels.");}
    if(state.usdVars.length&&!state.usdRate)msgs.push("USD needs a rate.");
    return msgs;
  }
  function stataCommand(){
    var order=Q.map(function(q){return q.v;}).filter(isSel);
    var head="surveye "+order.join(" ")+" using "+sq(SPEC.questionnairePath||"questionnaire.html");
    if(state.weightVar)head+=" ["+state.weightType+"="+state.weightVar+"]";
    head+=",";
    var parts=["saving("+sq(state.saving||"dashboard.html")+")","replace"];
    if(state.title&&state.title!==SPEC.title)parts.push("title("+sq(state.title)+")");
    if(state.subtitle)parts.push("subtitle("+sq(state.subtitle)+")");
    if(state.byline)parts.push("byline("+sq(state.byline)+")");
    if(state.filters.length)parts.push("filters("+state.filters.join(" ")+")");
    var validGroups=state.groups.filter(function(g){return g.label.trim()&&g.members.length>=2;});
    if(validGroups.length)parts.push("vargroups("+sq(validGroups.map(function(g){
      return g.label.trim()+":: "+g.members.join(" ");}).join("|"))+")");
    if(state.cmp.members.length&&state.cmp.by){
      parts.push("compare("+state.cmp.members.join(" ")+")");
      parts.push("compareby("+state.cmp.by+")");
      if(state.cmp.levels.length)parts.push("comparelevels("+sq(state.cmp.levels.join(" "))+")");
      if(state.cmp.title)parts.push("comparetitle("+sq(state.cmp.title)+")");
    }
    ["bars","donut","hist","discrete","continuous"].forEach(function(k){
      var vars=kindBucket(k);if(!vars.length)return;
      var opt={bars:"bars",donut:"donuts",hist:"histograms",discrete:"discrete",continuous:"continuous"}[k];
      parts.push(opt+"("+vars.join(" ")+")");});
    if(state.usdVars.length&&state.usdRate){
      parts.push("usdvars("+state.usdVars.join(" ")+")");
      parts.push("usdrate("+state.usdRate+")");
      if(state.currency)parts.push("currency("+sq(state.currency)+")");
    }
    if(state.theme!=="worldbank")parts.push("theme("+state.theme+")");
    if(state.lang!=="auto")parts.push("uilanguage("+state.lang+")");
    return wrapCmd(head,parts);
  }
  function tsvConfig(){
    var order=Q.map(function(q){return q.v;}).filter(isSel);
    var lines=[["mode","build"],["questionnaire",SPEC.questionnairePath||"questionnaire.html"],
      ["data","<your_export.csv>"],["output",state.saving||"dashboard.html"],["replace","1"],
      ["variables",order.join(" ")]];
    if(state.title)lines.push(["title",state.title]);
    if(state.subtitle)lines.push(["subtitle",state.subtitle]);
    if(state.byline)lines.push(["byline",state.byline]);
    if(state.filters.length)lines.push(["filters",state.filters.join(" ")]);
    var validGroups=state.groups.filter(function(g){return g.label.trim()&&g.members.length>=2;});
    if(validGroups.length)lines.push(["vargroups",validGroups.map(function(g){
      return g.label.trim()+":: "+g.members.join(" ");}).join("|")]);
    if(state.cmp.members.length&&state.cmp.by){
      lines.push(["compare",state.cmp.members.join(" ")]);
      lines.push(["compareby",state.cmp.by]);
      if(state.cmp.levels.length)lines.push(["comparelevels",state.cmp.levels.join(" ")]);
      if(state.cmp.title)lines.push(["comparetitle",state.cmp.title]);
    }
    ["bars","donut","hist","discrete","continuous"].forEach(function(k){
      var vars=kindBucket(k);if(!vars.length)return;
      var key={bars:"bars",donut:"donuts",hist:"histograms",discrete:"discrete",continuous:"continuous"}[k];
      lines.push([key,vars.join(" ")]);});
    if(state.weightVar){lines.push(["weight",state.weightVar]);lines.push(["weighttype",state.weightType]);}
    if(state.usdVars.length&&state.usdRate){
      lines.push(["usdvars",state.usdVars.join(" ")]);
      lines.push(["usdrate",state.usdRate]);
      if(state.currency)lines.push(["currency",state.currency]);
    }
    if(state.lang!=="auto")lines.push(["uilanguage",state.lang]);
    return lines.map(function(l){return l[0]+"\t"+l[1];}).join("\n")+"\n";
  }

  // ---------------------------------------------------------- command bar
  var bar=el("div","cmdbar"),inner=el("div","cmd-inner");
  var tabs=el("div","cmd-tabs");
  var tabStata=el("button","cmd-tab","Stata command");
  var tabTsv=el("button","cmd-tab","Engine config (TSV)");
  var note=el("span","cmd-note");
  tabs.appendChild(tabStata);tabs.appendChild(tabTsv);tabs.appendChild(note);
  var box=el("div","cmd-box");
  var text=el("pre","cmd-text");text.id="cmd-output";
  var actions=el("div","cmd-actions");
  var btnCopy=el("button","cbtn","Copy");
  var btnDo=el("button","cbtn alt","Download .do");
  var btnTsv=el("button","cbtn alt","Download .tsv");
  actions.appendChild(btnCopy);actions.appendChild(btnDo);actions.appendChild(btnTsv);
  box.appendChild(text);box.appendChild(actions);
  inner.appendChild(tabs);inner.appendChild(box);bar.appendChild(inner);
  app.appendChild(bar);

  function setTab(which){state.tab=which;
    tabStata.classList.toggle("on",which==="stata");
    tabTsv.classList.toggle("on",which==="tsv");
    refreshCommandOnly();}
  tabStata.addEventListener("click",function(){setTab("stata");});
  tabTsv.addEventListener("click",function(){setTab("tsv");});
  function refreshCommandOnly(){
    text.textContent=state.tab==="stata"?stataCommand():tsvConfig();
    var msgs=validation();
    note.textContent=msgs.length?msgs[0]:"Ready \u2014 paste into Stata and run.";
    note.classList.toggle("ok",!msgs.length);
    persist();
  }
  function download(name,content,mime){
    var a=document.createElement("a");
    a.href="data:"+mime+";charset=utf-8,"+encodeURIComponent(content);
    a.download=name;document.body.appendChild(a);a.click();a.remove();
  }
  btnCopy.addEventListener("click",function(){
    var value=text.textContent;
    function done(){btnCopy.classList.add("copied");btnCopy.textContent="Copied \u2713";
      setTimeout(function(){btnCopy.classList.remove("copied");btnCopy.textContent="Copy";},1400);}
    if(navigator.clipboard&&navigator.clipboard.writeText)
      navigator.clipboard.writeText(value).then(done,function(){fallback();});
    else fallback();
    function fallback(){var ta=document.createElement("textarea");ta.value=value;
      document.body.appendChild(ta);ta.select();
      try{document.execCommand("copy");}catch(e){}ta.remove();done();}
  });
  btnDo.addEventListener("click",function(){
    download(slug(state.title)+".do",
      "* SurvEye "+SPEC.engineVersion+" \u00b7 generated by the configurator\n"+
      "* "+new Date().toISOString().slice(0,10)+"\n\n"+stataCommand()+"\n","text/plain");});
  btnTsv.addEventListener("click",function(){
    download(slug(state.title)+"-config.tsv",tsvConfig(),"text/tab-separated-values");});

  function refresh(){buildRail();syncRows();refreshCommandOnly();}
  buildRows();setTab(state.tab||"stata");refresh();
})();

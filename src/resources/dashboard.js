(function(){
  "use strict";

  var palette=[
    css("--chart-blue","#009fda"),css("--chart-gold","#fdb714"),
    css("--chart-green","#00a887"),css("--chart-coral","#e5552b"),
    css("--chart-purple","#7c5ba6"),css("--chart-teal","#1c8c9b"),
    css("--chart-navy","#002244"),css("--chart-stone","#b7b09c"),
    css("--chart-sky","#5bc0eb"),css("--chart-amber","#d58e00"),
    css("--chart-leaf","#6e9f3c"),css("--chart-rose","#b85c8a")
  ];
  var muted=css("--chart-special","#9aa0a6"),yesColor=css("--chart-blue","#009fda"),
      noColor=css("--chart-binary-no","#d9d3c5"),answeredColor=css("--chart-green","#00a887");
  var compact=!CONFIG.density||CONFIG.density==="compact";
  var rtl=CONFIG.direction==="rtl",arabicUi=CONFIG.uiLanguage==="arabic"||CONFIG.uiLanguage==="urdu",UI=CONFIG.strings||{},locale=CONFIG.locale||"en";
  var weightAvailable=!!CONFIG.weighted;
  function dictionary(){return Object.create(null);}
  function owns(object,key){return !!object&&Object.prototype.hasOwnProperty.call(object,key);}
  function metaFor(variable){return owns(META,variable)?META[variable]:null;}
  var state={filters:dictionary(),charts:dictionary(),visible:dictionary(),renderedRevision:dictionary(),chartRevision:0,visibilityRefreshPending:false,weightsEnabled:null,usdEnabled:false,mapRows:[],leafletMap:null,leafletPointRenderer:null,mapPointLayer:null,mapBoundaryLayer:null,mapNeedsFit:true,mapRedrawing:false};

  function tr(key,fallback){return owns(UI,key)?UI[key]:(fallback===undefined?key:fallback);}
  function localNumber(value){return Number(value).toLocaleString(locale);}
  function weightsActive(){return !!CONFIG.weighted&&state.weightsEnabled!==false;}
  function usdAvailable(){return !!(CONFIG.usd&&CONFIG.usd.enabled&&Array.isArray(CONFIG.usd.variables)&&CONFIG.usd.variables.length&&isFinite(+CONFIG.usd.rate)&&+CONFIG.usd.rate>0);}
  function usdActive(){return usdAvailable()&&state.usdEnabled;}
  function isCurrencyVariable(variable){return usdAvailable()&&CONFIG.usd.variables.indexOf(variable)>=0;}
  function convertedNumber(variable,value){return usdActive()&&isCurrencyVariable(variable)?value/(+CONFIG.usd.rate):value;}
  function currencyCode(variable){if(!isCurrencyVariable(variable))return"";return usdActive()?"USD":((CONFIG.usd.currency||"").trim());}

  Chart.defaults.font.family=arabicUi?'"Noto Sans Arabic","Noto Naskh Arabic","DejaVu Sans",Tahoma,Arial,sans-serif':'Inter,"Segoe UI",Roboto,Helvetica,Arial,"Noto Sans",sans-serif';
  Chart.defaults.color=css("--muted","#5e6b7b");
  Chart.defaults.locale=locale;
  var reducedMotion=!!(window.matchMedia&&window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  Chart.defaults.animation.duration=reducedMotion?0:800;
  Chart.defaults.animation.easing="easeOutCubic";
  Chart.defaults.plugins.legend.labels.boxWidth=11;
  Chart.defaults.plugins.legend.labels.boxHeight=11;
  Chart.defaults.plugins.legend.labels.font={size:10};
  Chart.defaults.plugins.legend.rtl=rtl;
  Chart.defaults.plugins.legend.textDirection=rtl?"rtl":"ltr";
  Chart.defaults.plugins.tooltip.rtl=rtl;
  Chart.defaults.plugins.tooltip.textDirection=rtl?"rtl":"ltr";
  Chart.defaults.plugins.tooltip.titleAlign=rtl?"right":"left";
  Chart.defaults.plugins.tooltip.bodyAlign=rtl?"right":"left";
  Chart.defaults.plugins.tooltip.footerAlign=rtl?"right":"left";

  function weight(row){
    if(!weightsActive())return 1;
    var w=+row._w;return isFinite(w)&&w>=0?w:0;
  }
  function isMissing(v){return v===null||v===undefined||v==="";}
  function isSpecial(variable,value){
    var meta=metaFor(variable),key=""+value;
    return !!(meta&&meta.special&&meta.special.indexOf(key)>=0);
  }
  function isConfiguredMissing(variable,value){
    var meta=metaFor(variable),key=""+value;
    return !!(meta&&meta.missing&&meta.missing.indexOf(key)>=0);
  }
  function labelFor(variable,value){
    var meta=metaFor(variable),key=""+value;
    return meta&&meta.labels&&owns(meta.labels,key)?meta.labels[key]:key;
  }
  function filterMatches(variable,value,selected){
    var meta=metaFor(variable)||{},mode=meta.filterMode||(meta.multi?"multi":meta.kind==="completion"?"completion":meta.kind==="hist"?"numeric":"scalar");
    if(mode==="completion")return selected.indexOf(isMissing(value)?"false":"true")>=0;
    if(mode==="multi"){
      if(!Array.isArray(value))return false;
      for(var i=0;i<value.length;i++)if(!isConfiguredMissing(variable,value[i])&&selected.indexOf(""+value[i])>=0)return true;
      return false;
    }
    if(isMissing(value)||isConfiguredMissing(variable,value))return false;
    if(mode==="numeric"){
      var number=+value;if(!isFinite(number))return false;
      if(number<0&&isSpecial(variable,value))return false;
      for(var j=0;j<selected.length;j++)if(isFinite(+selected[j])&&+selected[j]===number)return true;
      return false;
    }
    return selected.indexOf(""+value)>=0;
  }
  function filteredRows(excludedFilter){
    var out=[];
    outer:for(var i=0;i<DATA.length;i++){
      var row=DATA[i];
      for(var variable in state.filters){
        if(excludedFilter&&variable===excludedFilter)continue;
        var selected=state.filters[variable];
        if(!selected||!selected.length)continue;
        var value=row[variable];
        if(!filterMatches(variable,value,selected))continue outer;
      }
      out.push(row);
    }
    return out;
  }
  function rows(){return filteredRows(null);}
  function fmt(value,digits){
    if(value===null||value===undefined||!isFinite(value))return tr("notAvailable","n/a");
    var abs=Math.abs(value),d=digits===undefined?1:digits;
    if(abs>=1e9)return (value/1e9).toFixed(d)+"B";
    if(abs>=1e6)return (value/1e6).toFixed(d)+"M";
    if(abs>=1e4)return localNumber(Math.round(value));
    return (+value.toFixed(d)).toLocaleString(locale);
  }
  function metricFmt(variable,value,digits){var rendered=fmt(value,digits),code=currencyCode(variable);return code&&rendered!==tr("notAvailable","n/a")?code+" "+rendered:rendered;}
  var percentFormatter=null;
  try{percentFormatter=new Intl.NumberFormat(locale,{style:"percent",minimumFractionDigits:0,maximumFractionDigits:1});}catch(ignore){}
  function pct(value){var rounded=Math.round(value*10)/10;return percentFormatter?percentFormatter.format(rounded/100):rounded.toLocaleString(locale)+"%";}
  function normalQuantile(p){
    if(p<=0||p>=1)return p===0?-Infinity:p===1?Infinity:NaN;
    var a=[-39.6968302866538,220.946098424521,-275.928510446969,138.357751867269,-30.6647980661472,2.50662827745924];
    var b=[-54.4760987982241,161.585836858041,-155.698979859887,66.8013118877197,-13.2806815528857];
    var c=[-.00778489400243029,-.322396458041136,-2.40075827716184,-2.54973253934373,4.37466414146497,2.93816398269878];
    var d=[.00778469570904146,.32246712907004,2.445134137143,3.75440866190742],q,r;
    if(p<.02425){q=Math.sqrt(-2*Math.log(p));return (((((c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5])/((((d[0]*q+d[1])*q+d[2])*q+d[3])*q+1);}
    if(p>.97575){q=Math.sqrt(-2*Math.log(1-p));return -(((((c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5])/((((d[0]*q+d[1])*q+d[2])*q+d[3])*q+1);}
    q=p-.5;r=q*q;return (((((a[0]*r+a[1])*r+a[2])*r+a[3])*r+a[4])*r+a[5])*q/(((((b[0]*r+b[1])*r+b[2])*r+b[3])*r+b[4])*r+1);
  }
  function effectiveN(cat){
    if(!weightsActive())return cat.n;
    if((CONFIG.weightType||"").toLowerCase()==="fweight")return cat.total;
    return cat.sumW2>0?cat.total*cat.total/cat.sumW2:0;
  }
  function confidenceInterval(cat,count){
    if(!CONFIG.showCI||(weightsActive()&&(CONFIG.weightType||"").toLowerCase()==="iweight")||!cat||cat.total<=0)return null;var n=effectiveN(cat),p=Math.max(0,Math.min(1,count/cat.total));if(!(n>0))return null;
    var level=+(CONFIG.ciLevel||95),z=normalQuantile(.5+level/200),z2=z*z,den=1+z2/n,center=(p+z2/(2*n))/den,half=z*Math.sqrt(p*(1-p)/n+z2/(4*n*n))/den;
    return{low:100*Math.max(0,center-half),high:100*Math.min(1,center+half),level:level,approx:!!(weightsActive()&&(CONFIG.weightType||"").toLowerCase()!=="fweight")};
  }
  function confidenceText(interval){return interval?(interval.approx?tr("approx","approx.")+" ":"")+pct(interval.level)+" "+tr("confidenceInterval","CI")+" "+pct(interval.low)+"–"+pct(interval.high):"";}
  function quantile(sorted,p){
    if(!sorted.length)return null;
    if(p<=0)return sorted[0];if(p>=1)return sorted[sorted.length-1];
    var target=sorted.length*p,cumulative=0,tolerance=1e-12*Math.max(1,sorted.length);
    for(var i=0;i<sorted.length;i++){
      var previous=cumulative;cumulative+=1;
      if(cumulative>target){
        if(i>0&&Math.abs(previous-target)<=tolerance)return(sorted[i-1]+sorted[i])/2;
        return sorted[i];
      }
    }
    return sorted[sorted.length-1];
  }
  function median(values){
    var copy=values.slice().sort(function(a,b){return a-b;});return quantile(copy,.5);
  }
  function weightedQuantile(points,p){
    if(!points.length)return null;
    var sorted=points.slice().sort(function(a,b){return a.value-b.value;});
    if(!weightsActive())return quantile(sorted.map(function(point){return point.value;}),p);
    sorted=sorted.filter(function(point){return isFinite(point.w)&&point.w>0;});if(!sorted.length)return null;
    var total=sorted.reduce(function(sum,point){return sum+point.w;},0);if(total<=0)return null;
    if(p<=0)return sorted[0].value;if(p>=1)return sorted[sorted.length-1].value;
    var target=total*p,cumulative=0,tolerance=1e-12*Math.max(1,total);
    for(var i=0;i<sorted.length;i++){
      var previous=cumulative;cumulative+=sorted[i].w;
      if(cumulative>target){
        if(i>0&&Math.abs(previous-target)<=tolerance)return(sorted[i-1].value+sorted[i].value)/2;
        return sorted[i].value;
      }
    }
    return sorted[sorted.length-1].value;
  }
  function weightedMean(points){
    if(!points.length)return null;var total=0,sum=0;points.forEach(function(point){var w=weightsActive()?Math.max(0,point.w):1;total+=w;sum+=point.value*w;});
    return total>0?sum/total:null;
  }
  var monthNames={jan:1,feb:2,mar:3,apr:4,may:5,jun:6,jul:7,aug:8,sep:9,oct:10,nov:11,dec:12};
  function stataDateType(variable){var meta=metaFor(variable)||{},match=(meta.format||"").toLowerCase().match(/^%t([cdwmqhy])/);return match?match[1]:"";}
  function monthKey(variable,value){
    if(isMissing(value)||isConfiguredMissing(variable,value))return null;var text=(""+value).trim(),match,year,month,type=stataDateType(variable);
    match=text.match(/^(\d{4})m(\d{1,2})$/i);if(match){year=+match[1];month=+match[2];}
    if(!match){match=text.match(/^(\d{4})q([1-4])$/i);if(match){year=+match[1];month=(+match[2]-1)*3+1;}}
    if(!match){match=text.match(/^(\d{4})h([12])$/i);if(match){year=+match[1];month=+match[2]===1?1:7;}}
    if(!match){match=text.match(/^(\d{4})w(\d{1,2})$/i);if(match){var shownWeek=new Date(Date.UTC(+match[1],0,1)+(+match[2]-1)*7*86400000);year=shownWeek.getUTCFullYear();month=shownWeek.getUTCMonth()+1;}}
    if(!match){match=text.match(/(\d{4})[-\/.](\d{1,2})/);if(match){year=+match[1];month=+match[2];}}
    if(!match){match=text.match(/\b\d{1,2}[\s-]*([A-Za-z]{3})[A-Za-z]*[\s-]*(\d{4})\b/);if(match){month=monthNames[match[1].toLowerCase()];year=+match[2];}}
    if(!match){match=text.match(/\b(\d{1,2})[-\/.](\d{1,2})[-\/.](\d{4})\b/);if(match){year=+match[3];var first=+match[1],second=+match[2];month=first>12?second:first;}}
    if(!match&&/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(text)){
      var numeric=+text,origin=Date.UTC(1960,0,1),date,index;
      if(type==="m"){index=Math.floor(numeric);year=1960+Math.floor(index/12);month=((index%12)+12)%12+1;match=true;}
      else if(type==="q"){index=Math.floor(numeric);year=1960+Math.floor(index/4);month=(((index%4)+4)%4)*3+1;match=true;}
      else if(type==="h"){index=Math.floor(numeric);year=1960+Math.floor(index/2);month=(((index%2)+2)%2)*6+1;match=true;}
      else if(type==="y"){year=Math.floor(numeric);month=1;match=true;}
      else if(type==="w"){
        index=Math.floor(numeric);year=1960+Math.floor(index/52);var week=((index%52)+52)%52+1;
        date=new Date(Date.UTC(year,0,1)+(week-1)*7*86400000);month=date.getUTCMonth()+1;match=true;
      }
      else{
        var milliseconds=type==="c"?numeric:type==="d"?numeric*86400000:Math.abs(numeric)>1000000?numeric:numeric*86400000;
        date=new Date(origin+milliseconds);if(isFinite(date.getTime())){year=date.getUTCFullYear();month=date.getUTCMonth()+1;match=true;}
      }
    }
    return match&&year>=1000&&month>=1&&month<=12?year+"-"+("0"+month).slice(-2):null;
  }
  function shortened(text,max){text=""+(text||"");return text.length>max?text.slice(0,max-1)+"…":text;}
  function wrapped(text,max,lines){
    text=(""+(text||"")).trim();if(!text)return "";var words=text.split(/\s+/),out=[],line="",limit=lines||2;
    words.forEach(function(word){var next=line?line+" "+word:word;if(next.length<=max||!line)line=next;else{out.push(line);line=word;}});if(line)out.push(line);
    if(out.length>limit){out=out.slice(0,limit);out[limit-1]=shortened(out[limit-1]+"…",max);}return out.length===1?out[0]:out;
  }
  function colorFor(variable,key,index){
    if(isSpecial(variable,key))return muted;
    var meta=metaFor(variable);
    if(meta&&meta.kind==="yesno"){
      var code=""+key,label=labelFor(variable,key).trim().toLocaleLowerCase();
      if((meta.affirmative||[]).indexOf(code)>=0||/^(yes|y|true|sí|si|oui|ja|sim|evet|да|是|はい|ඔව්|نعم|أجل|اجل|ہاں|جی|جی ہاں|ہاں جی)$/.test(label))return yesColor;
      if((meta.negative||[]).indexOf(code)>=0||/^(no|n|false|non|nein|não|nao|hayır|нет|否|いいえ|නැත|لا|كلا|نہیں|نہ|جی نہیں)$/.test(label))return noColor;
    }
    return palette[stableColorIndex(variable,key,index)];
  }
  function stableColorIndex(variable,key,fallback){
    var meta=metaFor(variable)||{},code=""+key,position=(meta.order||[]).map(String).indexOf(code);
    if(position>=0)return position%palette.length;
    var text=variable+"\u0000"+code,hash=2166136261;
    for(var i=0;i<text.length;i++){hash^=text.charCodeAt(i);hash=Math.imul(hash,16777619);}
    return (hash>>>0)%palette.length;
  }
  function rgba(hex,alpha){var raw=(hex||"#009fda").replace("#","");if(raw.length===3)raw=raw.replace(/(.)/g,"$1$1");var n=parseInt(raw,16);return "rgba("+((n>>16)&255)+","+((n>>8)&255)+","+(n&255)+","+alpha+")";}
  function css(name,fallback){var value=getComputedStyle(document.body).getPropertyValue(name).trim();return value||fallback;}
  function chartSummary(variable){return document.getElementById("summary-"+safeId(variable));}
  function clearSummary(variable,message){
    var root=chartSummary(variable);if(!root)return;root.textContent="";
    if(message){var hidden=document.createElement("span");hidden.className="summary-accessible";hidden.textContent=message;root.appendChild(hidden);}
  }
  function describe(canvas,message){
    if(!canvas._surveyeLabel)canvas._surveyeLabel=canvas.getAttribute("aria-label")||tr("chart","Chart");
    canvas.setAttribute("aria-label",canvas._surveyeLabel);
    if(message)canvas.setAttribute("data-chart-summary",message);else canvas.removeAttribute("data-chart-summary");
  }
  function summaryText(items,total){
    return items.map(function(item){return item.label+" "+pct(total?100*item.count/total:0)+" ("+tr("rawN","raw n")+"="+localNumber(item.raw)+")"+(item.interval?"; "+confidenceText(item.interval):"");}).join("; ");
  }
  function splitSummary(variable,items,total,leadIndex){
    var root=chartSummary(variable);if(!root)return;root.textContent="";var lead=items[leadIndex]||items[0],leadRow=document.createElement("div");leadRow.className="summary-lead";
    var value=document.createElement("strong"),label=document.createElement("span");value.textContent=pct(total?100*lead.count/total:0);label.textContent=lead.label;leadRow.appendChild(value);leadRow.appendChild(label);if(lead.interval){var ci=document.createElement("em");ci.textContent=confidenceText(lead.interval);leadRow.appendChild(ci);}root.appendChild(leadRow);
    var parts=document.createElement("div");parts.className="summary-parts";items.forEach(function(item){var part=document.createElement("span"),dot=document.createElement("i"),name=document.createElement("span"),share=document.createElement("b");part.className="summary-part";dot.style.backgroundColor=item.color;name.textContent=item.label;share.textContent=pct(total?100*item.count/total:0);part.appendChild(dot);part.appendChild(name);part.appendChild(share);parts.appendChild(part);});root.appendChild(parts);
    var hidden=document.createElement("span");hidden.className="summary-accessible";hidden.textContent=summaryText(items,total);root.appendChild(hidden);
  }
  function horizontalScale(options,directionRtl){
    options=options||{};options.reverse=directionRtl===undefined?rtl:!!directionRtl;return options;
  }
  function barValuePlacement(bar,anchor,area,width,directionRtl){
    var isRtl=directionRtl===undefined?rtl:!!directionRtl,endRoom=isRtl?anchor-area.left:area.right-anchor,barRoom=Math.abs(bar.x-bar.base),inside=endRoom<width+7&&barRoom>width+11;
    return{inside:inside,textAlign:inside?(isRtl?"left":"right"):(isRtl?"right":"left"),x:inside?bar.x+(isRtl?5:-5):anchor+(isRtl?-5:5)};
  }
  var barValueLabels={id:"barValueLabels",afterDatasetsDraw:function(chart,args,options){
    if(!options||options.display===false)return;var meta=chart.getDatasetMeta(0),data=chart.data.datasets[0].data,ctx=chart.ctx,ink=css("--ink","#16202e"),onAccent=css("--chart-on-accent","#16202e");ctx.save();ctx.font="700 9px "+Chart.defaults.font.family;ctx.textBaseline="middle";ctx.direction=rtl?"rtl":"ltr";
    meta.data.forEach(function(bar,index){var text=pct(+data[index]||0),width=ctx.measureText(text).width,interval=options.intervals&&options.intervals[index],anchor=interval?chart.scales.x.getPixelForValue(interval.high):bar.x,placement=barValuePlacement(bar,anchor,chart.chartArea,width);ctx.textAlign=placement.textAlign;ctx.fillStyle=placement.inside?onAccent:ink;ctx.fillText(text,placement.x,bar.y);});ctx.restore();
  }};
  var barConfidenceIntervals={id:"barConfidenceIntervals",afterDatasetsDraw:function(chart,args,options){
    if(!options||!options.intervals||!CONFIG.showCI)return;var bars=chart.getDatasetMeta(0).data,scale=chart.scales.x,ctx=chart.ctx;ctx.save();
    options.intervals.forEach(function(interval,index){if(!interval||!bars[index])return;var y=bars[index].y,x1=scale.getPixelForValue(interval.low),x2=scale.getPixelForValue(interval.high);ctx.strokeStyle=css("--card","#fff");ctx.lineWidth=3.4;ctx.beginPath();ctx.moveTo(x1,y);ctx.lineTo(x2,y);ctx.stroke();ctx.strokeStyle=css("--ink","#16202e");ctx.lineWidth=1.35;ctx.beginPath();ctx.moveTo(x1,y);ctx.lineTo(x2,y);ctx.moveTo(x1,y-3);ctx.lineTo(x1,y+3);ctx.moveTo(x2,y-3);ctx.lineTo(x2,y+3);ctx.stroke();});ctx.restore();
  }};
  var groupedValueLabels={id:"groupedValueLabels",afterDatasetsDraw:function(chart,args,options){
    if(!options||options.display===false)return;var ctx=chart.ctx,ink=css("--ink","#16202e"),onAccent=css("--chart-on-accent","#16202e");ctx.save();ctx.font="700 9px "+Chart.defaults.font.family;ctx.textBaseline="middle";ctx.direction=rtl?"rtl":"ltr";
    chart.data.datasets.forEach(function(dataset,datasetIndex){var bars=chart.getDatasetMeta(datasetIndex).data||[];bars.forEach(function(bar,index){var value=dataset.data[index];if(value===null||value===undefined||!isFinite(+value))return;var text=pct(+value),width=ctx.measureText(text).width,placement=barValuePlacement(bar,bar.x,chart.chartArea,width);ctx.textAlign=placement.textAlign;ctx.fillStyle=placement.inside?onAccent:ink;ctx.fillText(text,placement.x,bar.y);});});ctx.restore();
  }};
  var familyPrimaryValueLabels={id:"familyPrimaryValueLabels",afterDatasetsDraw:function(chart,args,options){
    if(!options||!Array.isArray(options.indexes))return;var ctx=chart.ctx,ink=css("--ink","#16202e"),onAccent=css("--chart-on-accent","#16202e");ctx.save();ctx.font="700 9px "+Chart.defaults.font.family;ctx.textBaseline="middle";ctx.direction=rtl?"rtl":"ltr";
    options.indexes.forEach(function(datasetIndex,rowIndex){if(datasetIndex===null||datasetIndex===undefined||datasetIndex<0)return;var dataset=chart.data.datasets[datasetIndex],bar=chart.getDatasetMeta(datasetIndex).data[rowIndex];if(!dataset||!bar)return;var value=dataset.data[rowIndex];if(value===null||value===undefined||!isFinite(+value))return;var label=pct(+value),width=ctx.measureText(label).width,placement=barValuePlacement(bar,bar.x,chart.chartArea,width);ctx.textAlign=placement.textAlign;ctx.fillStyle=placement.inside?onAccent:ink;ctx.fillText(label,placement.x,bar.y);});ctx.restore();
  }};
  function meanPlusThreeSd(stats,minimum,maximum){
    if(!stats||!isFinite(+stats.mean)||!isFinite(+stats.sd)||!(+stats.sd>0)||!isFinite(+minimum)||!isFinite(+maximum)||!(+maximum>+minimum))return null;
    var value=+stats.mean+3*(+stats.sd);return isFinite(value)&&value>+minimum&&value<+maximum?value:null;
  }
  var threeSdGuide={id:"threeSdGuide",afterDatasetsDraw:function(chart,args,options){
    if(!options||!chart.scales||!chart.scales.x)return;var value=meanPlusThreeSd({mean:options.mean,sd:options.sd},options.minimum,options.maximum);if(value===null)return;
    var area=chart.chartArea,x=chart.scales.x.getPixelForValue(value),ctx=chart.ctx,color=css("--chart-coral","#e5552b"),label=tr("meanPlusThreeSd","Mean + 3 SD")+" "+metricFmt(options.variable||"",value,2);ctx.save();ctx.strokeStyle=color;ctx.lineWidth=1.7;ctx.lineCap="round";ctx.setLineDash([2,4]);ctx.beginPath();ctx.moveTo(x,area.top);ctx.lineTo(x,area.bottom);ctx.stroke();ctx.setLineDash([]);ctx.fillStyle=color;ctx.font="700 9px "+Chart.defaults.font.family;ctx.textBaseline="bottom";var width=ctx.measureText(label).width,padding=3,drawX=Math.max(area.left+padding,Math.min(area.right-width-padding,x-width/2));ctx.textAlign="left";ctx.direction=rtl?"rtl":"ltr";ctx.fillText(label,drawX,area.top-4);ctx.restore();
  }};
  function setEmpty(canvas,on,message){
    var empty=document.getElementById("empty-"+canvas.id);
    if(!empty)return;
    empty.classList.toggle("show",!!on);
    var text=empty.querySelector("span");if(text&&message)text.textContent=message;
  }
  function destroy(id){if(state.charts[id]){state.charts[id].destroy();delete state.charts[id];}}
  function statistic(variable,text){var el=document.getElementById("stat-"+safeId(variable));if(el){el.textContent=text||"";el.title=text||"";}}
  function safeId(value){return (""+value).replace(/[^A-Za-z0-9_-]/g,"_");}
  function panelKey(canvas){return canvas.getAttribute("data-panel-id")||canvas.getAttribute("data-variable")||canvas.id;}
  function configuredRecord(collection,key){return collection&&owns(collection,key)?collection[key]:null;}
  function configuredMembers(canvas,record){
    var value=record&&record.members!==undefined?record.members:canvas.getAttribute("data-members"),members=[];
    if(Array.isArray(value))members=value.slice();else if(value!==null&&value!==undefined)members=(""+value).trim().split(/[\s,]+/);
    var unique=[];members.forEach(function(member){member=(""+member).trim();if(member&&unique.indexOf(member)<0)unique.push(member);});return unique;
  }
  function configuredMemberLabel(record,member,index){
    var labels=record&&record.labels,label=null;
    if(Array.isArray(labels)&&labels[index]!==undefined)label=labels[index];
    else if(labels&&typeof labels==="object"&&owns(labels,member))label=labels[member];
    var meta=metaFor(member);return ""+(label!==null&&label!==undefined&&(""+label).trim()?label:(meta&&meta.label?meta.label:member));
  }
  function memberAxisLabel(record,member,index){
    var lines=wrapped(configuredMemberLabel(record,member,index),compact?28:44,2);if(!Array.isArray(lines))lines=[lines];lines.push(member);return lines;
  }
  function configuredList(value){
    if(Array.isArray(value))return value.slice();if(value===null||value===undefined)return[];return (""+value).trim().split(/[\s,|]+/).filter(function(item){return item!=="";});
  }

  function categorical(variable,source,multi){
    var meta=metaFor(variable)||{},counts=dictionary(),raw=dictionary(),seen=dictionary(),order=(meta.order||[]).slice(),validRaw=0,validWeight=0,sumW2=0,respondents=[];
    for(var i=0;i<source.length;i++){
      var value=source[i][variable],values=multi?(Array.isArray(value)?value:[]):[value];
      // Survey Solutions exports an answered multiselect with every option
      // unselected as []; null means the question was not answered.  Empty
      // arrays therefore belong in the respondent denominator even though
      // they add no category count.
      if(multi&&(isMissing(value)||!Array.isArray(value)))continue;
      if(!multi&&(isMissing(value)||isConfiguredMissing(variable,value)))continue;
      var rowWeight=weight(source[i]),unique=[],rowSeen=dictionary();
      for(var j=0;j<values.length;j++){
        var item=values[j];if(isMissing(item)||isConfiguredMissing(variable,item))continue;var key=""+item;
        if(rowSeen[key])continue;rowSeen[key]=1;unique.push(key);
      }
      // [] is an answered multiselect with no options; [missing-code] is an
      // explicitly excluded response and does not enter the denominator.
      if(multi&&values.length&&!unique.length)continue;
      validRaw++;validWeight+=rowWeight;sumW2+=rowWeight*rowWeight;
      for(j=0;j<unique.length;j++){key=unique[j];counts[key]=(counts[key]||0)+rowWeight;raw[key]=(raw[key]||0)+1;seen[key]=1;}
      respondents.push({keys:unique,w:rowWeight});
    }
    var keys=[];
    for(i=0;i<order.length;i++)if(seen[""+order[i]])keys.push(""+order[i]);
    Object.keys(seen).forEach(function(key){if(keys.indexOf(key)<0)keys.push(key);});
    return {keys:keys,counts:counts,raw:raw,n:validRaw,total:validWeight,sumW2:sumW2,multi:multi,respondents:respondents};
  }

  function displayedCategories(variable,cat){
    var meta=metaFor(variable)||{},items=cat.keys.map(function(key){return{key:key,label:labelFor(variable,key),count:cat.counts[key]||0,raw:cat.raw[key]||0,other:false};});
    if(!(meta.order&&meta.order.length))items.sort(function(a,b){return b.count-a.count;});
    var configured=Math.max(2,+(CONFIG.maxCategories||12));
    var maximum=compact?Math.min(configured,7):configured;
    if(items.length<=maximum)return items;
    var ranked=items.slice().sort(function(a,b){return b.count-a.count;}),kept=Object.create(null);
    ranked.slice(0,maximum-1).forEach(function(item){kept[item.key]=1;});
    var shown=[],omitted=[];items.forEach(function(item){(kept[item.key]?shown:omitted).push(item);});
    var otherCount=0,otherRaw=0;
    if(cat.multi){var omittedKeys=dictionary();omitted.forEach(function(item){omittedKeys[item.key]=1;});cat.respondents.forEach(function(row){for(var i=0;i<row.keys.length;i++)if(omittedKeys[row.keys[i]]){otherCount+=row.w;otherRaw++;break;}});}
    else{otherCount=omitted.reduce(function(sum,item){return sum+item.count;},0);otherRaw=omitted.reduce(function(sum,item){return sum+item.raw;},0);}
    shown.push({key:"__surveye_other__",label:(cat.multi?tr("anyOtherResponse","Any other response"):tr("other","Other"))+" ("+localNumber(omitted.length)+" "+(cat.multi?tr("options","options"):tr("categories","categories"))+")",count:otherCount,raw:otherRaw,other:true});
    return shown;
  }

  function splitItems(variable,cat){
    var meta=metaFor(variable)||{},keys=cat.keys.slice();
    if(meta.kind==="yesno"&&meta.order&&meta.order.length){
      meta.order.forEach(function(key){key=""+key;if(keys.indexOf(key)<0&&!isSpecial(variable,key))keys.push(key);});
    }
    return keys.map(function(key,index){return{key:key,label:labelFor(variable,key),count:cat.counts[key]||0,raw:cat.raw[key]||0,color:colorFor(variable,key,index)};});
  }

  function buildSplit(canvas,variable,items,total,leadIndex){
    // Split composition cards deliberately never report confidence intervals:
    // neither a stacked-bar whisker nor interval text has an unambiguous
    // interpretation here.  Explicit bars() overrides use buildBar instead.
    items.forEach(function(item){delete item.interval;});
    splitSummary(variable,items,total,leadIndex);
    var message=summaryText(items,total);describe(canvas,message);
    // A whisker on a stacked composition bar appears to measure the whole
    // stack and is therefore ambiguous, so the bar and its text stay CI-free.
    state.charts[canvas.id]=new Chart(canvas,{type:"bar",data:{labels:[""],datasets:items.map(function(item){return{label:item.label,data:[total?100*item.count/total:0],backgroundColor:item.color,borderColor:css("--card","#fff"),borderWidth:1,borderSkipped:false,barThickness:18};})},options:{indexAxis:"y",maintainAspectRatio:false,scales:{x:horizontalScale({display:false,stacked:true,min:0,max:100}),y:{display:false,stacked:true}},plugins:{legend:{display:false},tooltip:{callbacks:{label:function(ctx){var item=items[ctx.datasetIndex];return " "+item.label+": "+pct(ctx.parsed.x)+" ("+tr("rawN","raw n")+"="+localNumber(item.raw)+")"+(item.interval?"; "+confidenceText(item.interval):"");}}}}}});
  }

  function buildYesNo(canvas,variable){
    var source=rows(),cat=categorical(variable,source,false);destroy(canvas.id);clearSummary(variable);
    if(!cat.n||(weightsActive()&&cat.total<=0)){var message=cat.n?tr("noPositiveWeight","No positive weight for this selection"):tr("noValidResponses","No valid responses for this selection");setEmpty(canvas,true,message);statistic(variable,"");clearSummary(variable,message);describe(canvas,message);return;}
    setEmpty(canvas,false);statistic(variable,tr("validN","valid n")+"="+localNumber(cat.n));
    var items=splitItems(variable,cat),lead=0,best=-1;items.forEach(function(item,index){if(!isSpecial(variable,item.key)&&item.count>0&&item.count>best){best=item.count;lead=index;}});if(best<0)items.forEach(function(item,index){if(item.count>best){best=item.count;lead=index;}});
    buildSplit(canvas,variable,items,cat.total,lead);
  }

  function buildDonut(canvas,variable){
    var source=rows(),cat=categorical(variable,source,false);destroy(canvas.id);clearSummary(variable);
    if(!cat.n||(weightsActive()&&cat.total<=0)){var message=cat.n?tr("noPositiveWeight","No positive weight for this selection"):tr("noValidResponses","No valid responses for this selection");setEmpty(canvas,true,message);statistic(variable,"");clearSummary(variable,message);describe(canvas,message);return;}
    setEmpty(canvas,false);statistic(variable,tr("validN","valid n")+"="+localNumber(cat.n));
    var display=displayedCategories(variable,cat),labels=display.map(function(item){return item.label;}),values=display.map(function(item){return item.count;});
    clearSummary(variable,summaryText(display,cat.total));describe(canvas,summaryText(display,cat.total));
    state.charts[canvas.id]=new Chart(canvas,{type:"doughnut",data:{labels:labels.map(function(label){return shortened(label,38);}),datasets:[{data:values,backgroundColor:display.map(function(item,i){return item.other?muted:colorFor(variable,item.key,i);}),borderColor:css("--card","#fff"),borderWidth:2}]},options:{maintainAspectRatio:false,cutout:"58%",plugins:{legend:{position:"bottom",labels:{boxWidth:11,font:{size:10}}},tooltip:{callbacks:{label:function(ctx){var item=display[ctx.dataIndex],share=cat.total?100*item.count/cat.total:0;return " "+item.label+": "+pct(share)+" ("+tr("rawN","raw n")+"="+localNumber(item.raw)+")";}}}}}});
  }

  function buildBar(canvas,variable,multi){
    var source=rows(),cat=categorical(variable,source,multi);destroy(canvas.id);clearSummary(variable);
    if(multi)canvas.dataset.respondentN=""+cat.n;else delete canvas.dataset.respondentN;
    if(!cat.n||!cat.keys.length||(weightsActive()&&cat.total<=0)){var message=cat.n?tr("noPositiveWeight","No positive weight for this selection"):tr("noValidResponses","No valid responses for this selection");setEmpty(canvas,true,message);statistic(variable,"");clearSummary(variable,message);describe(canvas,message);return;}
    setEmpty(canvas,false);statistic(variable,(multi?tr("respondentsN","respondents n"):tr("validN","valid n"))+"="+localNumber(cat.n));
    var display=displayedCategories(variable,cat),labels=display.map(function(item){return item.label;}),values=display.map(function(item){item.interval=confidenceInterval(cat,item.count);return cat.total?100*item.count/cat.total:0;}),intervals=display.map(function(item){return item.interval;});
    var height=compact?Math.max(126,28+display.length*23):Math.max(255,68+display.length*28);canvas.parentElement.style.setProperty("--chart-height",Math.min(height,compact?236:620)+"px");
    var message=summaryText(display,cat.total);clearSummary(variable,message);describe(canvas,message);
    var primary=rgba(css("--chart-blue","#009fda"),.9),gridColor=css("--chart-grid","rgba(90,100,115,.13)");
    state.charts[canvas.id]=new Chart(canvas,{type:"bar",plugins:[barConfidenceIntervals,barValueLabels],data:{labels:labels,datasets:[{data:values,backgroundColor:display.map(function(item){return item.other||isSpecial(variable,item.key)?muted:primary;}),borderRadius:5,borderSkipped:false,maxBarThickness:compact?18:25}]},options:{indexAxis:"y",maintainAspectRatio:false,layout:{padding:rtl?{left:2}:{right:2}},scales:{x:horizontalScale({display:!compact,beginAtZero:true,max:compact?105:110,ticks:{maxTicksLimit:3,font:{size:9},callback:function(v){return v<=100?pct(v):"";}},grid:{color:gridColor,drawTicks:false},border:{display:false}}),y:{position:rtl?"right":"left",ticks:{autoSkip:false,font:{size:compact?9:10},padding:3,callback:function(v){return wrapped(this.getLabelForValue(v),compact?22:30,2);}},grid:{display:false},border:{display:false}}},plugins:{barConfidenceIntervals:{intervals:intervals},barValueLabels:{display:true,intervals:intervals},legend:{display:false},tooltip:{callbacks:{title:function(items){return labels[items[0].dataIndex];},label:function(ctx){var item=display[ctx.dataIndex];return " "+pct(ctx.parsed.x)+" ("+tr("rawN","raw n")+"="+localNumber(item.raw)+")"+(item.interval?"; "+confidenceText(item.interval):"");}}}}}});
  }

  function sharedCategoryLabel(members,key){
    for(var i=0;i<members.length;i++){var meta=metaFor(members[i]);if(meta&&meta.labels&&owns(meta.labels,key))return meta.labels[key];}
    return key;
  }
  function sharedCategorySpecial(members,key){for(var i=0;i<members.length;i++)if(isSpecial(members[i],key))return true;return false;}
  function familyCategoryKeys(members,categories){
    var keys=[],seen=dictionary();
    function add(member,key){key=""+key;if(!key||seen[key]||isConfiguredMissing(member,key))return;seen[key]=1;keys.push(key);}
    members.forEach(function(member){var meta=metaFor(member)||{};(meta.order||[]).forEach(function(key){add(member,key);});var cat=categories[member];if(cat)(cat.keys||[]).forEach(function(key){add(member,key);});});return keys;
  }
  function buildFamily(canvas){
    var key=panelKey(canvas),record=configuredRecord(CONFIG.families,key)||{},members=configuredMembers(canvas,record),source=rows(),categories=dictionary();destroy(canvas.id);clearSummary(key);
    if(!members.length){var missingMembers=tr("noFamilyMembers","No variables were configured for this grouped figure");setEmpty(canvas,true,missingMembers);statistic(key,"");clearSummary(key,missingMembers);describe(canvas,missingMembers);return;}
    var anyValid=false;members.forEach(function(member){var cat=categorical(member,source,false);categories[member]=cat;if(cat.n&&(!weightsActive()||cat.total>0))anyValid=true;});
    if(!anyValid){var noFamilyData=tr("noValidResponses","No valid responses for this selection");setEmpty(canvas,true,noFamilyData);statistic(key,"");clearSummary(key,noFamilyData);describe(canvas,noFamilyData);return;}
    var categoryKeys=familyCategoryKeys(members,categories);if(!categoryKeys.length){var noFamilyCategories=tr("noValidResponses","No valid responses for this selection");setEmpty(canvas,true,noFamilyCategories);statistic(key,"");clearSummary(key,noFamilyCategories);describe(canvas,noFamilyCategories);return;}
    setEmpty(canvas,false);statistic(key,localNumber(members.length)+" "+tr(members.length===1?"variable":"variables",members.length===1?"variable":"variables"));
    var labels=members.map(function(member,index){return memberAxisLabel(record,member,index);}),details=[];
    var datasets=categoryKeys.map(function(categoryKey,categoryIndex){
      var categoryLabel=sharedCategoryLabel(members,categoryKey),color=sharedCategorySpecial(members,categoryKey)?muted:colorFor(members[0],categoryKey,categoryIndex),values=[];
      members.forEach(function(member){var cat=categories[member],count=cat.counts[categoryKey]||0,raw=cat.raw[categoryKey]||0,share=cat.total>0?100*count/cat.total:0;values.push(share);details.push({member:member,memberLabel:configuredMemberLabel(record,member,members.indexOf(member)),category:categoryLabel,share:share,raw:raw,n:cat.n,total:cat.total});});
      return{label:categoryLabel,data:values,backgroundColor:color,borderColor:css("--card","#fff"),borderWidth:1,borderSkipped:false,barThickness:compact?17:22};
    });
    var message=details.map(function(item){return item.memberLabel+" ["+item.member+"] — "+item.category+" "+pct(item.share)+"; "+tr("validN","valid n")+"="+localNumber(item.n)+(weightsActive()?"; "+tr("weightedBase","weighted base")+"="+fmt(item.total,1):"");}).join("; ");clearSummary(key,message);describe(canvas,message);canvas.dataset.memberCount=""+members.length;
    if(canvas.parentElement)canvas.parentElement.style.setProperty("--chart-height",Math.max(compact?145:230,(compact?57:92)+members.length*(compact?32:42))+"px");
    var primaryIndexes=members.map(function(member){var meta=metaFor(member)||{},preferred=(meta.affirmative||[])[0],index=preferred===undefined?-1:categoryKeys.indexOf(""+preferred);if(index>=0)return index;var best=-1,bestValue=-1;categoryKeys.forEach(function(categoryKey,categoryIndex){if(sharedCategorySpecial(members,categoryKey))return;var cat=categories[member],share=cat.total>0?(cat.counts[categoryKey]||0)/cat.total:0;if(share>bestValue){bestValue=share;best=categoryIndex;}});return best;});
    state.charts[canvas.id]=new Chart(canvas,{type:"bar",plugins:[familyPrimaryValueLabels],data:{labels:labels,datasets:datasets},options:{indexAxis:"y",maintainAspectRatio:false,scales:{x:horizontalScale({display:!compact,stacked:true,min:0,max:100,ticks:{maxTicksLimit:5,font:{size:9},callback:function(v){return pct(v);}},grid:{color:css("--chart-grid","rgba(90,100,115,.13)"),drawTicks:false},border:{display:false}}),y:{position:rtl?"right":"left",stacked:true,ticks:{autoSkip:false,font:{size:compact?9:10},padding:5},grid:{display:false},border:{display:false}}},plugins:{familyPrimaryValueLabels:{indexes:primaryIndexes},legend:{display:true,position:"bottom",labels:{boxWidth:10,boxHeight:10,font:{size:9}}},tooltip:{callbacks:{title:function(items){var index=items[0].dataIndex,member=members[index];return configuredMemberLabel(record,member,index)+" ["+member+"]";},label:function(ctx){var member=members[ctx.dataIndex],cat=categories[member],categoryKey=categoryKeys[ctx.datasetIndex],raw=cat.raw[categoryKey]||0,text=" "+ctx.dataset.label+": "+pct(ctx.parsed.x)+" ("+tr("rawN","raw n")+"="+localNumber(raw)+"; "+tr("validN","valid n")+"="+localNumber(cat.n)+")";if(weightsActive())text+="; "+tr("weightedBase","weighted base")+"="+fmt(cat.total,1);return text;}}}}}});
  }

  function sameCategoryValue(left,right){if(""+left===""+right)return true;var a=+left,b=+right;return isFinite(a)&&isFinite(b)&&a===b;}
  function comparisonLevelRecords(record,by,source){
    var configured=configuredList(record&&record.levels),levels=[],seen=dictionary(),meta=metaFor(by)||{};
    function add(value,label,explicit){if(value===null||value===undefined)return;value=""+value;if(!value||seen[value]||isConfiguredMissing(by,value)||(!explicit&&isSpecial(by,value)))return;seen[value]=1;levels.push({value:value,label:label||labelFor(by,value)});}
    configured.forEach(function(level){if(level&&typeof level==="object")add(level.value!==undefined?level.value:level.code,level.label,true);else add(level,null,true);});
    if(!levels.length)(meta.order||[]).forEach(function(level){add(level,null,false);});
    if(!levels.length)source.forEach(function(row){var value=row[by];if(!isMissing(value))add(value,null,false);});return levels;
  }
  function affirmativeCategory(variable,cat){
    var meta=metaFor(variable)||{},candidates=(meta.affirmative||[]).slice();if(!candidates.length)(meta.order||[]).forEach(function(key){key=""+key;if(!isSpecial(variable,key)&&!isConfiguredMissing(variable,key))candidates.push(key);});
    if(!candidates.length)(cat.keys||[]).forEach(function(key){if(!isSpecial(variable,key)&&!isConfiguredMissing(variable,key))candidates.push(key);});return candidates.length?""+candidates[0]:null;
  }
  function buildComparison(canvas){
    var key=panelKey(canvas),record=configuredRecord(CONFIG.comparisons,key)||{},members=configuredMembers(canvas,record),by=record.by||canvas.getAttribute("data-by"),source=rows();destroy(canvas.id);clearSummary(key);
    if(!members.length||!by){var missingComparison=tr("comparisonNotConfigured","This comparison is missing its variables or grouping variable");setEmpty(canvas,true,missingComparison);statistic(key,"");clearSummary(key,missingComparison);describe(canvas,missingComparison);return;}
    var levels=comparisonLevelRecords(record,by,source),labels=members.map(function(member,index){return memberAxisLabel(record,member,index);}),matrix=[],anyValid=false;
    var comparisonPalette=[css("--chart-purple","#7c5ba6"),css("--chart-comparison-neutral","#b8c2cf"),css("--chart-blue","#009fda"),css("--chart-green","#00a887"),css("--chart-gold","#fdb714")];
    var datasets=levels.map(function(level,levelIndex){var details=[],values=[];members.forEach(function(member){var subset=source.filter(function(row){var value=row[by];return !isMissing(value)&&!isConfiguredMissing(by,value)&&sameCategoryValue(value,level.value);}),cat=categorical(member,subset,false),affirmative=affirmativeCategory(member,cat),count=affirmative===null?0:(cat.counts[affirmative]||0),raw=affirmative===null?0:(cat.raw[affirmative]||0),share=cat.total>0?100*count/cat.total:null;if(share!==null)anyValid=true;values.push(share);details.push({member:member,level:level.value,levelLabel:level.label,affirmative:affirmative,share:share,raw:raw,n:cat.n,total:cat.total});});matrix.push(details);var color=comparisonPalette[levelIndex%comparisonPalette.length];return{label:level.label,data:values,backgroundColor:rgba(color,.9),borderColor:color,borderWidth:1,borderSkipped:false,borderRadius:4,maxBarThickness:compact?14:19};});
    if(!anyValid){var noComparisonData=tr("noValidResponses","No valid responses for this selection");setEmpty(canvas,true,noComparisonData);statistic(key,"");clearSummary(key,noComparisonData);describe(canvas,noComparisonData);return;}
    setEmpty(canvas,false);statistic(key,localNumber(members.length)+" "+tr("variables","variables")+" · "+localNumber(levels.length)+" "+tr(levels.length===1?"group":"groups",levels.length===1?"group":"groups"));
    var summary=[];matrix.forEach(function(details){details.forEach(function(item){summary.push(configuredMemberLabel(record,item.member,members.indexOf(item.member))+" ["+item.member+"] — "+item.levelLabel+": "+(item.share===null?tr("notAvailable","n/a"):pct(item.share))+"; "+tr("validN","valid n")+"="+localNumber(item.n)+(weightsActive()?"; "+tr("weightedBase","weighted base")+"="+fmt(item.total,1):""));});});var message=summary.join("; ");clearSummary(key,message);describe(canvas,message);canvas.dataset.memberCount=""+members.length;canvas.dataset.comparisonLevels=""+levels.length;
    if(canvas.parentElement)canvas.parentElement.style.setProperty("--chart-height",Math.max(compact?155:240,(compact?65:98)+members.length*Math.max(compact?30:40,levels.length*(compact?15:20)))+"px");
    state.charts[canvas.id]=new Chart(canvas,{type:"bar",plugins:[groupedValueLabels],data:{labels:labels,datasets:datasets},options:{indexAxis:"y",maintainAspectRatio:false,layout:{padding:rtl?{left:4}:{right:4}},scales:{x:horizontalScale({display:!compact,beginAtZero:true,max:compact?112:108,ticks:{maxTicksLimit:5,font:{size:9},callback:function(v){return v<=100?pct(v):"";}},grid:{color:css("--chart-grid","rgba(90,100,115,.13)"),drawTicks:false},border:{display:false}}),y:{position:rtl?"right":"left",ticks:{autoSkip:false,font:{size:compact?9:10},padding:5},grid:{display:false},border:{display:false}}},plugins:{groupedValueLabels:{display:true},legend:{display:levels.length>1,position:"bottom",labels:{boxWidth:10,boxHeight:10,font:{size:9}}},tooltip:{callbacks:{title:function(items){var index=items[0].dataIndex,member=members[index];return configuredMemberLabel(record,member,index)+" ["+member+"]";},label:function(ctx){var item=matrix[ctx.datasetIndex][ctx.dataIndex],answer=item.affirmative===null?tr("affirmativeResponse","Affirmative response"):labelFor(item.member,item.affirmative),text=" "+item.levelLabel+" · "+answer+": "+(item.share===null?tr("notAvailable","n/a"):pct(item.share))+" ("+tr("rawN","raw n")+"="+localNumber(item.raw)+"; "+tr("validN","valid n")+"="+localNumber(item.n)+")";if(weightsActive())text+="; "+tr("weightedBase","weighted base")+"="+fmt(item.total,1);return text;}}}}}});
  }

  function numericValues(variable,source){
    var values=[],meta=metaFor(variable)||{};
    for(var i=0;i<source.length;i++){
      var raw=source[i][variable],number=+raw;
      // Negative numeric special responses (for example Survey Solutions -4
      // "too many to count" and -9 "don't know") are response codes, not
      // measurements.  Keep nonnegative shortcuts such as a genuine 100 in
      // scope, and do not discard legitimate negative measures unless the
      // questionnaire explicitly marks them as special.
      if(isMissing(raw)||!isFinite(number)||isConfiguredMissing(variable,raw)
          ||(number<0&&(isSpecial(variable,raw)||meta.nonnegative===true)))continue;
      values.push({value:convertedNumber(variable,number),w:weight(source[i])});
    }
    return values;
  }
  function weightedStd(points,mean,totalWeight){
    if(points.length<2||mean===null)return null;var sum=0,positive=0;points.forEach(function(point){var w=weightsActive()?Math.max(0,point.w):1,d=point.value-mean;if(w>0)positive++;sum+=w*d*d;});
    var type=(CONFIG.weightType||"").toLowerCase(),variance;
    if(!weightsActive())variance=sum/(points.length-1);
    else if(type==="fweight")variance=totalWeight>1?sum/(totalWeight-1):null;
    else variance=positive>1&&totalWeight>0?positive*sum/(totalWeight*(positive-1)):null;
    return variance!==null&&variance>=0?Math.sqrt(variance):null;
  }
  function renderNumericStats(variable,stats){
    var root=document.getElementById("stats-"+safeId(variable));if(!root)return;root.textContent="";
    if(!stats){var empty=document.createElement("div");empty.className="stats-empty";empty.textContent=tr("noNumericValues","No numeric values for this selection");root.appendChild(empty);return;}
    var table=document.createElement("table");table.className="stats-table";table.setAttribute("aria-label",tr("summaryStatistics","Summary statistics"));var body=document.createElement("tbody");
    var rows=[[tr("validRawN","Valid raw n"),localNumber(stats.n),tr("missingExcluded","Missing/excluded"),localNumber(stats.missing)],[tr("mean","Mean"),metricFmt(variable,stats.mean,2),tr("stdDev","Std. dev."),metricFmt(variable,stats.sd,2)],[tr("minimum","Minimum"),metricFmt(variable,stats.min,2),tr("maximum","Maximum"),metricFmt(variable,stats.max,2)],["P25",metricFmt(variable,stats.q1,2),"P75",metricFmt(variable,stats.q3,2)],[tr("median","Median"),metricFmt(variable,stats.median,2),tr("meanPlusThreeSd","Mean + 3 SD"),stats.meanPlusThreeSd===null?"—":metricFmt(variable,stats.meanPlusThreeSd,2)]];
    rows.forEach(function(values){var tr=document.createElement("tr");for(var i=0;i<values.length;i++){var cell=document.createElement(i%2===0?"th":"td");cell.textContent=values[i];if(i%2===0)cell.setAttribute("scope","row");tr.appendChild(cell);}body.appendChild(tr);});table.appendChild(body);root.appendChild(table);
    if(weightsActive()){var note=document.createElement("div");note.className="stats-note";note.textContent=tr("weightedSum","weighted sum")+" "+fmt(stats.totalWeight,1)+" · "+tr("descriptiveWeighted","descriptive weighted statistics");root.appendChild(note);}
  }
  function numericStatistics(points,sourceLength){
    if(!points.length)return null;var totalWeight=points.reduce(function(sum,point){return sum+point.w;},0);if(weightsActive()&&totalWeight<=0)return null;
    var sorted=points.map(function(point){return point.value;}).sort(function(a,b){return a-b;}),mean=weightedMean(points),q1=weightedQuantile(points,.25),medianValue=weightedQuantile(points,.5),q3=weightedQuantile(points,.75),sd=weightedStd(points,mean,totalWeight),threshold=meanPlusThreeSd({mean:mean,sd:sd},sorted[0],sorted[sorted.length-1]);
    return{n:points.length,missing:Math.max(0,sourceLength-points.length),mean:mean,sd:sd,min:sorted[0],max:sorted[sorted.length-1],q1:q1,q3:q3,median:medianValue,meanPlusThreeSd:threshold,totalWeight:totalWeight};
  }
  function refreshNumericStats(source,canvases){
    source=source||rows();var refreshed=dictionary(),explicit=canvases!==undefined&&canvases!==null,nodes=explicit?canvases:document.querySelectorAll("canvas.chart");
    Array.prototype.forEach.call(nodes,function(canvas){
      var kind=canvas.getAttribute("data-kind"),variable=canvas.getAttribute("data-variable");
      if((kind!=="hist"&&kind!=="discrete")||!variable||owns(refreshed,variable))return;
      if(!explicit){var panel=canvas.closest(".panel"),pane=panel&&panel.querySelector('[data-panel-view-pane="stats"]');if(!pane||!pane.classList.contains("is-active"))return;}
      var summary=numericStatistics(numericValues(variable,source),source.length);refreshed[variable]=summary;renderNumericStats(variable,summary);
    });
    return refreshed;
  }
  function niceNumericStep(raw,integerOnly,roundToNearest){
    if(!isFinite(raw)||raw<=0)return integerOnly?1:1;var exponent=Math.floor(Math.log(raw)/Math.LN10),power=Math.pow(10,exponent),fraction=raw/power,choices=integerOnly?[1,2,5,10]:[1,2,2.5,5,10],chosen=choices[choices.length-1];
    if(roundToNearest){var distance=Infinity;for(var i=0;i<choices.length;i++){var candidate=choices[i],candidateDistance=Math.abs(Math.log(candidate/fraction));if(candidateDistance<distance){distance=candidateDistance;chosen=candidate;}}}
    else{for(var j=0;j<choices.length;j++){if(fraction<=choices[j]+1e-12){chosen=choices[j];break;}}}
    var step=chosen*power;return integerOnly?Math.max(1,Math.round(step)):step;
  }
  function stepDigits(step){
    var scaled=Math.abs(step),digits=0;while(digits<6&&Math.abs(scaled-Math.round(scaled))>1e-8){scaled*=10;digits++;}return digits;
  }
  function axisNumber(value,step){return fmt(+value,stepDigits(step));}
  function discreteDistributionPlan(points,stats,compactMode){
    if(!points||!points.length||!stats)return null;var rounded=points.map(function(point){return{value:Math.round(point.value),w:point.w};}),observed=rounded.map(function(point){return point.value;}),observedMin=Math.min.apply(null,observed),observedMax=Math.max.apply(null,observed),exactLimit=compactMode?36:60,displayMin=observedMin,displayMax=observedMax,support=displayMax-displayMin+1,maxBins=compactMode?36:56,binWidth=support<=exactLimit?1:niceNumericStep(support/maxBins,true,false);
    if(binWidth>1){displayMin=Math.floor(displayMin/binWidth)*binWidth;displayMax=Math.ceil((displayMax+1)/binWidth)*binWidth-1;support=displayMax-displayMin+1;}
    var binCount=Math.max(1,Math.round(support/binWidth)),bins=[],weighted=new Array(binCount).fill(0),raw=new Array(binCount).fill(0);
    for(var b=0;b<binCount;b++){var lower=displayMin+b*binWidth,upper=lower+binWidth-1;bins.push({x:(lower+upper)/2,lower:lower,upper:upper,y:0,raw:0});}
    rounded.forEach(function(point){var index=Math.floor((point.value-displayMin)/binWidth);index=Math.max(0,Math.min(binCount-1,index));weighted[index]+=point.w;raw[index]++;});
    for(var k=0;k<bins.length;k++){bins[k].y=weighted[k];bins[k].raw=raw[k];}
    var span=Math.max(0,displayMax-displayMin),tickStep=span<=12?1:niceNumericStep(span/(compactMode?8:11),true,false);if(binWidth>tickStep)tickStep=binWidth;
    return{bins:bins,minimum:displayMin,maximum:displayMax,binWidth:binWidth,tickStep:tickStep,exact:binWidth===1};
  }
  function buildDiscrete(canvas,variable){
    var source=rows(),points=numericValues(variable,source),fullPoints=numericValues(variable,DATA);destroy(canvas.id);clearSummary(variable);var stats=numericStatistics(points,source.length);renderNumericStats(variable,stats);
    if(!points.length||(weightsActive()&&(!stats||stats.totalWeight<=0))){var noDataMessage=weightsActive()&&points.length?tr("noPositiveWeight","No positive weight for this selection"):tr("noValidNumeric","No valid numeric values for this selection");setEmpty(canvas,true,noDataMessage);statistic(variable,"");clearSummary(variable,noDataMessage);describe(canvas,noDataMessage);return;}
    if(!fullPoints.length)fullPoints=points.slice();var allInteger=fullPoints.every(function(point){return isFinite(point.value)&&Math.abs(point.value-Math.round(point.value))<1e-9;});
    if(!allInteger){var integerMessage=tr("discreteNeedsIntegers","This discrete chart requires integer values; use a histogram for continuous values");setEmpty(canvas,true,integerMessage);statistic(variable,tr("validN","valid n")+"="+localNumber(points.length));clearSummary(variable,integerMessage);describe(canvas,integerMessage);return;}
    var plan=discreteDistributionPlan(points,stats,compact);if(!plan){var rangeMessage=tr("discreteRangeTooWide","The integer distribution could not be displayed");setEmpty(canvas,true,rangeMessage);statistic(variable,tr("validN","valid n")+"="+localNumber(points.length));clearSummary(variable,rangeMessage);describe(canvas,rangeMessage);return;}
    var zeroGaps=0;plan.bins.forEach(function(bin){if(bin.y===0)zeroGaps++;});
    setEmpty(canvas,false);var statisticText=(weightsActive()?tr("weightedMedian","weighted median"):tr("median","median"))+" "+metricFmt(variable,stats.median,2)+" · "+(weightsActive()?tr("weightedMean","weighted mean"):tr("mean","mean"))+" "+metricFmt(variable,stats.mean,2)+" · "+tr("rawN","raw n")+"="+localNumber(stats.n);statistic(variable,statisticText);
    var nonzero=[];plan.bins.forEach(function(bin){if(bin.y>0){var label=plan.exact?axisNumber(bin.x,1):axisNumber(bin.lower,plan.binWidth)+"–"+axisNumber(bin.upper,plan.binWidth);nonzero.push(label+": "+fmt(bin.y,weightsActive()?1:0)+" ("+tr("rawN","raw n")+"="+localNumber(bin.raw)+")");}});var message=statisticText+"; "+tr("integerRange","integer range")+" "+plan.minimum+"–"+plan.maximum+(plan.exact?"":"; "+tr("integerBinWidth","integer bin width")+" "+plan.binWidth)+"; "+localNumber(zeroGaps)+" "+tr("zeroFrequencyValues",plan.exact?"zero-frequency values":"zero-frequency bins")+"; "+nonzero.join("; ");clearSummary(variable,message);describe(canvas,message);canvas.dataset.discreteMin=""+plan.minimum;canvas.dataset.discreteMax=""+plan.maximum;canvas.dataset.discreteBinWidth=""+plan.binWidth;canvas.dataset.zeroFrequencyGaps=""+zeroGaps;var guide=meanPlusThreeSd(stats,plan.minimum,plan.maximum);canvas.dataset.threeSdGuide=guide===null?"":""+guide;
    var discreteColor=rgba(css("--chart-purple","#7c5ba6"),.9),xPadding=plan.binWidth/2;
    state.charts[canvas.id]=new Chart(canvas,{type:"bar",plugins:[threeSdGuide],data:{datasets:[{data:plan.bins,parsing:{xAxisKey:"x",yAxisKey:"y"},backgroundColor:discreteColor,borderRadius:plan.binWidth===1?2:3,borderSkipped:false,categoryPercentage:.9,barPercentage:.96,maxBarThickness:plan.binWidth===1?(compact?14:20):(compact?28:36)}]},options:{maintainAspectRatio:false,layout:{padding:{top:22}},scales:{x:{type:"linear",min:plan.minimum-xPadding,max:plan.maximum+xPadding,offset:false,ticks:{stepSize:plan.tickStep,maxTicksLimit:compact?9:13,includeBounds:false,maxRotation:0,minRotation:0,precision:0,font:{size:9},callback:function(value){return axisNumber(value,1);}},grid:{display:false},border:{display:false},title:{display:!compact,text:(plan.exact?tr("integerValue","Integer value"):tr("integerValueBins","Integer value (grouped)"))+(currencyCode(variable)?" ("+currencyCode(variable)+")":"")}},y:{beginAtZero:true,ticks:{maxTicksLimit:compact?4:6,font:{size:9},precision:weightsActive()?undefined:0},border:{display:false},title:{display:!compact,text:weightsActive()?tr("weightedFrequency","weighted frequency"):tr("observations","observations")},grid:{color:css("--chart-grid","rgba(90,100,115,.13)")}}},plugins:{threeSdGuide:{mean:stats.mean,sd:stats.sd,minimum:plan.minimum,maximum:plan.maximum,variable:variable},legend:{display:false},tooltip:{callbacks:{title:function(items){var datum=items[0].raw;return plan.exact?tr("value","Value")+" "+metricFmt(variable,datum.x,0):tr("values","Values")+" "+metricFmt(variable,datum.lower,0)+"–"+metricFmt(variable,datum.upper,0);},label:function(ctx){var datum=ctx.raw;return " "+fmt(datum.y,weightsActive()?1:0)+" "+(weightsActive()?tr("weightedFrequency","weighted frequency"):tr("observations","observations"))+"; "+tr("rawN","raw n")+"="+localNumber(datum.raw);}}}}}});
  }
  function buildHistogram(canvas,variable){
    var source=rows(),points=numericValues(variable,source);destroy(canvas.id);clearSummary(variable);
    var totalPointWeight=points.reduce(function(sum,point){return sum+point.w;},0);
    if(!points.length||(weightsActive()&&totalPointWeight<=0)){var noDataMessage=weightsActive()&&points.length?tr("noPositiveWeight","No positive weight for this selection"):tr("noValidNumeric","No valid numeric values for this selection");renderNumericStats(variable,null);setEmpty(canvas,true,noDataMessage);statistic(variable,"");clearSummary(variable,noDataMessage);describe(canvas,noDataMessage);return;}
    var stats=numericStatistics(points,source.length),sorted=points.map(function(p){return p.value;}).sort(function(a,b){return a-b;}),med=stats.median,mean=stats.mean,q1=stats.q1,q3=stats.q3,iqr=q3-q1,low=stats.min,high=stats.max;renderNumericStats(variable,stats);
    if(points.length<2){var oneMessage=tr("oneValidValue","One valid value; open Stats for its summary");setEmpty(canvas,true,oneMessage);statistic(variable,tr("validN","valid n")+"=1 · "+tr("value","value")+" "+metricFmt(variable,points[0].value,2));clearSummary(variable,oneMessage);describe(canvas,oneMessage);return;}
    setEmpty(canvas,false);if(high<=low){low=stats.min-.5;high=stats.max+.5;}
    var span=high-low,width=iqr>0?2*iqr/Math.cbrt(points.length):span/Math.sqrt(points.length),minBins=compact?6:8,maxBins=compact?14:28,proposedBins=Math.max(minBins,Math.min(maxBins,Math.round(span/(width||1))));if(!isFinite(proposedBins)||proposedBins<1)proposedBins=compact?10:12;
    var step=niceNumericStep(span/proposedBins,false,true),alignedLow=Math.floor(low/step)*step,alignedHigh=Math.ceil(high/step)*step,bins=Math.max(1,Math.round((alignedHigh-alignedLow)/step));
    if(bins>maxBins){step=niceNumericStep(span/maxBins,false,false);alignedLow=Math.floor(low/step)*step;alignedHigh=Math.ceil(high/step)*step;bins=Math.max(1,Math.round((alignedHigh-alignedLow)/step));}
    if(!(alignedHigh>alignedLow)){alignedLow=low;alignedHigh=high;step=alignedHigh-alignedLow;bins=1;}
    var counts=new Array(bins).fill(0),raw=new Array(bins).fill(0),histogramData=[];
    points.forEach(function(p){var index=Math.floor((p.value-alignedLow)/step);if(index>=bins)index=bins-1;if(index<0)index=0;counts[index]+=p.w;raw[index]++;});
    for(var b=0;b<bins;b++){var binLow=alignedLow+b*step,binHigh=binLow+step;histogramData.push({x:binLow+step/2,y:counts[b],lower:binLow,upper:binHigh,raw:raw[b]});}
    low=alignedLow;high=alignedHigh;var guide=meanPlusThreeSd(stats,low,high);canvas.dataset.threeSdGuide=guide===null?"":""+guide;
    var statisticText=(weightsActive()?tr("weightedMedian","weighted median"):tr("median","median"))+" "+metricFmt(variable,med,2)+" · "+(weightsActive()?tr("weightedMean","weighted mean"):tr("mean","mean"))+" "+metricFmt(variable,mean,2)+" · "+tr("rawN","raw n")+"="+localNumber(sorted.length);
    statistic(variable,statisticText);clearSummary(variable,statisticText);describe(canvas,statisticText);
    var tickStep=niceNumericStep((high-low)/(compact?6:9),false,false);
    state.charts[canvas.id]=new Chart(canvas,{type:"bar",plugins:[threeSdGuide],data:{datasets:[{data:histogramData,parsing:{xAxisKey:"x",yAxisKey:"y"},backgroundColor:rgba(css("--chart-purple","#7c5ba6"),.9),borderRadius:2,categoryPercentage:1,barPercentage:1}]},options:{maintainAspectRatio:false,layout:{padding:{top:25}},scales:{x:{type:"linear",min:low,max:high,offset:false,ticks:{stepSize:tickStep,maxTicksLimit:compact?7:10,includeBounds:false,maxRotation:0,minRotation:0,font:{size:9},callback:function(value){return axisNumber(value,tickStep);}},grid:{display:false},border:{display:false},title:{display:!compact,text:tr("value","Value")+(currencyCode(variable)?" ("+currencyCode(variable)+")":"")}},y:{beginAtZero:true,ticks:{maxTicksLimit:compact?4:6,font:{size:9},precision:0},border:{display:false},title:{display:!compact,text:weightsActive()?tr("weightedFrequency","weighted frequency"):tr("observations","observations")},grid:{color:css("--chart-grid","rgba(90,100,115,.13)")}}},plugins:{threeSdGuide:{mean:stats.mean,sd:stats.sd,minimum:low,maximum:high,variable:variable},legend:{display:false},tooltip:{callbacks:{title:function(items){var datum=items[0].raw;return metricFmt(variable,datum.lower,stepDigits(step))+" "+tr("to","to")+" "+metricFmt(variable,datum.upper,stepDigits(step));},label:function(ctx){return " "+fmt(ctx.raw.y,1)+" "+(weightsActive()?tr("weighted","weighted"):tr("observations","observations"))+"; "+tr("rawN","raw n")+"="+localNumber(ctx.raw.raw);}}}}}});
  }

  function buildDate(canvas,variable){
    var source=rows(),counts=dictionary(),raw=0,total=0;destroy(canvas.id);clearSummary(variable);
    for(var i=0;i<source.length;i++){
      var key=monthKey(variable,source[i][variable]);if(!key)continue;var w=weight(source[i]);counts[key]=(counts[key]||0)+w;total+=w;raw++;
    }
    var keys=Object.keys(counts).sort();if(!keys.length||(weightsActive()&&total<=0)){var emptyMessage=keys.length?tr("noPositiveWeight","No positive weight for this selection"):tr("noRecognizableDates","No recognizable dates for this selection");setEmpty(canvas,true,emptyMessage);statistic(variable,"");clearSummary(variable,emptyMessage);describe(canvas,emptyMessage);return;}
    var statisticText=tr("validDatesN","valid dates n")+"="+localNumber(raw)+" · "+keys[0]+" "+tr("to","to")+" "+keys[keys.length-1];setEmpty(canvas,false);statistic(variable,statisticText);clearSummary(variable,statisticText);describe(canvas,statisticText);
    var trend=css("--chart-navy","#002244"),trendFill=rgba(css("--chart-blue","#009fda"),.13);
    state.charts[canvas.id]=new Chart(canvas,{type:"line",data:{labels:keys,datasets:[{data:keys.map(function(k){return counts[k];}),borderColor:trend,backgroundColor:trendFill,fill:true,tension:.22,pointRadius:compact?1.5:2,pointHoverRadius:4}]},options:{maintainAspectRatio:false,scales:{x:{grid:{display:false},border:{display:false},ticks:{maxTicksLimit:compact?6:10,font:{size:9}}},y:{beginAtZero:true,ticks:{maxTicksLimit:compact?4:6,font:{size:9},precision:0},border:{display:false},title:{display:!compact,text:weightsActive()?tr("weightedFrequency","weighted frequency"):tr("observations","observations")},grid:{color:css("--chart-grid","rgba(90,100,115,.13)")}}},plugins:{legend:{display:false}}}});
  }

  function buildCompletion(canvas,variable){
    var source=rows(),answered=0,missing=0,aw=0,mw=0;destroy(canvas.id);clearSummary(variable);
    source.forEach(function(row){var w=weight(row),value=row[variable];if(isMissing(value)||(Array.isArray(value)&&!value.length)){missing++;mw+=w;}else{answered++;aw+=w;}});
    if(!source.length||(weightsActive()&&aw+mw<=0)){var emptyMessage=source.length?tr("noPositiveWeight","No positive weight for this selection"):tr("noObservations","No observations for this selection");setEmpty(canvas,true,emptyMessage);statistic(variable,"");clearSummary(variable,emptyMessage);describe(canvas,emptyMessage);return;}setEmpty(canvas,false);
    statistic(variable,tr("answeredN","answered n")+"="+localNumber(answered)+" · "+tr("missingN","missing n")+"="+localNumber(missing));
    var items=[{key:"answered",label:tr("answered","Answered"),count:aw,raw:answered,color:answeredColor},{key:"missing",label:tr("missing","Missing"),count:mw,raw:missing,color:muted}];
    buildSplit(canvas,variable,items,aw+mw,0);
  }

  function build(canvas){
    var variable=canvas.getAttribute("data-variable"),kind=canvas.getAttribute("data-kind");
    try{
      if(kind==="family")buildFamily(canvas);
      else if(kind==="comparison")buildComparison(canvas);
      else if(kind==="discrete")usdActive()&&isCurrencyVariable(variable)?buildHistogram(canvas,variable):buildDiscrete(canvas,variable);
      else if(kind==="hist")buildHistogram(canvas,variable);
      else if(kind==="yesno")buildYesNo(canvas,variable);
      else if(kind==="bar")buildBar(canvas,variable,false);
      else if(kind==="multibar")buildBar(canvas,variable,true);
      else if(kind==="date")buildDate(canvas,variable);
      else if(kind==="completion")buildCompletion(canvas,variable);
      else buildDonut(canvas,variable);
    }catch(error){console.error("Chart failed",variable||panelKey(canvas),error);destroy(canvas.id);setEmpty(canvas,true,tr("chartFailed","This chart could not be rendered"));}
  }
  function ensureBuilt(canvas){if(!canvas)return;if(state.renderedRevision[canvas.id]===state.chartRevision)return;build(canvas);state.renderedRevision[canvas.id]=state.chartRevision;}
  function invalidateCharts(){state.chartRevision++;}
  function chartIsGenuinelyVisible(canvas){
    if(!canvas||document.hidden===true)return false;
    var section=canvas.closest("details.story");if(section&&!section.open)return false;
    var panel=canvas.closest(".panel");if(panel&&panel.classList.contains("hidden"))return false;
    var pane=canvas.closest("[data-panel-view-pane]");if(pane&&!pane.classList.contains("is-active"))return false;
    var rect=canvas.getBoundingClientRect(),viewportWidth=window.innerWidth||document.documentElement.clientWidth||0,viewportHeight=window.innerHeight||document.documentElement.clientHeight||0;
    if(!(rect.width>0&&rect.height>0&&viewportWidth>0&&viewportHeight>0))return false;
    var visibleWidth=Math.max(0,Math.min(rect.right,viewportWidth)-Math.max(rect.left,0)),visibleHeight=Math.max(0,Math.min(rect.bottom,viewportHeight)-Math.max(rect.top,0));
    return visibleWidth*visibleHeight>=rect.width*rect.height*.12;
  }
  function syncChartVisibility(canvas){
    if(!chartIsGenuinelyVisible(canvas)){delete state.visible[canvas.id];return;}
    state.visible[canvas.id]=1;ensureBuilt(canvas);
  }
  function refreshChartVisibility(){state.visibilityRefreshPending=false;document.querySelectorAll("canvas.chart").forEach(syncChartVisibility);}
  function scheduleChartVisibilityRefresh(){
    if(document.hidden===true){state.visibilityRefreshPending=false;Object.keys(state.visible).forEach(function(id){delete state.visible[id];});return;}
    if(state.visibilityRefreshPending)return;state.visibilityRefreshPending=true;requestAnimationFrame(refreshChartVisibility);
  }
  function renderVisible(){Object.keys(state.visible).forEach(function(id){var canvas=document.getElementById(id);if(canvas&&chartIsGenuinelyVisible(canvas))ensureBuilt(canvas);else delete state.visible[id];});}

  function highlight(variable,source){
    var meta=metaFor(variable)||{},kind=meta.kind;
    if(kind==="hist"){
      var points=numericValues(variable,source);
      var numericMedian=points.length?weightedQuantile(points,.5):null;
      return numericMedian!==null?{value:metricFmt(variable,numericMedian,2),detail:(weightsActive()?tr("weightedMedian","Weighted median"):tr("median","Median"))+" · "+tr("validN","valid n")+"="+localNumber(points.length)}:{value:tr("notAvailable","n/a"),detail:points.length?tr("noPositiveWeight","No positive weight"):tr("noValidResponses","No valid responses")};
    }
    if(kind==="multibar"){
      var multi=categorical(variable,source,true);if(!multi.n||!multi.keys.length||multi.total<=0)return{value:tr("notAvailable","n/a"),detail:multi.n?tr("noPositiveWeight","No positive weight"):tr("noValidResponses","No valid responses")};
      var best=multi.keys.sort(function(a,b){return multi.counts[b]-multi.counts[a];})[0];
      return{value:pct(100*multi.counts[best]/multi.total),detail:shortened(labelFor(variable,best),46)+" · "+tr("respondentsN","respondents n")+"="+localNumber(multi.n)};
    }
    if(kind==="completion"){
      var answered=0,answeredWeight=0,totalWeight=0;source.forEach(function(row){var w=weight(row),value=row[variable];totalWeight+=w;if(!isMissing(value)){answered++;answeredWeight+=w;}});
      return source.length&&totalWeight>0?{value:pct(100*answeredWeight/totalWeight),detail:tr("answeredN","Answered n")+"="+localNumber(answered)+" "+tr("of","of")+" "+localNumber(source.length)}:{value:tr("notAvailable","n/a"),detail:source.length?tr("noPositiveWeight","No positive weight"):tr("noObservations","No observations")};
    }
    var cat=categorical(variable,source,false);if(!cat.n||!cat.keys.length||cat.total<=0)return{value:tr("notAvailable","n/a"),detail:cat.n?tr("noPositiveWeight","No positive weight"):tr("noValidResponses","No valid responses")};
    var key=cat.keys.sort(function(a,b){return cat.counts[b]-cat.counts[a];})[0];
    return{value:pct(100*cat.counts[key]/cat.total),detail:shortened(labelFor(variable,key),46)+" · "+tr("validN","valid n")+"="+localNumber(cat.n)};
  }

  function profileLevels(variable,source){
    var meta=metaFor(variable)||{},observed=dictionary(),seen=dictionary(),levels=[];
    source.forEach(function(row){var value=row[variable];if(isMissing(value)||isConfiguredMissing(variable,value)||isSpecial(variable,value))return;observed[""+value]=1;});
    function add(value){if(isMissing(value)||isConfiguredMissing(variable,value)||isSpecial(variable,value))return;var key=""+value;if(!owns(observed,key)||owns(seen,key))return;seen[key]=1;levels.push(key);}
    (meta.order||[]).forEach(add);source.forEach(function(row){add(row[variable]);});return levels;
  }
  function profileMetric(variable,spec,source){
    var rawSpec=((spec||"auto")+"").trim(),meta=metaFor(variable)||{},mode=rawSpec.toLowerCase(),code=null,colon=rawSpec.indexOf(":");
    if(colon>0){mode=rawSpec.substring(0,colon).trim().toLowerCase();code=rawSpec.substring(colon+1).trim();}
    if(mode==="auto")mode=(meta.kind==="hist"||meta.kind==="discrete")?"median":"share";
    if(mode==="share"){
      var cat=categorical(variable,source,!!meta.multi);if(!cat.n||cat.total<=0)return{display:"—",value:null};
      if(code!==null&&code!==""&&cat.keys.indexOf(""+code)<0&&meta.labels){Object.keys(meta.labels).some(function(key){if((""+meta.labels[key]).toLocaleLowerCase()===code.toLocaleLowerCase()){code=key;return true;}return false;});}
      if(code===null||code==="")code=affirmativeCategory(variable,cat);if(code===null&&meta.kind==="completion"&&cat.keys.indexOf("true")>=0)code="true";
      if(code===null||code===undefined)return{display:"—",value:null};var share=100*(cat.counts[""+code]||0)/cat.total;return{display:pct(share),value:share};
    }
    var points=numericValues(variable,source);if(!points.length)return{display:"—",value:null};var value=null;
    if(mode==="mean")value=weightedMean(points);
    else if(mode==="sum"){value=0;points.forEach(function(point){value+=point.value*(weightsActive()?point.w:1);});}
    else value=weightedQuantile(points,.5);
    return{display:metricFmt(variable,value,mode==="sum"?0:2),value:value};
  }
  function renderProfileTable(){
    var config=CONFIG.table,root=document.getElementById("summary-profile")||document.getElementById("summary-profile-table");if(!root||!config||!config.enabled||!config.by)return;
    var source=filteredRows(config.by),levels=profileLevels(config.by,source),variables=config.variables||[],stats=config.stats||[],labels=config.labels||[],table=document.getElementById("summary-profile-table");
    if(table===root&&table.tagName&&table.tagName.toLowerCase()==="table")root=table.parentElement||root;
    if(!table){var scroll=document.createElement("div");scroll.className="profile-table-scroll";table=document.createElement("table");table.className="profile-table";scroll.appendChild(table);root.appendChild(scroll);}
    table.setAttribute("aria-label",config.title||tr("summaryTable","Summary table"));table.textContent="";
    var thead=document.createElement("thead"),headRow=document.createElement("tr"),byMeta=metaFor(config.by)||{};
    function header(text){var cell=document.createElement("th");cell.scope="col";cell.textContent=text;headRow.appendChild(cell);}
    header(byMeta.label||config.by);header(tr("sampleN","Sample n"));if(weightsActive())header(config.weightLabel||tr("weightedTotal","Weighted total"));
    variables.forEach(function(variable,index){header(labels[index]||((metaFor(variable)||{}).label)||variable);});thead.appendChild(headRow);table.appendChild(thead);
    var tbody=document.createElement("tbody"),selected=state.filters[config.by]||[];
    function addRow(label,subset,total,key){var row=document.createElement("tr");if(total)row.className="profile-total";else if(selected.indexOf(""+key)>=0)row.className="profile-selected";
      var name=document.createElement("th");name.scope="row";name.textContent=label;row.appendChild(name);
      var n=document.createElement("td");n.textContent=localNumber(subset.length);row.appendChild(n);
      if(weightsActive()){var estimate=document.createElement("td"),sum=subset.reduce(function(totalWeight,item){return totalWeight+weight(item);},0);estimate.textContent=sum>0?"≈"+localNumber(Math.round(sum)):"—";row.appendChild(estimate);}
      variables.forEach(function(variable,index){var cell=document.createElement("td");cell.textContent=profileMetric(variable,stats[index]||"auto",subset).display;row.appendChild(cell);});tbody.appendChild(row);
    }
    levels.forEach(function(level){var subset=source.filter(function(row){return sameCategoryValue(row[config.by],level);});addRow(labelFor(config.by,level),subset,false,level);});
    if(source.length)addRow(config.totalLabel||tr("allFilteredInterviews","All filtered interviews"),source,true,"__total__");table.appendChild(tbody);
    var status=root.querySelector("[data-profile-status]");if(status){var bits=[weightsActive()?tr("weightedEstimates","Weighted estimates"):tr("unweightedEstimates","Unweighted estimates")];if(usdAvailable())bits.push(usdActive()?tr("valuesInUsd","Values in USD"):(CONFIG.usd.currency||tr("localCurrency","Local currency")));status.textContent=bits.join(" · ");}
    root.hidden=!source.length;
  }
  function updateOverview(){
    var source=rows(),n=source.length;
    // Numeric charts are rendered lazily and their canvas is deliberately
    // hidden while the Stats tab is active.  Refresh the table independently
    // so a filter change can never leave a visible Stats tab on the previous
    // dashboard revision.
    refreshNumericStats(source);
    document.querySelectorAll("[data-matched]").forEach(function(el){el.textContent=localNumber(n);});
    document.querySelectorAll(".highlight").forEach(function(card){var result=highlight(card.getAttribute("data-variable"),source);card.querySelector(".highlight-value").textContent=result.value;card.querySelector(".highlight-detail").textContent=result.detail;});
    renderProfileTable();
    drawMap(source);
  }

  function activeFilterCount(){var count=0;Object.keys(state.filters).forEach(function(variable){var selected=state.filters[variable];if(selected&&selected.length)count+=selected.length;});return count;}
  function updateControlSummary(){
    var count=activeFilterCount(),badge=document.getElementById("active-filter-count"),reset=document.getElementById("reset"),search=document.getElementById("indicator-search");
    if(badge){badge.dataset.activeCount=""+count;badge.hidden=count===0;var number=badge.querySelector("b"),label=badge.querySelector("[data-active-filter-label]");if(number)number.textContent=localNumber(count);if(label)label.textContent=tr(count===1?"activeFilter":"activeFilters",count===1?"active filter":"active filters");}
    if(reset)reset.disabled=count===0&&!(search&&search.value.trim());
  }
  function refreshControlsLayout(){requestAnimationFrame(function(){requestAnimationFrame(function(){balanceGrids();Object.keys(state.charts).forEach(function(id){var chart=state.charts[id];if(chart&&typeof chart.resize==="function")chart.resize();});if(state.leafletMap)state.leafletMap.invalidateSize(false);scheduleChartVisibilityRefresh();});});}
  function setControlsExpanded(expanded){
    var controls=document.getElementById("dashboard-controls"),toggle=document.getElementById("controls-toggle"),body=document.getElementById("controls-body");if(!controls||!toggle||!body)return;
    var returnFocus=!expanded&&body.contains(document.activeElement);controls.dataset.expanded=expanded?"true":"false";toggle.setAttribute("aria-expanded",expanded?"true":"false");body.hidden=!expanded;
    var label=toggle.querySelector(".controls-toggle-label");if(label)label.textContent=tr(expanded?"hideFilters":"showFilters",expanded?"Hide filters":"Show filters");
    if(returnFocus)toggle.focus();refreshControlsLayout();
  }
  function refreshEstimateMode(){
    document.body.dataset.weightMode=weightsActive()?"weighted":"unweighted";
    document.body.dataset.currencyMode=usdActive()?"usd":"local";
    document.querySelectorAll("[data-weight-mode-label]").forEach(function(weightLabel){weightLabel.textContent=weightsActive()?tr("weightedBadge","Weighted estimates"):tr("unweightedEstimates","Unweighted estimates");});
    document.querySelectorAll("[data-weight-footer-label]").forEach(function(footerLabel){footerLabel.textContent=weightsActive()?tr("footerWeighted","Weighted statistics use the supplied positive Stata weights."):tr("footerUnweighted","Statistics are calculated from raw interviews.");});
    document.querySelectorAll("[data-weighted-ci-note]").forEach(function(note){note.hidden=!weightsActive();});
    invalidateCharts();updateOverview();renderVisible();scheduleChartVisibilityRefresh();updateControlSummary();
  }
  function wireEstimateToggles(){
    var weightToggle=document.getElementById("weight-toggle"),usdToggle=document.getElementById("usd-toggle");
    if(weightToggle){weightToggle.checked=weightsActive();weightToggle.addEventListener("change",function(){state.weightsEnabled=!!weightToggle.checked;refreshEstimateMode();});}
    if(usdToggle){usdToggle.checked=usdActive();usdToggle.disabled=!usdAvailable();usdToggle.addEventListener("change",function(){state.usdEnabled=!!usdToggle.checked;refreshEstimateMode();});}
    document.body.dataset.weightMode=weightsActive()?"weighted":"unweighted";document.body.dataset.currencyMode=usdActive()?"usd":"local";
  }
  function wireControls(){
    var toggle=document.getElementById("controls-toggle"),body=document.getElementById("controls-body");if(!toggle||!body)return;
    toggle.addEventListener("click",function(){setControlsExpanded(toggle.getAttribute("aria-expanded")!=="true");});
    body.addEventListener("keydown",function(event){if(event.key!=="Escape")return;event.preventDefault();setControlsExpanded(false);toggle.focus();});
    wireEstimateToggles();setControlsExpanded(false);updateControlSummary();
  }
  function applyIndicatorSearch(){
    var input=document.getElementById("indicator-search");if(!input)return;var query=input.value.trim().toLocaleLowerCase(),shown=0;
    document.querySelectorAll(".panel").forEach(function(panel){var hit=!query||panel.getAttribute("data-search").indexOf(query)>=0;panel.classList.toggle("hidden",!hit);if(hit){shown++;if(query)panel.closest("details").open=true;}});
    document.querySelectorAll("details.story").forEach(function(section){var any=section.querySelector(".panel:not(.hidden)");section.style.display=any?"":"none";});
    document.getElementById("no-results").classList.toggle("show",shown===0);balanceGrids();renderVisible();scheduleChartVisibilityRefresh();updateControlSummary();
  }

  function wireFilters(){
    document.querySelectorAll(".chip[data-filter]").forEach(function(chip){chip.addEventListener("click",function(){
      var variable=chip.getAttribute("data-filter"),value=chip.getAttribute("data-value"),selected=state.filters[variable]||(state.filters[variable]=[]),index=selected.indexOf(value);
      if(index<0)selected.push(value);else selected.splice(index,1);
      chip.setAttribute("aria-pressed",index<0?"true":"false");invalidateCharts();updateOverview();renderVisible();updateControlSummary();
    });});
    var reset=document.getElementById("reset");if(reset)reset.addEventListener("click",function(){state.filters=dictionary();document.querySelectorAll(".chip[data-filter]").forEach(function(chip){chip.setAttribute("aria-pressed","false");});var search=document.getElementById("indicator-search");if(search)search.value="";applyIndicatorSearch();invalidateCharts();updateOverview();renderVisible();updateControlSummary();});
  }

  function balancedRows(count,maximum){
    var rows=[],remaining=Math.max(0,count|0),max=Math.max(1,Math.min(3,maximum|0));
    if(max===1){while(remaining-->0)rows.push(1);return rows;}
    if(max===2){while(remaining>=2){rows.push(2);remaining-=2;}if(remaining)rows.push(1);return rows;}
    while(remaining>0){
      if(remaining===4){rows.push(2,2);break;}
      if(remaining>=3){rows.push(3);remaining-=3;}
      else{rows.push(remaining);break;}
    }
    return rows;
  }
  function gridMaximum(){
    if(window.matchMedia&&window.matchMedia("(max-width: 680px)").matches)return 1;
    if(!compact||(window.matchMedia&&window.matchMedia("(max-width: 1120px)").matches))return 2;
    return 3;
  }
  function balanceGrids(maximum){
    var max=maximum||gridMaximum();
    document.querySelectorAll(".grid").forEach(function(grid){
      var panels=Array.prototype.slice.call(grid.children).filter(function(panel){return panel.classList.contains("panel")&&!panel.classList.contains("hidden");}),pattern=[],rowIndex=0,run=[];
      Array.prototype.forEach.call(grid.children,function(panel){panel.style.removeProperty("--panel-span");delete panel.dataset.gridRow;delete panel.dataset.gridRowSize;delete panel.dataset.gridFull;});
      function flush(){var rows=balancedRows(run.length,max),cursor=0;rows.forEach(function(size){rowIndex++;pattern.push(""+size);var span=6/size;for(var i=0;i<size;i++){var panel=run[cursor++];panel.style.setProperty("--panel-span",""+span);panel.dataset.gridRow=""+rowIndex;panel.dataset.gridRowSize=""+size;}});run=[];}
      panels.forEach(function(panel){var full=panel.classList.contains("panel--family")||panel.classList.contains("panel--comparison")||panel.dataset.panelWidth==="full";if(!full){run.push(panel);return;}flush();rowIndex++;pattern.push("1f");panel.style.setProperty("--panel-span","6");panel.dataset.gridRow=""+rowIndex;panel.dataset.gridRowSize="1";panel.dataset.gridFull="true";});flush();grid.dataset.rowPattern=pattern.join("-");
    });
  }
  function wireGridBalance(){
    balanceGrids();var pending=false;
    window.addEventListener("resize",function(){if(pending)return;pending=true;requestAnimationFrame(function(){pending=false;balanceGrids();scheduleChartVisibilityRefresh();});});
  }

  function wireSearch(){
    var input=document.getElementById("indicator-search");if(!input)return;
    input.addEventListener("input",applyIndicatorSearch);
  }

  function wirePanelTabs(){
    document.querySelectorAll(".panel-tabs").forEach(function(tablist){var buttons=Array.prototype.slice.call(tablist.querySelectorAll('[role="tab"]')),panel=tablist.closest(".panel");
      function activate(button){var target=button.getAttribute("data-panel-view");buttons.forEach(function(item){var selected=item===button;item.setAttribute("aria-selected",selected?"true":"false");item.setAttribute("tabindex",selected?"0":"-1");});panel.querySelectorAll("[data-panel-view-pane]").forEach(function(pane){var active=pane.getAttribute("data-panel-view-pane")===target;pane.classList.toggle("is-active",active);pane.setAttribute("aria-hidden",active?"false":"true");});var canvas=panel.querySelector("canvas.chart");if(target==="stats"&&canvas)refreshNumericStats(rows(),[canvas]);if(target==="distribution"&&canvas&&state.charts[canvas.id])requestAnimationFrame(function(){state.charts[canvas.id].resize();});scheduleChartVisibilityRefresh();}
      buttons.forEach(function(button,index){button.addEventListener("click",function(){activate(button);});button.addEventListener("keydown",function(event){if(event.key!=="ArrowLeft"&&event.key!=="ArrowRight")return;event.preventDefault();var forward=event.key==="ArrowRight"?1:-1;if(rtl)forward=-forward;var next=(index+forward+buttons.length)%buttons.length;buttons[next].focus();activate(buttons[next]);});});
    });
  }

  function observeCharts(){
    var canvases=document.querySelectorAll("canvas.chart");
    if("IntersectionObserver" in window){
      var observer=new IntersectionObserver(function(entries){entries.forEach(function(entry){var canvas=entry.target;if(entry.isIntersecting&&entry.intersectionRatio>=.12)syncChartVisibility(canvas);else delete state.visible[canvas.id];});},{rootMargin:"0px",threshold:[0,.12]});
      canvases.forEach(function(canvas){observer.observe(canvas);});
    }else{window.addEventListener("scroll",scheduleChartVisibilityRefresh,{passive:true});}
    document.querySelectorAll("details.story").forEach(function(section){section.addEventListener("toggle",scheduleChartVisibilityRefresh);});
    document.addEventListener("visibilitychange",scheduleChartVisibilityRefresh);
    scheduleChartVisibilityRefresh();
  }

  function wireNavigation(){
    document.querySelectorAll("#section-nav a").forEach(function(link){link.addEventListener("click",function(){var id=link.getAttribute("href").slice(1),target=document.getElementById(id),toggle=document.getElementById("controls-toggle");if(toggle&&toggle.getAttribute("aria-expanded")==="true")setControlsExpanded(false);if(target&&target.tagName.toLowerCase()==="details")target.open=true;});});
    if("IntersectionObserver" in window){
      var links=dictionary();document.querySelectorAll("#section-nav a").forEach(function(link){links[link.getAttribute("href").slice(1)]=link;});
      var observer=new IntersectionObserver(function(entries){entries.forEach(function(entry){if(!entry.isIntersecting)return;Object.keys(links).forEach(function(key){links[key].classList.remove("active");});if(links[entry.target.id])links[entry.target.id].classList.add("active");});},{rootMargin:"-38% 0px -56% 0px"});
      document.querySelectorAll("#overview,details.story").forEach(function(section){observer.observe(section);});
    }
  }

  function groupLabel(group){return group&&CONFIG.map.groupLabels&&owns(CONFIG.map.groupLabels,group)?CONFIG.map.groupLabels[group]:group;}
  function normalizeMapLon(lon){var value=((+lon+180)%360+360)%360-180;return Object.is(value,-0)?0:value;}
  function unwrapMapLon(lon){var center=CONFIG.map&&isFinite(+CONFIG.map.lonCenter)?+CONFIG.map.lonCenter:0,value=normalizeMapLon(lon);while(value-center>180)value-=360;while(value-center < -180)value+=360;return value;}
  function mapPoints(source){var points=[];source.forEach(function(row,index){var lat=+row._lat,rawLon=+row._lon;if(isMissing(row._lat)||isMissing(row._lon)||!isFinite(lat)||!isFinite(rawLon))return;var group=CONFIG.map.by&&!isMissing(row._mapby)?""+row._mapby:"";points.push({lat:lat,lon:normalizeMapLon(rawLon),mapLon:unwrapMapLon(rawLon),group:group,row:row,index:index});});return points;}
  function decodeBoundaryRing(encoded){var index=0,lat=0,lon=0,ring=[];function delta(){var result=0,factor=1,byte=0;do{if(index>=encoded.length)return null;byte=encoded.charCodeAt(index++)-63;result+=(byte&31)*factor;factor*=32;}while(byte>=32);return result%2?-(Math.floor(result/2)+1):Math.floor(result/2);}while(index<encoded.length){var dlat=delta(),dlon=delta();if(dlat===null||dlon===null)return[];lat+=dlat;lon+=dlon;ring.push([lat/100000,lon/100000]);}if(ring.length>=3)ring.push([ring[0][0],ring[0][1]]);return ring;}
  function boundaryLines(){var lines=[];(CONFIG.map.boundary||[]).forEach(function(feature){(feature||[]).forEach(function(encoded){var ring=decodeBoundaryRing(encoded);if(ring.length>=4)lines.push(ring);});});return lines;}
  function mapBases(){var googleOpts={subdomains:["0","1","2","3"],maxZoom:21,attribution:"Google"},bases=dictionary();
    bases[tr("googleHybrid","Google Hybrid")]=L.tileLayer("https://mt{s}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}",googleOpts);
    bases[tr("googleSatellite","Google Satellite")]=L.tileLayer("https://mt{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}",googleOpts);
    bases[tr("googleRoads","Google Roads")]=L.tileLayer("https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}",googleOpts);
    bases[tr("openStreetMap","OpenStreetMap")]=L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",{maxZoom:19,attribution:"&copy; OpenStreetMap contributors"});return bases;}
  function baseName(value){return value==="google_sat"?tr("googleSatellite","Google Satellite"):value==="google_road"?tr("googleRoads","Google Roads"):value==="osm"?tr("openStreetMap","OpenStreetMap"):tr("googleHybrid","Google Hybrid");}
  function mapLegend(groups,colors){var legend=document.getElementById("map-legend");if(!legend)return;legend.textContent="";groups.forEach(function(group){var item=document.createElement("span"),dot=document.createElement("i"),label=document.createElement("b");dot.style.backgroundColor=colors[group];label.dir="auto";label.textContent=groupLabel(group);item.appendChild(dot);item.appendChild(label);legend.appendChild(item);});legend.style.display=groups.length?"flex":"none";}
  function escapeHtml(value){return String(value).replace(/[&<>"']/g,function(c){return{"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c];});}
  function mapTooltipNode(value){var node=document.createElement("span");node.className="map-tooltip-value";node.dir="auto";node.textContent=value;return node;}
  function popupHtml(point,spidered){var group=groupLabel(point.group),html='<div dir="'+(rtl?'rtl':'ltr')+'"><div class="map-popup-title">'+escapeHtml(tr("interviewLocation","Interview location"))+'</div><div class="map-popup-row">'+escapeHtml(tr("coordinates","Coordinates"))+': <span dir="ltr" class="map-popup-value">'+point.lat.toFixed(6)+", "+point.lon.toFixed(6)+"</span></div>";if(group)html+='<div class="map-popup-row">'+escapeHtml(tr("group","Group"))+': <bdi dir="auto" class="map-popup-value">'+escapeHtml(group)+"</bdi></div>";if(spidered)html+='<div class="map-popup-note">'+escapeHtml(tr("coincidentPopup","Coincident point separated slightly for visibility."))+'</div>';return html+"</div>";}
  function pointAriaLabel(point){var label=tr("interviewLocation","Interview location")+"; "+tr("coordinates","Coordinates")+" "+point.lat.toFixed(6)+", "+point.lon.toFixed(6),group=groupLabel(point.group);return group?label+"; "+tr("group","Group")+" "+group:label;}
  function makePointMarkerAccessible(marker,point){var element=marker.getElement?marker.getElement():marker._path;if(!element)return;element.setAttribute("tabindex","0");element.setAttribute("focusable","true");element.setAttribute("role","button");element.setAttribute("aria-label",pointAriaLabel(point));element.addEventListener("keydown",function(event){if(event.key!=="Enter"&&event.key!==" "&&event.key!=="Spacebar")return;event.preventDefault();event.stopPropagation();marker.openPopup();});}
  function spiderPoints(points,map){var buckets=dictionary();points.forEach(function(point){var key=point.lat.toFixed(6)+":"+point.mapLon.toFixed(6);(buckets[key]||(buckets[key]=[])).push(point);});Object.keys(buckets).forEach(function(key){var bucket=buckets[key];if(bucket.length===1){bucket[0].displayLatLng=L.latLng(bucket[0].lat,bucket[0].mapLon);bucket[0].spidered=false;return;}var base=map.project([bucket[0].lat,bucket[0].mapLon],map.getZoom());bucket.forEach(function(point,index){var ring=Math.floor(index/10),slots=Math.min(10,bucket.length-ring*10),slot=index-ring*10,angle=2*Math.PI*slot/slots-Math.PI/2,radius=8+ring*6,offset=L.point(base.x+Math.cos(angle)*radius,base.y+Math.sin(angle)*radius);point.displayLatLng=map.unproject(offset,map.getZoom());point.spidered=true;});});return points;}
  function renderLeafletPoints(fit){if(!state.leafletMap||state.mapRedrawing)return;state.mapRedrawing=true;try{var map=state.leafletMap,mapElement=map.getContainer(),points=mapPoints(state.mapRows),groups=(CONFIG.map.groups||[]).map(function(group){return ""+group;}),colors=dictionary();points.forEach(function(point){if(point.group&&groups.indexOf(point.group)<0)groups.push(point.group);});groups.forEach(function(group,index){colors[group]=palette[index%palette.length];});mapLegend(groups,colors);state.mapPointLayer.clearLayers();var count=document.getElementById("map-count");if(count)count.textContent=localNumber(points.length);
    if((fit||state.mapNeedsFit)&&points.length){var bounds=L.latLngBounds(points.map(function(point){return[point.lat,point.mapLon];}));if(points.length===1)map.setView(bounds.getCenter(),Math.min(15,map.getMaxZoom()),{animate:false});else map.fitBounds(bounds,{padding:[28,28],maxZoom:15,animate:false});state.mapNeedsFit=false;if(mapElement){mapElement.dataset.fitRevision=""+((+mapElement.dataset.fitRevision||0)+1);mapElement.dataset.fitBounds=bounds.toBBoxString();}}
    if(CONFIG.map.type==="points"){
      spiderPoints(points,map).forEach(function(point){var color=point.group?colors[point.group]:css("--chart-blue","#009fda"),marker=L.circleMarker(point.displayLatLng,{renderer:state.leafletPointRenderer||undefined,className:"surveye-point",radius:5.3,color:"#fff",weight:1.5,fillColor:color,fillOpacity:.86});marker.bindTooltip(mapTooltipNode(groupLabel(point.group)||tr("interviewLocation","Interview location")),{direction:"top",offset:[0,-4],opacity:.95});marker.bindPopup(popupHtml(point,point.spidered),{maxWidth:300});marker.addTo(state.mapPointLayer);makePointMarkerAccessible(marker,point);});
    }else{
      var cell=CONFIG.map.type==="heat"?25:34,buckets=dictionary();points.forEach(function(point){var pixel=map.latLngToContainerPoint([point.lat,point.mapLon]),key=Math.floor(pixel.x/cell)+":"+Math.floor(pixel.y/cell)+(point.group?":"+point.group:""),bucket=buckets[key]||(buckets[key]={x:0,y:0,count:0,group:point.group});bucket.x+=pixel.x;bucket.y+=pixel.y;bucket.count++;});var maximum=1;Object.keys(buckets).forEach(function(key){maximum=Math.max(maximum,buckets[key].count);});Object.keys(buckets).forEach(function(key){var bucket=buckets[key],latlng=map.containerPointToLatLng([bucket.x/bucket.count,bucket.y/bucket.count]),color=bucket.group?colors[bucket.group]:(CONFIG.map.type==="heat"?css("--chart-coral","#e5552b"):css("--chart-blue","#009fda")),radius=CONFIG.map.type==="heat"?10+18*Math.sqrt(bucket.count/maximum):7+12*Math.sqrt(bucket.count/maximum),marker=L.circleMarker(latlng,{className:"surveye-point",radius:radius,color:CONFIG.map.type==="heat"?color:"#fff",weight:CONFIG.map.type==="heat"?0:1.5,fillColor:color,fillOpacity:CONFIG.map.type==="heat"?.24:.76}),tooltip=localNumber(bucket.count)+" "+(bucket.count===1?tr("interview","interview"):tr("interviews","interviews"))+(bucket.group?" · "+groupLabel(bucket.group):"");marker.bindTooltip(mapTooltipNode(tooltip),{direction:"top"});marker.addTo(state.mapPointLayer);});
    }
    if(mapElement)mapElement.dataset.renderedMarkers=state.mapPointLayer.getLayers().length;
  }finally{state.mapRedrawing=false;}}
  function initializeLeafletMap(){if(state.leafletMap||!CONFIG.map||typeof L==="undefined")return;var loading=document.getElementById("map-loading"),bases=mapBases(),requestedBase=baseName(CONFIG.map.basemap),selected=owns(bases,requestedBase)?bases[requestedBase]:bases[tr("googleHybrid","Google Hybrid")],map=L.map("leaflet-map",{preferCanvas:true,zoomControl:false,attributionControl:false});state.leafletMap=map;state.leafletPointRenderer=typeof L.svg==="function"?L.svg({padding:.5}):null;selected.addTo(map);state.mapPointLayer=L.layerGroup().addTo(map);L.control.zoom({position:rtl?"topright":"topleft",zoomInTitle:tr("zoomIn","Zoom in"),zoomOutTitle:tr("zoomOut","Zoom out")}).addTo(map);L.control.attribution({position:rtl?"bottomleft":"bottomright"}).addTo(map);L.control.scale({imperial:false,position:rtl?"bottomright":"bottomleft"}).addTo(map);var overlays=dictionary();
    var lines=boundaryLines();if(lines.length){var admin2=!!CONFIG.map.boundaryAdmin2,casing=L.polyline(lines,{interactive:false,color:"#ffffff",weight:admin2?1.9:3.2,opacity:admin2?.5:.62,lineCap:"round",lineJoin:"round",smoothFactor:0}),detail=L.polyline(lines,{interactive:false,color:admin2?"#173f5f":"#002244",weight:admin2?.72:1.45,opacity:admin2?.78:.9,lineCap:"round",lineJoin:"round",smoothFactor:0});state.mapBoundaryLayer=L.layerGroup([casing,detail]).addTo(map);overlays[tr("surveyBoundary","Survey boundary")]=state.mapBoundaryLayer;var mapElement=map.getContainer();mapElement.dataset.boundaryReady="true";mapElement.dataset.boundaryRings=""+lines.length;}
    var layerControl=L.control.layers(bases,overlays,{collapsed:compact,position:rtl?"topleft":"topright"}).addTo(map),layerToggle=layerControl.getContainer().querySelector(".leaflet-control-layers-toggle");if(layerToggle){layerToggle.title=tr("mapLayers","Map layers");layerToggle.setAttribute("aria-label",tr("mapLayers","Map layers"));}map.on("zoomend",function(){renderLeafletPoints(false);});map.whenReady(function(){if(loading)loading.classList.add("is-hidden");renderLeafletPoints(true);});map.fitBounds([[CONFIG.map.minLat,CONFIG.map.minLon],[CONFIG.map.maxLat,CONFIG.map.maxLon]],{padding:[18,18],maxZoom:9,animate:false});
  }
  function drawMap(source){if(!CONFIG.map)return;state.mapRows=source||[];var count=document.getElementById("map-count"),points=mapPoints(state.mapRows);if(count)count.textContent=localNumber(points.length);var card=document.getElementById("spatial-map");if(card&&card.open){initializeLeafletMap();if(state.leafletMap){state.leafletMap.invalidateSize(false);renderLeafletPoints(true);}}}
  function wireMap(){if(!CONFIG.map)return;var card=document.getElementById("spatial-map");if(card&&card.tagName.toLowerCase()==="details")card.addEventListener("toggle",function(){if(card.open)requestAnimationFrame(function(){initializeLeafletMap();if(state.leafletMap){state.leafletMap.invalidateSize(false);renderLeafletPoints(state.mapNeedsFit);}});});if(card&&card.open)requestAnimationFrame(initializeLeafletMap);var stage=document.getElementById("map-stage");if(stage&&"ResizeObserver" in window)new ResizeObserver(function(){if(state.leafletMap)state.leafletMap.invalidateSize(false);}).observe(stage);else window.addEventListener("resize",function(){if(state.leafletMap)state.leafletMap.invalidateSize(false);});}

  function beforePrint(){balanceGrids(2);document.querySelectorAll("details.story").forEach(function(section){section.open=true;});var map=document.getElementById("spatial-map");if(map&&map.tagName.toLowerCase()==="details")map.open=true;document.querySelectorAll("canvas.chart").forEach(function(canvas){ensureBuilt(canvas);});drawMap(rows());}
  if(window.__SURVEYE_TEST_ONLY__){window.__SURVEYE_STATS__={quantile:quantile,weightedQuantile:weightedQuantile,weightedMean:weightedMean,weightedStd:weightedStd,meanPlusThreeSd:meanPlusThreeSd,effectiveN:effectiveN,confidenceInterval:confidenceInterval,categorical:categorical,numericValues:numericValues,numericStatistics:numericStatistics,refreshNumericStats:refreshNumericStats,niceNumericStep:niceNumericStep,discreteDistributionPlan:discreteDistributionPlan,monthKey:monthKey,isSpecial:isSpecial,isConfiguredMissing:isConfiguredMissing,filterMatches:filterMatches,filteredRows:filteredRows,labelFor:labelFor,colorFor:colorFor,stableColorIndex:stableColorIndex,balancedRows:balancedRows,groupLabel:groupLabel,horizontalScale:horizontalScale,barValuePlacement:barValuePlacement,chartIsGenuinelyVisible:chartIsGenuinelyVisible,buildSplit:buildSplit,buildFamily:buildFamily,buildDiscrete:buildDiscrete,buildHistogram:buildHistogram,buildComparison:buildComparison,panelKey:panelKey,weightsActive:weightsActive,usdActive:usdActive,convertedNumber:convertedNumber,currencyCode:currencyCode,metricFmt:metricFmt,profileMetric:profileMetric,profileLevels:profileLevels,setWeightsEnabled:function(value){state.weightsEnabled=!!value;},setUsdEnabled:function(value){state.usdEnabled=!!value;},setFilters:function(value){state.filters=value||dictionary();}};return;}
  function init(){wireControls();wireFilters();wireGridBalance();wireSearch();wirePanelTabs();wireNavigation();observeCharts();wireMap();updateOverview();window.addEventListener("beforeprint",beforePrint);window.addEventListener("afterprint",function(){balanceGrids();scheduleChartVisibilityRefresh();});}
  if(document.readyState!=="loading")init();else document.addEventListener("DOMContentLoaded",init);
})();

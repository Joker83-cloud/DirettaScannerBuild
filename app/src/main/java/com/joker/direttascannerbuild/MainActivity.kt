package com.joker.direttascannerbuild

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var detailWeb: WebView
    private lateinit var historyWeb: WebView
    private lateinit var status: TextView
    private lateinit var scanBtn: Button
    private lateinit var copyBtn: Button
    private lateinit var reloadBtn: Button
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner

    data class Match(
        val home:String,val away:String,val time:String,val url:String,
        var o1:String="",var ox:String="",var o2:String="",var attempted:Boolean=false,var snaiState:String="",
        var homeUrl:String="",var awayUrl:String="",var historyState:String="",
        var homeGa3:Int=-1,var awayLoss3:Int=-1,var awayGa3:Int=-1,var profilePass:Boolean=false,var historyNote:String=""
    )

    private val matches=LinkedHashMap<String,Match>()
    private val detailQueue=ArrayDeque<String>()
    private val historyQueue=ArrayDeque<String>()
    private val handler=Handler(Looper.getMainLooper())
    private var currentDetailKey:String?=null
    private var currentHistoryKey:String?=null
    private var historyPhase=""
    private var detailGeneration=0
    private var historyGeneration=0
    private var rowsSeen=0;private var rowsWithTeams=0;private var rowsWithTime=0;private var autoExpanded=0
    private var detailsAttempted=0;private var detailsWithSnai=0;private var detailsWithoutSnai=0;private var oddsTabsOpened=0
    private var historyCompleted=0;private var profilePassed=0;private var historyUnavailable=0
    private var firstDetailDiagnostic="";private var firstHistoryDiagnostic=""
    private val hours=(0..23).map{String.format("%02d:00",it)}+listOf("23:59")

    @SuppressLint("SetJavaScriptEnabled","AddJavascriptInterface")
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE);setPadding(8,8,8,0)}
        val title=TextView(this).apply{text="DIRETTA SCANNER";textSize=25f;setTextColor(Color.rgb(35,35,35));setPadding(8,8,8,6)}
        val profile=TextView(this).apply{text="PROFILO 70% · SNAI 1 1,50–1,58 + storico casa/trasferta";textSize=13f;setPadding(10,2,8,4)}
        val filterLabel=TextView(this).apply{text="FASCIA ORARIA DA ANALIZZARE";textSize=14f;setPadding(10,4,8,2)}
        val filters=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        fromSpinner=Spinner(this);toSpinner=Spinner(this)
        val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,hours)
        fromSpinner.adapter=adapter;toSpinner.adapter=adapter;fromSpinner.setSelection(12);toSpinner.setSelection(15)
        filters.addView(TextView(this).apply{text="  DALLE  ";textSize=13f})
        filters.addView(fromSpinner,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        filters.addView(TextView(this).apply{text="  ALLE  ";textSize=13f})
        filters.addView(toSpinner,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        scanBtn=Button(this).apply{text="APPLICA E SCANSIONA"}
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        reloadBtn=Button(this).apply{text="APRI / RICARICA DIRETTA"};copyBtn=Button(this).apply{text="COPIA REPORT"}
        actions.addView(reloadBtn,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));actions.addView(copyBtn,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        status=TextView(this).apply{text="Diretta Scanner v0.14.0 · PROFILO 70%";setPadding(10,6,10,6);textSize=13f}
        web=WebView(this);detailWeb=WebView(this).apply{visibility=View.INVISIBLE};historyWeb=WebView(this).apply{visibility=View.INVISIBLE}
        root.addView(title);root.addView(profile);root.addView(filterLabel);root.addView(filters);root.addView(scanBtn,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));root.addView(actions);root.addView(status)
        root.addView(web,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));root.addView(detailWeb,LinearLayout.LayoutParams(1,1));root.addView(historyWeb,LinearLayout.LayoutParams(1,1));setContentView(root)
        configureWeb(web,false);configureWeb(detailWeb,true);configureWeb(historyWeb,true)
        web.webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView?,url:String?){super.onPageFinished(view,url);installMainObserver()}}
        detailWeb.webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView?,url:String?){super.onPageFinished(view,url);val key=currentDetailKey?:return;val gen=detailGeneration;handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)openOddsTab()},800);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},2600);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},5000);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(true)},8000)}}
        historyWeb.webViewClient=object:WebViewClient(){override fun onPageFinished(view:WebView?,url:String?){super.onPageFinished(view,url);val key=currentHistoryKey?:return;val gen=historyGeneration;handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(false)},1800);handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(false)},3500);handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(true)},6000)}}
        scanBtn.setOnClickListener{extractMain(false);handler.postDelayed({startDetailQueue()},900)}
        reloadBtn.setOnClickListener{web.loadUrl("https://www.diretta.it/")}
        copyBtn.setOnClickListener{extractMain(false);handler.postDelayed({startDetailQueue();copyReport()},700)}
        web.loadUrl("https://www.diretta.it/")
    }

    @SuppressLint("SetJavaScriptEnabled","AddJavascriptInterface")
    private fun configureWeb(w:WebView,hidden:Boolean){w.settings.javaScriptEnabled=true;w.settings.domStorageEnabled=true;w.settings.databaseEnabled=true;w.settings.loadsImagesAutomatically=!hidden;w.settings.userAgentString=w.settings.userAgentString+" DirettaScanner/0.14.0";w.addJavascriptInterface(Bridge(),"DirettaScanner");w.webChromeClient=WebChromeClient()}

    private fun mainJs(observer:Boolean):String{
        val tail=if(observer)"""if(!window.__dsObs140){window.__dsObs140=true;const ob=new MutationObserver(()=>{clearTimeout(window.__dsT140);window.__dsT140=setTimeout(scan,500);});ob.observe(document.documentElement,{childList:true,subtree:true,characterData:true});setInterval(scan,2500);}""" else ""
        return """(function(){function txt(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}function first(r,s){for(const x of s){const e=r.querySelector(x);if(e)return e;}return null;}function expand(){let n=0;for(const h of [...document.querySelectorAll('.event__header,[class*=\"event__header\"]')]){const low=(String(h.className||'')+' '+txt(h)).toLowerCase();if(/menu|drawer|navigation|sidebar|filter|setting|preferit/.test(low))continue;const c=h.querySelector('[aria-expanded=\"false\"],button[data-testid*=\"expand\"],[role=\"button\"][data-testid*=\"expand\"]');if(c&&c.getAttribute('aria-expanded')==='false'){try{c.click();n++;}catch(e){}}}return n;}function scan(){const ex=expand(),rows=[...document.querySelectorAll('.event__match[data-event-row=\"true\"],.event__match')],out=[];let teams=0,times=0;for(const r of rows){const h=first(r,['.event__homeParticipant','[class*=\"homeParticipant\"]']),a=first(r,['.event__awayParticipant','[class*=\"awayParticipant\"]']);const home=txt(h),away=txt(a);if(!home||!away||home===away)continue;teams++;const raw=txt(first(r,['.event__time','[class*=\"event__time\"]']));const m=(raw.match(/(?:^|\s)([01]?\d|2[0-3]):([0-5]\d)(?:\s|$)/)||[]);const time=m.length?String(m[1]).padStart(2,'0')+':'+m[2]:'';if(time)times++;const l=first(r,['a.eventRowLink','a[href*=\"/partita/calcio/\"]']);const url=l&&l.href?l.href:'';if(time&&url)out.push({home,away,time,url});}try{DirettaScanner.onMain(JSON.stringify(out),rows.length,teams,times,ex);}catch(e){}}scan();$tail return 'ok';})();"""
    }

    private fun openOddsTabJs()="""(function(){function t(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}let z=null;for(const e of [...document.querySelectorAll('a,button,[role=\"tab\"],[role=\"button\"]')]){const x=t(e).toUpperCase(),h=(e.getAttribute('href')||'').toLowerCase(),d=(e.getAttribute('data-testid')||'').toLowerCase();if(x==='QUOTE'||x==='ODDS'||/quote|odds/.test(h)||/quote|odds/.test(d)){z=e;if(x==='QUOTE'||x==='ODDS')break;}}let c=false;if(z){try{z.click();c=true;}catch(e){}}try{DirettaScanner.onOddsTab(c?1:0,t(z),location.href);}catch(e){}return 'ok';})();"""

    private fun snaiDetailJs(finalAttempt:Boolean):String{
        val fin=if(finalAttempt)"true" else "false"
        return """(function(){function txt(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}function dec(s){const o=[],r=/(?:^|\s)(\d{1,2}[.,]\d{2})(?=\s|$)/g;let m;s=(s||'').replace(/,/g,'.');while((m=r.exec(s))!==null){const n=parseFloat(m[1]);if(n>=1.01&&n<=99){const v=n.toFixed(2);if(!o.includes(v))o.push(v);}}return o;}function meta(e){if(!e)return '';let s=txt(e)+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('alt')||'')+' '+(e.getAttribute('data-bookmaker-name')||'')+' '+(e.getAttribute('data-testid')||'')+' '+(e.getAttribute('href')||'')+' '+(e.getAttribute('src')||'');const im=e.querySelector?e.querySelector('img'):null;if(im)s+=' '+(im.getAttribute('alt')||'')+' '+(im.getAttribute('src')||'');return s.replace(/\s+/g,' ').trim();}let best=null;const all=[...document.querySelectorAll('img,a,div,span,button,[data-bookmaker-name],[data-testid]')],markers=all.filter(e=>(/(^|[^a-z0-9])snai([^a-z0-9]|$)/i).test(meta(e)));for(const marker of markers){let p=marker;for(let d=0;p&&d<8;d++,p=p.parentElement){const x=txt(p);if(!x||x.length>1400)continue;const v=dec(x);if(v.length>=3){let s=100-d*8;if(v.length===3)s+=18;const c={score:s,vals:v.slice(0,3),text:x.slice(0,700),marker:meta(marker).slice(0,220)};if(!best||c.score>best.score)best=c;}}}const links=[...document.querySelectorAll('a[href*=\"/squadra/\"]')].map(a=>({t:txt(a),u:a.href||''})).filter(x=>x.t&&x.u);const p={ok:!!best,final:$fin,odds:best?best.vals:[],sample:best?('SNAI '+best.marker+' | '+best.text):txt(document.body).slice(0,1200),title:document.title||'',url:location.href,markerCount:markers.length,teamLinks:links.slice(0,24)};try{DirettaScanner.onDetail(JSON.stringify(p));}catch(e){}return 'ok';})();"""
    }

    private fun historyJs(finalAttempt:Boolean):String{
        val key=currentHistoryKey?:return "'no-key'";val m=matches[key]?:return "'no-match'";val team=if(historyPhase=="HOME")m.home else m.away;val fin=if(finalAttempt)"true" else "false"
        return """(function(){function txt(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}function norm(s){return (s||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]+/g,' ').trim();}const target=norm('${jsEscape(team)}'),phase='$historyPhase',rows=[...document.querySelectorAll('.event__match[data-event-row=\"true\"],.event__match')],out=[];for(const r of rows){const he=r.querySelector('.event__homeParticipant,[class*=\"homeParticipant\"]'),ae=r.querySelector('.event__awayParticipant,[class*=\"awayParticipant\"]');const home=txt(he),away=txt(ae);if(!home||!away)continue;let hs=txt(r.querySelector('.event__score--home,[class*=\"score--home\"]')),as=txt(r.querySelector('.event__score--away,[class*=\"score--away\"]'));if(!/^\d+$/.test(hs)||!/^\d+$/.test(as)){const sc=[...r.querySelectorAll('[class*=\"event__score\"],[class*=\"score\"]')].map(txt).filter(x=>/^\d+$/.test(x));if(sc.length>=2){hs=sc[0];as=sc[1];}}if(!/^\d+$/.test(hs)||!/^\d+$/.test(as))continue;if((phase==='HOME'?norm(home):norm(away))!==target)continue;out.push({home,away,hg:parseInt(hs),ag:parseInt(as),row:txt(r).slice(0,260)});if(out.length>=3)break;}if(out.length<3&&!window.__dsMore140){const b=[...document.querySelectorAll('button,a,[role=\"button\"]')].find(e=>/mostra piu incontri|mostra più incontri|show more matches/i.test(txt(e)));if(b){window.__dsMore140=true;try{b.click();}catch(e){}}}const p={phase,final:$fin,matches:out,title:document.title||'',url:location.href,rows:rows.length,sample:out.length?out.map(x=>x.row).join(' || '):txt(document.body).slice(0,1000)};try{DirettaScanner.onHistory(JSON.stringify(p));}catch(e){}return 'ok';})();"""
    }

    private fun jsEscape(s:String)=s.replace("\\","\\\\").replace("'","\\'").replace("\n"," ")
    private fun installMainObserver(){web.evaluateJavascript(mainJs(true),null)}
    private fun extractMain(observer:Boolean){web.evaluateJavascript(mainJs(observer),null)}
    private fun openOddsTab(){if(currentDetailKey!=null)detailWeb.evaluateJavascript(openOddsTabJs(),null)}
    private fun extractDetailSnai(finalAttempt:Boolean){if(currentDetailKey!=null)detailWeb.evaluateJavascript(snaiDetailJs(finalAttempt),null)}
    private fun extractHistory(finalAttempt:Boolean){if(currentHistoryKey!=null)historyWeb.evaluateJavascript(historyJs(finalAttempt),null)}

    inner class Bridge{
        @JavascriptInterface fun onMain(json:String,seen:Int,teams:Int,times:Int,expanded:Int){try{val a=JSONArray(json);synchronized(matches){rowsSeen=maxOf(rowsSeen,seen);rowsWithTeams=maxOf(rowsWithTeams,teams);rowsWithTime=maxOf(rowsWithTime,times);autoExpanded+=expanded;for(i in 0 until a.length()){val o=a.getJSONObject(i);val h=o.optString("home").trim();val aw=o.optString("away").trim();val t=o.optString("time").trim();val u=o.optString("url").trim();if(h.isNotBlank()&&aw.isNotBlank()&&t.isNotBlank()&&u.isNotBlank()&&matches[u]==null)matches[u]=Match(h,aw,t,u)}};runOnUiThread{updateStatus()}}catch(_:Throwable){}}
        @JavascriptInterface fun onOddsTab(opened:Int,label:String,url:String){if(opened==1)oddsTabsOpened++}
        @JavascriptInterface fun onDetail(json:String){try{val o=JSONObject(json);val key=currentDetailKey?:return;val odds=o.optJSONArray("odds");val ok=odds!=null&&odds.length()>=3;val fin=o.optBoolean("final",false);if(!ok&&!fin)return;synchronized(matches){val m=matches[key];if(m!=null&&!m.attempted){m.attempted=true;detailsAttempted++;val links=o.optJSONArray("teamLinks");if(links!=null)for(i in 0 until links.length()){val x=links.optJSONObject(i)?:continue;val t=x.optString("t");val u=x.optString("u");if(sameTeam(t,m.home)&&m.homeUrl.isBlank())m.homeUrl=u;if(sameTeam(t,m.away)&&m.awayUrl.isBlank())m.awayUrl=u};if(ok){m.o1=odds!!.optString(0);m.ox=odds.optString(1);m.o2=odds.optString(2);m.snaiState="AVAILABLE";detailsWithSnai++;val q=m.o1.toDoubleOrNull();if(q!=null&&q in 1.50..1.58){if(m.homeUrl.isNotBlank()&&m.awayUrl.isNotBlank()){m.historyState="QUEUED";if(!historyQueue.contains(key))historyQueue.add(key)}else{m.historyState="NO_TEAM_URL";historyUnavailable++}}else m.historyState="ODDS_OUT"}else{m.snaiState="NOT_AVAILABLE";m.historyState="NO_SNAI";detailsWithoutSnai++};if(firstDetailDiagnostic.isBlank())firstDetailDiagnostic="Titolo: ${o.optString("title")}\nURL: ${o.optString("url")}\nMarker SNAI: ${o.optInt("markerCount",0)}\nTeam URL casa: ${m.homeUrl}\nTeam URL ospite: ${m.awayUrl}\nCampione: ${o.optString("sample").take(800)}"}};runOnUiThread{currentDetailKey=null;detailGeneration++;updateStatus();handler.postDelayed({processNextDetail()},250);handler.postDelayed({processNextHistory()},200)}}catch(_:Throwable){}}
        @JavascriptInterface fun onHistory(json:String){try{val o=JSONObject(json);val key=currentHistoryKey?:return;val phase=o.optString("phase");if(phase!=historyPhase)return;val arr=o.optJSONArray("matches")?:JSONArray();val fin=o.optBoolean("final",false);if(arr.length()<3&&!fin)return;val m=matches[key]?:return;if(arr.length()<3){m.historyState="INSUFFICIENT";m.historyNote="$phase: solo ${arr.length()} partite";historyUnavailable++;finishHistoryMatch();return};if(phase=="HOME"){var ga=0;for(i in 0 until 3)ga+=arr.getJSONObject(i).optInt("ag");m.homeGa3=ga;historyPhase="AWAY";historyGeneration++;historyWeb.loadUrl(resultsUrl(m.awayUrl))}else{var losses=0;var ga=0;for(i in 0 until 3){val x=arr.getJSONObject(i);val hg=x.optInt("hg");val ag=x.optInt("ag");ga+=hg;if(ag<hg)losses++};m.awayLoss3=losses;m.awayGa3=ga;m.profilePass=(m.o1.toDoubleOrNull()?.let{it in 1.50..1.58}==true&&m.homeGa3<=3&&m.awayLoss3>=2&&m.awayGa3>=4);m.historyState=if(m.profilePass)"PASS" else "FAIL";historyCompleted++;if(m.profilePass)profilePassed++;if(firstHistoryDiagnostic.isBlank())firstHistoryDiagnostic="Partita: ${m.home} vs ${m.away}\nCasa GA ultime 3 interne: ${m.homeGa3}\nOspite sconfitte ultime 3 trasferte: ${m.awayLoss3}\nOspite GA ultime 3 trasferte: ${m.awayGa3}\nPagina: ${o.optString("url")}\nCampione: ${o.optString("sample").take(800)}";finishHistoryMatch()}}catch(_:Throwable){}}
    }

    private fun sameTeam(a:String,b:String):Boolean{fun n(s:String)=s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+")," ").trim();val x=n(a);val y=n(b);return x==y||x.startsWith(y)||y.startsWith(x)}
    private fun resultsUrl(url:String):String{if(url.isBlank())return url;val b=url.substringBefore("?").trimEnd('/');return if(b.endsWith("/risultati"))"$b/" else "$b/risultati/"}
    private fun finishHistoryMatch(){runOnUiThread{currentHistoryKey=null;historyPhase="";historyGeneration++;updateStatus();handler.postDelayed({processNextHistory()},300)}}
    private fun mins(s:String):Int{val p=s.split(":");if(p.size!=2)return -1;return(p[0].toIntOrNull()?:-99)*60+(p[1].toIntOrNull()?:0)}
    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:"00:00");val z=mins(toSpinner.selectedItem?.toString()?:"23:59");val x=mins(t);return if(f<=z)x in f..z else x>=f||x<=z}
    private fun startDetailQueue(){synchronized(matches){matches.forEach{(k,m)->if(inSelectedRange(m.time)&&!m.attempted&&!detailQueue.contains(k)&&currentDetailKey!=k)detailQueue.add(k)}};processNextDetail()}
    private fun processNextDetail(){if(currentDetailKey!=null)return;val key=if(detailQueue.isEmpty())null else detailQueue.removeFirst();if(key==null){updateStatus();processNextHistory();return};val m=matches[key]?:run{processNextDetail();return};currentDetailKey=key;detailGeneration++;val gen=detailGeneration;status.text="SNAI… ${detailsAttempted+1} · ${m.time} ${m.home} vs ${m.away}";detailWeb.loadUrl(m.url);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)openOddsTab()},1800);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},4200);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(true)},9500)}
    private fun processNextHistory(){if(currentHistoryKey!=null)return;val key=if(historyQueue.isEmpty())null else historyQueue.removeFirst();if(key==null){updateStatus();return};val m=matches[key]?:run{processNextHistory();return};currentHistoryKey=key;historyPhase="HOME";historyGeneration++;m.historyState="RUNNING";status.text="PROFILO 70%… ${m.home} vs ${m.away} · storico casa";historyWeb.loadUrl(resultsUrl(m.homeUrl))}
    private fun selectedMatches():List<Match> = synchronized(matches){matches.values.filter{inSelectedRange(it.time)}.sortedWith(compareBy<Match>{mins(it.time)}.thenBy{it.home})}
    private fun updateStatus(){val s=selectedMatches();val snai=s.count{it.snaiState=="AVAILABLE"};val c=s.count{it.o1.toDoubleOrNull()?.let{q->q in 1.50..1.58}==true};status.text="Diretta Scanner v0.14.0 · PROFILO 70%\nEventi ${s.size} · SNAI $snai · quota 1,50–1,58: $c · storici $historyCompleted · ✅ profilo: $profilePassed"+if(currentDetailKey!=null||currentHistoryKey!=null)" · scansione in corso" else ""}
    private fun report():String{val list=selectedMatches();val from=fromSpinner.selectedItem?.toString()?:"00:00";val to=toSpinner.selectedItem?.toString()?:"23:59";val pass=list.filter{it.profilePass};return buildString{append("DIRETTA SCANNER · PROFILO 70% v0.14.0\nFASCIA: $from - $to\nPALETTI: SNAI 1 1,50-1,58 · casa max 3 gol subiti ultime 3 interne · ospite >=2 sconfitte ultime 3 trasferte · ospite >=4 gol subiti ultime 3 trasferte\nEVENTI: ${list.size} · SNAI: ${list.count{it.snaiState=="AVAILABLE"}} · CANDIDATE QUOTA: ${list.count{it.o1.toDoubleOrNull()?.let{q->q in 1.50..1.58}==true}}\n✅ PROFILO 70% SUPERATO: ${pass.size}");if(pass.isEmpty())append("\n⛔ NESSUNA PARTITA SUPERA TUTTI I PALETTI");pass.forEachIndexed{i,m->append("\n${i+1}. ${m.time} · ${m.home} vs ${m.away} | SNAI 1 ${m.o1} · X ${m.ox} · 2 ${m.o2} | Casa GA3=${m.homeGa3} · Ospite L3=${m.awayLoss3} · GA3=${m.awayGa3} ✅")};append("\n\nCANDIDATE QUOTA / CONTROLLI");list.filter{it.o1.toDoubleOrNull()?.let{q->q in 1.50..1.58}==true}.forEach{m->append("\n${m.time} · ${m.home} vs ${m.away} · 1 @${m.o1} · ");when(m.historyState){"PASS"->append("✅ PROFILO 70%");"FAIL"->append("❌ NO · casa GA3=${m.homeGa3} · ospite L3=${m.awayLoss3} · ospite GA3=${m.awayGa3}");"INSUFFICIENT","NO_TEAM_URL"->append("⚠️ STORICO NON DISPONIBILE · ${m.historyNote}");else->append("⏳ ${m.historyState.ifBlank{"DA VERIFICARE"}}")}};append("\n\nDIAGNOSTICA\nRighe evento max: $rowsSeen · squadre: $rowsWithTeams · orari: $rowsWithTime\nSchede QUOTE: $oddsTabsOpened · dettagli: $detailsAttempted · SNAI: $detailsWithSnai · senza SNAI: $detailsWithoutSnai\nStorici completati: $historyCompleted · non disponibili: $historyUnavailable · profilo: $profilePassed\nCode residue: quote ${detailQueue.size+if(currentDetailKey!=null)1 else 0} · storico ${historyQueue.size+if(currentHistoryKey!=null)1 else 0}");if(firstDetailDiagnostic.isNotBlank())append("\n\nPRIMO DETTAGLIO\n$firstDetailDiagnostic");if(firstHistoryDiagnostic.isNotBlank())append("\n\nPRIMO STORICO\n$firstHistoryDiagnostic");append("\n\nNota: PROFILO 70% indica una regola storica osservata, non una probabilità garantita della singola partita.")}}
    private fun copyReport(){val t=report();val cb=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cb.setPrimaryClip(ClipData.newPlainText("Diretta Scanner report",t));Toast.makeText(this,"Report copiato · profilo superato: $profilePassed",Toast.LENGTH_SHORT).show()}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);web.destroy();detailWeb.destroy();historyWeb.destroy();super.onDestroy()}
    @Deprecated("Deprecated in Java") override fun onBackPressed(){if(web.canGoBack())web.goBack()else super.onBackPressed()}
}

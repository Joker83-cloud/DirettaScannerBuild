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
    private lateinit var status: TextView
    private lateinit var scanBtn: Button
    private lateinit var copyBtn: Button
    private lateinit var reloadBtn: Button
    private lateinit var fromSpinner: Spinner
    private lateinit var toSpinner: Spinner

    data class Match(
        val home:String,
        val away:String,
        val time:String,
        val url:String,
        var o1:String="",
        var ox:String="",
        var o2:String="",
        var attempted:Boolean=false,
        var snaiState:String=""
    )

    private val matches = LinkedHashMap<String, Match>()
    private val detailQueue = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var currentDetailKey:String? = null
    private var rowsSeen = 0
    private var rowsWithTeams = 0
    private var rowsWithTime = 0
    private var autoExpanded = 0
    private var detailsAttempted = 0
    private var detailsWithSnai = 0
    private var detailsWithoutSnai = 0
    private var oddsTabsOpened = 0
    private var firstDetailDiagnostic = ""
    private var detailGeneration = 0

    private val hours = (0..23).map { String.format("%02d:00", it) } + listOf("23:59")

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(8,8,8,0)
        }
        val title = TextView(this).apply {
            text = "DIRETTA SCANNER"
            textSize = 25f
            setTextColor(Color.rgb(35,35,35))
            setPadding(8,8,8,6)
        }
        val filterLabel = TextView(this).apply {
            text = "FASCIA ORARIA DA ANALIZZARE"
            textSize = 14f
            setPadding(10,4,8,2)
        }
        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fromSpinner = Spinner(this)
        toSpinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, hours)
        fromSpinner.adapter = adapter
        toSpinner.adapter = adapter
        fromSpinner.setSelection(12)
        toSpinner.setSelection(15)
        filters.addView(TextView(this).apply { text="  DALLE  "; textSize=13f })
        filters.addView(fromSpinner, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        filters.addView(TextView(this).apply { text="  ALLE  "; textSize=13f })
        filters.addView(toSpinner, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))

        scanBtn = Button(this).apply { text = "APPLICA E SCANSIONA" }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        reloadBtn = Button(this).apply { text = "APRI / RICARICA DIRETTA" }
        copyBtn = Button(this).apply { text = "COPIA REPORT" }
        actions.addView(reloadBtn, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        actions.addView(copyBtn, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))

        status = TextView(this).apply {
            text = "Diretta Scanner v0.13.9 · SNAI\nQuote 1-X-2 esclusivamente dalla riga SNAI."
            setPadding(10,6,10,6)
            textSize = 13f
        }

        web = WebView(this)
        detailWeb = WebView(this).apply { visibility = View.INVISIBLE }

        root.addView(title)
        root.addView(filterLabel)
        root.addView(filters)
        root.addView(scanBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(actions)
        root.addView(status)
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        root.addView(detailWeb, LinearLayout.LayoutParams(1,1))
        setContentView(root)

        configureWeb(web, false)
        configureWeb(detailWeb, true)

        web.webViewClient = object: WebViewClient(){
            override fun onPageFinished(view:WebView?, url:String?){
                super.onPageFinished(view,url)
                installMainObserver()
            }
        }
        detailWeb.webViewClient = object: WebViewClient(){
            override fun onPageFinished(view:WebView?, url:String?){
                super.onPageFinished(view,url)
                val key=currentDetailKey ?: return
                val gen=detailGeneration
                handler.postDelayed({ if(currentDetailKey==key && detailGeneration==gen) openOddsTab() },800)
                handler.postDelayed({ if(currentDetailKey==key && detailGeneration==gen) extractDetailSnai(false) },2600)
                handler.postDelayed({ if(currentDetailKey==key && detailGeneration==gen) extractDetailSnai(false) },5000)
                handler.postDelayed({ if(currentDetailKey==key && detailGeneration==gen) extractDetailSnai(true) },8000)
            }
        }

        scanBtn.setOnClickListener {
            extractMain(false)
            handler.postDelayed({ startDetailQueue() },900)
        }
        reloadBtn.setOnClickListener { web.loadUrl("https://www.diretta.it/") }
        copyBtn.setOnClickListener {
            extractMain(false)
            handler.postDelayed({ startDetailQueue(); copyReport() },700)
        }
        web.loadUrl("https://www.diretta.it/")
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWeb(w:WebView, hidden:Boolean){
        w.settings.javaScriptEnabled=true
        w.settings.domStorageEnabled=true
        w.settings.databaseEnabled=true
        w.settings.loadsImagesAutomatically=!hidden
        w.settings.userAgentString=w.settings.userAgentString+" DirettaScanner/0.13.9"
        w.addJavascriptInterface(Bridge(),"DirettaScanner")
        w.webChromeClient=WebChromeClient()
    }

    private fun mainJs(observer:Boolean):String{
        val tail=if(observer) """
          if(!window.__dsObs139){
            window.__dsObs139=true;
            const ob=new MutationObserver(()=>{clearTimeout(window.__dsT139);window.__dsT139=setTimeout(scan,500);});
            ob.observe(document.documentElement,{childList:true,subtree:true,characterData:true});
            setInterval(scan,2500);
          }
        """ else ""
        return """
        (function(){
          function txt(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}
          function first(r,sels){for(const s of sels){const e=r.querySelector(s);if(e)return e;}return null;}
          function expandCompetitions(){
            let n=0;
            const headers=[...document.querySelectorAll('.event__header,[class*="event__header"]')];
            for(const h of headers){
              const low=(String(h.className||'')+' '+txt(h)).toLowerCase();
              if(/menu|drawer|navigation|sidebar|filter|setting|preferit/.test(low))continue;
              const ctl=h.querySelector('[aria-expanded="false"],button[data-testid*="expand"],[role="button"][data-testid*="expand"]');
              if(ctl && ctl.getAttribute('aria-expanded')==='false'){
                try{ctl.click();n++;}catch(e){}
              }
            }
            return n;
          }
          function scan(){
            const expanded=expandCompetitions();
            const rows=[...document.querySelectorAll('.event__match[data-event-row="true"],.event__match')];
            const out=[];let teams=0,times=0;
            for(const r of rows){
              const h=first(r,['.event__homeParticipant','[class*="homeParticipant"]','[class*="participant--home"]']);
              const a=first(r,['.event__awayParticipant','[class*="awayParticipant"]','[class*="participant--away"]']);
              const home=txt(h),away=txt(a);
              if(!home||!away||home===away)continue;
              teams++;
              const te=first(r,['.event__time','[class*="event__time"]']);
              const rawTime=txt(te);
              const tm=(rawTime.match(/(?:^|\s)([01]?\d|2[0-3]):([0-5]\d)(?:\s|$)/)||[]);
              const time=tm.length?String(tm[1]).padStart(2,'0')+':'+tm[2]:'';
              if(time)times++;
              const link=first(r,['a.eventRowLink','a[href*="/partita/calcio/"]']);
              const url=link&&link.href?link.href:'';
              if(!time||!url)continue;
              out.push({home,away,time,url});
            }
            try{DirettaScanner.onMain(JSON.stringify(out),rows.length,teams,times,expanded);}catch(e){}
          }
          scan();
          $tail
          return 'ok';
        })();
        """.trimIndent()
    }

    private fun openOddsTabJs():String = """
      (function(){
        function txt(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}
        const all=[...document.querySelectorAll('a,button,[role="tab"],[role="button"]')];
        let target=null;
        for(const e of all){
          const t=txt(e).toUpperCase();
          const href=(e.getAttribute('href')||'').toLowerCase();
          const test=(e.getAttribute('data-testid')||'').toLowerCase();
          if(t==='QUOTE' || t==='ODDS' || /quote|odds/.test(href) || /quote|odds/.test(test)){
            if(/preferit|favorite|livebet/.test((e.className||'').toString().toLowerCase()))continue;
            target=e; if(t==='QUOTE'||t==='ODDS')break;
          }
        }
        let clicked=false;
        if(target){try{target.click();clicked=true;}catch(e){}}
        try{DirettaScanner.onOddsTab(clicked?1:0, target?txt(target):'', location.href);}catch(e){}
        return clicked?'clicked':'not-found';
      })();
    """.trimIndent()

    private fun snaiDetailJs(finalAttempt:Boolean):String = """
      (function(){
        function txt(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}
        function decimals(s){
          const out=[]; const rx=/(?:^|\s)(\d{1,2}[.,]\d{2})(?=\s|$)/g; let m;
          s=(s||'').replace(/,/g,'.');
          while((m=rx.exec(s))!==null){
            const n=parseFloat(m[1]);
            if(n>=1.01&&n<=99){const v=n.toFixed(2);if(!out.includes(v))out.push(v);}
          }
          return out;
        }
        function meta(e){
          if(!e)return '';
          let s=txt(e)+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.getAttribute('alt')||'')+' '+(e.getAttribute('data-bookmaker-name')||'')+' '+(e.getAttribute('data-testid')||'')+' '+(e.getAttribute('href')||'')+' '+(e.getAttribute('src')||'');
          const im=e.querySelector?e.querySelector('img'):null;
          if(im)s+=' '+(im.getAttribute('alt')||'')+' '+(im.getAttribute('title')||'')+' '+(im.getAttribute('src')||'');
          return s.replace(/\s+/g,' ').trim();
        }
        function isSnai(e){return /(^|[^a-z0-9])snai([^a-z0-9]|$)/i.test(meta(e));}
        let best=null;
        const all=[...document.querySelectorAll('img,a,div,span,button,[data-bookmaker-name],[data-testid]')];
        const markers=all.filter(isSnai);
        for(const marker of markers){
          let p=marker;
          for(let depth=0; p && depth<8; depth++,p=p.parentElement){
            const t=txt(p);
            if(!t || t.length>1400)continue;
            const vals=decimals(t);
            if(vals.length>=3){
              let score=100-depth*8;
              const low=(String(p.className||'')+' '+(p.getAttribute('data-testid')||'')).toLowerCase();
              if(/bookmaker|odds|row|cell/.test(low))score+=12;
              if(vals.length===3)score+=18;
              else if(vals.length<=5)score+=8;
              else score-=Math.min(25,(vals.length-5)*4);
              const cand={score:score,vals:vals.slice(0,3),text:t.slice(0,900),marker:meta(marker).slice(0,250)};
              if(!best||cand.score>best.score)best=cand;
            }
          }
        }
        const body=txt(document.body).slice(0,1800);
        const payload={
          ok:!!best,
          snaiFound:markers.length>0,
          final:${if(finalAttempt) "true" else "false"},
          odds:best?best.vals:[],
          sample:best?('SNAI marker: '+best.marker+' | Riga: '+best.text):body,
          title:document.title||'',
          url:location.href,
          markerCount:markers.length
        };
        try{DirettaScanner.onDetail(JSON.stringify(payload));}catch(e){}
        return 'ok';
      })();
    """.trimIndent()

    private fun installMainObserver(){ web.evaluateJavascript(mainJs(true),null) }
    private fun extractMain(observer:Boolean){ web.evaluateJavascript(mainJs(observer),null) }
    private fun openOddsTab(){ if(currentDetailKey!=null) detailWeb.evaluateJavascript(openOddsTabJs(),null) }
    private fun extractDetailSnai(finalAttempt:Boolean){ if(currentDetailKey!=null) detailWeb.evaluateJavascript(snaiDetailJs(finalAttempt),null) }

    inner class Bridge {
        @JavascriptInterface fun onMain(json:String,seen:Int,teams:Int,times:Int,expanded:Int){
            try{
                val arr=JSONArray(json)
                synchronized(matches){
                    rowsSeen=maxOf(rowsSeen,seen)
                    rowsWithTeams=maxOf(rowsWithTeams,teams)
                    rowsWithTime=maxOf(rowsWithTime,times)
                    autoExpanded+=expanded
                    for(i in 0 until arr.length()){
                        val o=arr.getJSONObject(i)
                        val h=o.optString("home").trim(); val a=o.optString("away").trim()
                        val t=o.optString("time").trim(); val u=o.optString("url").trim()
                        if(h.isBlank()||a.isBlank()||t.isBlank()||u.isBlank())continue
                        if(matches[u]==null)matches[u]=Match(h,a,t,u)
                    }
                }
                runOnUiThread{ updateStatus() }
            }catch(_:Throwable){}
        }

        @JavascriptInterface fun onOddsTab(opened:Int,label:String,url:String){
            if(opened==1) oddsTabsOpened++
        }

        @JavascriptInterface fun onDetail(json:String){
            try{
                val o=JSONObject(json)
                val key=currentDetailKey ?: return
                val odds=o.optJSONArray("odds")
                val ok=odds!=null && odds.length()>=3
                val finalAttempt=o.optBoolean("final",false)
                if(!ok && !finalAttempt) return

                synchronized(matches){
                    val m=matches[key]
                    if(m!=null && !m.attempted){
                        m.attempted=true
                        detailsAttempted++
                        if(ok){
                            m.o1=odds!!.optString(0)
                            m.ox=odds.optString(1)
                            m.o2=odds.optString(2)
                            m.snaiState="AVAILABLE"
                            if(m.o1.isNotBlank()&&m.ox.isNotBlank()&&m.o2.isNotBlank())detailsWithSnai++
                        }else{
                            m.snaiState="NOT_AVAILABLE"
                            detailsWithoutSnai++
                        }
                        if(firstDetailDiagnostic.isBlank()){
                            firstDetailDiagnostic="Titolo: ${o.optString("title")}\nURL: ${o.optString("url")}\nMarker SNAI trovati: ${o.optInt("markerCount",0)}\nCampione: ${o.optString("sample").take(1100)}"
                        }
                    }
                }
                runOnUiThread{
                    currentDetailKey=null
                    detailGeneration++
                    updateStatus()
                    handler.postDelayed({ processNextDetail() },350)
                }
            }catch(_:Throwable){}
        }
    }

    private fun mins(s:String):Int{
        val p=s.split(":");if(p.size!=2)return -1
        return (p[0].toIntOrNull()?:-99)*60+(p[1].toIntOrNull()?:0)
    }

    private fun inSelectedRange(t:String):Boolean{
        val from=mins(fromSpinner.selectedItem?.toString()?:"00:00")
        val to=mins(toSpinner.selectedItem?.toString()?:"23:59")
        val x=mins(t)
        return if(from<=to)x in from..to else x>=from||x<=to
    }

    private fun startDetailQueue(){
        synchronized(matches){
            matches.forEach{(k,m)->if(inSelectedRange(m.time)&&!m.attempted&&!detailQueue.contains(k)&&currentDetailKey!=k)detailQueue.add(k)}
        }
        processNextDetail()
    }

    private fun processNextDetail(){
        if(currentDetailKey!=null)return
        val key=if(detailQueue.isEmpty())null else detailQueue.removeFirst()
        if(key==null){updateStatus();return}
        val m=matches[key]?:run{processNextDetail();return}
        currentDetailKey=key
        detailGeneration++
        val gen=detailGeneration
        status.text="Ricerca SNAI in background… ${detailsAttempted+1} · ${m.time} ${m.home} vs ${m.away}"
        detailWeb.loadUrl(m.url)
        handler.postDelayed({ if(currentDetailKey==key && detailGeneration==gen) openOddsTab() },1800)
        handler.postDelayed({ if(currentDetailKey==key && detailGeneration==gen) extractDetailSnai(false) },4200)
        handler.postDelayed({ if(currentDetailKey==key && detailGeneration==gen) extractDetailSnai(true) },9500)
    }

    private fun selectedMatches():List<Match> = synchronized(matches){
        matches.values.filter{inSelectedRange(it.time)}.sortedWith(compareBy<Match>{mins(it.time)}.thenBy{it.home})
    }

    private fun updateStatus(){
        val sel=selectedMatches()
        val snai=sel.count{it.snaiState=="AVAILABLE"}
        val noSnai=sel.count{it.snaiState=="NOT_AVAILABLE"}
        status.text="Diretta Scanner v0.13.9 · SNAI\nEventi nella fascia: ${sel.size} · conclusi: $detailsAttempted · SNAI: $snai · non disponibili: $noSnai" + if(currentDetailKey!=null) " · scansione in corso" else ""
    }

    private fun report():String{
        val list=selectedMatches()
        val from=fromSpinner.selectedItem?.toString()?:"00:00"
        val to=toSpinner.selectedItem?.toString()?:"23:59"
        return buildString{
            append("DIRETTA SCANNER · SNAI v0.13.9")
            append("\nFASCIA: ").append(from).append(" - ").append(to)
            append("\nEVENTI NELLA FASCIA: ").append(list.size)
            append("\nQUOTE DI RIFERIMENTO: SNAI")
            append("\nPARTITE CON QUOTE SNAI: ").append(list.count{it.snaiState=="AVAILABLE"})
            list.forEachIndexed{idx,m->
                append("\n").append(idx+1).append(". ").append(m.time).append(" · ").append(m.home).append("  vs  ").append(m.away).append("   |   ")
                if(m.snaiState=="AVAILABLE") append("SNAI · 1 ").append(m.o1).append(" · X ").append(m.ox).append(" · 2 ").append(m.o2)
                else if(m.snaiState=="NOT_AVAILABLE") append("SNAI NON DISPONIBILE")
                else append("SNAI DA VERIFICARE")
            }
            append("\n\nDIAGNOSTICA")
            append("\nRighe evento massime viste: ").append(rowsSeen)
            append("\nRighe con casa+ospite: ").append(rowsWithTeams)
            append("\nRighe con orario: ").append(rowsWithTime)
            append("\nSezioni aperte automaticamente: ").append(autoExpanded)
            append("\nSchede QUOTE aperte: ").append(oddsTabsOpened)
            append("\nDettagli partita conclusi: ").append(detailsAttempted)
            append("\nDettagli con SNAI: ").append(detailsWithSnai)
            append("\nDettagli senza SNAI: ").append(detailsWithoutSnai)
            append("\nDettagli ancora in coda: ").append(detailQueue.size + if(currentDetailKey!=null)1 else 0)
            if(firstDetailDiagnostic.isNotBlank())append("\n\nPRIMO DETTAGLIO DIAGNOSTICO\n").append(firstDetailDiagnostic)
            append("\n\nMetodo: elenco DOM -> squadra/orario/link; WebView nascosta -> scheda QUOTE 1X2 -> identificazione riga bookmaker SNAI -> quote 1-X-2. Nessun fallback su altri bookmaker.")
        }
    }

    private fun copyReport(){
        val text=report()
        val cb=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Diretta Scanner report",text))
        Toast.makeText(this,"Report copiato · ${selectedMatches().size} eventi nella fascia",Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy(){
        handler.removeCallbacksAndMessages(null)
        web.destroy();detailWeb.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed(){if(web.canGoBack())web.goBack() else super.onBackPressed()}
}

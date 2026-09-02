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
        var attempted:Boolean=false
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
    private var detailsWithOdds = 0
    private var firstDetailDiagnostic = ""

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
        val da = TextView(this).apply { text="  DALLE  "; textSize=13f }
        val a = TextView(this).apply { text="  ALLE  "; textSize=13f }
        filters.addView(da)
        filters.addView(fromSpinner, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        filters.addView(a)
        filters.addView(toSpinner, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))

        scanBtn = Button(this).apply { text = "APPLICA E SCANSIONA" }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        reloadBtn = Button(this).apply { text = "APRI / RICARICA DIRETTA" }
        copyBtn = Button(this).apply { text = "COPIA REPORT" }
        actions.addView(reloadBtn, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
        actions.addView(copyBtn, LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))

        status = TextView(this).apply {
            text = "Diretta Scanner v0.13.7 · DOM + DETTAGLI IN BACKGROUND\nPagina principale: squadre/orario/link. Dettagli: quote 1-X-2."
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
                if(currentDetailKey!=null){ handler.postDelayed({ extractDetailOdds() }, 1500) }
            }
        }

        scanBtn.setOnClickListener {
            extractMain(false)
            handler.postDelayed({ startDetailQueue() }, 900)
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
        w.settings.userAgentString=w.settings.userAgentString+" DirettaScanner/0.13.7"
        w.addJavascriptInterface(Bridge(),"DirettaScanner")
        w.webChromeClient=WebChromeClient()
    }

    private fun mainJs(observer:Boolean):String{
        val tail=if(observer) """
          if(!window.__dsObs137){
            window.__dsObs137=true;
            const ob=new MutationObserver(()=>{clearTimeout(window.__dsT137);window.__dsT137=setTimeout(scan,500);});
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

    private fun detailJs():String = """
      (function(){
        function txt(e){return e?(e.innerText||e.textContent||'').replace(/\s+/g,' ').trim():'';}
        function decimals(s){
          const out=[]; const rx=/(?:^|\s)(\d{1,2}[.,]\d{2})(?=\s|$)/g; let m;
          s=(s||'').replace(/,/g,'.');
          while((m=rx.exec(s))!==null){const n=parseFloat(m[1]);if(n>=1.01&&n<=99){const v=n.toFixed(2);if(!out.includes(v))out.push(v);}}
          return out;
        }
        function scoreContainer(c){
          const t=txt(c); if(!t||t.length>1800)return null;
          const low=t.toLowerCase();
          let score=0;
          if(/(^|\s)1\s+x\s+2($|\s)/i.test(t)||/1x2/i.test(t))score+=8;
          if(/esito finale|risultato finale|match result|full time result/i.test(low))score+=6;
          const vals=decimals(t); if(vals.length>=3)score+=5;
          if(vals.length>8)score-=5;
          return vals.length>=3?{score:score,vals:vals.slice(0,3),text:t.slice(0,500)}:null;
        }
        let best=null;
        const marketRoots=[...document.querySelectorAll('[class*="odds"],[data-testid*="odds"],[class*="market"],[data-testid*="market"],section,article')];
        for(const c of marketRoots){const x=scoreContainer(c);if(x&&(!best||x.score>best.score))best=x;}
        if(!best){
          const nodes=[...document.querySelectorAll('[data-testid*="odd"],[class*="oddsValue"],[class*="oddsCell"],[class*="oddValue"]')];
          const vals=[];for(const n of nodes){for(const v of decimals(txt(n))){if(!vals.includes(v))vals.push(v);}}
          if(vals.length>=3)best={score:1,vals:vals.slice(0,3),text:'fallback odds nodes'};
        }
        const body=txt(document.body).slice(0,1200);
        const payload={ok:!!best,odds:best?best.vals:[],sample:best?best.text:body,title:document.title||'',url:location.href};
        try{DirettaScanner.onDetail(JSON.stringify(payload));}catch(e){}
        return 'ok';
      })();
    """.trimIndent()

    private fun installMainObserver(){ web.evaluateJavascript(mainJs(true),null) }
    private fun extractMain(observer:Boolean){ web.evaluateJavascript(mainJs(observer),null) }
    private fun extractDetailOdds(){ if(currentDetailKey!=null) detailWeb.evaluateJavascript(detailJs(),null) }

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

        @JavascriptInterface fun onDetail(json:String){
            try{
                val o=JSONObject(json)
                val key=currentDetailKey ?: return
                val odds=o.optJSONArray("odds")
                synchronized(matches){
                    val m=matches[key]
                    if(m!=null){
                        m.attempted=true
                        detailsAttempted++
                        if(odds!=null && odds.length()>=3){
                            m.o1=odds.optString(0);m.ox=odds.optString(1);m.o2=odds.optString(2)
                            if(m.o1.isNotBlank()&&m.ox.isNotBlank()&&m.o2.isNotBlank())detailsWithOdds++
                        }
                        if(firstDetailDiagnostic.isBlank()){
                            firstDetailDiagnostic="Titolo: ${o.optString("title")}\nURL: ${o.optString("url")}\nCampione: ${o.optString("sample").take(700)}"
                        }
                    }
                }
                runOnUiThread{
                    currentDetailKey=null
                    updateStatus()
                    handler.postDelayed({ processNextDetail() },350)
                }
            }catch(_:Throwable){ runOnUiThread{currentDetailKey=null;handler.postDelayed({processNextDetail()},350)} }
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
            matches.forEach{(k,m)->if(inSelectedRange(m.time)&&!m.attempted&&m.o1.isBlank()&&!detailQueue.contains(k)&&currentDetailKey!=k)detailQueue.add(k)}
        }
        processNextDetail()
    }

    private fun processNextDetail(){
        if(currentDetailKey!=null)return
        val key=if(detailQueue.isEmpty())null else detailQueue.removeFirst()
        if(key==null){updateStatus();return}
        val m=matches[key]?:run{processNextDetail();return}
        currentDetailKey=key
        status.text="Scansione dettagli in background… ${detailsAttempted+1} · ${m.time} ${m.home} vs ${m.away}"
        detailWeb.loadUrl(m.url)
        handler.postDelayed({ if(currentDetailKey==key) extractDetailOdds() },4500)
    }

    private fun selectedMatches():List<Match> = synchronized(matches){
        matches.values.filter{inSelectedRange(it.time)}.sortedWith(compareBy<Match>{mins(it.time)}.thenBy{it.home})
    }

    private fun updateStatus(){
        val sel=selectedMatches(); val complete=sel.count{it.o1.isNotBlank()&&it.ox.isNotBlank()&&it.o2.isNotBlank()}
        status.text="Diretta Scanner v0.13.7 · DOM + DETTAGLI BACKGROUND\nEventi nella fascia: ${sel.size} · dettagli provati: $detailsAttempted · con 1-X-2: $complete" + if(currentDetailKey!=null) " · scansione in corso" else ""
    }

    private fun report():String{
        val list=selectedMatches()
        val complete=list.filter{it.o1.isNotBlank()&&it.ox.isNotBlank()&&it.o2.isNotBlank()}
        val from=fromSpinner.selectedItem?.toString()?:"00:00"
        val to=toSpinner.selectedItem?.toString()?:"23:59"
        return buildString{
            append("DIRETTA SCANNER · DOM + DETTAGLI v0.13.7")
            append("\nFASCIA: ").append(from).append(" - ").append(to)
            append("\nEVENTI NELLA FASCIA: ").append(list.size)
            append("\nPARTITE CON 1-X-2: ").append(complete.size)
            complete.forEachIndexed{idx,m->append("\n").append(idx+1).append(". ").append(m.time).append(" · ").append(m.home).append("  vs  ").append(m.away).append("   |   1 ").append(m.o1).append(" · X ").append(m.ox).append(" · 2 ").append(m.o2)}
            append("\n\nDIAGNOSTICA")
            append("\nRighe evento massime viste: ").append(rowsSeen)
            append("\nRighe con casa+ospite: ").append(rowsWithTeams)
            append("\nRighe con orario: ").append(rowsWithTime)
            append("\nSezioni aperte automaticamente: ").append(autoExpanded)
            append("\nDettagli partita tentati: ").append(detailsAttempted)
            append("\nDettagli con almeno 3 quote: ").append(detailsWithOdds)
            append("\nDettagli ancora in coda: ").append(detailQueue.size + if(currentDetailKey!=null)1 else 0)
            if(firstDetailDiagnostic.isNotBlank())append("\n\nPRIMO DETTAGLIO DIAGNOSTICO\n").append(firstDetailDiagnostic)
            append("\n\nMetodo: elenco principale DOM -> squadra/orario/link; pagina dettaglio caricata in WebView nascosta -> ricerca quote 1-X-2. Nessun OCR e nessuna apertura manuale dei match.")
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

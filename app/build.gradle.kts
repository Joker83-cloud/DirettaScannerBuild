plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.joker.direttascannerbuild"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joker.direttascannerbuild"
        minSdk = 24
        targetSdk = 35
        versionCode = 151
        versionName = "0.15.1-detail-web-fix"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = rootProject.file("stable-debug.keystore")
            storePassword = "android"
            keyAlias = "direttascanner"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val patchV0151 = tasks.register("patchV0151") {
    doLast {
        val src = file("src/main/java/com/joker/direttascannerbuild/MainActivity.kt")
        var s = src.readText()

        s = s.replace("v0.14.7 REAL AUTO-OPEN", "v0.15.1 DETAIL WEB FIX")
            .replace("DirettaScanner/0.14.7-real-auto-open", "DirettaScanner/0.15.1-detail-web-fix")

        val expandRx = Regex("function expand\\(\\)\\{.*?return \\{clicked,headers:headers.length,closed,before\\};\\}", RegexOption.DOT_MATCHES_ALL)
        val newExpand = """function expand(){const before=document.querySelectorAll('.event__match[data-event-row=\"true\"],.event__match').length;if(!window.__dsOpened151)window.__dsOpened151=new WeakSet();let closed=0,clicked=0;const specific=[...document.querySelectorAll('.wclIcon__leagueShowMoreCont,[class*=\"leagueShowMoreCont\"],[class*=\"leagueShowMore\"]')].filter(e=>!e.closest('.event__match'));for(const c of specific){if(window.__dsOpened151.has(c))continue;const trg=c.closest('button,[role=\"button\"]')||c.querySelector('button,[role=\"button\"]')||c;const aria=(trg.getAttribute&&trg.getAttribute('aria-expanded'))||'';const cls=(String(trg.className||'')+' '+String(c.className||'')).toLowerCase();const label=((trg.getAttribute&&trg.getAttribute('aria-label'))||'')+' '+((trg.getAttribute&&trg.getAttribute('title'))||'');const likelyClosed=aria==='false'||/closed|collapsed|showmore|leagueshowmore/.test(cls)||/show|display|mostra|espandi/i.test(label);if(!likelyClosed)continue;closed++;try{trg.click();window.__dsOpened151.add(c);clicked++;}catch(e){}}return {clicked,headers:specific.length,closed,before};}"""
        if (!expandRx.containsMatchIn(s)) error("v0.14.7 expand function not found")
        s = expandRx.replaceFirst(s, Regex.escapeReplacement(newExpand))

        val oldRange = "    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);return if(x<0||f<0||z<0)false else if(f<=z)x>=f&&x<z else x>=f||x<z}"
        val newRange = "    private fun currentMinute():Int{val c=java.util.Calendar.getInstance();return c.get(java.util.Calendar.HOUR_OF_DAY)*60+c.get(java.util.Calendar.MINUTE)}\n    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);if(x<0||f<0||z<0)return false;val inside=if(f<=z)x>=f&&x<z else x>=f||x<z;return inside&&x>currentMinute()}"
        if (!s.contains(oldRange)) error("v0.14.7 range function not found")
        s = s.replace(oldRange, newRange)

        val oldDiagBridge = "        @JavascriptInterface fun onExpandDiag(headers:Int,closed:Int,clicked:Int,before:Int,after:Int){synchronized(matches){leagueHeadersSeen=maxOf(leagueHeadersSeen,headers);leaguesClosedSeen=maxOf(leaguesClosedSeen,closed);rowsBeforeExpand=maxOf(rowsBeforeExpand,before);rowsAfterExpand=maxOf(rowsAfterExpand,after)}}"
        val newDiagBridge = "        @JavascriptInterface fun onExpandDiag(headers:Int,closed:Int,clicked:Int,before:Int,after:Int){synchronized(matches){leagueHeadersSeen=maxOf(leagueHeadersSeen,headers);leaguesClosedSeen=maxOf(leaguesClosedSeen,closed);if(rowsBeforeExpand==0||before<rowsBeforeExpand)rowsBeforeExpand=before;rowsAfterExpand=maxOf(rowsAfterExpand,after)}}"
        if (!s.contains(oldDiagBridge)) error("expand diagnostic bridge not found")
        s = s.replace(oldDiagBridge, newDiagBridge)

        val varsAnchor = "private var leagueHeadersSeen=0;private var leaguesClosedSeen=0;private var rowsBeforeExpand=0;private var rowsAfterExpand=0"
        val varsNew = varsAnchor + ";private var detailLoadRequested=0;private var detailPageStarted=0;private var detailPageFinished=0;private var detailLoadErrors=0;private var detailLastUrl=\"\""
        if (!s.contains(varsAnchor)) error("detail diagnostic vars anchor not found")
        s = s.replace(varsAnchor, varsNew)

        val webAnchor = "detailWeb=WebView(this).apply{visibility=View.INVISIBLE};historyWeb=WebView(this).apply{visibility=View.INVISIBLE}"
        val webNew = "detailWeb=WebView(this).apply{visibility=View.VISIBLE;alpha=0f};historyWeb=WebView(this).apply{visibility=View.VISIBLE;alpha=0f}"
        if (!s.contains(webAnchor)) error("hidden webviews anchor not found")
        s = s.replace(webAnchor, webNew)

        val detailClientRx = Regex("        detailWeb\\.webViewClient=object:WebViewClient\\(\\)\\{.*?\\n        historyWeb\\.webViewClient=", RegexOption.DOT_MATCHES_ALL)
        val detailClientNew = """        detailWeb.webViewClient=object:WebViewClient(){
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?){super.onPageStarted(view,url,favicon);detailPageStarted++;detailLastUrl=url?:\"\";runOnUiThread{updateStatus()}}
            override fun onPageFinished(view:WebView?,url:String?){super.onPageFinished(view,url);detailPageFinished++;detailLastUrl=url?:\"\";val key=currentDetailKey?:return;val gen=detailGeneration;val u=url?:\"\";if(u.contains(\"/quote/quote-1x2/\")){handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},1200);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},3200);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},6000)}else{handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)openOddsTab()},800);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},4500)}}
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,error:android.webkit.WebResourceError?){super.onReceivedError(view,request,error);if(request?.isForMainFrame==true){detailLoadErrors++;detailLastUrl=request.url?.toString()?:\"\";runOnUiThread{updateStatus()}}}
        }
        historyWeb.webViewClient="""
        if (!detailClientRx.containsMatchIn(s)) error("detail web client not found")
        s = detailClientRx.replaceFirst(s, Regex.escapeReplacement(detailClientNew))

        val oldClick = "        scanBtn.setOnClickListener{extractMain(false);handler.postDelayed({extractMain(false)},800);handler.postDelayed({extractMain(false)},1600);handler.postDelayed({extractMain(false)},2600);handler.postDelayed({extractMain(false)},3800);handler.postDelayed({startDetailQueue()},4600)}"
        val newClick = "        scanBtn.setOnClickListener{prepareDetailWebForScan();extractMain(false);handler.postDelayed({extractMain(false)},800);handler.postDelayed({extractMain(false)},1600);handler.postDelayed({extractMain(false)},2600);handler.postDelayed({extractMain(false)},3800);handler.postDelayed({startDetailQueue()},4600);handler.postDelayed({forceStartDetailQueue()},5200);handler.postDelayed({forceStartDetailQueue()},7500);handler.postDelayed({forceStartDetailQueue()},11000)}"
        if (!s.contains(oldClick)) error("scan listener not found")
        s = s.replace(oldClick, newClick)

        val startAnchor = "    private fun startDetailQueue(){synchronized(matches){matches.forEach{(k,m)->if(inSelectedRange(m.time)&&!m.attempted&&!detailQueue.contains(k)&&currentDetailKey!=k)detailQueue.add(k)}};processNextDetail()}"
        val startNew = startAnchor + "\n    private fun prepareDetailWebForScan(){currentDetailKey=null;detailGeneration++;detailQueue.clear();detailWeb.stopLoading();detailWeb.clearHistory();detailWeb.loadUrl(\"about:blank\")}\n    private fun forceStartDetailQueue(){runOnUiThread{if(currentDetailKey==null){if(detailQueue.isEmpty())startDetailQueue() else processNextDetail()}}}"
        if (!s.contains(startAnchor)) error("startDetailQueue not found")
        s = s.replace(startAnchor, startNew)

        val procRx = Regex("    private fun processNextDetail\\(\\)\\{.*?handler\\.postDelayed\\(\\{if\\(currentDetailKey==key&&detailGeneration==gen\\)extractDetailSnai\\(true\\)\\},15000\\)\\}", RegexOption.DOT_MATCHES_ALL)
        val procNew = """    private fun processNextDetail(){if(currentDetailKey!=null)return;val key=if(detailQueue.isEmpty())null else detailQueue.removeFirst();if(key==null){updateStatus();processNextHistory();return};val m=matches[key]?:run{processNextDetail();return};currentDetailKey=key;detailGeneration++;val gen=detailGeneration;val total=detailsAttempted+detailQueue.size+1;status.text=\"SNAI ${'$'}{detailsAttempted+1}/${'$'}total · ${'$'}{m.time} ${'$'}{m.home} vs ${'$'}{m.away}\";val base=m.url.substringBefore(\"?\").trimEnd('/');val query=m.url.substringAfter(\"?\",\"\");val qurl=if(base.contains(\"/quote/\"))m.url else base+\"/quote/quote-1x2/finale/\"+(if(query.isNotBlank())\"?\"+query else \"\");detailLoadRequested++;detailLastUrl=qurl;detailWeb.post{if(currentDetailKey==key&&detailGeneration==gen)detailWeb.loadUrl(qurl)};handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(false)},8500);handler.postDelayed({if(currentDetailKey==key&&detailGeneration==gen)extractDetailSnai(true)},15000)}"""
        if (!procRx.containsMatchIn(s)) error("process detail not found")
        s = procRx.replaceFirst(s, Regex.escapeReplacement(procNew))

        s = s.replace("AUTO-OPEN: intestazioni ${'$'}leagueHeadersSeen", "AUTO-OPEN: controlli campionato ${'$'}leagueHeadersSeen")
        s = s.replace("FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa)", "FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa · solo partite non iniziate)")

        val reportNeedle = "Schede QUOTE: ${'$'}oddsTabsOpened · dettagli: ${'$'}detailsAttempted · SNAI: ${'$'}detailsWithSnai · senza SNAI: ${'$'}detailsWithoutSnai"
        val reportNew = reportNeedle + "\\nDETAIL WEB: richieste ${'$'}detailLoadRequested · started ${'$'}detailPageStarted · finished ${'$'}detailPageFinished · errori ${'$'}detailLoadErrors · ultima URL ${'$'}detailLastUrl"
        if (!s.contains(reportNeedle)) error("report detail diagnostics not found")
        s = s.replace(reportNeedle, reportNew)

        src.writeText(s)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchV0151)
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}

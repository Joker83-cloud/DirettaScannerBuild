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
        versionCode = 153
        versionName = "0.15.3-fast-persistent"
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
        getByName("debug") { signingConfig = signingConfigs.getByName("stableDebug") }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val patchV0153 = tasks.register("patchV0153") {
    doLast {
        val src = file("src/main/java/com/joker/direttascannerbuild/MainActivity.kt")
        var s = src.readText()

        s = s.replace("v0.14.7 REAL AUTO-OPEN", "v0.15.3 FAST + SAVE")
            .replace("DirettaScanner/0.14.7-real-auto-open", "DirettaScanner/0.15.3-fast-persistent")

        val expandRx = Regex("function expand\\(\\)\\{.*?return \\{clicked,headers:headers.length,closed,before\\};\\}", RegexOption.DOT_MATCHES_ALL)
        val newExpand = """function expand(){const before=document.querySelectorAll('.event__match[data-event-row=\"true\"],.event__match').length;if(!window.__dsOpened153)window.__dsOpened153=new WeakSet();let closed=0,clicked=0;const specific=[...document.querySelectorAll('.wclIcon__leagueShowMoreCont,[class*=\"leagueShowMoreCont\"],[class*=\"leagueShowMore\"]')].filter(e=>!e.closest('.event__match'));for(const c of specific){if(window.__dsOpened153.has(c))continue;const trg=c.closest('button,[role=\"button\"]')||c.querySelector('button,[role=\"button\"]')||c;const aria=(trg.getAttribute&&trg.getAttribute('aria-expanded'))||'';const cls=(String(trg.className||'')+' '+String(c.className||'')).toLowerCase();const label=((trg.getAttribute&&trg.getAttribute('aria-label'))||'')+' '+((trg.getAttribute&&trg.getAttribute('title'))||'');const likelyClosed=aria==='false'||/closed|collapsed|showmore|leagueshowmore/.test(cls)||/show|display|mostra|espandi/i.test(label);if(!likelyClosed)continue;closed++;try{trg.click();window.__dsOpened153.add(c);clicked++;}catch(e){}}return {clicked,headers:specific.length,closed,before};}"""
        if (!expandRx.containsMatchIn(s)) error("expand function not found")
        s = expandRx.replaceFirst(s, Regex.escapeReplacement(newExpand))

        val oldRange = "    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);return if(x<0||f<0||z<0)false else if(f<=z)x>=f&&x<z else x>=f||x<z}"
        val newRange = "    private fun currentMinute():Int{val c=java.util.Calendar.getInstance();return c.get(java.util.Calendar.HOUR_OF_DAY)*60+c.get(java.util.Calendar.MINUTE)}\n    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);if(x<0||f<0||z<0)return false;val inside=if(f<=z)x>=f&&x<z else x>=f||x<z;return inside&&x>currentMinute()}"
        if (!s.contains(oldRange)) error("range function not found")
        s = s.replace(oldRange, newRange)

        val oldDiagBridge = "        @JavascriptInterface fun onExpandDiag(headers:Int,closed:Int,clicked:Int,before:Int,after:Int){synchronized(matches){leagueHeadersSeen=maxOf(leagueHeadersSeen,headers);leaguesClosedSeen=maxOf(leaguesClosedSeen,closed);rowsBeforeExpand=maxOf(rowsBeforeExpand,before);rowsAfterExpand=maxOf(rowsAfterExpand,after)}}"
        val newDiagBridge = "        @JavascriptInterface fun onExpandDiag(headers:Int,closed:Int,clicked:Int,before:Int,after:Int){synchronized(matches){leagueHeadersSeen=maxOf(leagueHeadersSeen,headers);leaguesClosedSeen=maxOf(leaguesClosedSeen,closed);if(rowsBeforeExpand==0||before<rowsBeforeExpand)rowsBeforeExpand=before;rowsAfterExpand=maxOf(rowsAfterExpand,after)}}"
        if (!s.contains(oldDiagBridge)) error("expand diagnostic bridge not found")
        s = s.replace(oldDiagBridge, newDiagBridge)

        val oldClick = "        scanBtn.setOnClickListener{extractMain(false);handler.postDelayed({extractMain(false)},800);handler.postDelayed({extractMain(false)},1600);handler.postDelayed({extractMain(false)},2600);handler.postDelayed({extractMain(false)},3800);handler.postDelayed({startDetailQueue()},4600)}"
        val newClick = "        scanBtn.setOnClickListener{resetDetailEngineForScan();extractMain(false);handler.postDelayed({extractMain(false)},600);handler.postDelayed({extractMain(false)},1200);handler.postDelayed({extractMain(false)},2000);handler.postDelayed({extractMain(false)},3000);handler.postDelayed({startDetailQueue()},3400);handler.postDelayed({forceStartDetailQueue()},3900);handler.postDelayed({forceStartDetailQueue()},5500)}"
        if (!s.contains(oldClick)) error("scan listener not found")
        s = s.replace(oldClick, newClick)

        val queueAnchor = "    private fun startDetailQueue(){synchronized(matches){matches.forEach{(k,m)->if(inSelectedRange(m.time)&&!m.attempted&&!detailQueue.contains(k)&&currentDetailKey!=k)detailQueue.add(k)}};processNextDetail()}"
        val queueNew = queueAnchor + "\n    private fun resetDetailEngineForScan(){currentDetailKey=null;detailGeneration++;detailQueue.clear();detailWeb.stopLoading();detailWeb.loadUrl(\"about:blank\")}\n    private fun forceStartDetailQueue(){if(currentDetailKey==null){if(detailQueue.isEmpty())startDetailQueue() else processNextDetail()}}"
        if (!s.contains(queueAnchor)) error("detail queue anchor not found")
        s = s.replace(queueAnchor, queueNew)

        val exactHistoryCheck = "if((phase==='HOME'?norm(home):norm(away))!==target)continue;out.push"
        val relaxedHistoryCheck = "const side=phase==='HOME'?norm(home):norm(away);if(!(side===target||side.startsWith(target)||target.startsWith(side)))continue;out.push"
        if (!s.contains(exactHistoryCheck)) error("history side check not found")
        s = s.replace(exactHistoryCheck, relaxedHistoryCheck, true)

        s = s.replace("if(out.length<3&&!window.__dsMore140){", "if(out.length<3&&(window.__dsMore153||0)<2){", true)
        s = s.replace("window.__dsMore140=true;", "window.__dsMore153=(window.__dsMore153||0)+1;", true)

        val oldInsufficient = "m.historyNote=\"\$phase: solo \${arr.length()} partite\";"
        val newInsufficient = "m.historyNote=phase+\": solo \"+arr.length()+\" partite · righe DOM \"+o.optInt(\"rows\",0);saveState();"
        if (!s.contains(oldInsufficient)) error("history insufficient note not found")
        s = s.replace(oldInsufficient, newInsufficient, true)

        s = s.replace("handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(false)},1800);handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(false)},3500);handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(true)},6000)",
            "handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(false)},650);handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(false)},1400);handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(true)},3000)")

        val oldFinish = "    private fun finishHistoryMatch(){runOnUiThread{currentHistoryKey=null;historyPhase=\"\";historyGeneration++;updateStatus();handler.postDelayed({processNextHistory()},300)}}"
        val newFinish = "    private fun finishHistoryMatch(){saveState();runOnUiThread{currentHistoryKey=null;historyPhase=\"\";historyGeneration++;historyWeb.stopLoading();historyWeb.loadUrl(\"about:blank\");updateStatus();handler.postDelayed({processNextHistory()},100)}}"
        if (!s.contains(oldFinish)) error("finishHistoryMatch not found")
        s = s.replace(oldFinish, newFinish)

        val oldProcessHistory = "    private fun processNextHistory(){if(currentHistoryKey!=null)return;val key=if(historyQueue.isEmpty())null else historyQueue.removeFirst();if(key==null){updateStatus();return};val m=matches[key]?:run{processNextHistory();return};currentHistoryKey=key;historyPhase=\"HOME\";historyGeneration++;m.historyState=\"RUNNING\";status.text=\"PROFILO 70%… ${'$'}{m.home} vs ${'$'}{m.away} · storico casa\";historyWeb.loadUrl(resultsUrl(m.homeUrl))}"
        val newProcessHistory = "    private fun processNextHistory(){if(currentHistoryKey!=null)return;val key=if(historyQueue.isEmpty())null else historyQueue.removeFirst();if(key==null){updateStatus();saveState();return};val m=matches[key]?:run{processNextHistory();return};currentHistoryKey=key;historyPhase=\"HOME\";historyGeneration++;val gen=historyGeneration;m.historyState=\"RUNNING\";status.text=\"PROFILO 70%… ${'$'}{m.home} vs ${'$'}{m.away} · storico casa\";historyWeb.loadUrl(resultsUrl(m.homeUrl));handler.postDelayed({if(currentHistoryKey==key&&historyGeneration==gen)extractHistory(true)},4500)}"
        if (!s.contains(oldProcessHistory)) error("processNextHistory not found")
        s = s.replace(oldProcessHistory, newProcessHistory)

        s = s.replace("handler.postDelayed({processNextDetail()},250)", "handler.postDelayed({processNextDetail()},120)")

        val onDetailTail = "runOnUiThread{currentDetailKey=null;detailGeneration++;updateStatus();handler.postDelayed({processNextDetail()},120);handler.postDelayed({processNextHistory()},200)}"
        val onDetailTailNew = "saveState();runOnUiThread{currentDetailKey=null;detailGeneration++;detailWeb.stopLoading();detailWeb.loadUrl(\"about:blank\");updateStatus();handler.postDelayed({processNextDetail()},120);handler.postDelayed({processNextHistory()},120)}"
        if (s.contains(onDetailTail)) s = s.replace(onDetailTail, onDetailTailNew)

        val persistAnchor = "    private fun copyReport(){val t=report();val cb=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cb.setPrimaryClip(ClipData.newPlainText(\"Diretta Scanner report\",t));Toast.makeText(this,\"Report copiato · profilo superato: ${'$'}profilePassed\",Toast.LENGTH_SHORT).show()}"
        val persistCode = """
    private fun saveState(){try{val root=JSONObject();val arr=JSONArray();synchronized(matches){for((k,m) in matches){val o=JSONObject();o.put("k",k);o.put("home",m.home);o.put("away",m.away);o.put("time",m.time);o.put("url",m.url);o.put("o1",m.o1);o.put("ox",m.ox);o.put("o2",m.o2);o.put("attempted",m.attempted);o.put("snaiState",m.snaiState);o.put("homeUrl",m.homeUrl);o.put("awayUrl",m.awayUrl);o.put("historyState",m.historyState);o.put("homeGa3",m.homeGa3);o.put("awayLoss3",m.awayLoss3);o.put("awayGa3",m.awayGa3);o.put("profilePass",m.profilePass);o.put("historyNote",m.historyNote);arr.put(o)}};root.put("matches",arr);root.put("rowsSeen",rowsSeen);root.put("rowsWithTeams",rowsWithTeams);root.put("rowsWithTime",rowsWithTime);root.put("autoExpanded",autoExpanded);root.put("detailsAttempted",detailsAttempted);root.put("detailsWithSnai",detailsWithSnai);root.put("detailsWithoutSnai",detailsWithoutSnai);root.put("oddsTabsOpened",oddsTabsOpened);root.put("historyCompleted",historyCompleted);root.put("profilePassed",profilePassed);root.put("historyUnavailable",historyUnavailable);root.put("firstDetailDiagnostic",firstDetailDiagnostic);root.put("firstHistoryDiagnostic",firstHistoryDiagnostic);root.put("fromPos",fromSpinner.selectedItemPosition);root.put("toPos",toSpinner.selectedItemPosition);getSharedPreferences("diretta_scanner_state",Context.MODE_PRIVATE).edit().putString("state",root.toString()).apply()}catch(_:Throwable){}}
    private fun restoreState(){try{val raw=getSharedPreferences("diretta_scanner_state",Context.MODE_PRIVATE).getString("state",null)?:return;val root=JSONObject(raw);val arr=root.optJSONArray("matches")?:JSONArray();synchronized(matches){matches.clear();for(i in 0 until arr.length()){val o=arr.optJSONObject(i)?:continue;val m=Match(o.optString("home"),o.optString("away"),o.optString("time"),o.optString("url"),o.optString("o1"),o.optString("ox"),o.optString("o2"),o.optBoolean("attempted"),o.optString("snaiState"),o.optString("homeUrl"),o.optString("awayUrl"),o.optString("historyState"),o.optInt("homeGa3",-1),o.optInt("awayLoss3",-1),o.optInt("awayGa3",-1),o.optBoolean("profilePass"),o.optString("historyNote"));val k=o.optString("k",m.url);if(k.isNotBlank())matches[k]=m}};rowsSeen=root.optInt("rowsSeen",0);rowsWithTeams=root.optInt("rowsWithTeams",0);rowsWithTime=root.optInt("rowsWithTime",0);autoExpanded=root.optInt("autoExpanded",0);detailsAttempted=root.optInt("detailsAttempted",0);detailsWithSnai=root.optInt("detailsWithSnai",0);detailsWithoutSnai=root.optInt("detailsWithoutSnai",0);oddsTabsOpened=root.optInt("oddsTabsOpened",0);historyCompleted=root.optInt("historyCompleted",0);profilePassed=root.optInt("profilePassed",0);historyUnavailable=root.optInt("historyUnavailable",0);firstDetailDiagnostic=root.optString("firstDetailDiagnostic");firstHistoryDiagnostic=root.optString("firstHistoryDiagnostic");val fp=root.optInt("fromPos",fromSpinner.selectedItemPosition);val tp=root.optInt("toPos",toSpinner.selectedItemPosition);if(fp in 0 until fromSpinner.count)fromSpinner.setSelection(fp);if(tp in 0 until toSpinner.count)toSpinner.setSelection(tp)}catch(_:Throwable){}}
"""
        if (!s.contains(persistAnchor)) error("copyReport anchor not found")
        s = s.replace(persistAnchor, persistAnchor + persistCode)

        val loadAnchor = "        web.loadUrl(\"https://www.diretta.it/\")"
        if (!s.contains(loadAnchor)) error("initial web load anchor not found")
        s = s.replace(loadAnchor, "        restoreState();updateStatus();web.loadUrl(\"https://www.diretta.it/\")", true)

        val destroyAnchor = "    override fun onDestroy(){handler.removeCallbacksAndMessages(null);web.destroy();detailWeb.destroy();historyWeb.destroy();super.onDestroy()}"
        val lifecycle = "    override fun onPause(){saveState();super.onPause()}\n    override fun onStop(){saveState();super.onStop()}\n    override fun onDestroy(){saveState();handler.removeCallbacksAndMessages(null);web.destroy();detailWeb.destroy();historyWeb.destroy();super.onDestroy()}"
        if (!s.contains(destroyAnchor)) error("onDestroy anchor not found")
        s = s.replace(destroyAnchor, lifecycle)

        s = s.replace("AUTO-OPEN: intestazioni ${'$'}leagueHeadersSeen", "AUTO-OPEN: controlli campionato ${'$'}leagueHeadersSeen")
        s = s.replace("FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa)", "FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa · solo partite non iniziate)")

        src.writeText(s)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(patchV0153) }

dependencies { implementation("androidx.appcompat:appcompat:1.7.0") }

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
        versionCode = 152
        versionName = "0.15.2-history-fix"
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

val patchV0152 = tasks.register("patchV0152") {
    doLast {
        val src = file("src/main/java/com/joker/direttascannerbuild/MainActivity.kt")
        var s = src.readText()

        s = s.replace("v0.14.7 REAL AUTO-OPEN", "v0.15.2 HISTORY FIX")
            .replace("DirettaScanner/0.14.7-real-auto-open", "DirettaScanner/0.15.2-history-fix")

        val expandRx = Regex("function expand\\(\\)\\{.*?return \\{clicked,headers:headers.length,closed,before\\};\\}", RegexOption.DOT_MATCHES_ALL)
        val newExpand = """function expand(){const before=document.querySelectorAll('.event__match[data-event-row=\"true\"],.event__match').length;if(!window.__dsOpened152)window.__dsOpened152=new WeakSet();let closed=0,clicked=0;const specific=[...document.querySelectorAll('.wclIcon__leagueShowMoreCont,[class*=\"leagueShowMoreCont\"],[class*=\"leagueShowMore\"]')].filter(e=>!e.closest('.event__match'));for(const c of specific){if(window.__dsOpened152.has(c))continue;const trg=c.closest('button,[role=\"button\"]')||c.querySelector('button,[role=\"button\"]')||c;const aria=(trg.getAttribute&&trg.getAttribute('aria-expanded'))||'';const cls=(String(trg.className||'')+' '+String(c.className||'')).toLowerCase();const label=((trg.getAttribute&&trg.getAttribute('aria-label'))||'')+' '+((trg.getAttribute&&trg.getAttribute('title'))||'');const likelyClosed=aria==='false'||/closed|collapsed|showmore|leagueshowmore/.test(cls)||/show|display|mostra|espandi/i.test(label);if(!likelyClosed)continue;closed++;try{trg.click();window.__dsOpened152.add(c);clicked++;}catch(e){}}return {clicked,headers:specific.length,closed,before};}"""
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
        val newClick = "        scanBtn.setOnClickListener{resetDetailEngineForScan();extractMain(false);handler.postDelayed({extractMain(false)},800);handler.postDelayed({extractMain(false)},1600);handler.postDelayed({extractMain(false)},2600);handler.postDelayed({extractMain(false)},3800);handler.postDelayed({startDetailQueue()},4600);handler.postDelayed({forceStartDetailQueue()},5200);handler.postDelayed({forceStartDetailQueue()},7000);handler.postDelayed({forceStartDetailQueue()},10000)}"
        if (!s.contains(oldClick)) error("scan listener not found")
        s = s.replace(oldClick, newClick)

        val queueAnchor = "    private fun startDetailQueue(){synchronized(matches){matches.forEach{(k,m)->if(inSelectedRange(m.time)&&!m.attempted&&!detailQueue.contains(k)&&currentDetailKey!=k)detailQueue.add(k)}};processNextDetail()}"
        val queueNew = queueAnchor + "\n    private fun resetDetailEngineForScan(){currentDetailKey=null;detailGeneration++;detailQueue.clear();detailWeb.stopLoading();detailWeb.loadUrl(\"about:blank\")}\n    private fun forceStartDetailQueue(){if(currentDetailKey==null){if(detailQueue.isEmpty())startDetailQueue() else processNextDetail()}}"
        if (!s.contains(queueAnchor)) error("detail queue anchor not found")
        s = s.replace(queueAnchor, queueNew)

        val varsAnchor = "    private var leagueHeadersSeen=0;private var leaguesClosedSeen=0;private var rowsBeforeExpand=0;private var rowsAfterExpand=0"
        val varsNew = varsAnchor + ";private var historyRowsSeen=0;private var historyScoredSeen=0;private var historySideMatched=0"
        if (!s.contains(varsAnchor)) error("diagnostic vars not found")
        s = s.replace(varsAnchor, varsNew)

        val oldHistHead = "        val key=currentHistoryKey?:return \"'no-key'\";val m=matches[key]?:return \"'no-match'\";val team=if(historyPhase==\"HOME\")m.home else m.away;val fin=if(finalAttempt)\"true\" else \"false\""
        val newHistHead = "        val key=currentHistoryKey?:return \"'no-key'\";val m=matches[key]?:return \"'no-match'\";val team=if(historyPhase==\"HOME\")m.home else m.away;val teamUrl=if(historyPhase==\"HOME\")m.homeUrl else m.awayUrl;val targetId=teamUrl.trimEnd('/').substringAfterLast('/');val fin=if(finalAttempt)\"true\" else \"false\""
        if (!s.contains(oldHistHead)) error("history head not found")
        s = s.replace(oldHistHead, newHistHead, true)

        val oldTarget = "const target=norm('\${jsEscape(team)}'),phase='\$historyPhase',rows=[...document.querySelectorAll('.event__match[data-event-row=\\\"true\\\"],.event__match')],out=[];"
        val newTarget = "const target=norm('\${jsEscape(team)}'),targetId='\${jsEscape(targetId)}',phase='\$historyPhase',rows=[...document.querySelectorAll('.event__match[data-event-row=\\\"true\\\"],.event__match')],out=[];let scored=0,sideMatched=0;"
        if (!s.contains(oldTarget)) error("history target block not found")
        s = s.replace(oldTarget, newTarget, true)

        val oldSide = "if((phase==='HOME'?norm(home):norm(away))!==target)continue;out.push"
        val newSide = "scored++;const homeHref=(he&&he.closest('a')?he.closest('a').getAttribute('href'):'')||(he&&he.querySelector('a')?he.querySelector('a').getAttribute('href'):'')||'';const awayHref=(ae&&ae.closest('a')?ae.closest('a').getAttribute('href'):'')||(ae&&ae.querySelector('a')?ae.querySelector('a').getAttribute('href'):'')||'';const homeById=!!targetId&&homeHref.includes(targetId),awayById=!!targetId&&awayHref.includes(targetId);const nh=norm(home),na=norm(away);const homeByName=nh===target||nh.startsWith(target)||target.startsWith(nh);const awayByName=na===target||na.startsWith(target)||target.startsWith(na);const sideOk=phase==='HOME'?(homeById||homeByName):(awayById||awayByName);if(!sideOk)continue;sideMatched++;out.push"
        if (!s.contains(oldSide)) error("history side matcher not found")
        s = s.replace(oldSide, newSide, true)

        s = s.replace("if(out.length<3&&!window.__dsMore140){", "if(out.length<3&&(window.__dsMore152||0)<3){", true)
        s = s.replace("window.__dsMore140=true;", "window.__dsMore152=(window.__dsMore152||0)+1;", true)

        val oldPayload = "const p={phase,final:\$fin,matches:out,title:document.title||'',url:location.href,rows:rows.length,sample:"
        val newPayload = "const p={phase,final:\$fin,matches:out,title:document.title||'',url:location.href,rows:rows.length,scored,sideMatched,targetId,sample:"
        if (!s.contains(oldPayload)) error("history payload not found")
        s = s.replace(oldPayload, newPayload, true)

        val oldHistBridge = "val phase=o.optString(\"phase\");if(phase!=historyPhase)return;val arr=o.optJSONArray(\"matches\")?:JSONArray();"
        val newHistBridge = "val phase=o.optString(\"phase\");if(phase!=historyPhase)return;historyRowsSeen=maxOf(historyRowsSeen,o.optInt(\"rows\",0));historyScoredSeen=maxOf(historyScoredSeen,o.optInt(\"scored\",0));historySideMatched=maxOf(historySideMatched,o.optInt(\"sideMatched\",0));val arr=o.optJSONArray(\"matches\")?:JSONArray();"
        if (!s.contains(oldHistBridge)) error("history bridge not found")
        s = s.replace(oldHistBridge, newHistBridge, true)

        val oldInsufficient = "m.historyState=\"INSUFFICIENT\";m.historyNote=\"\$phase: solo \${arr.length()} partite\";historyUnavailable++;"
        val newInsufficient = "m.historyState=\"INSUFFICIENT\";m.historyNote=phase+\": solo \"+arr.length()+\" partite (righe \"+o.optInt(\"rows\",0)+\", concluse \"+o.optInt(\"scored\",0)+\", lato \"+o.optInt(\"sideMatched\",0)+\")\";if(firstHistoryDiagnostic.isBlank())firstHistoryDiagnostic=\"Partita: \"+m.home+\" vs \"+m.away+\"\\nFase: \"+phase+\"\\nRighe: \"+o.optInt(\"rows\",0)+\" · concluse: \"+o.optInt(\"scored\",0)+\" · lato corretto: \"+o.optInt(\"sideMatched\",0)+\"\\nTeam ID: \"+o.optString(\"targetId\")+\"\\nPagina: \"+o.optString(\"url\")+\"\\nCampione: \"+o.optString(\"sample\").take(1000);historyUnavailable++;"
        if (!s.contains(oldInsufficient)) error("insufficient history block not found")
        s = s.replace(oldInsufficient, newInsufficient, true)

        val reportNeedle = "Schede QUOTE: \$oddsTabsOpened · dettagli: \$detailsAttempted · SNAI: \$detailsWithSnai · senza SNAI: \$detailsWithoutSnai\\nStorici completati:"
        val reportNew = "Schede QUOTE: \$oddsTabsOpened · dettagli: \$detailsAttempted · SNAI: \$detailsWithSnai · senza SNAI: \$detailsWithoutSnai\\nSTORICO DOM: righe max \$historyRowsSeen · concluse max \$historyScoredSeen · lato corretto max \$historySideMatched\\nStorici completati:"
        if (!s.contains(reportNeedle)) error("report history diagnostic anchor not found")
        s = s.replace(reportNeedle, reportNew, true)

        s = s.replace("AUTO-OPEN: intestazioni ${'$'}leagueHeadersSeen", "AUTO-OPEN: controlli campionato ${'$'}leagueHeadersSeen")
        s = s.replace("FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa)", "FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa · solo partite non iniziate)")

        src.writeText(s)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(patchV0152) }

dependencies { implementation("androidx.appcompat:appcompat:1.7.0") }

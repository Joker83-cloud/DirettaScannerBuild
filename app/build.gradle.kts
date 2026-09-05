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

        val exactHistoryCheck = "if((phase==='HOME'?norm(home):norm(away))!==target)continue;out.push"
        val relaxedHistoryCheck = "const side=phase==='HOME'?norm(home):norm(away);if(!(side===target||side.startsWith(target)||target.startsWith(side)))continue;out.push"
        if (!s.contains(exactHistoryCheck)) error("history side check not found")
        s = s.replace(exactHistoryCheck, relaxedHistoryCheck, true)

        s = s.replace("if(out.length<3&&!window.__dsMore140){", "if(out.length<3&&(window.__dsMore152||0)<3){", true)
        s = s.replace("window.__dsMore140=true;", "window.__dsMore152=(window.__dsMore152||0)+1;", true)

        val oldInsufficient = "m.historyNote=\"\$phase: solo \${arr.length()} partite\";"
        val newInsufficient = "m.historyNote=phase+\": solo \"+arr.length()+\" partite · righe DOM \"+o.optInt(\"rows\",0);"
        if (!s.contains(oldInsufficient)) error("history insufficient note not found")
        s = s.replace(oldInsufficient, newInsufficient, true)

        s = s.replace("AUTO-OPEN: intestazioni ${'$'}leagueHeadersSeen", "AUTO-OPEN: controlli campionato ${'$'}leagueHeadersSeen")
        s = s.replace("FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa)", "FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa · solo partite non iniziate)")

        src.writeText(s)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(patchV0152) }

dependencies { implementation("androidx.appcompat:appcompat:1.7.0") }

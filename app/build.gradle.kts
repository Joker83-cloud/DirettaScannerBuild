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
        versionCode = 149
        versionName = "0.14.9-auto-open-verified-queue-watchdog"
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

val patchV0149 = tasks.register("patchV0149") {
    doLast {
        val src = file("src/main/java/com/joker/direttascannerbuild/MainActivity.kt")
        var s = src.readText()

        s = s.replace("v0.14.7 REAL AUTO-OPEN", "v0.14.9 AUTO-OPEN VERIFIED")
            .replace("DirettaScanner/0.14.7-real-auto-open", "DirettaScanner/0.14.9-auto-open-verified")

        val expandRx = Regex("function expand\\(\\)\\{.*?return \\{clicked,headers:headers.length,closed,before\\};\\}", RegexOption.DOT_MATCHES_ALL)
        val newExpand = """function expand(){const before=document.querySelectorAll('.event__match[data-event-row=\"true\"],.event__match').length;if(!window.__dsOpened149)window.__dsOpened149=new WeakSet();let closed=0,clicked=0;const specific=[...document.querySelectorAll('.wclIcon__leagueShowMoreCont,[class*=\"leagueShowMoreCont\"],[class*=\"leagueShowMore\"]')].filter(e=>!e.closest('.event__match'));for(const c of specific){if(window.__dsOpened149.has(c))continue;const trg=c.closest('button,[role=\"button\"]')||c.querySelector('button,[role=\"button\"]')||c;const aria=(trg.getAttribute&&trg.getAttribute('aria-expanded'))||'';const cls=(String(trg.className||'')+' '+String(c.className||'')).toLowerCase();const label=((trg.getAttribute&&trg.getAttribute('aria-label'))||'')+' '+((trg.getAttribute&&trg.getAttribute('title'))||'');const likelyClosed=aria==='false'||/closed|collapsed|showmore|leagueshowmore/.test(cls)||/show|display|mostra|espandi/i.test(label);if(!likelyClosed)continue;closed++;try{trg.click();window.__dsOpened149.add(c);clicked++;}catch(e){}}return {clicked,headers:specific.length,closed,before};}"""
        if (!expandRx.containsMatchIn(s)) error("v0.14.7 expand function not found")
        s = expandRx.replaceFirst(s, newExpand)

        val oldRange = "    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);return if(x<0||f<0||z<0)false else if(f<=z)x>=f&&x<z else x>=f||x<z}"
        val newRange = "    private fun currentMinute():Int{val c=java.util.Calendar.getInstance();return c.get(java.util.Calendar.HOUR_OF_DAY)*60+c.get(java.util.Calendar.MINUTE)}\n    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);if(x<0||f<0||z<0)return false;val inside=if(f<=z)x>=f&&x<z else x>=f||x<z;return inside&&x>currentMinute()}"
        if (!s.contains(oldRange)) error("v0.14.7 range function not found")
        s = s.replace(oldRange, newRange)

        val oldDiagBridge = "        @JavascriptInterface fun onExpandDiag(headers:Int,closed:Int,clicked:Int,before:Int,after:Int){synchronized(matches){leagueHeadersSeen=maxOf(leagueHeadersSeen,headers);leaguesClosedSeen=maxOf(leaguesClosedSeen,closed);rowsBeforeExpand=maxOf(rowsBeforeExpand,before);rowsAfterExpand=maxOf(rowsAfterExpand,after)}}"
        val newDiagBridge = "        @JavascriptInterface fun onExpandDiag(headers:Int,closed:Int,clicked:Int,before:Int,after:Int){synchronized(matches){leagueHeadersSeen=maxOf(leagueHeadersSeen,headers);leaguesClosedSeen=maxOf(leaguesClosedSeen,closed);if(rowsBeforeExpand==0||before<rowsBeforeExpand)rowsBeforeExpand=before;rowsAfterExpand=maxOf(rowsAfterExpand,after)}}"
        if (!s.contains(oldDiagBridge)) error("expand diagnostic bridge not found")
        s = s.replace(oldDiagBridge, newDiagBridge)

        val oldClick = "        scanBtn.setOnClickListener{extractMain(false);handler.postDelayed({extractMain(false)},800);handler.postDelayed({extractMain(false)},1600);handler.postDelayed({extractMain(false)},2600);handler.postDelayed({extractMain(false)},3800);handler.postDelayed({startDetailQueue()},4600)}"
        val newClick = "        scanBtn.setOnClickListener{extractMain(false);handler.postDelayed({extractMain(false)},800);handler.postDelayed({extractMain(false)},1600);handler.postDelayed({extractMain(false)},2600);handler.postDelayed({extractMain(false)},3800);handler.postDelayed({startDetailQueue()},4600);handler.postDelayed({kickDetailQueue()},6000);handler.postDelayed({kickDetailQueue()},9000)}"
        if (!s.contains(oldClick)) error("scan listener not found")
        s = s.replace(oldClick, newClick)

        val anchor = "    private fun startDetailQueue(){synchronized(matches){matches.forEach{(k,m)->if(inSelectedRange(m.time)&&!m.attempted&&!detailQueue.contains(k)&&currentDetailKey!=k)detailQueue.add(k)}};processNextDetail()}"
        val replacement = anchor + "\n    private fun kickDetailQueue(){if(currentDetailKey==null&&detailQueue.isNotEmpty())processNextDetail();else if(currentDetailKey==null){startDetailQueue()}}"
        if (!s.contains(anchor)) error("startDetailQueue not found")
        s = s.replace(anchor, replacement)

        s = s.replace("AUTO-OPEN: intestazioni ${'$'}leagueHeadersSeen", "AUTO-OPEN: controlli campionato ${'$'}leagueHeadersSeen")
        s = s.replace("FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa)", "FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa · solo partite non iniziate)")

        src.writeText(s)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchV0149)
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}

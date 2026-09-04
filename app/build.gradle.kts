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
        versionCode = 148
        versionName = "0.14.8-auto-open2-future-only"
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

val patchV0148 = tasks.register("patchV0148") {
    doLast {
        val src = file("src/main/java/com/joker/direttascannerbuild/MainActivity.kt")
        var s = src.readText()

        s = s.replace("v0.14.7 REAL AUTO-OPEN", "v0.14.8 AUTO-OPEN 2")
            .replace("DirettaScanner/0.14.7-real-auto-open", "DirettaScanner/0.14.8-auto-open2")

        val expandRx = Regex("function expand\\(\\)\\{.*?return \\{clicked,headers:headers.length,closed,before\\};\\}", RegexOption.DOT_MATCHES_ALL)
        val newExpand = """function expand(){const before=document.querySelectorAll('.event__match[data-event-row=\"true\"],.event__match').length;if(!window.__dsOpened148)window.__dsOpened148=new WeakSet();let closed=0,clicked=0;const specific=[...document.querySelectorAll('.wclIcon__leagueShowMoreCont,[class*=\"leagueShowMoreCont\"],[class*=\"leagueShowMore\"]')].filter(e=>!e.closest('.event__match'));for(const c of specific){if(window.__dsOpened148.has(c))continue;closed++;const trg=c.closest('button,[role=\"button\"]')||c.querySelector('button,[role=\"button\"]')||c;try{trg.click();window.__dsOpened148.add(c);clicked++;}catch(e){}}for(const b of [...document.querySelectorAll('button[aria-expanded=\"false\"],[role=\"button\"][aria-expanded=\"false\"]')]){if(window.__dsOpened148.has(b)||b.closest('nav,[class*=\"sidebar\"],[class*=\"menu\"],[class*=\"filter\"],[class*=\"navigation\"]'))continue;let p=b,ok=false;for(let i=0;p&&i<5;i++,p=p.parentElement){if(p.querySelector&&p.querySelector('a[href*=\"/calcio/\"]:not([href*=\"/partita/\"]):not([href*=\"/squadra/\"])')){ok=true;break;}}if(!ok)continue;closed++;try{b.click();window.__dsOpened148.add(b);clicked++;}catch(e){}}return {clicked,headers:specific.length,closed,before};}"""
        if (!expandRx.containsMatchIn(s)) error("v0.14.7 expand function not found")
        s = expandRx.replaceFirst(s, newExpand)

        val oldRange = "    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);return if(x<0||f<0||z<0)false else if(f<=z)x>=f&&x<z else x>=f||x<z}"
        val newRange = "    private fun currentMinute():Int{val c=java.util.Calendar.getInstance();return c.get(java.util.Calendar.HOUR_OF_DAY)*60+c.get(java.util.Calendar.MINUTE)}\n    private fun inSelectedRange(t:String):Boolean{val f=mins(fromSpinner.selectedItem?.toString()?:\"00:00\");val z=mins(toSpinner.selectedItem?.toString()?:\"23:59\");val x=mins(t);if(x<0||f<0||z<0)return false;val inside=if(f<=z)x>=f&&x<z else x>=f||x<z;return inside&&x>currentMinute()}"
        if (!s.contains(oldRange)) error("v0.14.7 range function not found")
        s = s.replace(oldRange, newRange)

        s = s.replace("AUTO-OPEN: intestazioni ${'$'}leagueHeadersSeen", "AUTO-OPEN: controlli campionato ${'$'}leagueHeadersSeen")
        s = s.replace("FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa)", "FASCIA: ${'$'}from - ${'$'}to (ora finale esclusa · solo partite non iniziate)")

        src.writeText(s)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(patchV0148)
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}

package com.oku.app

/**
 * Basit reklam engelleme: bilinen reklam sunucularına giden istekleri iptal eder ve
 * sayfadaki reklam alanlarını gizler. Liste: adblock/domains.txt (Windows sürümüyle aynı).
 */
object AdBlock {
    private val DOMAINS = hashSetOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "adservice.google.com",
        "adservice.google.com.tr",
        "2mdn.net",
        "moatads.com",
        "adnxs.com",
        "adsrvr.org",
        "advertising.com",
        "adform.net",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "mgid.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "smartadserver.com",
        "adsafeprotected.com",
        "scorecardresearch.com",
        "quantserve.com",
        "teads.tv",
        "yieldmo.com",
        "sharethrough.com",
        "bidswitch.net",
        "contextweb.com",
        "lijit.com",
        "sovrn.com",
        "33across.com",
        "zedo.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "juicyads.com",
        "adskeeper.com",
        "adsterra.com",
        "onclickads.net",
        "clickadu.com",
        "hilltopads.net",
        "bidvertiser.com",
        "infolinks.com",
        "an.yandex.ru",
        "yandexadexchange.net",
        "adtech.de",
        "adtechus.com",
        "serving-sys.com",
        "yieldlab.net",
        "improvedigital.com",
        "triplelift.com",
        "gumgum.com",
        "fastclick.net",
        "mookie1.com",
        "tribalfusion.com",
        "undertone.com",
        "spotxchange.com",
        "spotx.tv",
        "springserve.com",
        "connatix.com",
        "primis.tech",
        "seedtag.com",
        "rtbhouse.com",
        "adroll.com",
        "admatic.com.tr",
        "adocean-global.com",
        "gemius.pl"
    )

    /** Host, listedeki bir alan adının kendisi veya alt alan adı ise true. */
    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = host.lowercase()
        while (true) {
            if (h in DOMAINS) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** Sayfa yüklenince çalıştırılır: reklam alanlarını gizleyen stil ekler. */
    const val COSMETIC_JS =
        "(function(){try{if(document.getElementById('__mv_adblock'))return;" +
        "var s=document.createElement('style');s.id='__mv_adblock';" +
        "s.textContent='ins.adsbygoogle,.adsbygoogle,.google-auto-placed,[id^=\"google_ads_iframe\"],[id^=\"div-gpt-ad\"],iframe[id^=\"aswift_\"],iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication.com\"],iframe[src*=\"adnxs.com\"],iframe[src*=\"taboola.com\"],iframe[src*=\"outbrain.com\"]{display:none!important}';" +
        "(document.head||document.documentElement).appendChild(s);}catch(e){}})();"
}

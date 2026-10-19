package com.blacksingularities.openexplorer.browser

/** Skrypty wstrzykiwane do stron. */
object Scripts {
    /** Ciemny motyw wymuszony filtrem (gdy strona nie ma własnego). */
    const val FORCE_DARK = """
(function(){
  if (document.getElementById('__oe_dark')) return;
  var s = document.createElement('style'); s.id = '__oe_dark';
  s.textContent = 'html{filter:invert(1) hue-rotate(180deg)!important;background:#fff!important}' +
    'img,video,picture,canvas,iframe,svg image,[style*="background-image"]{filter:invert(1) hue-rotate(180deg)!important}';
  (document.head || document.documentElement).appendChild(s);
})();"""

    /** Odblokowanie powiększania na stronach, które je wyłączają. */
    const val FORCE_ZOOM = """
(function(){
  var m = document.querySelector('meta[name=viewport]');
  if (!m) return;
  var c = m.getAttribute('content') || '';
  c = c.replace(/user-scalable\s*=\s*(no|0)/ig, 'user-scalable=yes').replace(/maximum-scale\s*=\s*[0-9.]+/ig, 'maximum-scale=10');
  m.setAttribute('content', c);
})();"""

    /** Tryb czytania: wyodrębnia główną treść i podaje ją w czytelnym układzie. */
    fun reader(dark: Boolean): String {
        val bg = if (dark) "#15181d" else "#fbfaf7"
        val fg = if (dark) "#e6e6e6" else "#1d1d1f"
        val link = if (dark) "#8ab4ff" else "#1a56db"
        return """
(function(){
  function score(el){ var t = 0; el.querySelectorAll('p').forEach(function(p){ t += p.innerText.length; }); return t; }
  var best = document.querySelector('article') || document.querySelector('main') || document.querySelector('[role=main]');
  if (!best || score(best) < 400) {
    var max = 0;
    document.querySelectorAll('div,section,td').forEach(function(el){ var s = score(el); if (s > max * 1.0 && el.querySelectorAll('p').length > 2) { if (s > max) { max = s; best = el; } } });
  }
  if (!best) return 'none';
  var clone = best.cloneNode(true);
  clone.querySelectorAll('script,style,noscript,iframe,form,button,nav,aside,footer,header,svg,[aria-hidden=true],.ad,.ads,.advert,.share,.social,.related,.comments').forEach(function(e){ e.remove(); });
  var title = document.title;
  var h = document.querySelector('h1'); if (h) title = h.innerText;
  document.open();
  document.write('<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title></title>' +
    '<style>body{margin:0;background:$bg;color:$fg;font:19px/1.7 Georgia,serif}main{max-width:720px;margin:0 auto;padding:28px 22px 80px}' +
    'h1{font:700 30px/1.25 system-ui,sans-serif;margin:0 0 24px}img,video,figure{max-width:100%;height:auto;border-radius:10px}' +
    'a{color:$link}pre,code{white-space:pre-wrap;font-size:15px}blockquote{margin:0;padding:4px 18px;background:rgba(127,127,127,.12);border-radius:10px}</style></head>' +
    '<body><main><h1></h1></main></body></html>');
  document.close();
  document.title = title;
  document.querySelector('h1').innerText = title;
  document.querySelector('main').appendChild(clone);
  return 'ok';
})();"""
    }

    const val SOURCE = "document.documentElement.outerHTML"

    /** Tekst strony do czytania na głos (bez menu, stopek i skryptów). */
    const val PAGE_TEXT = """
(function(){
  var root = document.querySelector('article') || document.querySelector('main') || document.body;
  var c = root.cloneNode(true);
  c.querySelectorAll('script,style,noscript,nav,aside,footer,header,form,button,svg').forEach(function(e){ e.remove(); });
  return (document.title ? document.title + '. ' : '') + (c.innerText || '').replace(/\s+\n/g, '\n');
})();"""

    /** Wstrzyknięcie (lub podmiana) arkusza stylów o danym id. */
    fun injectStyle(id: String, css: String): String {
        val q = org.json.JSONObject.quote(css)
        return "(function(){var s=document.getElementById('$id');if(!s){s=document.createElement('style');s.id='$id';" +
            "(document.head||document.documentElement).appendChild(s);}s.textContent=$q;})();"
    }

    /** Ukrywanie typowych miejsc na reklamy (uzupełnia blokowanie domen). */
    const val COSMETIC_CSS =
        ".adsbygoogle,ins.adsbygoogle,[id^='google_ads_'],[id^='div-gpt-ad'],[data-ad-slot],[data-google-query-id]," +
            ".ad-container,.ad-wrapper,.ad-slot,.ad-banner,.adBanner,.advertisement,.sponsored-ad,.taboola,#taboola-below-article," +
            ".OUTBRAIN,.ob-widget,[id*='adocean'],[class*='adocean'],.dfp-ad,.top-ad,.sticky-ad,.adhesion" +
            "{display:none!important}"

    /** Ukrywanie banerów zgody na ciasteczka (popularne platformy CMP). */
    const val COOKIE_CSS =
        "#onetrust-banner-sdk,#onetrust-consent-sdk,#CybotCookiebotDialog,#CybotCookiebotDialogBodyUnderlay,.qc-cmp2-container," +
            "#qc-cmp2-container,#didomi-host,.didomi-popup-backdrop,.fc-consent-root,#cookie-law-info-bar,.cc-window,.cc-banner," +
            "#cookie-notice,#cookie-banner,.cookie-banner,#cookieConsent,.cookie-consent,#gdpr-consent,.gdpr-banner,#usercentrics-root," +
            "#sp_message_container,.sp_veil,[id^='sp_message_container'],#cmpbox,#cmpbox2,.cmpboxBG,#truste-consent-track,.osano-cm-window," +
            "#iubenda-cs-banner,.iubenda-cs-container,#rodo-popup,.rodo-popup,.cookies-info,#cookies-info" +
            "{display:none!important}"

    /** Adres największej ikony strony (apple-touch-icon / icon). */
    const val TOUCH_ICON = """
(function(){
  var best = null, size = 0;
  document.querySelectorAll('link[rel~=icon],link[rel=apple-touch-icon],link[rel=apple-touch-icon-precomposed]').forEach(function(l){
    var s = parseInt((l.getAttribute('sizes') || '0').split('x')[0]) || (l.rel.indexOf('apple') >= 0 ? 180 : 16);
    if (s > size) { size = s; best = l.href; }
  });
  return best || '';
})();"""
}

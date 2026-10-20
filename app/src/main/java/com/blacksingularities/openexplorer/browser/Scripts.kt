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

    const val SOURCE = """(function(){
  var d = document.doctype, t = '';
  if (d) t = '<!DOCTYPE ' + d.name + (d.publicId ? ' PUBLIC "' + d.publicId + '"' : '') + (d.systemId ? ' "' + d.systemId + '"' : '') + '>
';
  return t + document.documentElement.outerHTML;
})();"""

    /** Zasoby strony: skrypty, style, obrazy, ramki, czcionki i inne pobrane pliki. */
    const val RESOURCES = """(function(){
  var seen = {}, out = [];
  function add(type, url) {
    if (!url || url.indexOf('data:') === 0 || url.indexOf('blob:') === 0 || seen[url]) return;
    seen[url] = 1; out.push({type: type, url: url});
  }
  document.querySelectorAll('script[src]').forEach(function(e){ add('script', e.src); });
  document.querySelectorAll('link[rel~="stylesheet"][href]').forEach(function(e){ add('style', e.href); });
  document.querySelectorAll('iframe[src]').forEach(function(e){ add('frame', e.src); });
  document.querySelectorAll('img[src]').forEach(function(e){ add('image', e.currentSrc || e.src); });
  try {
    performance.getEntriesByType('resource').forEach(function(r){
      var t = r.initiatorType;
      add(t === 'script' ? 'script' : t === 'css' || t === 'link' ? 'style' : t === 'img' ? 'image' :
          t === 'xmlhttprequest' || t === 'fetch' ? 'fetch' : 'other', r.name);
    });
  } catch (e) {}
  return JSON.stringify(out.slice(0, 1000));
})();"""

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

    /** Nazwa obiektu łączącego strony z menedżerem haseł (WebViewCompat.addWebMessageListener). */
    const val PASS_BRIDGE = "__oePassBridge"

    /**
     * Menedżer haseł po stronie strony (tylko główna ramka): wykrywa formularze logowania,
     * zgłasza wysłanie loginu i hasła, wypełnia pola na prośbę aplikacji i dodaje przycisk
     * z okiem do pól haseł. Hasła trafiają do strony wyłącznie po stronie aplikacji
     * (odpowiedź do tej samej ramki, z której przyszło zgłoszenie).
     */
    const val PASSWORDS = """
(function(){
  if (window.top !== window || window.__oePassInit) return;
  window.__oePassInit = true;
  var B = window.__oePassBridge;
  if (!B) return;
  var cfg = { eye: false, on: false };
  var revealed = [], eyes = [], lastSent = '', announced = '', pending = false, raf = 0;
  var EYE = '<svg viewBox="0 0 24 24"><path d="M12 5C7 5 2.7 8.1 1 12c1.7 3.9 6 7 11 7s9.3-3.1 11-7c-1.7-3.9-6-7-11-7zm0 11.5a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9zm0-7a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"/></svg>';
  var EYE_OFF = '<svg viewBox="0 0 24 24"><path d="M2.1 3.5 3.5 2.1l18.4 18.4-1.4 1.4-3.3-3.3A11.7 11.7 0 0 1 12 19c-5 0-9.3-3.1-11-7a12.5 12.5 0 0 1 3.9-4.9L2.1 3.5zM12 5c5 0 9.3 3.1 11 7a12.7 12.7 0 0 1-3.3 4.4l-3.1-3.1a4.5 4.5 0 0 0-5-5L8.4 5.4C9.5 5.1 10.7 5 12 5zm-4.4 5.4a4.5 4.5 0 0 0 6 6l-1.6-1.6a2.5 2.5 0 0 1-2.8-2.8l-1.6-1.6z"/></svg>';
  function post(o){ try { B.postMessage(JSON.stringify(o)); } catch (e) {} }
  function vis(el){ if (!el || !el.isConnected) return false; var r = el.getBoundingClientRect(); return r.width > 2 && r.height > 2; }
  function arr(l){ return Array.prototype.slice.call(l); }
  function pwInputs(){
    var a = arr(document.querySelectorAll('input[type=password]'));
    revealed.forEach(function(x){ if (x.isConnected && a.indexOf(x) < 0) a.push(x); });
    return a;
  }
  function typeOf(i){ return (i.getAttribute('type') || 'text').toLowerCase(); }
  function isUserLike(i){
    var t = typeOf(i);
    if (t !== 'text' && t !== 'email' && t !== 'tel') return false;
    var ac = (i.getAttribute('autocomplete') || '').toLowerCase();
    var n = ((i.name || '') + ' ' + (i.id || '')).toLowerCase();
    return ac.indexOf('username') >= 0 || ac.indexOf('email') >= 0 || t === 'email' || /user|login|mail|konto|account|nick|phone|identifier/.test(n);
  }
  function isNew(pw){
    var ac = (pw.getAttribute('autocomplete') || '').toLowerCase();
    var n = ((pw.name || '') + ' ' + (pw.id || '')).toLowerCase();
    return ac.indexOf('new-password') >= 0 || /new|confirm|repeat|retype|nowe|powt/.test(n);
  }
  function userFor(pw){
    var scope = (pw && (pw.form || pw.closest('form'))) || document;
    var ins = arr(scope.querySelectorAll('input'));
    var idx = pw ? ins.indexOf(pw) : ins.length;
    for (var i = idx - 1; i >= 0; i--) {
      var t = typeOf(ins[i]);
      if ((t === 'text' || t === 'email' || t === 'tel') && vis(ins[i]) && revealed.indexOf(ins[i]) < 0) return ins[i];
    }
    return ins.filter(function(x){ return isUserLike(x) && vis(x); })[0] || null;
  }
  function setVal(el, v){
    try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, v); } catch (e) { el.value = v; }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  function collect(){
    var pws = pwInputs().filter(function(p){ return p.value; });
    if (!pws.length) return null;
    var nw = pws.filter(isNew);
    var pw = nw.length ? nw[nw.length - 1] : (pws.length >= 2 ? pws[pws.length - 1] : pws[0]);
    var u = userFor(pws[0]);
    return { u: u ? u.value : '', p: pw.value, n: nw.length > 0 || pws.length >= 2 };
  }
  function submit(){
    if (!cfg.on) return;
    var c = collect();
    if (!c) return;
    var key = c.u + '\n' + c.p;
    if (key === lastSent) return;
    lastSent = key;
    post({ t: 'submit', u: c.u, p: c.p, n: c.n });
  }
  function fill(u, p){
    var pws = pwInputs().filter(vis);
    var pw = pws.filter(function(x){ return !isNew(x); })[0] || pws[0] || null;
    var uf = pw ? userFor(pw) : arr(document.querySelectorAll('input')).filter(function(x){ return vis(x) && isUserLike(x); })[0];
    if (uf && u) setVal(uf, u);
    if (pw && p) setVal(pw, p);
    lastSent = (u || '') + '\n' + (p || '');
  }
  function gen(p){
    var pws = pwInputs().filter(vis);
    var targets = pws.filter(isNew);
    if (!targets.length) targets = pws.length >= 2 ? pws.slice(1) : pws;
    targets.forEach(function(x){ setVal(x, p); });
  }
  function ownToggle(pw){
    var sel = '[aria-label*="assword" i],[aria-label*="asło" i],[title*="assword" i],[title*="asło" i],[class*="toggle" i],[class*="reveal" i],[class*="eye" i],[class*="show-pass" i],[class*="visibility" i]';
    var p = pw.parentElement;
    for (var i = 0; i < 3 && p; i++, p = p.parentElement) {
      var hit = arr(p.querySelectorAll(sel)).some(function(x){ return x !== pw && x.tagName !== 'INPUT'; });
      if (hit) return true;
      if (p.tagName === 'FORM') break;
    }
    return false;
  }
  function place(e){
    var r = e.pw.getBoundingClientRect();
    if (r.width < 48 || r.height < 16 || getComputedStyle(e.pw).visibility === 'hidden') { e.btn.style.display = 'none'; return; }
    var s = 36;
    e.btn.style.display = 'flex';
    e.btn.style.left = (r.right + window.scrollX - s - 2) + 'px';
    e.btn.style.top = (r.top + window.scrollY + (r.height - s) / 2) + 'px';
    e.btn.style.color = getComputedStyle(e.pw).color;
  }
  function placeAll(){
    eyes = eyes.filter(function(e){ if (!e.pw.isConnected) { e.host.remove(); e.pw.__oeEye = null; return false; } return true; });
    eyes.forEach(place);
  }
  function addEye(pw){
    if (pw.__oeEye || pw.__oeNoEye) return;
    if (ownToggle(pw)) { pw.__oeNoEye = true; return; }
    var host = document.createElement('span');
    host.style.cssText = 'position:absolute!important;top:0!important;left:0!important;width:0!important;height:0!important;margin:0!important;padding:0!important;border:0!important;display:block!important;z-index:2147483646!important';
    var sh = host.attachShadow({ mode: 'closed' });
    sh.innerHTML = '<style>button{all:initial;box-sizing:border-box;position:absolute;width:36px;height:36px;display:flex;align-items:center;justify-content:center;border-radius:18px;cursor:pointer;-webkit-tap-highlight-color:transparent}button:active{background:rgba(127,127,127,.22)}svg{width:20px;height:20px;fill:currentColor;opacity:.72}</style><button type="button" aria-label="Pokaż hasło"></button>';
    var btn = sh.querySelector('button');
    btn.innerHTML = EYE;
    btn.addEventListener('mousedown', function(ev){ ev.preventDefault(); });
    btn.addEventListener('click', function(ev){
      ev.preventDefault(); ev.stopPropagation();
      var show = pw.type === 'password';
      pw.type = show ? 'text' : 'password';
      if (show && revealed.indexOf(pw) < 0) revealed.push(pw);
      btn.innerHTML = show ? EYE_OFF : EYE;
      btn.setAttribute('aria-label', show ? 'Ukryj hasło' : 'Pokaż hasło');
    });
    document.documentElement.appendChild(host);
    var e = { pw: pw, host: host, btn: btn };
    pw.__oeEye = e;
    eyes.push(e);
    place(e);
  }
  function scan(){
    var pws = pwInputs().filter(vis);
    var users = pws.length ? [] : arr(document.querySelectorAll('input[autocomplete~=username],input[type=email]')).filter(vis);
    var sig = pws.length + ':' + users.length + ':' + pws.some(isNew);
    if (cfg.on && (pws.length || users.length) && sig !== announced) {
      announced = sig;
      post({ t: 'form', pw: pws.length, nw: pws.some(isNew) });
    }
    if (cfg.eye) pws.forEach(addEye);
    placeAll();
  }
  function later(){
    if (pending) return;
    pending = true;
    setTimeout(function(){ pending = false; scan(); }, 350);
  }
  document.addEventListener('submit', submit, true);
  document.addEventListener('click', function(ev){
    var b = ev.target && ev.target.closest && ev.target.closest('button,input[type=submit],input[type=button],[role=button]');
    if (!b) return;
    var t = ((b.innerText || b.value || '') + ' ' + (b.getAttribute('aria-label') || '')).toLowerCase();
    var isSubmit = (b.tagName === 'BUTTON' && (b.getAttribute('type') || 'submit').toLowerCase() === 'submit' && b.form) ||
      (b.tagName === 'INPUT' && typeOf(b) === 'submit') ||
      /log ?in|sign|zaloguj|dalej|next|continue|submit|wejd|rejestr|utw[oó]rz|create|register|zapisz|save|zmie[nń]/.test(t);
    if (isSubmit) submit();
  }, true);
  document.addEventListener('keydown', function(ev){
    if (ev.key === 'Enter' && ev.target && ev.target.tagName === 'INPUT') submit();
  }, true);
  document.addEventListener('change', function(ev){
    var t = ev.target;
    if (cfg.on && t && t.tagName === 'INPUT' && isUserLike(t) && t.value && t.value.length < 200) post({ t: 'user', v: t.value });
  }, true);
  document.addEventListener('focusin', later, true);
  document.addEventListener('scroll', function(){
    if (!eyes.length || raf) return;
    raf = requestAnimationFrame(function(){ raf = 0; placeAll(); });
  }, true);
  window.addEventListener('resize', placeAll);
  B.onmessage = function(ev){
    var m;
    try { m = JSON.parse(ev.data); } catch (x) { return; }
    if (m.t === 'cfg') { cfg = m; announced = ''; scan(); }
    else if (m.t === 'fill') fill(m.u, m.p);
    else if (m.t === 'gen') gen(m.p);
  };
  function start(){
    scan();
    new MutationObserver(later).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['type', 'hidden'] });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
  post({ t: 'hello' });
})();"""

    /** Płynne przewinięcie na początek strony. */
    const val SCROLL_TOP = "window.scrollTo({top:0,behavior:'smooth'});"

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

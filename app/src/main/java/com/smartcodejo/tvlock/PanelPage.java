package com.smartcodejo.tvlock;

/** Phone control panel for several TVs (addresses kept in the phone's browser). */
final class PanelPage {
    private PanelPage() {}

    static String html() {
        return "<!doctype html><html lang='ar' dir='rtl'><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>لوحة TV Lock</title>"
            + "<style>body{font-family:sans-serif;background:#031230;color:#fff;max-width:420px;margin:20px auto;padding:0 14px}"
            + "input,select,button{font-size:18px;width:100%;padding:9px;margin-top:6px;box-sizing:border-box}"
            + ".tv{background:#0b2350;border-radius:12px;padding:12px;margin-top:14px}.s{color:#9cf;font-size:14px}"
            + ".g{display:grid;grid-template-columns:1fr 1fr;gap:6px}.r{color:#f88}.k{color:#8f8}</style></head><body>"
            + "<h2>لوحة التحكم</h2>"
            + "<input id=pin type=password inputmode=numeric placeholder='PIN (يُستخدم فقط من هالصفحة)'>"
            + "<div id=tvs></div>"
            + "<div class=tv><input id=nm placeholder='اسم التلفزيون'><input id=ip placeholder='عنوان IP مثل 192.168.1.60'>"
            + "<button onclick='addTv()'>➕ إضافة تلفزيون</button></div>"
            + "<script>"
            + "var L=[];try{L=JSON.parse(localStorage.getItem('tvs')||'[]')}catch(e){}"
            + "function save(){try{localStorage.setItem('tvs',JSON.stringify(L))}catch(e){}}"
            + "function addTv(){var n=document.getElementById('nm').value||'TV',i=document.getElementById('ip').value.trim();"
            + "if(!i)return;L.push({n:n,i:i});save();draw()}"
            + "function del(k){L.splice(k,1);save();draw()}"
            + "function call(k,path,extra){var p=document.getElementById('pin').value;"
            + "var o=document.getElementById('o'+k);o.textContent='...';o.className='s';"
            + "var b='json=1&pin='+encodeURIComponent(p)+(extra||'');"
            + "fetch('http://'+L[k].i+':8765'+path,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b})"
            + ".then(function(r){return r.json()}).then(function(j){o.textContent=j.msg+(j.status?' — '+j.status:'');o.className=j.ok?'k':'r'})"
            + ".catch(function(){o.textContent='لا يوجد اتصال';o.className='r'})}"
            + "function draw(){var h='';L.forEach(function(t,k){"
            + "h+='<div class=tv><b>'+t.n+'</b> <span class=s>'+t.i+'</span> <a href=# onclick=\"del('+k+');return false\" class=s>حذف</a>'"
            + "+'<div class=g><button onclick=\"call('+k+',\\'/unlock\\')\">🔓 فتح</button>'"
            + "+'<button onclick=\"call('+k+',\\'/bonus\\')\">⏱ +30 د</button>'"
            + "+'<button onclick=\"call('+k+',\\'/lock\\',\\'&min=30\\')\">🔒 30 د</button>'"
            + "+'<button onclick=\"call('+k+',\\'/lock\\',\\'&min=60\\')\">🔒 60 د</button>'"
            + "+'<button onclick=\"call('+k+',\\'/exam\\',\\'&on=1&start=16:00&end=20:00\\')\">📚 امتحانات</button>'"
            + "+'<button onclick=\"call('+k+',\\'/exam\\',\\'&on=0\\')\">إيقاف امتحانات</button></div>'"
            + "+'<div id=o'+k+' class=s></div></div>'});"
            + "document.getElementById('tvs').innerHTML=h}"
            + "draw();"
            + "</script></body></html>";
    }
}

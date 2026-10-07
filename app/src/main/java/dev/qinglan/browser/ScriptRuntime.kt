package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject

/** Runs in the page world for unsafeWindow compatibility; privileged requests stay grant checked. */
object ScriptRuntime {
    fun javascript(s:UserScript,bridge:String,token:String,values:JSONObject):String {
        val info=JSONObject().put("scriptHandler","Qinglan").put("version",BuildConfig.VERSION_NAME).put("scriptMetaStr",s.source.substringBefore("// ==/UserScript==")+"// ==/UserScript==")
            .put("script",JSONObject().put("name",s.name).put("version",s.version).put("namespace",s.meta["namespace"]?.firstOrNull().orEmpty()).put("description",s.meta["description"]?.firstOrNull().orEmpty()).put("runAt",s.runAt).put("includes",JSONArray(s.meta["include"].orEmpty())).put("matches",JSONArray(s.meta["match"].orEmpty())).put("excludes",JSONArray(s.meta["exclude"].orEmpty())).put("grant",JSONArray(s.meta["grant"].orEmpty()))).toString()
        val schedule=when(s.runAt) {
            "document-start"->"run();"
            "document-body"->"if(document.body)run();else {const o=new MutationObserver(()=>{if(document.body){o.disconnect();run();}});o.observe(document,{childList:true,subtree:true});}"
            "document-end"->"if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',run,{once:true});else run();"
            else->"const idle=()=>setTimeout(run,0);if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',idle,{once:true});else idle();"
        }
        val requirements=s.meta["require"].orEmpty().joinToString("\n;\n"){s.dependencies[it]?:"throw Error('Missing cached @require');"}
        return """
        (()=>{
          if(!/^https?:${'$'}/.test(location.protocol)||${"noframes" in s.meta}&&window.top!==window)return;
          const url=location.href.split('#')[0];
          if(!${JSONArray(s.includes)}.some(p=>new RegExp(p).test(url))||${JSONArray(s.excludes)}.some(p=>new RegExp(p).test(url)))return;
          const done=window[Symbol.for('qinglan.script.executed')]||(window[Symbol.for('qinglan.script.executed')]=new Set());
          if(done.has(${JSONObject.quote(s.id)}))return;done.add(${JSONObject.quote(s.id)});
          if(${"window.onurlchange" in s.meta["grant"].orEmpty()}&&!window[Symbol.for('qinglan.urlchange')]){
            window[Symbol.for('qinglan.urlchange')]=true;if(!('onurlchange' in window))window.onurlchange=null;let previous=location.href;
            const changed=()=>{const current=location.href;if(current===previous)return;const event=new CustomEvent('urlchange');event.oldURL=previous;event.newURL=current;previous=current;window.dispatchEvent(event);};
            for(const name of ['pushState','replaceState']){const original=history[name].bind(history);history[name]=function(...args){const result=original(...args);changed();return result;};}
            window.addEventListener('popstate',changed);window.addEventListener('hashchange',changed);window.addEventListener('urlchange',e=>{if(typeof window.onurlchange==='function')window.onurlchange(e);});
          }
          const native=window[${JSONObject.quote(bridge)}],post=native&&native.postMessage.bind(native),stringify=JSON.stringify.bind(JSON),parse=JSON.parse.bind(JSON);
          const token=${JSONObject.quote(token)},doc=String(Math.random())+Date.now(),pending=new Map(),listeners=new Map(),menus=new Map();
          let sequence=0;const values=Object.assign(Object.create(null),$values);
          function request(op,data={}) {return new Promise((resolve,reject)=>{if(!post){reject(Error('WebView bridge unavailable'));return;}
            const seq=++sequence,timer=setTimeout(()=>{pending.delete(seq);reject(Error('Script request timed out'));},65000);
            pending.set(seq,{resolve,reject,timer});post(stringify({token,doc,seq,op,url:location.href,data}));
          });}
          function changed(key,oldValue,newValue,remote){for(const entry of listeners.values())if(entry.key===key)try{entry.callback(key,oldValue,newValue,remote);}catch(e){console.warn(e);}}
          if(native){native.onmessage=e=>{const msg=parse(e.data);if(msg.doc!==doc)return;
            if(msg.event==='menu'){const f=menus.get(msg.key);if(f)f();return;}
            if(msg.event==='change'){const old=values[msg.key];if(msg.deleted)delete values[msg.key];else values[msg.key]=msg.value;changed(msg.key,old,values[msg.key],true);return;}
            const entry=pending.get(msg.seq);if(!entry)return;pending.delete(msg.seq);clearTimeout(entry.timer);if(msg.error)entry.reject(Error(msg.error));else entry.resolve(msg.value);
          };request('hello').catch(()=>{});}
          function run(){(async function(){
            const unsafeWindow=window,GM_info=$info,GM_log=(...x)=>console.log(...x);
            const GM_addStyle=css=>{const s=document.createElement('style');s.textContent=String(css);const add=()=>{const root=document.head||document.documentElement;if(!root)return false;root.appendChild(s);return true;};if(!add()){const o=new MutationObserver(()=>{if(add())o.disconnect();});o.observe(document,{childList:true,subtree:true});}return s;};
            const GM_addElement=(parent,tag,attrs)=>{if(typeof parent==='string'){attrs=tag;tag=parent;parent=document.body||document.documentElement;}const el=document.createElement(tag);for(const [key,value] of Object.entries(attrs||{})){if(key==='textContent')el.textContent=value;else el.setAttribute(key,String(value));}parent.appendChild(el);return el;};
            const hasOwn=(obj,key)=>Object.prototype.hasOwnProperty.call(obj,key);
            const clone=v=>v===undefined?undefined:parse(stringify(v));
            const GM_getValue=(key,fallback)=>hasOwn(values,String(key))?clone(values[String(key)]):fallback;
            const GM_listValues=()=>Object.keys(values);
            const set=(key,value)=>{key=String(key);const old=GM_getValue(key);value=clone(value);values[key]=value;changed(key,old,value,false);return request('set',{key,value});};
            const del=key=>{key=String(key);const old=GM_getValue(key);delete values[key];changed(key,old,undefined,false);return request('delete',{key});};
            const GM_setValue=(key,value)=>{set(key,value).catch(e=>console.warn(e));};
            const GM_deleteValue=key=>{del(key).catch(e=>console.warn(e));};
            const GM_getValues=keys=>Object.fromEntries((keys==null?Object.keys(values):Array.isArray(keys)?keys:Object.keys(keys)).map(key=>[key,GM_getValue(key,keys==null||Array.isArray(keys)?undefined:keys[key])]));
            const GM_setValues=map=>{for(const [key,value] of Object.entries(map))GM_setValue(key,value);};
            const GM_deleteValues=keys=>keys.forEach(GM_deleteValue);
            const GM_addValueChangeListener=(key,callback)=>{const id=++sequence;listeners.set(id,{key:String(key),callback});return id;};
            const GM_removeValueChangeListener=id=>listeners.delete(id);
            const GM_getTab=callback=>{request('getTab').then(callback).catch(e=>console.warn(e));};
            const GM_saveTab=data=>{request('saveTab',data).catch(e=>console.warn(e));};
            const GM_getTabs=callback=>{request('getTabs').then(callback).catch(e=>console.warn(e));};
            const texts=${JSONObject(s.resources)},urls=${JSONObject(s.resourceUrls)};
            const GM_getResourceText=name=>{if(!hasOwn(texts,name))throw Error('Unknown @resource: '+name);return texts[name];};
            const GM_getResourceURL=name=>{if(!hasOwn(urls,name))throw Error('Unknown @resource: '+name);return urls[name];};
            const GM_getResourceUrl=GM_getResourceURL;
            const GM_registerMenuCommand=(label,callback,options)=>{const id=options&&typeof options==='object'&&options.id||String(++sequence);menus.set(String(id),callback);request('menu',{key:String(id),label:String(label)}).catch(e=>console.warn(e));return id;};
            const GM_unregisterMenuCommand=id=>{menus.delete(String(id));request('unmenu',{key:String(id)}).catch(()=>{});};
            const GM_openInTab=(url,options)=>{const opened=request('openTab',{url:new URL(url,location.href).href,active:typeof options==='boolean'?!options:!options||options.active!==false});const tab={closed:false,close:()=>opened.then(id=>request('closeTab',{id})).then(()=>{tab.closed=true;})};Object.defineProperty(tab,'ready',{value:opened});opened.catch(e=>console.warn(e));return tab;};
            const GM_setClipboard=(text,type,callback)=>{const html=type==='html'||type&&typeof type==='object'&&(type.type==='html'||type.mimetype==='text/html');const result=request('clipboard',{text:String(text),html:!!html});result.then(()=>{if(callback)callback();}).catch(e=>console.warn(e));return result;};
            function xhr(details,modern){let aborted=false;const context=details.context;let rejectAbort;
              const abortPromise=new Promise((_,reject)=>{rejectAbort=reject;});
              const job=Promise.race([request('xhr',{url:new URL(details.url,location.href).href,method:details.method||'GET',headers:details.headers||{},body:details.data==null?null:String(details.data),timeout:details.timeout||30000}),abortPromise])
                .then(response=>{if(aborted)return;Object.assign(response,{readyState:4,context});
                  if(details.responseType==='json')response.response=parse(response.responseText);
                  else if(details.responseType==='arraybuffer'||details.responseType==='blob'){const binary=atob(response.base64),bytes=Uint8Array.from(binary,c=>c.charCodeAt(0));response.response=details.responseType==='blob'?new Blob([bytes]):bytes.buffer;}
                  else if(details.responseType==='document'){response.response=new DOMParser().parseFromString(response.responseText,'text/html');response.responseXML=response.response;}
                  else response.response=response.responseText;
                  delete response.base64;if(details.onreadystatechange)details.onreadystatechange(response);if(details.onprogress)details.onprogress({lengthComputable:true,loaded:response.responseText.length,total:response.responseText.length});if(details.onload)details.onload(response);return response;
                }).catch(error=>{const event={error:String(error),status:0,readyState:4,context};const callback=aborted?details.onabort:/timed out|timeout/i.test(String(error))?details.ontimeout:details.onerror;if(callback)callback(event);if(modern)throw error;});
              job.abort=()=>{aborted=true;rejectAbort(Error('aborted'));};if(details.onloadstart)details.onloadstart({readyState:1,context});return job;
            }
            const GM_xmlhttpRequest=details=>{const job=xhr(details,false);return {abort:job.abort};};
            const GM_xmlHttpRequest=GM_xmlhttpRequest;
            const GM={info:GM_info,log:GM_log,addStyle:GM_addStyle,addElement:GM_addElement,getValue:async(...a)=>GM_getValue(...a),setValue:set,deleteValue:del,listValues:async()=>GM_listValues(),getValues:async keys=>GM_getValues(keys),setValues:map=>Promise.all(Object.entries(map).map(([key,value])=>set(key,value))),deleteValues:keys=>Promise.all(keys.map(del)),getTab:()=>request('getTab'),saveTab:data=>request('saveTab',data),getTabs:()=>request('getTabs'),addValueChangeListener:async(...a)=>GM_addValueChangeListener(...a),removeValueChangeListener:async id=>GM_removeValueChangeListener(id),getResourceText:async name=>GM_getResourceText(name),getResourceUrl:async name=>GM_getResourceURL(name),getResourceURL:async name=>GM_getResourceURL(name),xmlHttpRequest:details=>xhr(details,true),xmlhttpRequest:details=>xhr(details,true),registerMenuCommand:GM_registerMenuCommand,unregisterMenuCommand:GM_unregisterMenuCommand,openInTab:(...args)=>{const tab=GM_openInTab(...args);return tab.ready.then(()=>tab);},setClipboard:GM_setClipboard};
            // Script declarations must not collide with the manager's private helpers.
            await (async function(){
              $requirements
              ;
              ${s.source}
            }).call(window);
          }).call(window).catch(e=>console.warn('Qinglan user script '+${JSONObject.quote(s.name)},e));}
          $schedule
        })();
        """.trimIndent()
    }
}

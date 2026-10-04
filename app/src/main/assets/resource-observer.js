(() => {
  if(!/^https?:$/.test(location.protocol)||!window.qinglanResources)return;
  if(window.__qinglanResourceScan){window.__qinglanResourceScan();return;}
  let epoch=null, queue=[], timer=0, active=false, started=false;
  const seen=new Map();
  const send=data=>{try{qinglanResources.postMessage(JSON.stringify(Object.assign({page:location.href},data)))}catch(_){}};
  function flush(){
    timer=0;if(!active||epoch===null||!queue.length)return;
    send({op:'resources',epoch,items:queue.splice(0,16)});
    if(queue.length)timer=setTimeout(flush,100);
  }
  function record(raw,mime='',hint='',source='performance'){
    if(!active)return;
    try{
      const url=new URL(raw,location.href).href;
      if(url.length>3000||!/^https?:|^blob:https?:/.test(url))return;
      // Reduce bridge traffic; MIME and DOM hints also identify extensionless media.
      if(!/\.(mp4|webm|m4v|mov|mkv|flv|3gp|ogv|mp3|m4a|aac|wav|flac|ogg|opus|m3u8|mpd|ts|m4s|jpg|jpeg|png|gif|webp|avif|svg|bmp|ico)(?:[?#]|$)/i.test(url)&&
         !/^(video|audio|image)\//i.test(mime)&&!/mpegurl|dash\+xml/i.test(mime)&&!['video','audio','image','img','source'].includes(hint))return;
      const signature=mime+'|'+hint;
      if(seen.get(url)===signature||(!seen.has(url)&&seen.size>=1000))return;
      seen.set(url,signature);if(queue.length>=200)queue.shift();queue.push({url,mime:mime.slice(0,120),hint,source});
      if(!timer)timer=setTimeout(flush,100);
    }catch(_){}
  }
  function element(node){
    if(!(node instanceof Element))return;
    const tag=node.tagName.toLowerCase();
    if(['video','audio','img','source'].includes(tag)){
      const hint=tag==='source'?(node.parentElement?.tagName.toLowerCase()||'source'):tag;
      // type can list codecs; video/audio/source declarations are useful without suffixes.
      for(const url of [node.currentSrc,node.src])if(url)record(url,node.getAttribute('type')||'',hint,'dom');
      if(node.poster)record(node.poster,'','image','dom');
    }
  }
  function scan(){
    document.querySelectorAll('video,audio,img,source').forEach(element);
    performance.getEntriesByType('resource').slice(-1000).forEach(e=>record(e.name,'',e.initiatorType));
  }
  window.__qinglanResourceScan=()=>{seen.clear();send({op:'hello'});if(active)scan()};
  qinglanResources.onmessage=e=>{try{const data=JSON.parse(e.data);epoch=data.epoch;active=data.enabled!==false;if(active){if(!started){started=true;start()}scan();flush()}}catch(_){}};
  send({op:'hello'});
  function start(){
  let domTimer=0;
  const observer=new MutationObserver(()=>{if(!domTimer)domTimer=setTimeout(()=>{domTimer=0;scan()},400)});
  function ready(){observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['src','srcset']});scan()}
  if(document.documentElement)ready();else document.addEventListener('DOMContentLoaded',ready,{once:true});
  document.addEventListener('loadedmetadata',e=>element(e.target),true);
  document.addEventListener('load',e=>element(e.target),true);
  try{new PerformanceObserver(list=>list.getEntries().forEach(e=>record(e.name,'',e.initiatorType))).observe({type:'resource',buffered:true})}catch(_){}
  // Observe metadata only. Keep original promises/responses and never read response bodies.
  const originalFetch=window.fetch;
  if(originalFetch)window.fetch=function(...args){const promise=Reflect.apply(originalFetch,this,args);promise.then(response=>{try{record(response.url,response.headers.get('content-type')||'','','fetch')}catch(_){}},()=>{});return promise};
  const originalSend=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send=function(...args){this.addEventListener('loadend',()=>{try{record(this.responseURL,this.getResponseHeader('content-type')||'','','xhr')}catch(_){}},{once:true});return Reflect.apply(originalSend,this,args)};
  }
})();

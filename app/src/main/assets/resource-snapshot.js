(() => {
  const out=[];const seen=new Set();
  function add(url,mime,hint,source){if(!url||seen.has(url)||out.length>=100)return;seen.add(url);out.push({url,mime:mime||'',hint,source})}
  document.querySelectorAll('video,audio,source,img').forEach(e=>{
    const hint=e.tagName==='SOURCE'?(e.parentElement?.tagName.toLowerCase()||'source'):e.tagName.toLowerCase();
    add(e.currentSrc||e.src,e.getAttribute('type'),hint,'dom');if(e.poster)add(e.poster,'','image','dom');
  });
  performance.getEntriesByType('resource').slice(-1000).forEach(e=>add(e.name,'',e.initiatorType,'performance'));
  return JSON.stringify(out);
})();

(() => {
  if (window.__qinglanCancelPicker) { window.__qinglanCancelPicker(); return; }
  const tip=document.createElement('div');
  tip.textContent=__QINGLAN_PICKER_TIP__;
  tip.style.cssText='position:fixed;top:8px;left:8px;right:8px;z-index:2147483647;background:#24312c;color:white;padding:18px;border-radius:12px;font:16px sans-serif;text-align:center';
  document.documentElement.appendChild(tip);
  function stop() { document.removeEventListener('click', pick, true); tip.remove(); delete window.__qinglanCancelPicker; }
  window.__qinglanCancelPicker=stop;
  function pick(event) {
    event.preventDefault(); event.stopImmediatePropagation();
    const target=event.target; if (tip.contains(target)) { stop(); return; }
    if (!(target instanceof Element) || ['HTML','BODY'].includes(target.tagName)) return;
    const parts=[]; let node=target;
    while(node && node!==document.documentElement && parts.length<10) {
      if(node.id && /^[a-zA-Z_][a-zA-Z0-9_-]*$/.test(node.id)) { parts.unshift('#'+node.id); break; }
      let part=node.tagName.toLowerCase();
      if(node.parentElement) part+=':nth-child('+(Array.from(node.parentElement.children).indexOf(node)+1)+')';
      parts.unshift(part); node=node.parentElement;
    }
    const selector=parts.join(' > '); stop();
    if(selector && window.qinglanFilter) qinglanFilter.postMessage('pick:'+selector);
  }
  document.addEventListener('click', pick, true);
})();

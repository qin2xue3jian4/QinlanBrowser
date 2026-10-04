(() => {
  if (!/^https?:$/.test(location.protocol)) return;
  if (window.__qinglanApplyFilters) { if (window.qinglanFilter) qinglanFilter.postMessage('styles'); return; }
  let style;
  window.__qinglanApplyFilters = selectors => {
    if (!document.documentElement) { document.addEventListener('DOMContentLoaded', () => window.__qinglanApplyFilters(selectors), {once:true}); return; }
    if (!style || !style.isConnected) { style=document.createElement('style'); document.documentElement.appendChild(style); }
    style.textContent='';
    for (const selector of selectors) { try { style.sheet.insertRule(selector+' { display: none !important; }', style.sheet.cssRules.length); } catch (_) {} }
  };
  if (window.qinglanFilter) {
    qinglanFilter.onmessage = event => { try { window.__qinglanApplyFilters(JSON.parse(event.data)); } catch (_) {} };
    qinglanFilter.postMessage('styles');
  }
})();

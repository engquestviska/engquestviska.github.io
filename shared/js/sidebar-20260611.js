/* Compatibility shim: use sidebar.js as the canonical source. */
(function() {
  var script = document.createElement('script');
  script.src = new URL('sidebar.js', document.currentScript.src).href;
  document.head.appendChild(script);
})();

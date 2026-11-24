'use strict';
// Dev frontend talks directly to the sandbox backend through the same-origin proxy.
// Retire the earlier gateway token UI and clear its old per-tab tokens.
try {
  sessionStorage.removeItem('huixue.gateway-token');
  sessionStorage.removeItem('huixue.auth-session');
} catch { }
window.apiFetch=(url,options={})=>fetch(url,options);

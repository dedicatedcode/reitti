/**
 * Supplies the CSRF token for HTMX requests and same-origin fetch/XHR mutations.
 * The token is stored by Spring Security in the XSRF-TOKEN cookie (HttpOnly=false).
 */
(function () {
    'use strict';

    // The CSRF cookie is "XSRF-TOKEN", but Spring Security reads the token from the
    // "X-XSRF-TOKEN" header (CookieCsrfTokenRepository.DEFAULT_CSRF_HEADER_NAME).
    // CsrfProtectionIntegrationTest asserts these two stay in sync with the server.
    const CSRF_COOKIE = 'XSRF-TOKEN';
    const CSRF_HEADER = 'X-XSRF-TOKEN';

    function readCookie(name) {
        const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }

    function readMeta(name) {
        const meta = document.querySelector('meta[name="' + name + '"]');
        return meta ? meta.getAttribute('content') : null;
    }

    function currentToken() {
        return readCookie(CSRF_COOKIE) || readMeta('_csrf');
    }

    document.addEventListener('htmx:configRequest', function (event) {
        const token = currentToken();
        if (token) {
            event.detail.headers[CSRF_HEADER] = token;
        }
    });

    const mutating = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
    const originalFetch = window.fetch;
    if (originalFetch) {
        window.fetch = function (input, init) {
            try {
                init = init || {};
                let method = (init.method || (input && input.method) || 'GET').toUpperCase();
                let url = typeof input === 'string' ? input : (input && input.url) || '';
                const sameOrigin = url.indexOf('http') !== 0 || url.indexOf(window.location.origin) === 0;
                if (mutating.has(method) && sameOrigin) {
                    const token = currentToken();
                    if (token) {
                        const headers = new Headers(init.headers || (input && input.headers) || {});
                        if (!headers.has(CSRF_HEADER)) {
                            headers.set(CSRF_HEADER, token);
                        }
                        init.headers = headers;
                    }
                }
            } catch (e) {
                // never break application requests because of header enrichment
            }
            return originalFetch.call(this, input, init);
        };
    }
})();

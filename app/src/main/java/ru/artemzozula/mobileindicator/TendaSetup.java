package ru.artemzozula.mobileindicator;

import org.json.JSONObject;

final class TendaSetup {
    private TendaSetup() {}

    static String script(String password, String origin, boolean startAllowed, boolean wifiAllowed) {
        return String.format(java.util.Locale.ROOT, """
            (() => {
                if (location.origin !== %s) return 'unknown';
                const visible = e => e && !e.disabled && e.getClientRects().length
                    && getComputedStyle(e).visibility === 'visible'
                    && !e.closest('[hidden],[aria-hidden="true"]');
                const byId = id => document.getElementById(id);
                const start = byId('start');
                const noWan = byId('go-wifi');
                const next = byId('wifiset-page-next');
                const ssid = byId('ssid-val');
                const password = byId('pwd-val');
                const headings = Array.from(document.querySelectorAll('h1,h2,h3'))
                    .filter(visible).map(e => e.textContent).join(' ');
                if (%s && visible(start) && /Tenda/i.test(headings) && /старт/i.test(start.textContent)) {
                    start.click();
                    return 'started';
                }
                if (%s && visible(noWan) && /WAN/i.test(headings) && /Wi-Fi/i.test(noWan.textContent)) {
                    noWan.click();
                    return 'wifi';
                }
                if (visible(next) && visible(ssid) && visible(password)
                        && password instanceof HTMLInputElement && ssid instanceof HTMLInputElement) {
                    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                    setter.call(password, %s);
                    password.dispatchEvent(new Event('input', {bubbles: true}));
                    password.dispatchEvent(new Event('change', {bubbles: true}));
                    return 'filled';
                }
                return 'unknown';
            })()
            """, JSONObject.quote(origin), startAllowed, wifiAllowed, JSONObject.quote(password));
    }
}

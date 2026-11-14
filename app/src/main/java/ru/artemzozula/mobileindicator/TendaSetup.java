package ru.artemzozula.mobileindicator;

import org.json.JSONObject;

final class TendaSetup {
    private TendaSetup() {}

    static String script(String password, String origin, int step) {
        return String.format(java.util.Locale.ROOT, """
            (() => {
                if (location.origin !== %s) return 'wait';
                const visible = e => e && !e.disabled && e.getClientRects().length
                    && getComputedStyle(e).visibility === 'visible'
                    && !e.closest('[hidden],[aria-hidden="true"]');
                const byId = id => document.getElementById(id);
                const click = (id, label) => {
                    const e = byId(id);
                    if (!visible(e) || !label.test(e.textContent.trim())) return 'wait';
                    e.click();
                    return 'advance';
                };
                switch (%d) {
                    case 0:
                        if (!Array.from(document.querySelectorAll('h1,h2,h3'))
                                .some(e => visible(e) && /Tenda/i.test(e.textContent))) return 'wait';
                        return click('start', /^старт$/i);
                    case 1:
                        return click('continue', /продолжить/i);
                    case 2: {
                        // The custom WAN selector can hide its backing input.
                        if (!visible(byId('wanset-page-next'))) return 'wait';
                        const wan = byId('wantype-val-input');
                        const value = (wan?.value || '').trim();
                        if (/pppoe|статическ|static/i.test(value)) return 'wan';
                        return click('wanset-page-next', /^далее$/i);
                    }
                    case 3:
                        return click('go-wifi', /перейти.*wi-fi/i);
                    case 4:
                        return click('skip-btn1', /перейти.*wi-fi/i);
                    case 5: {
                        const ssid = byId('ssid-val'), field = byId('pwd-val');
                        if (!visible(byId('wifiset-page-next')) || !visible(ssid) || !visible(field)
                                || !(field instanceof HTMLInputElement)) return 'wait';
                        if (field.value) return 'password';
                        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(field, %s);
                        field.dispatchEvent(new Event('input', {bubbles: true}));
                        field.dispatchEvent(new Event('change', {bubbles: true}));
                        return 'advance';
                    }
                    case 6:
                        if (!visible(byId('pwd-val')) || byId('pwd-val').value !== %s) return 'password';
                        return click('wifiset-page-next', /^далее$/i);
                    case 7:
                        return click('complete', /^завершить$/i);
                    default:
                        return 'wait';
                }
            })()
            """, JSONObject.quote(origin), step, JSONObject.quote(password), JSONObject.quote(password));
    }
}

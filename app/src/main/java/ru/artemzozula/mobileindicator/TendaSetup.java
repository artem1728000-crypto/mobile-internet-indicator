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
                        return visible(byId('ssid-val')) ? 'ssid:' + byId('ssid-val').value : 'wait';
                    case 7:
                        if (!visible(byId('pwd-val')) || byId('pwd-val').value !== %s) return 'password';
                        return click('wifiset-page-next', /^далее$/i);
                    case 8:
                        return visible(byId('complete')) ? 'ready-to-save' : 'wait';
                    case 9:
                        return click('complete', /^завершить$/i);
                    case 10: {
                        const field = byId('pwd');
                        if (!visible(field) || field.type !== 'password') return 'wait';
                        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(field, %s);
                        field.dispatchEvent(new Event('input', {bubbles: true}));
                        field.dispatchEvent(new Event('change', {bubbles: true}));
                        return click('login', /^вход$/i);
                    }
                    case 11: {
                        if (byId('double-val-check')) return 'advance';
                        const matches = e => visible(e) && /^настройки\\s+wi[\\s\\u2010-\\u2015-]*fi$/i
                            .test((e.textContent || '').trim());
                        const link = Array.from(document.querySelectorAll('body *'))
                            .find(e => matches(e) && !Array.from(e.children).some(matches));
                        if (!link) return 'wait';
                        link.click();
                        return 'advance';
                    }
                    case 12: {
                        const toggle = byId('double-val-check');
                        if (!toggle || toggle.type !== 'checkbox' || toggle.disabled) return 'wait';
                        if (!toggle.checked) {
                            const label = Array.from(toggle.labels || []).find(visible);
                            if (!visible(toggle) && !label) return 'wait';
                            (label || toggle).click();
                        }
                        return 'advance';
                    }
                    case 13: {
                        if (!byId('double-val-check')?.checked) return 'wait';
                        const save = Array.from(document.querySelectorAll('button,input[type=submit],a,[role=button]'))
                            .filter(e => visible(e) && /^сохранить$/i.test((e.value || e.textContent).trim()));
                        if (save.length !== 1) return 'manual-save';
                        save[0].click();
                        return 'advance';
                    }
                    default:
                        return 'wait';
                }
            })()
            """, JSONObject.quote(origin), step, JSONObject.quote(password), JSONObject.quote(password), JSONObject.quote(password));
    }
}

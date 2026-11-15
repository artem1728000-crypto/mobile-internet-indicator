package ru.artemzozula.mobileindicator;

import org.json.JSONObject;

final class TendaSetup {
    private TendaSetup() {}

    static String script(String password, String origin, int step) {
        return String.format(java.util.Locale.ROOT, """
            (() => {
                if (location.origin !== %s) return 'wait';
                const visible = e => e && e.getClientRects().length
                    && getComputedStyle(e).visibility === 'visible'
                    && !e.closest('[hidden],[aria-hidden="true"]');
                const byId = id => document.getElementById(id);
                const click = (id, label) => {
                    const e = byId(id);
                    if (!visible(e) || e.disabled || !label.test(e.textContent.trim())) return 'wait';
                    e.click();
                    return 'advance';
                };
                const step = %d;
                switch (step) {
                    case -1: {
                        if (visible(byId('pwd')) && visible(byId('login'))
                                && /^вход$/i.test(byId('login').textContent.trim())) return 'login';
                        if ((byId('double-val-check')?.type === 'checkbox' && visible(byId('wifiSSID-val')))
                                || (byId('goPc-close') && byId('goPc-btn0'))
                                || Array.from(document.querySelectorAll('body *'))
                                .some(e => visible(e) && /^настройки\\s+wi[\\s\\u2010-\\u2015-]*fi$/i.test(e.textContent.trim())))
                            return 'settings';
                        if (!visible(byId('start'))) return 'wait';
                        if (/^начать$/i.test(byId('start').textContent.trim())) return 'ax-wizard';
                        const model = document.body.innerText;
                        if (/\\bRX2\\s*PRO\\b/i.test(model)) return 'rx';
                        if (/\\bTX2L\\s*PRO\\b/i.test(model)) return 'tx';
                        return 'welcome';
                    }
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
                    case 30:
                    case 10: {
                        const field = byId('pwd');
                        if (!visible(field) || field.type !== 'password') return 'wait';
                        const value = %s;
                        if (field.value !== value) {
                            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(field, value);
                            field.dispatchEvent(new Event('input', {bubbles: true}));
                            field.dispatchEvent(new Event('change', {bubbles: true}));
                            return 'wait';
                        }
                        return click('login', /^вход$/i);
                    }
                    case 11: {
                        if (byId('double-val-check')?.type === 'checkbox' && visible(byId('wifiSSID-val'))) return 'advance';
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
                            .filter(e => visible(e) && !e.disabled && /^сохранить$/i.test((e.value || e.textContent).trim()));
                        if (save.length !== 1) return 'manual-save';
                        save[0].click();
                        return 'advance';
                    }
                    case 20:
                        return click('start', /^начать$/i);
                    case 21:
                        return click('continue', /продолжить/i);
                    case 22:
                        return click('extend-page-skip', /^пропустить$/i);
                    case 23: {
                        const ssid = byId('ssid-val'), field = byId('pwd-val');
                        if (!visible(ssid) || !visible(field) || !(field instanceof HTMLInputElement)
                                || !/^@Tenda_[0-9a-f]{6}$/i.test(ssid.value)) return 'wait';
                        if (field.value) return 'password';
                        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(field, %s);
                        field.dispatchEvent(new Event('input', {bubbles: true}));
                        field.dispatchEvent(new Event('change', {bubbles: true}));
                        return 'ssid:' + ssid.value;
                    }
                    case 24: {
                        const pattern = /^настроить пароль wifi как пароль учетной записи роутера$/i;
                        const matches = e => visible(e) && pattern.test(e.textContent.trim()
                            .replace(/[\\s-]+/g, ' ').replace(/wi fi/ig, 'wifi').replace(/ё/ig, 'е'));
                        const label = Array.from(document.querySelectorAll('body *'))
                            .find(e => matches(e) && !Array.from(e.children).some(matches));
                        if (!label) return 'ax-review';
                        const wrapper = label.closest('label,[role=checkbox],[role=switch]');
                        const input = wrapper?.control || wrapper?.querySelector('input[type=checkbox]');
                        const accountPassword = byId('login-pwd-val');
                        if (input?.checked || wrapper?.getAttribute('aria-checked') === 'true'
                                || (accountPassword && !visible(accountPassword))) return 'advance';
                        (wrapper || label).click();
                        return 'advance';
                    }
                    case 25: {
                        const field = byId('pwd-val');
                        if (!visible(field) || field.value !== %s) return 'password';
                        const buttons = Array.from(document.querySelectorAll('.wifi-set__btn-group button.button--primary'))
                            .filter(e => visible(e) && !e.disabled && /^далее$/i.test(e.textContent.trim()));
                        if (buttons.length !== 1) return 'wait';
                        buttons[0].click();
                        return 'advance';
                    }
                    case 26:
                        return click('complete', /^завершить$/i);
                    case 33:
                    case 31: {
                        const controls = Array.from(document.querySelectorAll('body *'));
                        if (controls.some(e => visible(e) && e.matches('.sub-nav__title'))) return 'open-wan';
                        const find = pattern => controls.find(e => visible(e) && !e.disabled
                            && pattern.test(e.textContent.trim())
                            && !Array.from(e.children).some(x => pattern.test(x.textContent.trim())));
                        const web = find(/^нажмите для посещения.*веб/i);
                        if (web) {
                            web.click();
                            return 'advance';
                        }
                        return step === 31 && find(/^далее$/i) ? clickNext() : 'wait';
                        function clickNext() {
                            const next = find(/^далее$/i);
                            (next.closest('button,a,[role=button]') || next).click();
                            return 'next-clicked';
                        }
                    }
                    case 32:
                        return document.querySelector('.sub-nav__title') ? 'open-wan' : 'wait';
                    case 34:
                    case 35: {
                        const title = 'Автосогласование WAN/LAN';
                        if (location.hash !== '#/advance/network/auto-detect'
                                || document.querySelector('.page-title')?.textContent.trim() !== title)
                            return 'wait';
                        const items = Array.from(document.querySelectorAll('.v-form-item'))
                            .filter(e => e.querySelector('.v-form-item__label')?.textContent.trim() === title)
                            .flatMap(e => Array.from(e.querySelectorAll('.v-switch > .v-switch__item')))
                            .filter(visible);
                        if (items.length !== 1) return 'wait';
                        const toggle = items[0];
                        const icon = toggle.querySelector('.v-switch__icon');
                        if (!icon) return 'wait';
                        if (!icon.classList.contains('v-switch__icon--active')) return 'wan-off';
                        if (step === 35) return 'wait';
                        toggle.click();
                        return 'advance';
                    }
                    default:
                        return 'wait';
                }
            })()
            """, JSONObject.quote(origin), step, JSONObject.quote(password), JSONObject.quote(password), JSONObject.quote(password), JSONObject.quote(password), JSONObject.quote(password));
    }
}

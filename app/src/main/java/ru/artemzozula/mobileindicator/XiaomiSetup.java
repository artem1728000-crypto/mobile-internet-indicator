package ru.artemzozula.mobileindicator;

import org.json.JSONObject;

final class XiaomiSetup {
    private XiaomiSetup() {}

    static String script(String origin, String password, String expected) {
        return String.format("""
                (() => {
                  if (location.origin !== %s) return 'unknown';
                  const password = %s;
                  const expected = %s;
                  const visible = e => e.getClientRects().length
                      && getComputedStyle(e).visibility === 'visible'
                      && !e.closest('[hidden],[aria-hidden="true"]') && !e.disabled;
                  const find = (selector, label) => [...document.querySelectorAll(selector)]
                      .find(e => visible(e) && (label === undefined || e.textContent.trim() === label));
                  const click = (step, target) => {
                    if (expected === 'auto' && !target.dataset.routerClicked) { target.dataset.routerClicked = '1'; target.click(); }
                    return step;
                  };
                  const fill = (step, field) => {
                    if (password) {
                      field.value = password;
                      field.dispatchEvent(new Event('input', {bubbles: true}));
                      field.dispatchEvent(new Event('change', {bubbles: true}));
                    }
                    return step;
                  };

                  const start = find('a.button', '马上体验');
                  if (start && find('input[name=protocal]')) {
                    const agreement = find('input[name=protocal]'); if (!agreement.checked) agreement.click(); return click('welcome', start);
                  }
                  const offline = find('a', '不插网线，继续配置');
                  if (offline && find('h4', '未检测到入户网线')) return click('offline', offline);
                  const dhcp = [...document.querySelectorAll('li,button,a,[role=button]')]
                      .find(e => visible(e) && /^(自动获取IP|DHCP)(\\s*[(（].*[)）])?$/i.test(
                          e.textContent.trim().replace(/\\s+/g, ' ')))
                      || [...document.querySelectorAll('li')].find(e => visible(e)
                          && e.textContent.replace(/\\s+/g, '').includes('自动获取IP'));
                  if (dhcp && (find('h3', '选择配置类型') || /PPPoE/i.test(document.body.textContent))) return click('dhcp', dhcp);

                  const wifi = find('input[name=password]');
                  if (wifi && find('input[name=name]') && find('input[name=passwd][type=checkbox]')
                      && find('button', '下一步') && find('h4', 'Wi-Fi设置')) {
                    fill('wifi', wifi);
                    const same = find('input[name=passwd][type=checkbox]');
                    if (!same.checked) same.click();
                    if (expected === 'apply') {
                      const next = find('button', '下一步');
                      if (!next.dataset.routerClicked) {
                        next.dataset.routerClicked = '1';
                        next.click();
                        return 'applied';
                      }
                      return 'wait';
                    }
                    return 'ssid:' + find('input[name=name]').value;
                  }
                  const login = find('input[name=passwd][type=password]');
                  if (login && find('button[type=submit]', '登录') && find('span', '忘记密码')) {
                    fill('login', login); return click('login', find('button[type=submit]', '登录'));
                  }

                  const desktop = find('.gotoPCVersion', '前往电脑版');
                  if (desktop) return click('desktop', desktop);
                  const more = find('.more', '更多高级功能');
                  if (more) return click('more', more);
                  const wan = find('h3', 'WAN口选择');
                  const mode = document.querySelector('select#wanCheck[name=wancheckSelect]');
                  if (wan && mode && !mode.disabled) {
                    const fixed = [...mode.options].find(o => o.label.trim() === '固定WAN口');
                    if (expected === 'auto' && fixed && mode.value !== fixed.value) {
                      const choice = find('a.nowrap', '固定WAN口');
                      if (choice) return click('wan-fixed-choice', choice);
                      const dropdown = find('.ipt-text.dummy', '智能选择WAN口（推荐）');
                      if (dropdown) return click('wan-mode-menu', dropdown);
                    }
                    const fixedLabel = find('.ipt-text.dummy', '固定WAN口');
                    const port = find('.wanListDiv .portsIndex', '1');
                    if (expected === 'verify') {
                      return fixed && mode.value === fixed.value && port
                          && port.closest('.wanListDiv').classList.contains('active') ? 'wan-confirmed' : 'wan-mismatch';
                    }
                    if (expected === 'auto') {
                      if (fixedLabel && port && !port.closest('.wanListDiv').classList.contains('active')
                          && find('span', '保存')) (port.closest('.wanListDiv').querySelector('.wanPortImg') || port.closest('.wanListDiv')).click();
                      (fixedLabel || find('.ipt-text.dummy', '智能选择WAN口（推荐）') || wan)
                          .scrollIntoView({block: 'center'});
                    }
                    if (fixedLabel && port && port.closest('.wanListDiv').classList.contains('active')) {
                      const save = find('button#btnSubmitwanCheck');
                      if (save && !save.dataset.routerClicked) {
                        save.dataset.routerClicked = '1';
                        save.click();
                        return 'wan-saved';
                      }
                    }
                    return 'wan';
                  }
                  const internet = find('a', '上网设置') || find('a span', '上网设置')?.closest('a');
                  if (internet && find('a', '常用设置')) return click('internet', internet);
                  const settings = find('a', '常用设置');
                  if (settings && find('#routermodel')) return click('settings', settings);
                  return 'unknown';
                })()
                """, JSONObject.quote(origin), JSONObject.quote(password), JSONObject.quote(expected));
    }
}

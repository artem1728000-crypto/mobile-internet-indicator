package ru.artemzozula.mobileindicator;

import org.json.JSONObject;

final class XiaomiSetup {
    private XiaomiSetup() {}

    static String script(String origin, String password, String expected) {
        return """
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
                    if (expected === step) target.click();
                    return step;
                  };
                  const fill = (step, field) => {
                    if (expected === step && password) {
                      field.value = password;
                      field.dispatchEvent(new Event('input', {bubbles: true}));
                      field.dispatchEvent(new Event('change', {bubbles: true}));
                    }
                    return step;
                  };

                  const start = find('a.button', '马上体验');
                  if (start && find('input[name=protocal]')) {
                    if (expected === 'welcome') start.scrollIntoView({block: 'center'});
                    return 'welcome';
                  }
                  const offline = find('a', '不插网线，继续配置');
                  if (offline && find('h4', '未检测到入户网线')) return click('offline', offline);
                  const dhcp = find('li.iconfont', '自动获取IP');
                  if (dhcp && find('h3', '选择配置类型')) return click('dhcp', dhcp);

                  const wifi = find('input[name=password]');
                  if (wifi && find('input[name=name]') && find('input[name=passwd][type=checkbox]')
                      && find('button', '下一步') && find('h4', 'Wi-Fi设置')) return fill('wifi', wifi);
                  const login = find('input[name=passwd][type=password]');
                  if (login && find('button[type=submit]', '登录') && find('span', '忘记密码')) {
                    return fill('login', login);
                  }

                  const desktop = find('.gotoPCVersion', '前往电脑版');
                  if (desktop) return click('desktop', desktop);
                  const more = find('.more', '更多高级功能');
                  if (more) return click('more', more);
                  const wan = find('h3', 'WAN口选择');
                  const mode = document.querySelector('select#wanCheck[name=wancheckSelect]');
                  if (wan && mode && !mode.disabled) {
                    const fixed = [...mode.options].find(o => o.label.trim() === '固定WAN口');
                    if (expected === 'wan' && fixed && mode.value !== fixed.value
                        && find('.ipt-text.dummy', '智能选择WAN口（推荐）')) {
                      mode.value = fixed.value;
                      mode.dispatchEvent(new Event('change', {bubbles: true}));
                    }
                    const fixedLabel = find('.ipt-text.dummy', '固定WAN口');
                    const port = find('.wanListDiv .portsIndex', '1');
                    if (expected === 'wan') {
                      if (fixedLabel && port && !port.closest('.wanListDiv').classList.contains('active')
                          && find('span', '保存')) port.closest('.wanListDiv').click();
                      (fixedLabel || find('.ipt-text.dummy', '智能选择WAN口（推荐）') || wan)
                          .scrollIntoView({block: 'center'});
                    }
                    return 'wan';
                  }
                  const internet = find('a', '上网设置') || find('a span', '上网设置')?.closest('a');
                  if (internet && find('a', '常用设置')) return click('internet', internet);
                  const settings = find('a', '常用设置');
                  if (settings && find('#routermodel')) return click('settings', settings);
                  return 'unknown';
                })()
                """.formatted(JSONObject.quote(origin), JSONObject.quote(password), JSONObject.quote(expected));
    }
}

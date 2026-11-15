package ru.artemzozula.mobileindicator;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RouterActivity extends Activity {
    private static boolean webViewConfigured;
    private static final int SAVE = 1;
    private static final int SAVE_WIFI = 2;
    private static final String CAPTURE = """
            (() => {
              const clean = s => (s || '').trim().replace(/\\s+/g, ' ').slice(0, 160);
              return JSON.stringify({
                headings: Array.from(document.querySelectorAll('h1,h2,h3')).slice(0, 40)
                  .map(e => clean(e.textContent)),
                uiElements: Array.from(document.querySelectorAll('body *')).filter(e => {
                  if (e.closest('.config-mess,.config-row,.config-value')) return false;
                  if (!e.getClientRects().length || getComputedStyle(e).visibility !== 'visible'
                      || e.closest('[hidden],[aria-hidden="true"]')) return false;
                  return e.matches('button,input,select,a,label,[role],[tabindex],[onclick]')
                    || getComputedStyle(e).cursor === 'pointer'
                    || Array.from(e.childNodes).some(n => n.nodeType === 3 && n.textContent.trim());
                }).slice(0, 450).map(e => ({
                  tag: e.localName, id: e.id, classes: e.getAttribute('class') || '',
                  role: e.getAttribute('role') || '', target: e.getAttribute('for') || '',
                  label: clean(e.getAttribute('aria-label') || e.getAttribute('title')
                    || Array.from(e.childNodes).filter(n => n.nodeType === 3).map(n => n.textContent).join(' ')),
                  checked: typeof e.checked === 'boolean' ? e.checked : e.getAttribute('aria-checked'),
                  parent: {tag: e.parentElement.localName, id: e.parentElement.id,
                    classes: e.parentElement.getAttribute('class') || ''},
                  disabled: !!e.disabled || e.getAttribute('aria-disabled') === 'true'
                })),
                controls: Array.from(document.querySelectorAll(
                  'input,select,textarea,button,[role=button]')).slice(0, 250).map(e => ({
                    tag: e.tagName.toLowerCase(), type: e.getAttribute('type') || '',
                    id: e.id, name: e.getAttribute('name') || '',
                    label: clean(e.getAttribute('aria-label') || (e.labels?.[0]?.textContent)
                      || (e.tagName === 'BUTTON' ? e.textContent : '')),
                    options: e.tagName === 'SELECT' ? Array.from(e.options).slice(0, 50)
                      .map(o => clean(o.label)) : [],
                    disabled: !!e.disabled
                  }))
              });
            })()
            """;
    private ConnectivityManager connectivity;
    private Network network;
    private WebView browser;
    private String gateway;
    private String capture;
    private String tendaPassword;
    private int tendaAttempt;
    private int tendaSession;
    private int tendaStep;
    private String tendaSsid;
    private boolean directApply;
    private boolean axNextClicked;
    private boolean applyingTenda;
    private boolean savingWifi;
    private boolean routerLost;
    private android.widget.Button tendaButton;
    private ConnectivityManager.NetworkCallback callback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        gateway = getIntent().getStringExtra("gateway");
        if (state != null) capture = state.getString("capture");
        connectivity = getSystemService(ConnectivityManager.class);
        network = findWifi();
        if (network == null || !connectivity.bindProcessToNetwork(network)) {
            Toast.makeText(this, "Подключись к Wi-Fi этого роутера", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= 28 && !webViewConfigured) {
            WebView.setDataDirectorySuffix("router");
            webViewConfigured = true;
        }
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onLost(Network lost) {
                if (lost.equals(network)) runOnUiThread(() -> {
                    if (isDestroyed() || browser == null) return;
                    routerLost = true;
                    if (applyingTenda || savingWifi) return;
                    browser.stopLoading();
                    Toast.makeText(RouterActivity.this, "Связь с роутером потеряна", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        };
        connectivity.registerNetworkCallback(new android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback);

        LinearLayout page = Ui.column(this);
        page.setBackgroundColor(Ui.BACK);
        if (Build.VERSION.SDK_INT >= 30) page.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        LinearLayout header = Ui.column(this);
        Ui.pad(header, 12, 8);
        TextView title = Ui.text(this, "Роутер · " + gateway, 18, Ui.INK, true);
        Ui.add(header, title, 0);
        LinearLayout actions = new LinearLayout(this);
        addAction(actions, "Назад", this::onBackPressed);
        addAction(actions, "Обновить", () -> browser.reload());
        addAction(actions, "Снимок шага", this::takeCapture);
        Ui.add(header, actions, 6);
        tendaButton = Ui.button(this, "Tenda — настроить", true, this::promptTenda);
        Ui.add(header, tendaButton, 6);

        Ui.add(page, header, 0);

        browser = new WebView(this);
        browser.setSaveEnabled(false);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.setImportantForAutofill(WebView.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !allowed(request.getUrl());
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (allowed(url) || "data".equals(url.getScheme())) return null;
                return new WebResourceResponse("text/plain", "UTF-8",
                        new java.io.ByteArrayInputStream(new byte[0]));
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) title.setText("Роутер · ошибка загрузки");
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (allowed(Uri.parse(url))) title.setText("Роутер · " + gateway);
            }
        });
        page.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        browser.loadUrl("http://" + gateway + "/");
    }

    @SuppressWarnings("deprecation")
    private Network findWifi() {
        if (gateway == null || gateway.isEmpty()) return null;
        for (Network candidate : connectivity.getAllNetworks()) {
            NetworkCapabilities caps = connectivity.getNetworkCapabilities(candidate);
            if (candidate.getNetworkHandle() == getIntent().getLongExtra("network", 0)
                    && caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && gateway.equals(RouterLink.from(connectivity.getLinkProperties(candidate)).gateway())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean allowed(Uri url) {
        return ("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                && gateway.equals(url.getHost()) && url.getUserInfo() == null;
    }

    private void addAction(LinearLayout row, String label, Runnable action) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() != 0) params.setMarginStart(Ui.dp(this, 5));
        row.addView(Ui.button(this, label, false, action), params);
    }

    private void promptTenda() {
        if (tendaPassword != null) {
            finishTenda("Помощник остановлен.");
            return;
        }
        String url = browser.getUrl();
        if (url == null || !allowed(Uri.parse(url))) return;
        Uri uri = Uri.parse(url);
        String origin = uri.getScheme() + "://" + uri.getEncodedAuthority();
        browser.evaluateJavascript(TendaSetup.script("", origin, -1), result -> {
            if (isFinishing() || isDestroyed() || !url.equals(browser.getUrl()) || tendaPassword != null) return;
            if ("\"login\"".equals(result) || "\"settings\"".equals(result)) {
                boolean signedIn = "\"settings\"".equals(result);
                new android.app.AlertDialog.Builder(this).setTitle("Модель настроенного Tenda")
                        .setItems(new String[]{"RX2 Pro / TX2L Pro — объединить диапазоны",
                                "AX12 Pro — выключить автосогласование WAN/LAN"}, (dialog, choice) -> {
                            if (choice == 0) promptTenda(true, false, signedIn, false);
                            else promptTenda(false, false, signedIn, true, true);
                        }).show();
            } else if ("\"ax-wizard\"".equals(result)) {
                promptTenda(false, false, false, true);
            } else if ("\"rx\"".equals(result) || "\"tx\"".equals(result)) {
                promptTenda(false, "\"rx\"".equals(result), false, false);
            } else if ("\"welcome\"".equals(result)) {
                new android.app.AlertDialog.Builder(this).setTitle("Заводской мастер Tenda")
                        .setItems(new String[]{"RX2 Pro", "TX2L Pro"}, (dialog, choice) ->
                                promptTenda(false, choice == 0, false, false)).show();
            } else {
                Toast.makeText(this, "Открой приветствие или страницу входа Tenda", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void promptTenda(boolean merge, boolean rx, boolean signedIn, boolean ax) {
        promptTenda(merge, rx, signedIn, ax, false);
    }

    private void promptTenda(boolean merge, boolean rx, boolean signedIn, boolean ax, boolean wan) {
        if (tendaPassword != null) {
            finishTenda("Помощник остановлен.");
            return;
        }
        android.widget.EditText password = new android.widget.EditText(this);
        password.setHint("Пароль Wi-Fi");
        password.setSingleLine(true);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        password.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        password.setSaveEnabled(false);
        LinearLayout content = Ui.column(this);
        Ui.pad(content, 20, 8);
        Ui.add(content, Ui.text(this,
                wan ? "Введи пароль роутера. Помощник выключит автосогласование WAN/LAN, если оно включено." : ax ? "Отключи WAN. Помощник заполнит пароль. Затем отметь использование пароля Wi-Fi для входа в роутер и нажми «Далее»." : merge ? "Подключись к защищённой сети 2,4 ГГц. Введи пароль Wi-Fi для входа в роутер." : "Подключись к заводской сети Tenda и отключи WAN. Оставь выбранный мастером «Динамический IP-адрес». Помощник заполнит пароль Wi-Fi и применит настройки. Заводская сеть после этого может исчезнуть.",
                15, Ui.INK, false), 0);
        Ui.add(content, password, 8);
        android.content.SharedPreferences preferences = getSharedPreferences("tenda_setup", MODE_PRIVATE);
        String lastPassword = preferences.getString("last_password", "");
        android.widget.Button reuse = Ui.button(this, "Вставить последний пароль", false, () -> {
            password.setText(lastPassword);
            password.setSelection(password.length());
        });
        reuse.setEnabled(!lastPassword.isEmpty());
        Ui.add(content, reuse, 8);
        Ui.add(content, Ui.button(this, "Сгенерировать пароль", false, () -> {
            java.security.SecureRandom random = new java.security.SecureRandom();
            StringBuilder generated = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                char digit = (char) ('0' + random.nextInt(10));
                generated.append(digit).append(digit);
            }
            password.setText(generated);
            password.setSelection(password.length());
        }), 8);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(ax ? "Настройка Tenda AX12 Pro" : merge ? "Объединение диапазонов Tenda" : rx ? "Настройка Tenda RX2 Pro" : "Настройка Tenda TX2L Pro").setView(content)
                .setNegativeButton("Отмена", null).setPositiveButton("Начать", null).create();
        dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = password.getText().toString();
            if (value.length() < 8 || value.length() > 63 || !value.matches("[ -~]+")) {
                password.setError("Введи 8–63 печатных символа ASCII");
                return;
            }
            preferences.edit().putString("last_password", value).apply();
            password.getText().clear();
            dialog.dismiss();
            tendaPassword = value;
            tendaAttempt = 0;
            tendaStep = wan ? (signedIn ? 31 : 30) : ax ? 20 : merge ? (signedIn ? 11 : 10) : 0;
            directApply = rx;
            axNextClicked = false;
            tendaSsid = null;
            tendaButton.setText("Остановить помощник");
            runTenda(++tendaSession);
        });
    }

    private void runTenda(int session) {
        if (session != tendaSession || isFinishing() || isDestroyed() || tendaPassword == null) return;
        if (tendaStep == 32 && tendaAttempt >= 8 && network.equals(findWifi())) {
            openWan(session);
            return;
        }
        if (tendaAttempt++ >= 30 || !network.equals(findWifi())) {
            finishTenda("Шаг не распознан. Продолжи вручную. Шаг: " + (tendaStep + 1) + ".");
            return;
        }
        String url = browser.getUrl();
        if (url == null || !allowed(Uri.parse(url))) {
            browser.postDelayed(() -> runTenda(session), 500);
            return;
        }
        Uri uri = Uri.parse(url);
        String origin = uri.getScheme() + "://" + uri.getEncodedAuthority();
        applyingTenda = tendaStep == 25 || tendaStep == 26 || tendaStep == 9 || (directApply && tendaStep == 7);
        browser.evaluateJavascript(TendaSetup.script(tendaPassword, origin, axNextClicked && tendaStep == 31 ? 33 : tendaStep), result -> {
            if (session != tendaSession || isFinishing() || isDestroyed()) return;
            applyingTenda = false;
            if ("\"next-clicked\"".equals(result)) {
                axNextClicked = true;
                tendaAttempt = 0;
            }
            if ("\"open-wan\"".equals(result)) {
                openWan(session);
                return;
            }
            if ("\"wan-off\"".equals(result)) {
                finishTenda("Автосогласование WAN/LAN выключено.");
                return;
            }
            if ("\"ready-to-save\"".equals(result) && tendaStep == 8) {
                tendaStep = 9;
                tendaAttempt = 0;

            }
            if ("\"ax-review\"".equals(result)) {
                finishTenda("Пароль заполнен. Включи использование пароля Wi-Fi для учётной записи и нажми «Далее».");
                return;
            }
            if ("\"manual-save\"".equals(result)) {
                finishTenda("Диапазоны объединены в форме. Нажми «Сохранить» на странице роутера.");
                return;
            }
            if ((tendaStep == 6 || tendaStep == 23) && result != null && result.startsWith("\"ssid:")) {
                try {
                    String ssid = ((String) new JSONTokener(result).nextValue()).substring(5);
                    tendaStep++;
                    tendaAttempt = 0;
                    tendaSsid = ssid;
                } catch (org.json.JSONException ignored) {
                    finishTenda("Не удалось прочитать имя Wi-Fi.");
                    return;
                }
            }
            if ("\"wan\"".equals(result) || "\"password\"".equals(result)) {
                finishTenda("\"wan\"".equals(result) ? "Мастер выбрал PPPoE или статический IP. Проверь тип WAN вручную."
                        : "Поле пароля уже заполнено или значение изменилось. Проверь Wi-Fi вручную.");
                return;
            }
            if ("\"advance\"".equals(result)) {
                tendaAttempt = 0;
                if (directApply && tendaStep == 7) tendaStep = 9;
                ++tendaStep;
                if (tendaStep == 26 && !routerLost) {
                    browser.evaluateJavascript(TendaSetup.script(tendaPassword, origin, 26), ignored ->
                            completeAx(session));
                    browser.postDelayed(() -> completeAx(session), 1500);
                    return;
                }
                if ((tendaStep == 10 || tendaStep == 26) && tendaSsid != null && offerWifiSave(tendaSsid)) return;
                if (tendaStep == 10 || tendaStep == 14 || tendaStep == 26) {
                    finishTenda("Настройки отправлены. После применения подключись к сети 2,4 ГГц.");
                    return;
                }
            }
            if (routerLost) finish();
            else browser.postDelayed(() -> runTenda(session), 500);
        });
    }

    private void openWan(int session) {
        tendaStep = 34;
        tendaAttempt = 0;
        browser.loadUrl("http://" + gateway + "/index.html#/advance/network/auto-detect");
        browser.postDelayed(() -> runTenda(session), 700);
    }

    private void completeAx(int session) {
        if (session != tendaSession || tendaStep != 26 || isFinishing() || isDestroyed()) return;
        tendaStep = 27;
        if (tendaSsid != null && offerWifiSave(tendaSsid)) return;
        finishTenda("Настройки отправлены. Подключись к защищённой сети после перезапуска.");
    }

    private boolean offerWifiSave(String ssid) {
        if (Build.VERSION.SDK_INT < 30 || !ssid.matches("@?Tenda_[0-9A-Fa-f]{6}")) return false;
        try {
            android.net.wifi.WifiNetworkSuggestion suggestion =
                    new android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(ssid)
                            .setWpa2Passphrase(tendaPassword).build();
            java.util.ArrayList<android.net.wifi.WifiNetworkSuggestion> networks = new java.util.ArrayList<>();
            networks.add(suggestion);
            savingWifi = true;
            startActivityForResult(new Intent(android.provider.Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(android.provider.Settings.EXTRA_WIFI_NETWORK_LIST, networks), SAVE_WIFI);
            return true;
        } catch (android.content.ActivityNotFoundException | IllegalArgumentException ignored) {
            savingWifi = false;
            return false;
        }
    }

    private void finishTenda(String message) {
        tendaSession++;
        tendaPassword = null;
        tendaButton.setText("Tenda — настроить");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void takeCapture() {
        String url = browser.getUrl();
        if (url == null || !allowed(Uri.parse(url))) return;
        browser.evaluateJavascript(CAPTURE, result -> {
            if (isFinishing() || !url.equals(browser.getUrl()) || !network.equals(findWifi())) return;
            try {
                Object json = new JSONTokener(result).nextValue();
                if (!(json instanceof String)) throw new org.json.JSONException("Empty capture");
                JSONObject content = new JSONObject((String) json);
                content.put("capturedAt", java.time.OffsetDateTime.now().toString());
                content.put("gateway", gateway);
                capture = content.toString(2);
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_TITLE, "router-step-" + stamp + ".json"), SAVE);
            } catch (org.json.JSONException error) {
                Toast.makeText(this, "Не удалось прочитать страницу", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SAVE_WIFI) {
            savingWifi = false;
            java.util.ArrayList<Integer> codes = data == null ? null : data.getIntegerArrayListExtra(
                    android.provider.Settings.EXTRA_WIFI_NETWORK_RESULT_LIST);
            boolean saved = result == RESULT_OK && codes != null && !codes.isEmpty()
                    && (codes.get(0) == android.provider.Settings.ADD_WIFI_RESULT_SUCCESS
                    || codes.get(0) == android.provider.Settings.ADD_WIFI_RESULT_ALREADY_EXISTS);
            finishTenda(saved ? "Сеть сохранена в Android. Подключись после перезапуска Wi-Fi."
                    : "Android не подтвердил сохранение. Подключись к сети вручную.");
            if (routerLost) finish();
            return;
        }
        if (request != SAVE) return;
        if (result == RESULT_OK && data != null && data.getData() != null && capture != null) {
            try (OutputStream output = getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (output == null) throw new java.io.IOException("No output stream");
                output.write(capture.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Снимок сохранён", Toast.LENGTH_SHORT).show();
            } catch (java.io.IOException | SecurityException error) {
                Toast.makeText(this, "Не удалось сохранить снимок", Toast.LENGTH_LONG).show();
            }
        }
        capture = null;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("capture", capture);
    }

    @Override public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else finish();
    }

    @Override protected void onDestroy() {
        tendaPassword = null;
        if (callback != null) connectivity.unregisterNetworkCallback(callback);
        if (browser != null) {
            browser.stopLoading();
            ((LinearLayout) browser.getParent()).removeView(browser);
            browser.destroy();
        }
        if (network != null) connectivity.bindProcessToNetwork(null);
        super.onDestroy();
    }
}

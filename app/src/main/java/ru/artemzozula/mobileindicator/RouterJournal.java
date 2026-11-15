package ru.artemzozula.mobileindicator;

import android.content.Context;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class RouterJournal {
    private final File file;
    private boolean recording;
    private JSONArray events = new JSONArray();
    private String started;
    private boolean truncated;
    private int size;

    RouterJournal(Context context) {
        file = new File(context.getFilesDir(), "router-journal.json");
    }

    synchronized boolean isRecording() { return recording; }
    boolean hasSaved() { return file.isFile(); }

    synchronized void start() {
        events = new JSONArray();
        started = java.time.OffsetDateTime.now().toString();
        truncated = false;
        size = 0;
        recording = true;
        event("app", "recording-start");
    }

    synchronized void stop() {
        event("app", "recording-stop");
        recording = false;
    }

    synchronized void event(String source, String action) {
        if (!recording) return;
        try {
            JSONObject entry = new JSONObject().put("source", source).put("action", action);
            append(entry);
        } catch (org.json.JSONException ignored) { }
    }

    @JavascriptInterface public synchronized void record(String json) {
        if (!recording || json == null || json.length() > 24000) return;
        try { append(new JSONObject(json)); }
        catch (org.json.JSONException ignored) { }
    }

    private void append(JSONObject entry) throws org.json.JSONException {
        int length = entry.toString().length();
        if (events.length() >= 4000 || size + length > 2000000) { truncated = true; return; }
        size += length;
        entry.put("receivedAt", java.time.OffsetDateTime.now().toString());
        events.put(entry);
    }

    synchronized String export() throws java.io.IOException, org.json.JSONException {
        if (started == null) return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String json = new JSONObject().put("format", 2).put("startedAt", started)
                .put("truncated", truncated).put("events", events).toString(2);
        android.util.AtomicFile saved = new android.util.AtomicFile(file);
        java.io.FileOutputStream output = null;
        try {
            output = saved.startWrite();
            output.write(json.getBytes(StandardCharsets.UTF_8));
            saved.finishWrite(output);
        } catch (java.io.IOException error) {
            saved.failWrite(output);
            throw error;
        }
        return json;
    }

    String script() {
        return """
                (() => {
                  if (window.__routerJournalInstalled) return;
                  window.__routerJournalInstalled = true;
                  const safeUrl = value => {
                    try {
                      const url = new URL(value, location.href);
                      const path = value => value.split('?')[0]
                        .replace(/([;\\w-]+=)[^/;]*/g, '$1[redacted]')
                        .replace(/[a-f0-9]{24,}/gi, '[redacted]');
                      return {origin:url.origin, path:path(url.pathname),
                        route:path(url.hash.slice(1)),
                        queryKeys:[...url.searchParams.keys()].slice(0, 30)};
                    } catch (_) { return null; }
                  };
                  const describe = e => ({
                    tag: e.localName,
                    link: e.matches('a[href]') ? safeUrl(e.getAttribute('href')) : null,
                    id: (e.id || '').slice(0, 80),
                    classes: (e.getAttribute('class') || '').slice(0, 120),
                    name: (e.getAttribute('name') || '').slice(0, 80),
                    type: e.getAttribute('type') || '',
                    text: /^(button|a|option|li|label)$/.test(e.localName) && !e.querySelector('input,textarea,[contenteditable]')
                      ? e.textContent.trim().slice(0, 100) : '',
                    checked: /^(checkbox|radio)$/.test(e.type) ? e.checked : null,
                    selectedIndex: e.localName === 'select' ? e.selectedIndex : null
                  });
                  const snapshot = () => [...document.querySelectorAll('input,select,button,a,[role=button]')]
                    .filter(e => e.getClientRects().length).slice(0, 60).map(describe);
                  const send = data => RouterJournal.record(JSON.stringify({...data, page:safeUrl(location.href)}));
                  let lastAddress = location.href;
                  const navigation = () => {
                    if (lastAddress === location.href) return;
                    lastAddress = location.href;
                    send({source:'page', action:'navigation', controls:snapshot()});
                  };
                  for (const method of ['pushState', 'replaceState']) {
                    const original = history[method];
                    history[method] = function(...args) {
                      const result = original.apply(this, args);
                      navigation();
                      return result;
                    };
                  }
                  window.addEventListener('popstate', navigation);
                  window.addEventListener('hashchange', navigation);
                  for (const type of ['click','change','submit']) {
                    document.addEventListener(type, event => {
                      const target = event.target instanceof Element ? event.target : null;
                      if (!target) return;
                      const control = target.closest('button,a,input,select,label,[role=button],li') || target;
                      send({source: window.__routerAssistantAction ? 'assistant' : event.isTrusted ? 'user' : 'script', action: type,
                        target: describe(control), parent: control.parentElement ? describe(control.parentElement) : null,
                        controls: snapshot()});
                    }, true);
                  }
                  send({source:'page', action:'loaded', controls:snapshot()});
                })();
                """;
    }
}

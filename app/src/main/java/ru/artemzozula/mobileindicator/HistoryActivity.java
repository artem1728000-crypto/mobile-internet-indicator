package ru.artemzozula.mobileindicator;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class HistoryActivity extends Activity {
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru-RU");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", RUSSIAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", RUSSIAN);
    private final ZoneId zone = ZoneId.systemDefault();
    private LocalDate day = LocalDate.now();
    private boolean workHours;
    private TextView report;
    private LinearLayout timeline;
    private Button date;
    private Button next;
    private Button hours;
    private HistoryStore history;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            if (day.equals(LocalDate.now())) report.postDelayed(this, 15_000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        history = new HistoryStore(this);
        if (state != null) {
            day = LocalDate.ofEpochDay(state.getLong("day"));
            workHours = state.getBoolean("workHours");
        }
        LinearLayout layout = Ui.screen(this);
        Ui.add(layout, Ui.button(this, "‹  Диагностика", false, this::finish), 0);
        Ui.add(layout, Ui.text(this, "История интернета", 28, Ui.INK, true), 18);
        date = button(layout, "", this::pickDate);
        LinearLayout navigation = new LinearLayout(this);
        button(navigation, "‹ Назад", () -> go(day.minusDays(1)));
        button(navigation, "Сегодня", () -> go(LocalDate.now()));
        next = button(navigation, "Вперёд ›", () -> go(day.plusDays(1)));
        Ui.add(layout, navigation, 4);
        hours = button(layout, "", () -> {
            workHours = !workHours;
            render();
        });
        LinearLayout card = Ui.column(this);
        Ui.pad(card, 16, 16);
        card.setBackground(Ui.shape(this, Ui.SURFACE, Ui.LINE, 20));
        timeline = new LinearLayout(this);
        timeline.setBackground(Ui.shape(this, Ui.LINE, 0, 8));
        timeline.setClipToOutline(true);
        android.widget.LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, Ui.dp(this, 13));
        card.addView(timeline, bar);
        report = Ui.text(this, "", 15, Ui.INK, false);
        report.setLineSpacing(Ui.dp(this, 4), 1);
        Ui.add(card, report, 14);
        Ui.add(layout, card, 16);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh.run();
    }

    @Override protected void onPause() {
        report.removeCallbacks(refresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        history.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putLong("day", day.toEpochDay());
        state.putBoolean("workHours", workHours);
    }

    private static Button button(LinearLayout layout, String text, Runnable click) {
        Button button = Ui.button(layout.getContext(), text, false, click);
        if (layout.getOrientation() == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (layout.getChildCount() > 0) params.setMarginStart(Ui.dp(layout.getContext(), 5));
            layout.addView(button, params);
        } else {
            Ui.add(layout, button, 8);
        }
        return button;
    }

    private void go(LocalDate target) {
        day = target;
        report.removeCallbacks(refresh);
        refresh.run();
    }

    private void pickDate() {
        DatePickerDialog picker = new DatePickerDialog(this,
                (view, year, month, number) -> go(LocalDate.of(year, month + 1, number)),
                day.getYear(), day.getMonthValue() - 1, day.getDayOfMonth());
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    private void render() {
        date.setText(DATE.format(day));
        next.setEnabled(day.isBefore(LocalDate.now()));
        hours.setText(workHours ? "08:00–17:00 · Показать весь день" : "Весь день · Показать 08:00–17:00");
        long start = at(day, workHours ? LocalTime.of(8, 0) : LocalTime.MIDNIGHT);
        long endOfDay = at(day.plusDays(1), LocalTime.MIDNIGHT);
        long end = Math.min(System.currentTimeMillis(), workHours ? at(day, LocalTime.of(17, 0)) : endOfDay);
        timeline.removeAllViews();
        if (end <= start) {
            report.setText("Этот период ещё не начался");
            return;
        }
        long full = 0, limited = 0, none = 0, observed = 0;
        long cursor = start;
        StringBuilder periods = new StringBuilder();
        try (Cursor rows = history.between(start, end)) {
            while (rows.moveToNext()) {
                long from = Math.max(cursor, rows.getLong(0));
                long to = Math.min(end, rows.getLong(1));
                if (to <= from) continue;
                if (from > cursor) {
                    segment(from - cursor, Ui.LINE);
                    append(periods, cursor, from, "Нет наблюдения", endOfDay);
                }
                String status = rows.getString(2);
                long duration = to - from;
                observed += duration;
                segment(duration, status.equals(HistoryStore.FULL) ? Ui.GREEN
                        : status.equals(HistoryStore.LIMITED) ? Ui.AMBER : Ui.RED);
                switch (status) {
                    case HistoryStore.FULL: full += duration; break;
                    case HistoryStore.LIMITED: limited += duration; break;
                    default: none += duration;
                }
                String label = status + " · " + rows.getString(3);
                if (!rows.isNull(4) && from == rows.getLong(0) && to == rows.getLong(1)) {
                    int min = rows.getInt(4), max = rows.getInt(5);
                    label += " · " + min + (min == max ? "" : "…" + max) + " дБм";
                }
                append(periods, from, to, label, endOfDay);
                cursor = to;
            }
        }
        if (cursor < end) {
            segment(end - cursor, Ui.LINE);
            append(periods, cursor, end, "Нет наблюдения", endOfDay);
        }
        String summary = "● Доступ есть  " + duration(full) + "\n● Вероятный белый список  "
                + duration(limited) + "\n● Доступ не подтверждён  " + duration(none)
                + "\n● Нет наблюдения  " + duration(end - start - observed) + "\n\n";
        android.text.SpannableString content = new android.text.SpannableString(summary + periods);
        int index = -1;
        for (int color : new int[]{Ui.GREEN, Ui.AMBER, Ui.RED, Ui.MUTED}) {
            index = summary.indexOf('●', index + 1);
            content.setSpan(new android.text.style.ForegroundColorSpan(color), index, index + 1, 0);
        }
        report.setText(content);
    }

    private void segment(long millis, int color) {
        android.view.View block = new android.view.View(this);
        block.setBackgroundColor(color);
        timeline.addView(block, new LinearLayout.LayoutParams(0, -1, millis / 1000f));
    }

    private long at(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }

    private void append(StringBuilder result, long start, long end, String text, long endOfDay) {
        result.append(time(start)).append(" – ").append(end == endOfDay ? "24:00" : time(end))
                .append('\n').append(text).append("\n\n");
    }

    private String time(long value) {
        return TIME.format(Instant.ofEpochMilli(value).atZone(zone));
    }

    private static String duration(long millis) {
        long seconds = millis / 1000;
        return String.format(RUSSIAN, "%d ч %02d мин %02d с", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}

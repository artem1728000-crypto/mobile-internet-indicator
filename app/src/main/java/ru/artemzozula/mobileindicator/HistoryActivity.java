package ru.artemzozula.mobileindicator;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        button(layout, "← К диагностике", this::finish);
        TextView title = new TextView(this);
        title.setText("История интернета");
        title.setTextSize(26);
        layout.addView(title);
        date = button(layout, "", this::pickDate);
        LinearLayout navigation = new LinearLayout(this);
        button(navigation, "‹ Назад", () -> go(day.minusDays(1)));
        button(navigation, "Сегодня", () -> go(LocalDate.now()));
        next = button(navigation, "Вперёд ›", () -> go(day.plusDays(1)));
        layout.addView(navigation);
        hours = button(layout, "", () -> {
            workHours = !workHours;
            render();
        });
        report = new TextView(this);
        report.setTextSize(16);
        layout.addView(report);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            }
            return insets;
        });
        setContentView(scroll);
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
        Button button = new Button(layout.getContext());
        button.setText(text);
        button.setOnClickListener(view -> click.run());
        layout.addView(button);
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
                if (from > cursor) append(periods, cursor, from, "Нет наблюдения", endOfDay);
                String status = rows.getString(2);
                long duration = to - from;
                observed += duration;
                switch (status) {
                    case HistoryStore.FULL: full += duration; break;
                    case HistoryStore.LIMITED: limited += duration; break;
                    default: none += duration;
                }
                append(periods, from, to, status + " · " + rows.getString(3), endOfDay);
                cursor = to;
            }
        }
        if (cursor < end) append(periods, cursor, end, "Нет наблюдения", endOfDay);
        report.setText("Доступ есть  " + duration(full) + "\nВероятный белый список  "
                + duration(limited) + "\nДоступ не подтверждён  " + duration(none)
                + "\nНет наблюдения  " + duration(end - start - observed) + "\n\n" + periods);
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

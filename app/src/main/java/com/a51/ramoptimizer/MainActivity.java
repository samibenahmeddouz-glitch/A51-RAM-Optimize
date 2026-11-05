
package com.a51.ramoptimizer;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView dashboard;
    private TextView statusView;
    private TextView historyView;

    private HistoryChartView chartView;

    private SharedPreferences preferences;

    private double minUsage = 100.0;
    private double maxUsage = 0.0;
    private boolean hasValidReading = false;

    // Smart RAM alert
    private int highRamReadings = 0;
    private boolean highRamAlertShown = false;

    private long lastSampleTime = 0;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            updateDashboard();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        preferences = getSharedPreferences("ram_history", MODE_PRIVATE);

        getWindow().getDecorView().setBackgroundColor(0xFF101820);

        ScrollView scroll = new ScrollView(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(36, 36, 36, 36);

        TextView title = new TextView(this);
        title.setText("A51 RAM OPTIMIZER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 10, 0, 24);
        panel.addView(title);

        statusView = new TextView(this);
        statusView.setTextColor(Color.GREEN);
        statusView.setTextSize(19);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(12, 18, 12, 18);
        panel.addView(statusView);

        dashboard = new TextView(this);
        dashboard.setTextColor(0xFFEAF2F8);
        dashboard.setTextSize(15);
        dashboard.setLineSpacing(8f, 1f);
        dashboard.setPadding(0, 24, 0, 24);
        panel.addView(dashboard);

        TextView chartTitle = new TextView(this);
        chartTitle.setText("RAM / ZRAM CHART - 24 HOURS");
        chartTitle.setTextColor(0xFF40C4FF);
        chartTitle.setTextSize(19);
        chartTitle.setPadding(0, 20, 0, 12);
        panel.addView(chartTitle);

        chartView = new HistoryChartView();
        LinearLayout.LayoutParams chartParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(290)
                );
        panel.addView(chartView, chartParams);

        TextView historyTitle = new TextView(this);
        historyTitle.setText("RAM / ZRAM HISTORY");
        historyTitle.setTextColor(0xFF40C4FF);
        historyTitle.setTextSize(19);
        historyTitle.setPadding(0, 20, 0, 12);
        panel.addView(historyTitle);

        historyView = new TextView(this);
        historyView.setTextColor(0xFFEAF2F8);
        historyView.setTextSize(14);
        historyView.setLineSpacing(8f, 1f);
        panel.addView(historyView);

        scroll.addView(panel);
        setContentView(scroll);

        refresh.run();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateDashboard() {

        long total = readMemInfoValue("MemTotal");
        long available = readMemInfoValue("MemAvailable");

        if (total <= 0 || available < 0) {
            statusView.setText("RAM: Reading unavailable");
            statusView.setTextColor(Color.YELLOW);
            dashboard.setText(readReport());
            return;
        }

        double usage = ((double) (total - available) / total) * 100.0;

        if (usage < 0) usage = 0;
        if (usage > 100) usage = 100;

        if (!hasValidReading) {
            minUsage = usage;
            maxUsage = usage;
            hasValidReading = true;
        } else {
            minUsage = Math.min(minUsage, usage);
            maxUsage = Math.max(maxUsage, usage);
        }

        // Smart alert: 3 consecutive readings at or above 85%.
        if (usage >= 85.0) {

            highRamReadings++;

            if (highRamReadings >= 3 && !highRamAlertShown) {

                highRamAlertShown = true;

                new AlertDialog.Builder(this)
                        .setTitle("High RAM Usage Warning")
                        .setMessage(
                                "RAM usage has remained at or above 85% " +
                                "for 3 consecutive readings.\n\n" +
                                String.format(
                                        Locale.US,
                                        "Current RAM usage: %.1f%%",
                                        usage
                                )
                        )
                        .setPositiveButton("OK", null)
                        .show();
            }

        } else {

            highRamReadings = 0;
            highRamAlertShown = false;
        }

        String level;
        int color;

        if (usage >= 85.0) {
            level = "HIGH RAM USAGE - WARNING";
            color = Color.RED;
        } else if (usage >= 75.0) {
            level = "MODERATE RAM USAGE";
            color = 0xFFFFC107;
        } else {
            level = "RAM STATUS: NORMAL";
            color = Color.GREEN;
        }

        statusView.setText(
                level + "\n" +
                String.format(Locale.US, "RAM Usage: %.1f%%", usage)
        );

        statusView.setTextColor(color);

        dashboard.setText(readReport(usage, minUsage, maxUsage));

        saveHistorySample(usage);

        JSONArray samples = getHistorySamples();

        chartView.setSamples(samples);
        historyView.setText(readHistory());
    }

    private void saveHistorySample(double ramUsage) {

        long now = System.currentTimeMillis();

        if (lastSampleTime != 0 && now - lastSampleTime < 60000) {
            return;
        }

        long zramUsed = readZramUsed();

        if (zramUsed < 0) {
            return;
        }

        lastSampleTime = now;

        try {
            JSONArray history = getHistorySamples();
            JSONArray updated = new JSONArray();

            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.getJSONObject(i);

                if (now - item.getLong("time") <= 86400000L) {
                    updated.put(item);
                }
            }

            JSONObject sample = new JSONObject();
            sample.put("time", now);
            sample.put("ram", ramUsage);
            sample.put("zram", zramUsed / 1048576.0);

            updated.put(sample);

            JSONArray limited = new JSONArray();

            int start = Math.max(0, updated.length() - 1440);

            for (int i = start; i < updated.length(); i++) {
                limited.put(updated.getJSONObject(i));
            }

            preferences.edit()
                    .putString("samples", limited.toString())
                    .apply();

        } catch (Exception e) {
            // History errors do not interrupt monitoring.
        }
    }

    private JSONArray getHistorySamples() {
        try {
            return new JSONArray(
                    preferences.getString("samples", "[]")
            );
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private long readZramUsed() {

        String value = readTextFile("/sys/block/zram0/mm_stat");

        if (value == null) return -1;

        try {
            String[] parts = value.trim().split("\\s+");

            if (parts.length >= 3) {
                return Long.parseLong(parts[2]);
            }

        } catch (Exception e) {
            return -1;
        }

        return -1;
    }

    private String readHistory() {

        StringBuilder out = new StringBuilder();

        try {
            JSONArray history = getHistorySamples();

            if (history.length() == 0) {
                return "Waiting for first history reading...";
            }

            SimpleDateFormat format =
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

            out.append("Saved readings: ")
                    .append(history.length())
                    .append("\n\n");

            int start = Math.max(0, history.length() - 10);

            for (int i = history.length() - 1; i >= start; i--) {

                JSONObject item = history.getJSONObject(i);

                String time = format.format(
                        new Date(item.getLong("time"))
                );

                double ram = item.getDouble("ram");
                double zram = item.getDouble("zram");

                out.append(time)
                        .append(" | RAM: ")
                        .append(String.format(Locale.US, "%.1f%%", ram))
                        .append(" | ZRAM: ")
                        .append(String.format(Locale.US, "%.1f MB", zram))
                        .append('\n');
            }

            out.append("\nHistory is kept for 24 hours.");

        } catch (Exception e) {
            out.append("History unavailable.");
        }

        return out.toString();
    }

    private String readReport() {
        return readReport(-1, minUsage, maxUsage);
    }

    private String readReport(double usage, double minimum, double maximum) {

        StringBuilder out = new StringBuilder();

        out.append("RAM MONITOR\n");
        out.append("Refresh interval: 3 seconds\n\n");

        if (usage >= 0) {
            out.append(String.format(Locale.US,
                    "RAM Usage: %.1f%%\n", usage));

            out.append(String.format(Locale.US,
                    "Minimum Usage: %.1f%%\n", minimum));

            out.append(String.format(Locale.US,
                    "Maximum Usage: %.1f%%\n\n", maximum));
        }

        out.append("RAM (/proc/meminfo)\n");

        appendFile(
                out,
                "/proc/meminfo",
                new String[]{
                        "MemTotal:",
                        "MemFree:",
                        "MemAvailable:",
                        "Buffers:",
                        "Cached:",
                        "SwapTotal:",
                        "SwapFree:",
                        "SwapCached:"
                }
        );

        out.append("\nZRAM (read-only)\n");

        appendOne(out, "/sys/block/zram0/disksize", "disksize bytes");
        appendOne(out, "/sys/block/zram0/mm_stat", "mm_stat");
        appendOne(out, "/sys/block/zram0/comp_algorithm", "algorithms");

        out.append("\n\nNo tuning is active.");
        out.append("\nNo processes are killed.");
        out.append("\nAll values are read-only.");

        return out.toString();
    }

    private long readMemInfoValue(String key) {

        try (BufferedReader br =
                     new BufferedReader(new FileReader("/proc/meminfo"))) {

            String line;

            while ((line = br.readLine()) != null) {

                if (line.startsWith(key + ":")) {

                    String[] parts = line.trim().split("\\s+");

                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]);
                    }
                }
            }

        } catch (Exception e) {
            return -1;
        }

        return -1;
    }

    private String readTextFile(String file) {

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            StringBuilder value = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                value.append(line).append(' ');
            }

            return value.toString().trim();

        } catch (Exception e) {
            return null;
        }
    }

    private void appendFile(StringBuilder out, String file, String[] keys) {

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            String line;

            while ((line = br.readLine()) != null) {

                for (String key : keys) {

                    if (line.startsWith(key)) {
                        out.append(line).append('\n');
                        break;
                    }
                }
            }

        } catch (IOException e) {
            out.append("Unavailable: ").append(file).append('\n');
        }
    }

    private void appendOne(StringBuilder out, String file, String label) {

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            StringBuilder value = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {

                if (value.length() > 0) {
                    value.append(' ');
                }

                value.append(line);
            }

            out.append(label)
                    .append(": ")
                    .append(value.toString().trim())
                    .append('\n');

        } catch (Exception e) {

            out.append(label)
                    .append(": unavailable (")
                    .append(e.getClass().getSimpleName())
                    .append(")\n");
        }
    }

    // Custom chart: RAM and ZRAM use separate scales.

    private class HistoryChartView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final ArrayList<Sample> samples = new ArrayList<>();

        private final int backgroundColor = 0xFF17232D;
        private final int gridColor = 0xFF40515E;
        private final int textColor = 0xFFEAF2F8;
        private final int ramColor = 0xFF40C4FF;
        private final int zramColor = 0xFFFFC107;

        HistoryChartView() {
            super(MainActivity.this);
            setBackgroundColor(backgroundColor);
        }

        void setSamples(JSONArray history) {

            samples.clear();

            long now = System.currentTimeMillis();

            for (int i = 0; i < history.length(); i++) {
                try {
                    JSONObject item = history.getJSONObject(i);

                    long time = item.getLong("time");

                    if (now - time <= 86400000L) {
                        samples.add(new Sample(
                                time,
                                (float) item.getDouble("ram"),
                                (float) item.getDouble("zram")
                        ));
                    }

                } catch (Exception ignored) {
                }
            }

            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float density = getResources().getDisplayMetrics().density;

            float left = 54 * density;
            float right = getWidth() - 14 * density;
            float width = right - left;

            float top = 24 * density;
            float graphHeight = 82 * density;
            float gap = 55 * density;

            if (samples.isEmpty()) {
                drawText(canvas, "Waiting for history data",
                        left, top + 40 * density, textColor, 13 * density);
                return;
            }

            drawGraph(
                    canvas, "RAM USAGE (%)",
                    left, top, width, graphHeight,
                    100f, ramColor, true
            );

            float maxZram = 100f;

            for (Sample sample : samples) {
                maxZram = Math.max(maxZram, sample.zram);
            }

            maxZram = (float) (Math.ceil(maxZram / 100.0) * 100.0);

            drawGraph(
                    canvas, "ZRAM USED (MB)",
                    left, top + graphHeight + gap,
                    width, graphHeight,
                    maxZram, zramColor, false
            );

            float bottom = top + 2 * graphHeight + gap;

            String first = timeLabel(samples.get(0).time);
            String last = timeLabel(samples.get(samples.size() - 1).time);

            drawText(canvas, first,
                    left, bottom + 24 * density,
                    textColor, 10 * density);

            drawText(canvas, last,
                    right - 38 * density, bottom + 24 * density,
                    textColor, 10 * density);

            drawText(canvas, "BLUE = RAM     YELLOW = ZRAM",
                    left, bottom + 43 * density,
                    textColor, 10 * density);
        }

        private void drawGraph(
                Canvas canvas,
                String title,
                float left,
                float top,
                float width,
                float height,
                float maxValue,
                int lineColor,
                boolean isRam
        ) {

            float density = getResources().getDisplayMetrics().density;

            drawText(canvas, title, left, top - 8 * density,
                    lineColor, 12 * density);

            float graphTop = top + 8 * density;
            float graphBottom = graphTop + height;

            paint.setColor(gridColor);
            paint.setStrokeWidth(1f * density);
            paint.setStyle(Paint.Style.STROKE);

            for (int i = 0; i <= 4; i++) {
                float y = graphTop + height * i / 4f;
                canvas.drawLine(left, y, left + width, y, paint);
            }

            paint.setStyle(Paint.Style.FILL);

            for (int i = 0; i <= 4; i++) {
                float value = maxValue * (4 - i) / 4f;
                float y = graphTop + height * i / 4f;

                String label = String.format(
                        Locale.US, "%.0f", value
                );

                drawText(canvas, label,
                        5 * density, y + 4 * density,
                        textColor, 10 * density);
            }

            if (samples.size() == 1) {
                float value = isRam
                        ? samples.get(0).ram
                        : samples.get(0).zram;

                float y = graphBottom -
                        (value / maxValue) * height;

                paint.setColor(lineColor);
                canvas.drawCircle(left + width / 2f, y,
                        4 * density, paint);
                return;
            }

            Path path = new Path();

            for (int i = 0; i < samples.size(); i++) {

                Sample sample = samples.get(i);

                float x = left +
                        width * i / (samples.size() - 1f);

                float value = isRam ? sample.ram : sample.zram;

                float y = graphBottom -
                        (value / maxValue) * height;

                y = Math.max(graphTop, Math.min(graphBottom, y));

                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }

            paint.setColor(lineColor);
            paint.setStrokeWidth(2.5f * density);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.FILL);
        }

        private void drawText(
                Canvas canvas,
                String value,
                float x,
                float y,
                int color,
                float size
        ) {
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(android.graphics.Typeface.DEFAULT);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawText(value, x, y, paint);
        }

        private String timeLabel(long time) {
            return new SimpleDateFormat(
                    "HH:mm", Locale.getDefault()
            ).format(new Date(time));
        }
    }

    private static class Sample {
        long time;
        float ram;
        float zram;

        Sample(long time, float ram, float zram) {
            this.time = time;
            this.ram = ram;
            this.zram = zram;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }
}

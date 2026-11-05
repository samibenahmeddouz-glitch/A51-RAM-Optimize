
package com.a51.ramoptimizer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView dashboard;
    private TextView statusView;

    private double minUsage = 100.0;
    private double maxUsage = 0.0;
    private boolean hasValidReading = false;

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

        getWindow().getDecorView().setBackgroundColor(0xFF101820);

        ScrollView scroll = new ScrollView(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(36, 36, 36, 36);

        TextView title = new TextView(this);
        title.setText("A51 RAM OPTIMIZER");
        title.setTextColor(0xFFFFFFFF);
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
        dashboard.setPadding(0, 24, 0, 0);

        panel.addView(dashboard);
        scroll.addView(panel);
        setContentView(scroll);

        refresh.run();
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

            out.append("Unavailable: ")
                    .append(file)
                    .append('\n');
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

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }
}

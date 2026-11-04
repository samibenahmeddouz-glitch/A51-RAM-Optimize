
package com.a51.ramoptimizer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView dashboard;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (dashboard != null) {
                dashboard.setText(readReport());
            }
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
        panel.setPadding(36, 48, 36, 36);

        dashboard = new TextView(this);
        dashboard.setTextColor(0xFFEAF2F8);
        dashboard.setTextSize(15);
        dashboard.setLineSpacing(8f, 1f);

        panel.addView(dashboard);
        scroll.addView(panel);
        setContentView(scroll);

        refresh.run();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }

    private String readReport() {

        StringBuilder out = new StringBuilder();

        out.append("A51 RAM OPTIMIZER\n");
        out.append("Read-only monitor · refresh 3 sec\n\n");

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

        out.append("\n\nNo tuning is active. No processes are killed.\n");
        out.append("Values are read from kernel interfaces; access may vary by ROM.");

        return out.toString();
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
                    .append(" (")
                    .append(e.getMessage())
                    .append(")\n");
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
}

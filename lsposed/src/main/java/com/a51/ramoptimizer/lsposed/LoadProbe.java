
package com.a51.ramoptimizer.lsposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LoadProbe implements IXposedHookLoadPackage {

    private static final String DASHBOARD = "com.a51.ramoptimizer";

    private static final AtomicBoolean STARTED =
            new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(
            XC_LoadPackage.LoadPackageParam lpparam) {

        if (!DASHBOARD.equals(lpparam.packageName)) {
            return;
        }

        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        XposedBridge.log("A51RAM: Advanced monitor started.");

        ScheduledExecutorService executor =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "A51RAM-Monitor");
                    thread.setDaemon(true);
                    return thread;
                });

        executor.scheduleAtFixedRate(
                LoadProbe::collectStats,
                5,
                30,
                TimeUnit.SECONDS
        );
    }

    private static void collectStats() {

        try {
            long total = readMemInfo("MemTotal");
            long available = readMemInfo("MemAvailable");
            long free = readMemInfo("MemFree");

            String mmStat = readFile(
                    "/sys/block/zram0/mm_stat");

            String diskSize = readFile(
                    "/sys/block/zram0/disksize");

            String algorithm = readFile(
                    "/sys/block/zram0/comp_algorithm");

            String ramUsage = "unavailable";

            if (total > 0 && available >= 0) {
                double percentage =
                        ((double) (total - available) / total) * 100.0;

                ramUsage = String.format(
                        Locale.US, "%.1f%%", percentage);
            }

            String zramOriginal = "unavailable";
            String zramCompressed = "unavailable";
            String zramUsed = "unavailable";
            String compressionRatio = "unavailable";
            String savings = "unavailable";

            if (mmStat != null) {
                String[] values = mmStat.trim().split("\\s+");

                if (values.length >= 3) {
                    long original = Long.parseLong(values[0]);
                    long compressed = Long.parseLong(values[1]);
                    long used = Long.parseLong(values[2]);

                    zramOriginal = mb(original);
                    zramCompressed = mb(compressed);
                    zramUsed = mb(used);

                    if (compressed > 0) {
                        double ratio =
                                (double) original / compressed;

                        compressionRatio = String.format(
                                Locale.US, "%.2fx", ratio);
                    }

                    long saved = original - used;
                    savings = mb(saved);
                }
            }

            String sizeInfo = "unavailable";

            if (diskSize != null) {
                long bytes = Long.parseLong(diskSize.trim());
                sizeInfo = mb(bytes);
            }

            XposedBridge.log(
                    "A51RAM ADVANCED: " +
                    "Total=" + total + "kB " +
                    "Available=" + available + "kB " +
                    "Free=" + free + "kB " +
                    "RAM_Usage=" + ramUsage + " " +
                    "ZRAM_Size=" + sizeInfo + " " +
                    "Original=" + zramOriginal + " " +
                    "Compressed=" + zramCompressed + " " +
                    "Used=" + zramUsed + " " +
                    "Compression_Ratio=" + compressionRatio + " " +
                    "Savings=" + savings + " " +
                    "Algorithm=" + algorithm
            );

            if (total > 16L * 1024L * 1024L) {
                XposedBridge.log(
                        "A51RAM WARNING: Reported RAM total is unusually high; " +
                        "RAM usage percentage needs verification."
                );
            }

        } catch (Exception e) {
            XposedBridge.log(
                    "A51RAM ERROR: " + e.getMessage());
        }
    }

    private static long readMemInfo(String key)
            throws IOException {

        try (BufferedReader reader =
                     new BufferedReader(
                             new FileReader("/proc/meminfo"))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + ":")) {
                    String value = line.substring(
                            line.indexOf(':') + 1).trim();

                    String[] parts = value.split("\\s+");
                    return Long.parseLong(parts[0]);
                }
            }
        }

        return -1;
    }

    private static String readFile(String path) {

        try (BufferedReader reader =
                     new BufferedReader(
                             new FileReader(path))) {

            return reader.readLine();

        } catch (IOException e) {
            return null;
        }
    }

    private static String mb(long bytes) {
        return String.format(
                Locale.US,
                "%.2fMB",
                bytes / 1048576.0
        );
    }
}

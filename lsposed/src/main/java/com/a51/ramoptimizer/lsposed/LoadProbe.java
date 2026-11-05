
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

    private static ScheduledExecutorService executor;

    @Override
    public void handleLoadPackage(
            XC_LoadPackage.LoadPackageParam lpparam) {

        if (!DASHBOARD.equals(lpparam.packageName)) {
            return;
        }

        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        XposedBridge.log("A51RAM: RAM/ZRAM monitor started.");

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
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
            String memTotal = readMemInfo("MemTotal");
            String memAvailable = readMemInfo("MemAvailable");
            String memFree = readMemInfo("MemFree");

            String diskSize = readFile(
                    "/sys/block/zram0/disksize");

            String mmStat = readFile(
                    "/sys/block/zram0/mm_stat");

            String algorithm = readFile(
                    "/sys/block/zram0/comp_algorithm");

            String zramInfo = "unavailable";

            if (mmStat != null) {
                String[] values = mmStat.trim().split("\\s+");

                if (values.length >= 3) {
                    long original = Long.parseLong(values[0]);
                    long compressed = Long.parseLong(values[1]);
                    long used = Long.parseLong(values[2]);

                    zramInfo = String.format(
                            Locale.US,
                            "original=%dMB compressed=%dMB used=%dMB",
                            original / 1048576,
                            compressed / 1048576,
                            used / 1048576
                    );
                }
            }

            String sizeInfo = "unavailable";

            if (diskSize != null) {
                long bytes = Long.parseLong(diskSize.trim());

                sizeInfo = (bytes / 1048576) + "MB";
            }

            XposedBridge.log(
                    "A51RAM STATS: " +
                    "Total=" + memTotal +
                    " Available=" + memAvailable +
                    " Free=" + memFree +
                    " ZRAM_Size=" + sizeInfo +
                    " ZRAM={" + zramInfo + "}" +
                    " Algorithm=" + algorithm
            );

        } catch (Exception e) {
            XposedBridge.log(
                    "A51RAM ERROR: " + e.getMessage());
        }
    }

    private static String readMemInfo(String key) {

        try (BufferedReader reader =
                     new BufferedReader(
                             new FileReader("/proc/meminfo"))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.startsWith(key + ":")) {
                    return line.substring(
                            line.indexOf(':') + 1).trim();
                }
            }

        } catch (IOException e) {
            return "unavailable";
        }

        return "unavailable";
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
}

package com.a51.ramoptimizer.lsposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class LoadProbe implements IXposedHookLoadPackage {
    private static final String DASHBOARD = "com.a51.ramoptimizer";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // Intentionally scoped to our own dashboard process.
        // This validates LSPosed loading without hooking system_server/SystemUI
        // or changing app/process/VM memory behavior.
        if (DASHBOARD.equals(lpparam.packageName)) {
            XposedBridge.log("A51RAM: LSPosed load probe active for dashboard; no tuning applied.");
        }
    }
}

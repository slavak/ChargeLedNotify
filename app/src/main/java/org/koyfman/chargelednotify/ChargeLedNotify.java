package org.koyfman.chargelednotify;

import android.graphics.Color;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChargeLedNotify implements IXposedHookZygoteInit {
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String PKGNAME = ChargeLedNotify.class.getPackage().getName();
    private static final String CLASS_LIGHT_SERVICE_LIGHT = "com.android.server.LightsService$Light";

    private static final int LIGHT_ID_BATTERY = 3;
    private static final int LIGHT_ID_NOTIFICATIONS = 4;

    private static Object mLight;

    // For debug logging only
    public static final void log(String m)    { XposedBridge.log(PKGNAME + ": " + m); }

    private static void setChargeLED(boolean enable) {
        if (mLight == null) return;
        int color = enable ? Color.RED : 0;

        try {
            Object ls = XposedHelpers.getSurroundingThis(mLight);
            int np = XposedHelpers.getIntField(ls, "mNativePointer");
            if (DEBUG) log("setLight_native(color=" + color + ")");
            XposedHelpers.callMethod(ls, "setLight_native",
                                     np, LIGHT_ID_BATTERY, color, 0, 0, 0, 0);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void enableChargeLED()  { setChargeLED(true); }
    private static void disableChargeLED() { setChargeLED(false); }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        final Class<?> classLight = XposedHelpers.findClass(CLASS_LIGHT_SERVICE_LIGHT, null);

        XposedHelpers.findAndHookMethod(classLight, "setLightLocked",
                                        int.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {

                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                        if (mLight == null) mLight = param.thisObject;
                        int id = XposedHelpers.getIntField(param.thisObject, "mId");
                        int color = (Integer)param.args[0];

                        if (DEBUG) log("lightId=" + id + "; color=" + color +
                                       "; mode=" + param.args[1] + "; " + "onMS=" + param.args[2] +
                                       "; offMS=" + param.args[3] + "; bMode=" + param.args[4]);

                        switch (id) {
                            case LIGHT_ID_NOTIFICATIONS:
                                if (color != 0) {
                                    if (DEBUG) log("New notification");
                                    enableChargeLED();
                                } else {
                                    if (DEBUG) log("Notifications dismissed");
                                    disableChargeLED();
                                }
                                break;
                            case LIGHT_ID_BATTERY:
                                if (DEBUG) log("Charging LED is disabled; abort call");
                                param.setResult(null);
                                break;
                            default:
                                // Pass-through unrelated light events unhindered
                                break;
                        }
                    }
                });
    }
}
package org.koyfman.chargelednotify;

import android.graphics.Color;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ChargeLedNotify implements IXposedHookZygoteInit {
    static final int LIGHT_ID_BACKLIGHT = 0;
    static final int LIGHT_ID_KEYBOARD = 1;
    static final int LIGHT_ID_BUTTONS = 2;
    static final int LIGHT_ID_BATTERY = 3;
    static final int LIGHT_ID_NOTIFICATIONS = 4;
    static final int LIGHT_ID_ATTENTION = 5;
    static final int LIGHT_ID_BLUETOOTH = 6;
    static final int LIGHT_ID_WIFI = 7;
    static final int LIGHT_ID_COUNT = 8;

    static final String[] LIGHT_ID2NAME = {
        "BACKLIGHT",
        "KEYBOARD",
        "BUTTONS",
        "BATTERY",
        "NOTIFICATIONS",
        "ATTENTION",
        "BLUETOOTH",
        "WIFI",
        "COUNT"
    };

    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final String PKGNAME = ChargeLedNotify.class.getPackage().getName();
    private static final String CLASS_LIGHT_SERVICE_LIGHT = "com.android.server.LightsService$Light";

    private static Object mLightsService;
    private static int mNativePointer;
    private static boolean mLedEnabled = false;

    /**
     * Log message to Xposed log. Log will include identifying information (package name).
     * @param m Message to log
     */
    private static void log(String m)    { XposedBridge.log(PKGNAME + ": " + m); }

    /**
     * Turn the charging LED on or off.
     * @param enable    Charging LED will be turned on if true, or off otherwise.
     */
    private static void setChargeLED(boolean enable) {
        if (mLightsService == null) return;
        int color = enable ? Color.RED : 0;

        try {
            if (DEBUG) log("setLight_native(BATTERY, color=" + color + ")");
            XposedHelpers.callMethod(mLightsService, "setLight_native",
                                     mNativePointer, LIGHT_ID_BATTERY, color, 0, 0, 0, 0);
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
                        if (mLightsService == null) {
                            Object light = param.thisObject;
                            mLightsService = XposedHelpers.getSurroundingThis(light);
                            mNativePointer = XposedHelpers.getIntField(mLightsService, "mNativePointer");
                        }
                        int id = XposedHelpers.getIntField(param.thisObject, "mId");
                        int color = (Integer)param.args[0];

                        if (DEBUG) log("lightId=" + id + "(" + LIGHT_ID2NAME[id] + "); color=" + color +
                                       "; mode=" + param.args[1] + "; " + "onMS=" + param.args[2] +
                                       "; offMS=" + param.args[3] + "; bMode=" + param.args[4]);

                        switch (id) {
                            case LIGHT_ID_NOTIFICATIONS:
                                if (color != 0 && !mLedEnabled) {
                                    if (DEBUG) log("New notification");
                                    enableChargeLED();
                                    mLedEnabled = true;
                                } else if (color == 0 && mLedEnabled) {
                                    if (DEBUG) log("Notifications dismissed");
                                    disableChargeLED();
                                    mLedEnabled = false;
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

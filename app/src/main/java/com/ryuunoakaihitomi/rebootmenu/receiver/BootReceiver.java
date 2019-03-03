package com.ryuunoakaihitomi.rebootmenu.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.ryuunoakaihitomi.rebootmenu.activity.XposedWarning;
import com.ryuunoakaihitomi.rebootmenu.util.SpecialSupport;
import com.ryuunoakaihitomi.rebootmenu.util.hook.RMPowerActionManager;
import com.ryuunoakaihitomi.rebootmenu.util.hook.SuServiceStarter;
import com.ryuunoakaihitomi.rebootmenu.util.hook.XposedUtils;

/**
 * 计划做更多事情
 * <p>
 * Created by ZQY on 2019/1/6.
 */

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
            return;
        Log.v(TAG, "onReceive: ");
        //Xposed警告
        context.startActivity(new Intent(context, XposedWarning.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (XposedUtils.isActive)
            RMPowerActionManager.getInstance().testPrint();
        else if (!SpecialSupport.hasTvFeature(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            SuServiceStarter.invoke(context, false);
    }
}

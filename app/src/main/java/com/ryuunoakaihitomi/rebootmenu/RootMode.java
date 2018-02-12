package com.ryuunoakaihitomi.rebootmenu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.ryuunoakaihitomi.rebootmenu.util.ShellUtils;
import com.ryuunoakaihitomi.rebootmenu.util.TextToast;
import com.ryuunoakaihitomi.rebootmenu.util.UIUtils;

/**
 * Root模式活动
 * Created by ZQY on 2018/2/8.
 */

public class RootMode extends Activity {
    boolean isForceMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final AlertDialog.Builder mainDialog = UIUtils.LoadDialog(ConfigManager.get(ConfigManager.WHITE_THEME), this);
        mainDialog.setTitle(getString(R.string.root_title));
        //默认模式功能列表
        final String[] uiTextList = {
                getString(R.string.reboot),
                getString(R.string.shutdown),
                getString(R.string.recovery),
                getString(R.string.fastboot),
                getString(R.string.hot_reboot),
                getString(R.string.reboot_ui),
                getString(R.string.safety),
                getString(R.string.lockscreen)
        };
        //默认模式命令列表
        final String[] shellList = {
                "svc power reboot",
                "svc power shutdown",
                "svc power reboot recovery",
                "svc power reboot bootloader",
                "setprop ctl.restart zygote",
                "busybox pkill com.android.systemui",
                "setprop persist.sys.safemode 1",
                "input keyevent 26"
        };
        //强制模式功能列表
        final String[] uiTextListForce = {
                "*" + getString(R.string.reboot),
                "*" + getString(R.string.shutdown),
                "*" + getString(R.string.recovery),
                "*" + getString(R.string.fastboot),
                uiTextList[4],
                uiTextList[5],
                "*" + uiTextList[6],
                uiTextList[7]
        };
        //强制模式命令列表
        final String[] shellListForce = {
                "reboot",
                "reboot -p",
                "reboot recovery",
                "reboot bootloader",
                shellList[4],
                shellList[5],
                shellList[6],
                shellList[7]
        };
        //不按退出的退出监听
        final DialogInterface.OnCancelListener exitListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface p1) {
                new TextToast(getApplicationContext(), false, getString(R.string.exit_notice));
                finish();
            }
        };
        //功能监听
        final DialogInterface.OnClickListener mainListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, final int i) {
                //锁屏就直接锁了，不需要确认。
                if (i != 7 && !ConfigManager.get(ConfigManager.NO_NEED_TO_COMFIRM)) {
                    //确认界面显示（？YN）
                    final String[] confirmList = {
                            getString(R.string.confirm_operation),
                            getString(R.string.yes),
                            getString(R.string.no)
                    };
                    //在后面显示刚刚选择的功能名称
                    if (!isForceMode)
                        mainDialog.setTitle(getString(R.string.confirm_operation) + " " + uiTextList[i]);
                    else
                        mainDialog.setTitle(getString(R.string.confirm_operation) + " " + uiTextListForce[i]);
                    //点击是或者否的监听
                    DialogInterface.OnClickListener confirmListener = new DialogInterface.OnClickListener() {
                        @Override
                        //iConfirm不和i混淆
                        public void onClick(DialogInterface dialogInterface, int iConfirm) {
                            //若否
                            if (iConfirm == 1) {
                                new TextToast(getApplicationContext(), getString(R.string.no_seleted_notice));
                                finish();
                            } else
                                exeKernel(shellList, shellListForce, i);
                        }
                    };
                    //YN
                    String[] confirmSelect = {
                            confirmList[1], confirmList[2]
                    };
                    mainDialog.setItems(confirmSelect, confirmListener);
                    //取消之前设置的底部按钮
                    mainDialog.setNeutralButton(null, null);
                    mainDialog.setPositiveButton(null, null);
                    mainDialog.setNegativeButton(null, null);
                    UIUtils.alphaShow(mainDialog.create(), 0.9f);
                } else
                    //直接执行（无需确认）
                    exeKernel(shellList, shellListForce, i);
            }
        };
        mainDialog.setItems(uiTextList, mainListener);
        //是否需要退出键
        if (!ConfigManager.get(ConfigManager.CANCELABLE))
            mainDialog.setPositiveButton(R.string.exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    finish();
                }
            });
        //帮助
        mainDialog.setNegativeButton(R.string.help, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                UIUtils.helpDialog(RootMode.this, mainDialog, ConfigManager.get(ConfigManager.CANCELABLE), ConfigManager.get(ConfigManager.WHITE_THEME));
            }
        });
        //svc兼容性检查
        if (ShellUtils.svcCompatibilityCheck()) {
            mainDialog.setNeutralButton(R.string.mode_switch, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    //没问题就按照用户的选择
                    if (!isForceMode) {
                        mainDialog.setItems(uiTextListForce, mainListener);
                        isForceMode = true;
                        new TextToast(getApplicationContext(), getString(R.string.force_mode));
                    } else {
                        mainDialog.setItems(uiTextList, mainListener);
                        isForceMode = false;
                        new TextToast(getApplicationContext(), getString(R.string.normal_mode));
                    }
                    UIUtils.alphaShow(mainDialog.create(), 0.75f);
                }
            });
        } else {
            //不能兼容就只能选择强制
            mainDialog.setItems(uiTextListForce, mainListener);
            isForceMode = true;
            new TextToast(getApplicationContext(), getString(R.string.normal_not_support));
        }
        //是否取消（与是否需要退出键相对）
        mainDialog.setCancelable(ConfigManager.get(ConfigManager.CANCELABLE));
        mainDialog.setOnCancelListener(exitListener);
        UIUtils.alphaShow(mainDialog.create(), 0.75f);
    }

    private void exeKernel(String[] shellList, String[] shellListForce, int i) {
        String command;
        //模式选择
        if (!isForceMode)
            command = shellList[i];
        else
            command = shellListForce[i];
        ShellUtils.suCmdExec(command);
        //如果是安全模式，MIUI执行完不能立即重启，还得执行一次重启
        if (command == shellList[6])
            if (!isForceMode)
                ShellUtils.suCmdExec(shellList[0]);
            else
                ShellUtils.suCmdExec(shellListForce[0]);
        new TextToast(getApplicationContext(), true, getString(R.string.cmd_send_notice));
        finish();
    }
}

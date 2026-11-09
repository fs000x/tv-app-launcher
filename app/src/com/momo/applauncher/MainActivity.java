package com.momo.applauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 万能启动器：列出全部应用与其活动（含隐藏的），快捷打开原生设置/开发者选项。
 * 用途：在海信等厂商定制系统上进入 Android 原生设置以开启 ADB 调试。
 */
public class MainActivity extends Activity {

    private TextView status;
    private ListView list;
    private final List<AppEntry> apps = new ArrayList<AppEntry>();
    private AppAdapter adapter;
    private final Handler handler = new Handler();

    private static final String SETTINGS_PKG = "com.android.settings";

    private static class AppEntry {
        String label;
        String pkg;
        boolean system;
    }

    private static class Attempt {
        final String desc;
        final Intent intent;

        Attempt(String desc, Intent intent) {
            this.desc = desc;
            this.intent = intent;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main);
        status = (TextView) findViewById(R.id.status);
        list = (ListView) findViewById(R.id.list);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showActivities(apps.get(position));
            }
        });

        ((Button) findViewById(R.id.btn_dev)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openDevOptions();
            }
        });
        ((Button) findViewById(R.id.btn_settings)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openMainSettings();
            }
        });
        ((Button) findViewById(R.id.btn_apps)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openAppManagement();
            }
        });
        ((Button) findViewById(R.id.btn_reload)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reload();
            }
        });

        status.setText("正在加载应用列表...");
        reload();
    }

    /** 依次尝试多个启动方式，把每次结果写进状态栏，全部失败时给出原因清单。 */
    private void tryLaunch(List<Attempt> attempts, String goal) {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < attempts.size(); i++) {
            Attempt a = attempts.get(i);
            try {
                a.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(a.intent);
                setStatus("✓ 已启动 [" + goal + "] 方式" + (i + 1) + ": " + a.desc);
                return;
            } catch (Throwable t) {
                log.append("✗ 方式").append(i + 1).append(" ").append(a.desc)
                   .append(" → ").append(t.getClass().getSimpleName()).append('\n');
            }
        }
        String msg = "[" + goal + "] 全部方式失败：\n" + log;
        setStatus(msg);
        Toast.makeText(this, "启动失败，详见右上角状态区", Toast.LENGTH_LONG).show();
    }

    /** 开发者选项：显式组件名优先（确定性），隐式 Intent 兜底（部分 ROM 会劫持隐式 action）。 */
    private void openDevOptions() {
        List<Attempt> l = new ArrayList<Attempt>();
        l.add(new Attempt(SETTINGS_PKG + "/.DevelopmentSettings",
                new Intent().setClassName(SETTINGS_PKG, SETTINGS_PKG + ".DevelopmentSettings")));
        l.add(new Attempt(SETTINGS_PKG + "/.Settings$DevelopmentSettings",
                new Intent().setClassName(SETTINGS_PKG, SETTINGS_PKG + ".Settings$DevelopmentSettings")));
        l.add(new Attempt(SETTINGS_PKG + "/.Settings$DevelopmentSettingsActivity (海信 VIDAA 等)",
                new Intent().setClassName(SETTINGS_PKG, SETTINGS_PKG + ".Settings$DevelopmentSettingsActivity")));
        l.add(new Attempt("隐式 APPLICATION_DEVELOPMENT_SETTINGS",
                new Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")));
        l.add(new Attempt("隐式 android.settings.SETTINGS(降级)",
                new Intent("android.settings.SETTINGS")));
        tryLaunch(l, "开发者选项");
    }

    /** 原生设置主页。 */
    private void openMainSettings() {
        List<Attempt> l = new ArrayList<Attempt>();
        l.add(new Attempt(SETTINGS_PKG + "/.Settings",
                new Intent().setClassName(SETTINGS_PKG, SETTINGS_PKG + ".Settings")));
        l.add(new Attempt(SETTINGS_PKG + "/.BaseSettings (海信等)",
                new Intent().setClassName(SETTINGS_PKG, SETTINGS_PKG + ".BaseSettings")));
        l.add(new Attempt("隐式(限定包名) android.settings.SETTINGS",
                new Intent("android.settings.SETTINGS").setPackage(SETTINGS_PKG)));
        l.add(new Attempt("隐式 android.settings.SETTINGS",
                new Intent("android.settings.SETTINGS")));
        tryLaunch(l, "原生设置");
    }

    /** 应用管理（原生）。 */
    private void openAppManagement() {
        List<Attempt> l = new ArrayList<Attempt>();
        l.add(new Attempt(SETTINGS_PKG + "/.ManageApplications",
                new Intent().setClassName(SETTINGS_PKG, SETTINGS_PKG + ".ManageApplications")));
        l.add(new Attempt("隐式 MANAGE_ALL_APPLICATIONS_SETTINGS",
                new Intent("android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS")));
        l.add(new Attempt("隐式 APPLICATION_SETTINGS",
                new Intent("android.settings.APPLICATION_SETTINGS")));
        tryLaunch(l, "应用管理");
    }

    private void setStatus(final String s) {
        handler.post(new Runnable() {
            public void run() {
                status.setText(s);
            }
        });
    }

    private void reload() {
        new Thread(new Runnable() {
            public void run() {
                PackageManager pm = getPackageManager();
                List<PackageInfo> pis = pm.getInstalledPackages(0);
                final List<AppEntry> loaded = new ArrayList<AppEntry>();
                for (PackageInfo pi : pis) {
                    if (pi.packageName.equals(getPackageName())) {
                        continue;
                    }
                    AppEntry e = new AppEntry();
                    e.pkg = pi.packageName;
                    ApplicationInfo ai = pi.applicationInfo;
                    if (ai != null) {
                        e.label = String.valueOf(ai.loadLabel(pm));
                        e.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    } else {
                        e.label = pi.packageName;
                    }
                    loaded.add(e);
                }
                Collections.sort(loaded, new Comparator<AppEntry>() {
                    public int compare(AppEntry a, AppEntry b) {
                        boolean sa = a.system;
                        boolean sb = b.system;
                        if (sa != sb) {
                            return sa ? 1 : -1; // 用户应用排前面
                        }
                        return String.valueOf(a.label).compareToIgnoreCase(String.valueOf(b.label));
                    }
                });
                handler.post(new Runnable() {
                    public void run() {
                        apps.clear();
                        apps.addAll(loaded);
                        adapter.notifyDataSetChanged();
                        setStatus("✓ 共 " + apps.size() + " 个应用，点应用名查看可启动的活动");
                    }
                });
            }
        }).start();
    }

    /** 点某应用：后台取其全部 ActivityInfo，弹对话框供选择启动。 */
    private void showActivities(final AppEntry app) {
        setStatus("正在读取 " + app.label + " 的活动...");
        new Thread(new Runnable() {
            public void run() {
                PackageManager pm = getPackageManager();
                final List<String> names = new ArrayList<String>();
                final List<String> classes = new ArrayList<String>();
                final List<Boolean> exported = new ArrayList<Boolean>();
                try {
                    PackageInfo pi = pm.getPackageInfo(app.pkg, PackageManager.GET_ACTIVITIES);
                    ActivityInfo[] ais = pi.activities;
                    if (ais != null) {
                        for (ActivityInfo ai : ais) {
                            names.add((ai.exported ? "" : "✖ ") + String.valueOf(ai.loadLabel(pm))
                                    + "  ·  " + ai.name.substring(ai.name.lastIndexOf('.') + 1));
                            classes.add(ai.name);
                            exported.add(ai.exported);
                        }
                    }
                } catch (Exception e) {
                    handler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "读取活动失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                if (classes.isEmpty()) {
                    handler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "该应用没有可显示的活动", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                final String[] arr = names.toArray(new String[names.size()]);
                handler.post(new Runnable() {
                    public void run() {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(app.label + "（✖=未导出，可能无法直接启动）")
                                .setItems(arr, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        launchActivity(app.pkg, classes.get(which), exported.get(which));
                                    }
                                })
                                .setNegativeButton("关闭", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    private void launchActivity(String pkg, String cls, boolean exported) {
        try {
            Intent i = new Intent().setClassName(pkg, cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            setStatus("✓ 已启动 " + cls);
        } catch (Throwable t) {
            String why = t.getClass().getSimpleName();
            if (!exported) {
                why = "该活动未导出(不能跨应用启动) - " + why;
            }
            setStatus("✗ 启动 " + cls + " 失败：" + why);
            Toast.makeText(this, "启动失败：" + why, Toast.LENGTH_LONG).show();
        }
    }

    private class AppAdapter extends BaseAdapter {
        public int getCount() {
            return apps.size();
        }

        public Object getItem(int position) {
            return apps.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_app, parent, false);
            }
            AppEntry e = apps.get(position);
            TextView name = (TextView) convertView.findViewById(R.id.name);
            TextView pkg = (TextView) convertView.findViewById(R.id.pkg);
            TextView tag = (TextView) convertView.findViewById(R.id.tag);
            name.setText(e.label);
            pkg.setText(e.pkg);
            tag.setText(e.system ? "系统" : "用户");
            tag.setTextColor(e.system ? 0xFF888888 : 0xFF4CAF50);
            return convertView;
        }
    }
}

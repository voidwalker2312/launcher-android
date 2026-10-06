package com.ddarkness.blackholewidget;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private GridView grid;
    private EditText search;
    private AppAdapter adapter;
    private boolean searchVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        buildUi();
        reloadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadApps();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        hideSearch();
        if (grid != null) grid.setSelection(0);
    }

    @Override
    public void onBackPressed() {
        if (searchVisible) {
            hideSearch();
            return;
        }
        if (!isTaskRoot()) super.onBackPressed();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(14), dp(8), dp(14), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("All apps");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.ITALIC);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));

        TextView searchButton = iconButton("⌕");
        searchButton.setContentDescription("Search apps");
        searchButton.setOnClickListener(v -> toggleSearch());
        header.addView(searchButton, new LinearLayout.LayoutParams(dp(46), dp(50)));

        TextView menuButton = iconButton("⋮");
        menuButton.setContentDescription("Launcher menu");
        menuButton.setOnClickListener(this::showMenu);
        header.addView(menuButton, new LinearLayout.LayoutParams(dp(42), dp(50)));
        root.addView(header);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setHintTextColor(0xFF6D6D6D);
        search.setTextColor(Color.WHITE);
        search.setTextSize(14);
        search.setBackgroundColor(0xFF0E0E0E);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        slp.bottomMargin = dp(8);
        root.addView(search, slp);

        grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(dp(9));
        grid.setHorizontalSpacing(dp(1));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(2), 0, dp(12));
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (adapter != null && position < adapter.filtered.size()) launch(adapter.filtered.get(position));
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (adapter != null && position < adapter.filtered.size()) openAppInfo(adapter.filtered.get(position));
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) adapter.filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        setContentView(root);
    }

    private TextView iconButton(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(26);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void reloadApps() {
        List<AppEntry> apps = AppRepository.load(this);
        if (adapter == null) {
            adapter = new AppAdapter(apps);
            grid.setAdapter(adapter);
        } else {
            adapter.replace(apps);
        }
    }

    private void toggleSearch() {
        if (searchVisible) hideSearch(); else showSearch();
    }

    private void showSearch() {
        searchVisible = true;
        search.setVisibility(View.VISIBLE);
        search.requestFocus();
        search.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }, 120);
    }

    private void hideSearch() {
        if (search == null) return;
        searchVisible = false;
        search.setText("");
        search.clearFocus();
        search.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(isDefaultHome() ? "Default launcher ✓" : "Set as default launcher");
        menu.getMenu().add("Refresh apps");
        menu.getMenu().add("About");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.startsWith("Set as default") || title.startsWith("Default launcher")) {
                requestHomeRole();
                return true;
            }
            if (title.startsWith("Refresh")) {
                reloadApps();
                Toast.makeText(this, "Apps refreshed", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (title.startsWith("About")) {
                Toast.makeText(this, "Black Hole Launcher v0.2 • AMOLED • offline", Toast.LENGTH_LONG).show();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(home, 0);
        return resolved != null && resolved.activityInfo != null && getPackageName().equals(resolved.activityInfo.packageName);
    }

    private void requestHomeRole() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = getSystemService(RoleManager.class);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 200);
                    return;
                }
            }
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Settings → Apps → Default apps → Home app", Toast.LENGTH_LONG).show();
        }
    }

    private void launch(AppEntry app) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(app.packageName, app.className));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppInfo(AppEntry app) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + app.packageName));
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class AppAdapter extends BaseAdapter {
        final List<AppEntry> all = new ArrayList<>();
        final List<AppEntry> filtered = new ArrayList<>();
        final Map<String, Bitmap> icons = new HashMap<>();
        String query = "";

        AppAdapter(List<AppEntry> source) { replace(source); }

        void replace(List<AppEntry> source) {
            all.clear();
            all.addAll(source);
            icons.clear();
            filter(query);
        }

        void filter(String q) {
            query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            filtered.clear();
            for (AppEntry e : all) {
                if (query.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(query)) filtered.add(e);
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Cell cell;
            if (convertView == null) {
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                box.setPadding(dp(2), dp(3), dp(2), dp(3));

                ImageView iv = new ImageView(MainActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                box.addView(iv, new LinearLayout.LayoutParams(dp(51), dp(51)));

                TextView tv = new TextView(MainActivity.this);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(9);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                box.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

                cell = new Cell(iv, tv);
                box.setTag(cell);
                convertView = box;
            } else {
                cell = (Cell) convertView.getTag();
            }

            AppEntry e = filtered.get(position);
            cell.label.setText(e.label);
            Bitmap b = icons.get(e.packageName);
            if (b == null) {
                b = IconFactory.render(MainActivity.this, e.packageName, e.label);
                icons.put(e.packageName, b);
            }
            cell.icon.setImageBitmap(b);
            return convertView;
        }
    }

    private static final class Cell {
        final ImageView icon;
        final TextView label;
        Cell(ImageView icon, TextView label) { this.icon = icon; this.label = label; }
    }
}

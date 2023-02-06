package com.llw.wifi;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.llw.wifi.databinding.ActivityMainBinding;
import com.llw.wifi.databinding.DialogConnectWifiBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity implements WifiAdapter.OnItemClickListener, EasyWifi.WifiConnectCallback {

    public static final String TAG = MainActivity.class.getSimpleName();
    private ActivityMainBinding binding;
    private WifiManager wifiManager;//Wifi管理者
    private ActivityResultLauncher<Intent> openWifi;    //打开Wifi意图
    private ActivityResultLauncher<String[]> requestPermission;     //请求权限意图
    private final List<ScanResult> wifiList = new ArrayList<>();    //Wifi结果列表
    private WifiAdapter wifiAdapter;    //Wifi适配器
    private EasyWifi easyWifi;
    /**
     * Wifi扫描广播接收器
     */
    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            Log.e(TAG, "onReceive: " + (success ? "成功" : "失败"));
            //处理扫描结果
            wifiList.clear();
            for (ScanResult scanResult : wifiManager.getScanResults()) {
                if (!scanResult.SSID.isEmpty()) {
                    wifiList.add(scanResult);
                }
            }
            sortByLevel(wifiList);
            wifiAdapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //注册意图
        registerIntent();
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //通过Wifi服务获取wifi管理者对象
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        easyWifi = EasyWifi.initialize(this);
        easyWifi.setWifiConnectCallback(this);
        //初始化视图
        initView();
        //初始化扫描
        initScan();
    }

    /**
     * 初始化扫描
     */
    private void initScan() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);
    }

    /**
     * 根据Level排序
     *
     * @param list 扫描结果列表
     */
    private void sortByLevel(List<ScanResult> list) {
        Collections.sort(list, (lhs, rhs) -> rhs.level - lhs.level);
    }

    /**
     * 初始化视图
     */
    private void initView() {
        //打开/关闭Wifi 按钮点击事件
        binding.btnOpenWifi.setOnClickListener(v -> {
            //Android10及以上版本
            if (isAndroidTarget(Build.VERSION_CODES.Q)) {
                openWifi.launch(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
                checkWifiState();
            }
        });
        //扫描Wifi 按钮点击事件
        binding.btnScanWifi.setOnClickListener(v -> {
            //是否打开Wifi
            if (wifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) return;
            //Android13及以上版本
            if (isAndroidTarget(Build.VERSION_CODES.TIRAMISU)) {
                if (!hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES) && !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    requestPermission.launch(new String[]{Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES});
                    return;
                }
            } else {
                if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    requestPermission.launch(new String[]{Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.ACCESS_FINE_LOCATION});
                    return;
                }
            }
            //扫描Wifi
            showMsg(wifiManager.startScan() ? "扫描Wifi中" : "开启扫描失败");
        });
        //配置适配器
        wifiAdapter = new WifiAdapter(wifiList);
        //Item点击事件
        wifiAdapter.setOnItemClickListener(this);
        wifiAdapter.setWifiManager(wifiManager);
        binding.rvWifi.setLayoutManager(new LinearLayoutManager(this));
        binding.rvWifi.setAdapter(wifiAdapter);
    }

    /**
     * 检查当前WIFI状态
     */
    public void checkWifiState() {
        String msg;
        switch (wifiManager.getWifiState()) {
            case WifiManager.WIFI_STATE_DISABLING:
                msg = "Wifi正在关闭";
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                msg = "Wifi已经关闭";
                binding.btnOpenWifi.setText("打开Wifi");
                break;
            case WifiManager.WIFI_STATE_ENABLING:
                msg = "Wifi正在开启";
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                msg = "Wifi已经开启";
                binding.btnOpenWifi.setText("关闭Wifi");
                break;
            default:
                msg = "没有获取到WiFi状态";
                break;
        }
        showMsg(msg);
    }

    /**
     * 注册意图
     */
    private void registerIntent() {
        //打开Wifi开关
        openWifi = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> checkWifiState());
        //请求权限（Wifi、定位）
        requestPermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (Boolean.TRUE.equals(result.get(Manifest.permission.NEARBY_WIFI_DEVICES))
                    || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                //扫描Wifi
                showMsg(wifiManager.startScan() ? "扫描Wifi中" : "开启扫描失败");
            } else {
                showMsg("扫描设备需要此权限");
            }
        });
    }

    /**
     * 是否为目标版本
     *
     * @param targetVersion 目标版本
     */
    private boolean isAndroidTarget(int targetVersion) {
        return Build.VERSION.SDK_INT >= targetVersion;
    }

    /**
     * 检查权限
     *
     * @param permission 权限
     */
    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 提示文字
     *
     * @param msg 文字
     */
    private void showMsg(CharSequence msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * 列表Item点击
     *
     * @param position 位置
     */
    @Override
    public void onItemClick(int position) {
        ScanResult scanResult = wifiList.get(position);
        //获取Wifi扫描结果
        String capabilities = scanResult.capabilities;
        //Wifi状态标识 true：加密，false：开放
        boolean wifiStateFlag = capabilities.contains("WEP") || capabilities.contains("PSK") || capabilities.contains("EAP");

        if (wifiStateFlag) {
            Log.d(TAG, "connectWifi: 加密连接");
            showConnectWifiDialog(scanResult);
        } else {
            Log.d(TAG, "connectWifi: 非加密连接");
            easyWifi.connectWifi(scanResult,"");
        }
    }

    /**
     * 显示连接Wifi弹窗
     *
     * @param scanResult 扫描结果
     */
    private void showConnectWifiDialog(ScanResult scanResult) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        DialogConnectWifiBinding binding = DialogConnectWifiBinding.inflate(LayoutInflater.from(this), null, false);
        binding.materialToolbar.setTitle(scanResult.SSID);
        binding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        binding.btnConnect.setOnClickListener(v -> {
            //没有找到相同配置
            String password = binding.etPwd.getText().toString().trim();
            if (password.isEmpty()) {
                showMsg("请输入密码");
                return;
            }
            easyWifi.connectWifi(scanResult, password);
            dialog.dismiss();
        });
        dialog.setContentView(binding.getRoot());
        dialog.show();
    }

    @Override
    public void onSuccess(Network network) {
        showMsg("连接成功");
    }

    @Override
    public void onFailure() {
        showMsg("连接失败");
    }
}
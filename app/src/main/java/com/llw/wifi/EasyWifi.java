package com.llw.wifi;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Wifi工具类
 */
public class EasyWifi {

    private static final String TAG = EasyWifi.class.getSimpleName();

    private final ConnectivityManager connectivityManager;//连接管理者

    private final WifiManager wifiManager;//Wifi管理者

    private WifiConnectCallback wifiConnectCallback;

    @SuppressLint("StaticFieldLeak")
    private static volatile EasyWifi mInstance;

    private final Context mContext;

    public EasyWifi(Context context) {
        mContext = context;
        wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static EasyWifi initialize(Context context) {
        if (mInstance == null) {
            synchronized (EasyWifi.class) {
                if (mInstance == null) {
                    mInstance = new EasyWifi(context);
                }
            }
        }
        return mInstance;
    }

    public void setWifiConnectCallback(WifiConnectCallback wifiConnectCallback) {
        this.wifiConnectCallback = wifiConnectCallback;
    }

    /**
     * 连接Wifi
     *
     * @param scanResult 扫描结果
     * @param password   密码
     */
    public void connectWifi(ScanResult scanResult, String password) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectByNew(scanResult.SSID, password);
        } else {
            connectByOld(scanResult, password);
        }
    }

    /**
     * Android 10 以下使用
     *
     * @param scanResult 扫描结果
     * @param password   密码
     */
    private void connectByOld(ScanResult scanResult, String password) {
        String ssid = scanResult.SSID;
        boolean isSuccess;
        WifiConfiguration configured = isExist(ssid);
        if (configured != null) {
            //在配置表中找到了，直接连接
            isSuccess = wifiManager.enableNetwork(configured.networkId, true);
        } else {
            WifiConfiguration wifiConfig = createWifiConfig(ssid, password, getCipherType(scanResult.capabilities));
            int netId = wifiManager.addNetwork(wifiConfig);
            isSuccess = wifiManager.enableNetwork(netId, true);
        }
        Log.d(TAG, "connectWifi: " + (isSuccess ? "成功" : "失败"));
    }

    /**
     * Android 10及以上版本使用此方式连接Wifi
     *
     * @param ssid     名称
     * @param password 密码
     */
    @SuppressLint("NewApi")
    private void connectByNew(String ssid, String password) {
        WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build();
        //网络请求
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build();
        //网络回调处理
        ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (wifiConnectCallback != null) {
                    wifiConnectCallback.onSuccess(network);
                }
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                if (wifiConnectCallback != null) {
                    wifiConnectCallback.onFailure();
                }
            }
        };
        //请求连接网络
        connectivityManager.requestNetwork(request, networkCallback);
    }

    @SuppressLint("NewApi")
    private void connectBySug(String ssid, String password) {
        WifiNetworkSuggestion suggestion = new WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .setIsAppInteractionRequired(true)
                .build();
        List<WifiNetworkSuggestion> suggestionList = new ArrayList<>();
        suggestionList.add(suggestion);
        int status = wifiManager.addNetworkSuggestions(suggestionList);
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION);
        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (!intent.getAction().equals(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)) {
                    return;
                }
            }
        };
        mContext.registerReceiver(wifiScanReceiver, intentFilter);
    }

    /**
     * 创建Wifi配置
     *
     * @param ssid     名称
     * @param password 密码
     * @param type     类型
     */
    private WifiConfiguration createWifiConfig(String ssid, String password, WifiCapability type) {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\"" + ssid + "\"";
        WifiConfiguration configured = isExist(ssid);
        if (configured != null) {
            wifiManager.removeNetwork(configured.networkId);
            wifiManager.saveConfiguration();
        }

        //不需要密码的场景
        if (type == WifiCapability.WIFI_CIPHER_NO_PASS) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            //以WEP加密的场景
        } else if (type == WifiCapability.WIFI_CIPHER_WEP) {
            config.hiddenSSID = true;
            config.wepKeys[0] = "\"" + password + "\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
            //以WPA加密的场景，自己测试时，发现热点以WPA2建立时，同样可以用这种配置连接
        } else if (type == WifiCapability.WIFI_CIPHER_WPA) {
            config.preSharedKey = "\"" + password + "\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        }
        return config;
    }

    /**
     * 网络是否连接
     */
    public static boolean isNetConnected(ConnectivityManager connectivityManager) {
        return connectivityManager.getActiveNetwork() != null;
    }

    /**
     * 连接网络类型是否为Wifi
     */
    public static boolean isWifi(ConnectivityManager connectivityManager) {
        if (connectivityManager.getActiveNetwork() == null) {
            return false;
        }
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        if (networkCapabilities != null) {
            return false;
        }
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    /**
     * 配置表是否存在对应的Wifi配置
     * @param SSID
     * @return
     */
    @SuppressLint("MissingPermission")
    private WifiConfiguration isExist(String SSID) {
        List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                return existingConfig;
            }
        }
        return null;
    }

    private WifiCapability getCipherType(String capabilities) {
        if (capabilities.contains("WEB")) {
            return WifiCapability.WIFI_CIPHER_WEP;
        } else if (capabilities.contains("PSK")) {
            return WifiCapability.WIFI_CIPHER_WPA;
        } else if (capabilities.contains("WPS")) {
            return WifiCapability.WIFI_CIPHER_NO_PASS;
        } else {
            return WifiCapability.WIFI_CIPHER_NO_PASS;
        }
    }

    /**
     * wifi连接回调接口
     */
    public interface WifiConnectCallback {

        void onSuccess(Network network);

        void onFailure();
    }

    public enum WifiCapability {
        WIFI_CIPHER_WEP, WIFI_CIPHER_WPA, WIFI_CIPHER_NO_PASS
    }
}

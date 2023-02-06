package com.llw.wifi;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.llw.wifi.databinding.ItemWifiRvBinding;

import java.util.List;

/**
 * Wifi适配器
 */
public class WifiAdapter extends RecyclerView.Adapter<WifiAdapter.ViewHolder> {

    private final List<ScanResult> lists;

    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    private WifiManager wifiManager;

    private ConnectivityManager connectivityManager;

    public void setWifiManager(WifiManager wifiManager) {
        this.wifiManager = wifiManager;
    }

    public WifiAdapter(List<ScanResult> lists) {
        this.lists = lists;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWifiRvBinding binding = ItemWifiRvBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        ViewHolder viewHolder = new ViewHolder(binding);
        //添加视图点击事件
        binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(viewHolder.getAdapterPosition());
            }
        });
        connectivityManager = (ConnectivityManager) parent.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        //Wifi名称
        String ssid = lists.get(position).SSID;
        holder.binding.tvWifiName.setText(ssid);
        //Wifi功能
        String capabilities = lists.get(position).capabilities;
        //Wifi状态标识 true：加密，false：开放
        boolean wifiStateFlag = capabilities.contains("WEP") || capabilities.contains("PSK") || capabilities.contains("EAP");
        //Wifi状态描述
        String wifiState = wifiStateFlag ? "加密" : "开放";
        holder.binding.tvWifiState.setText(wifiState);
        //信号强度
        int imgLevel;
        int level = lists.get(position).level;
        if (level <= 0 && level >= -50) {
            imgLevel = 5;
        } else if (level < -50 && level >= -70) {
            imgLevel = 4;
        } else if (level < -70 && level >= -80) {
            imgLevel = 3;
        } else if (level < -80 && level >= -100) {
            imgLevel = 2;
        } else {
            imgLevel = 1;
        }
        //根据是否加密设置不同的图片资源
        holder.binding.ivSignal.setImageResource(wifiStateFlag ? R.drawable.wifi_lock_level : R.drawable.wifi_level);
        //设置图片等级
        holder.binding.ivSignal.setImageLevel(imgLevel);
    }

    @SuppressLint("MissingPermission")
    private WifiConfiguration isExist(String SSID) {
        List<WifiConfiguration> existingConfigs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration existingConfig : existingConfigs) {
            if (existingConfig.SSID.equals("\"" + SSID + "\"")) {
                return existingConfig;
            } else if (existingConfig.SSID.equals(SSID)) {
                return existingConfig;
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return lists.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ItemWifiRvBinding binding;

        public ViewHolder(@NonNull ItemWifiRvBinding itemWifiRvBinding) {
            super(itemWifiRvBinding.getRoot());
            binding = itemWifiRvBinding;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

}

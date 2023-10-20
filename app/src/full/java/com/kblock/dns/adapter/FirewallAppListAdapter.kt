/*
 * Copyright 2021 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kblock.dns.adapter

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kblock.dns.R
import com.kblock.dns.database.AppInfo
import com.kblock.dns.databinding.ListItemFirewallAppBinding
import com.kblock.dns.service.FirewallManager
import com.kblock.dns.service.FirewallManager.updateFirewallStatus
import com.kblock.dns.ui.AppInfoActivity
import com.kblock.dns.ui.AppInfoActivity.Companion.UID_INTENT_NAME
import com.kblock.dns.util.Utilities
import com.kblock.dns.util.Utilities.getIcon
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

class FirewallAppListAdapter(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : PagingDataAdapter<AppInfo, FirewallAppListAdapter.AppListViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<AppInfo>() {
                override fun areItemsTheSame(
                    oldConnection: AppInfo,
                    newConnection: AppInfo
                ): Boolean {
                    return oldConnection == newConnection
                }

                override fun areContentsTheSame(
                    oldConnection: AppInfo,
                    newConnection: AppInfo
                ): Boolean {
                    return (oldConnection.packageName == newConnection.packageName &&
                        oldConnection.firewallStatus == newConnection.firewallStatus &&
                        oldConnection.connectionStatus == newConnection.connectionStatus)
                }
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppListViewHolder {
        val itemBinding =
            ListItemFirewallAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppListViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
        val appInfo: AppInfo = getItem(position) ?: return
        holder.update(appInfo)
    }

    inner class AppListViewHolder(private val b: ListItemFirewallAppBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun update(appInfo: AppInfo) {
            displayDetails(appInfo)
            setupClickListeners(appInfo)
        }

        private fun displayDetails(appInfo: AppInfo) {
            val appStatus = FirewallManager.appStatus(appInfo.uid)
            val connStatus = FirewallManager.connectionStatus(appInfo.uid)

            b.firewallAppLabelTv.text = appInfo.appName
            b.firewallAppToggleOther.text = getFirewallText(appStatus, connStatus)

            displayIcon(getIcon(context, appInfo.packageName, appInfo.appName), b.firewallAppIconIv)
            displayConnectionStatus(appStatus, connStatus)
            showAppHint(b.firewallAppStatusIndicator, appInfo)
        }

        private fun getFirewallText(
            aStat: FirewallManager.FirewallStatus,
            cStat: FirewallManager.ConnectionStatus
        ): String {
            return when (aStat) {
                FirewallManager.FirewallStatus.NONE ->
                    when (cStat) {
                        FirewallManager.ConnectionStatus.ALLOW ->
                            context.getString(R.string.firewall_status_allow)
                        FirewallManager.ConnectionStatus.METERED ->
                            context.getString(R.string.firewall_status_block_metered)
                        FirewallManager.ConnectionStatus.UNMETERED ->
                            context.getString(R.string.firewall_status_block_unmetered)
                        FirewallManager.ConnectionStatus.BOTH ->
                            context.getString(R.string.firewall_status_blocked)
                    }
                FirewallManager.FirewallStatus.EXCLUDE ->
                    context.getString(R.string.firewall_status_excluded)
                FirewallManager.FirewallStatus.ISOLATE ->
                    context.getString(R.string.firewall_status_isolate)
                FirewallManager.FirewallStatus.BYPASS_UNIVERSAL ->
                    context.getString(R.string.firewall_status_whitelisted)
                FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL ->
                    context.getString(R.string.firewall_status_bypass_dns_firewall)
                FirewallManager.FirewallStatus.UNTRACKED ->
                    context.getString(R.string.firewall_status_unknown)
            }
        }

        private fun displayConnectionStatus(
            firewallStatus: FirewallManager.FirewallStatus,
            connStatus: FirewallManager.ConnectionStatus
        ) {
            when (firewallStatus) {
                FirewallManager.FirewallStatus.NONE -> {
                    when (connStatus) {
                        FirewallManager.ConnectionStatus.ALLOW -> {
                            showWifiEnabled()
                            showMobileDataEnabled()
                        }
                        FirewallManager.ConnectionStatus.UNMETERED -> {
                            showWifiDisabled()
                            showMobileDataEnabled()
                        }
                        FirewallManager.ConnectionStatus.METERED -> {
                            showWifiEnabled()
                            showMobileDataDisabled()
                        }
                        FirewallManager.ConnectionStatus.BOTH -> {
                            showWifiDisabled()
                            showMobileDataDisabled()
                        }
                    }
                }
                FirewallManager.FirewallStatus.EXCLUDE -> {
                    showMobileDataUnused()
                    showWifiUnused()
                }
                FirewallManager.FirewallStatus.BYPASS_UNIVERSAL -> {
                    showMobileDataUnused()
                    showWifiUnused()
                }
                FirewallManager.FirewallStatus.ISOLATE -> {
                    showMobileDataUnused()
                    showWifiUnused()
                }
                FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> {
                    showMobileDataUnused()
                    showWifiUnused()
                }
                else -> {
                    showWifiDisabled()
                    showMobileDataDisabled()
                }
            }
        }

        private fun showMobileDataDisabled() {
            b.firewallAppToggleMobileData.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_firewall_data_off)
            )
        }

        private fun showMobileDataEnabled() {
            b.firewallAppToggleMobileData.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_firewall_data_on)
            )
        }

        private fun showWifiDisabled() {
            b.firewallAppToggleWifi.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_off)
            )
        }

        private fun showWifiEnabled() {
            b.firewallAppToggleWifi.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_on)
            )
        }

        private fun showMobileDataUnused() {
            b.firewallAppToggleMobileData.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_firewall_data_on_grey)
            )
        }

        private fun showWifiUnused() {
            b.firewallAppToggleWifi.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_on_grey)
            )
        }

        private fun showAppHint(mIconIndicator: TextView, appInfo: AppInfo) {
            val connStatus = FirewallManager.connectionStatus(appInfo.uid)
            when (FirewallManager.appStatus(appInfo.uid)) {
                FirewallManager.FirewallStatus.NONE -> {
                    when (connStatus) {
                        FirewallManager.ConnectionStatus.ALLOW -> {
                            mIconIndicator.setBackgroundColor(
                                context.getColor(R.color.colorGreen_900)
                            )
                        }
                        FirewallManager.ConnectionStatus.METERED -> {
                            mIconIndicator.setBackgroundColor(
                                context.getColor(R.color.colorAmber_900)
                            )
                        }
                        FirewallManager.ConnectionStatus.UNMETERED -> {
                            mIconIndicator.setBackgroundColor(
                                context.getColor(R.color.colorAmber_900)
                            )
                        }
                        FirewallManager.ConnectionStatus.BOTH -> {
                            mIconIndicator.setBackgroundColor(
                                context.getColor(R.color.colorAmber_900)
                            )
                        }
                    }
                }
                FirewallManager.FirewallStatus.EXCLUDE -> {
                    mIconIndicator.setBackgroundColor(
                        context.getColor(R.color.primaryLightColorText)
                    )
                }
                FirewallManager.FirewallStatus.BYPASS_UNIVERSAL -> {
                    mIconIndicator.setBackgroundColor(
                        context.getColor(R.color.primaryLightColorText)
                    )
                }
                FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> {
                    mIconIndicator.setBackgroundColor(
                        context.getColor(R.color.primaryLightColorText)
                    )
                }
                FirewallManager.FirewallStatus.ISOLATE -> {
                    mIconIndicator.setBackgroundColor(context.getColor(R.color.colorAmber_900))
                }
                FirewallManager.FirewallStatus.UNTRACKED -> {
                    /* no-op */
                }
            }
        }

        private fun displayIcon(drawable: Drawable?, mIconImageView: ImageView) {
            Glide.with(context)
                .load(drawable)
                .error(Utilities.getDefaultIcon(context))
                .into(mIconImageView)
        }

        private fun setupClickListeners(appInfo: AppInfo) {

            b.firewallAppTextLl.setOnClickListener {
                enableAfterDelay(TimeUnit.SECONDS.toMillis(1L), b.firewallAppTextLl)
                openAppDetailActivity(appInfo.uid)
            }

            b.firewallAppIconIv.setOnClickListener {
                enableAfterDelay(TimeUnit.SECONDS.toMillis(1L), b.firewallAppIconIv)
                openAppDetailActivity(appInfo.uid)
            }

            b.firewallAppDetailsLl.setOnClickListener {
                enableAfterDelay(TimeUnit.SECONDS.toMillis(1L), b.firewallAppIconIv)
                openAppDetailActivity(appInfo.uid)
            }

            b.firewallAppToggleWifi.setOnClickListener {
                enableAfterDelay(TimeUnit.SECONDS.toMillis(1L), b.firewallAppToggleWifi)

                val appNames = FirewallManager.getAppNamesByUid(appInfo.uid)
                if (appNames.count() > 1) {
                    showDialog(appNames, appInfo, isWifi = true)
                    return@setOnClickListener
                }
                toggleWifi(appInfo)
            }

            b.firewallAppToggleMobileData.setOnClickListener {
                enableAfterDelay(TimeUnit.SECONDS.toMillis(1L), b.firewallAppToggleMobileData)
                val appNames = FirewallManager.getAppNamesByUid(appInfo.uid)
                if (appNames.count() > 1) {
                    showDialog(appNames, appInfo, isWifi = false)
                    return@setOnClickListener
                }

                toggleMobileData(appInfo)
            }
        }

        private fun toggleMobileData(appInfo: AppInfo) {
            when (FirewallManager.connectionStatus(appInfo.uid)) {
                FirewallManager.ConnectionStatus.METERED -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.ALLOW
                    )
                }
                FirewallManager.ConnectionStatus.UNMETERED -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.BOTH
                    )
                }
                FirewallManager.ConnectionStatus.BOTH -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.UNMETERED
                    )
                }
                FirewallManager.ConnectionStatus.ALLOW -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.METERED
                    )
                }
            }
        }

        private fun toggleWifi(appInfo: AppInfo) {

            when (FirewallManager.connectionStatus(appInfo.uid)) {
                FirewallManager.ConnectionStatus.METERED -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.BOTH
                    )
                }
                FirewallManager.ConnectionStatus.UNMETERED -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.ALLOW
                    )
                }
                FirewallManager.ConnectionStatus.BOTH -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.METERED
                    )
                }
                FirewallManager.ConnectionStatus.ALLOW -> {
                    updateFirewallStatus(
                        appInfo.uid,
                        FirewallManager.FirewallStatus.NONE,
                        FirewallManager.ConnectionStatus.UNMETERED
                    )
                }
            }
        }

        private fun openAppDetailActivity(uid: Int) {
            val intent = Intent(context, AppInfoActivity::class.java)
            intent.putExtra(UID_INTENT_NAME, uid)
            context.startActivity(intent)
        }

        private fun showDialog(packageList: List<String>, appInfo: AppInfo, isWifi: Boolean) {

            val builderSingle = MaterialAlertDialogBuilder(context)

            builderSingle.setIcon(R.drawable.ic_firewall_block_grey)
            val count = packageList.count()
            builderSingle.setTitle(
                context.getString(R.string.ctbs_block_other_apps, appInfo.appName, count.toString())
            )

            val arrayAdapter =
                ArrayAdapter<String>(context, android.R.layout.simple_list_item_activated_1)
            arrayAdapter.addAll(packageList)
            builderSingle.setCancelable(false)

            builderSingle.setItems(packageList.toTypedArray(), null)

            builderSingle
                .setPositiveButton(context.getString(R.string.lbl_proceed)) {
                    _: DialogInterface,
                    _: Int ->
                    if (isWifi) {
                        toggleWifi(appInfo)
                        return@setPositiveButton
                    }

                    toggleMobileData(appInfo)
                }
                .setNeutralButton(context.getString(R.string.ctbs_dialog_negative_btn)) {
                    _: DialogInterface,
                    _: Int ->
                }

            val alertDialog: AlertDialog = builderSingle.create()
            alertDialog.listView.setOnItemClickListener { _, _, _, _ -> }
            alertDialog.show()
        }
    }

    private fun enableAfterDelay(delay: Long, vararg views: View) {
        for (v in views) v.isEnabled = false

        Utilities.delay(delay, lifecycleOwner.lifecycleScope) {
            for (v in views) v.isEnabled = true
        }
    }
}

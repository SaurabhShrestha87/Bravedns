/*
 * Copyright 2023 RethinkDNS and its authors
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
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kblock.dns.R
import com.kblock.dns.database.WgConfigFiles
import com.kblock.dns.databinding.ListItemWgInterfaceBinding
import com.kblock.dns.service.ProxyManager
import com.kblock.dns.service.VpnController
import com.kblock.dns.service.WireguardManager
import com.kblock.dns.ui.WgConfigDetailActivity
import com.kblock.dns.ui.WgConfigEditorActivity.Companion.INTENT_EXTRA_WG_ID
import com.kblock.dns.util.UIUtils

class WgConfigAdapter(private val context: Context) :
    PagingDataAdapter<WgConfigFiles, WgConfigAdapter.WgInterfaceViewHolder>(DIFF_CALLBACK) {

    companion object {

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<WgConfigFiles>() {

                override fun areItemsTheSame(
                    oldConnection: WgConfigFiles,
                    newConnection: WgConfigFiles
                ): Boolean {
                    return (oldConnection == newConnection)
                }

                override fun areContentsTheSame(
                    oldConnection: WgConfigFiles,
                    newConnection: WgConfigFiles
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.name == newConnection.name &&
                        oldConnection.isActive == newConnection.isActive)
                }
            }
    }

    override fun onBindViewHolder(holder: WgInterfaceViewHolder, position: Int) {
        val item = getItem(position)
        val wgConfigFiles: WgConfigFiles = item ?: return
        holder.update(wgConfigFiles)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WgInterfaceViewHolder {
        val itemBinding =
            ListItemWgInterfaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WgInterfaceViewHolder(itemBinding)
    }

    inner class WgInterfaceViewHolder(private val b: ListItemWgInterfaceBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun update(config: WgConfigFiles) {
            b.interfaceNameText.text = config.name
            b.interfaceSwitch.isChecked = config.isActive
            updateStatus(config)
            setupClickListeners(config)
        }

        private fun updateStatus(config: WgConfigFiles) {
            val id = ProxyManager.ID_WG_BASE + config.id
            val apps = ProxyManager.getAppCountForProxy(id).toString()
            val appsCount = context.getString(R.string.firewall_card_status_active, apps)
            if (config.isActive) {
                val statusId = VpnController.getProxyStatusById(id)
                if (statusId != null) {
                    val resId = UIUtils.getProxyStatusStringRes(statusId)
                    b.interfaceStatus.text =
                        context.getString(
                            R.string.about_version_install_source,
                            context.getString(resId).replaceFirstChar(Char::titlecase),
                            appsCount
                        )
                } else {
                    b.interfaceStatus.text =
                        context.getString(
                            R.string.about_version_install_source,
                            context
                                .getString(R.string.status_failing)
                                .replaceFirstChar(Char::titlecase),
                            appsCount
                        )
                }
            } else {
                b.interfaceStatus.text =
                    context.getString(
                        R.string.about_version_install_source,
                        context.getString(R.string.lbl_disabled).replaceFirstChar(Char::titlecase),
                        appsCount
                    )
            }
        }

        fun setupClickListeners(config: WgConfigFiles) {
            b.interfaceNameLayout.setOnClickListener { launchConfigDetail(config.id) }

            b.interfaceSwitch.setOnCheckedChangeListener(null)
            b.interfaceSwitch.setOnClickListener {
                val checked = b.interfaceSwitch.isChecked
                if (checked) {
                    if (WireguardManager.canEnableConfig(config)) {
                        WireguardManager.enableConfig(config)
                        updateStatus(config)
                    } else {
                        b.interfaceSwitch.isChecked = false
                        Toast.makeText(
                                context,
                                context.getString(R.string.wireguard_enabled_failure),
                                Toast.LENGTH_LONG
                            )
                            .show()
                    }
                } else {
                    WireguardManager.disableConfig(config)
                    updateStatus(config)
                }
            }
        }

        private fun launchConfigDetail(id: Int) {
            val intent = Intent(context, WgConfigDetailActivity::class.java)
            intent.putExtra(INTENT_EXTRA_WG_ID, id)
            context.startActivity(intent)
        }
    }
}

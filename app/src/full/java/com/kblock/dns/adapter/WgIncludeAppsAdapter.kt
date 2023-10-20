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
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kblock.dns.R
import com.kblock.dns.database.ProxyApplicationMapping
import com.kblock.dns.databinding.ListItemWgIncludeAppsBinding
import com.kblock.dns.service.FirewallManager
import com.kblock.dns.service.ProxyManager
import com.kblock.dns.util.LoggerConstants.Companion.LOG_TAG_PROXY
import com.kblock.dns.util.Utilities.getDefaultIcon
import com.kblock.dns.util.Utilities.getIcon
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WgIncludeAppsAdapter(
    private val context: Context,
    private val proxyId: String,
    private val proxyName: String
) :
    PagingDataAdapter<ProxyApplicationMapping, WgIncludeAppsAdapter.IncludedAppInfoViewHolder>(
        DIFF_CALLBACK
    ) {

    companion object {

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ProxyApplicationMapping>() {

                // based on the apps package info and excluded status
                override fun areItemsTheSame(
                    oldConnection: ProxyApplicationMapping,
                    newConnection: ProxyApplicationMapping
                ): Boolean {
                    return (oldConnection.proxyId == newConnection.proxyId &&
                        oldConnection.uid == newConnection.uid)
                }

                // return false, when there is difference in excluded status
                override fun areContentsTheSame(
                    oldConnection: ProxyApplicationMapping,
                    newConnection: ProxyApplicationMapping
                ): Boolean {
                    return (oldConnection.proxyId == newConnection.proxyId &&
                        oldConnection.uid == newConnection.uid)
                }
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncludedAppInfoViewHolder {
        val itemBinding =
            ListItemWgIncludeAppsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IncludedAppInfoViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: IncludedAppInfoViewHolder, position: Int) {
        val apps: ProxyApplicationMapping = getItem(position) ?: return
        holder.update(apps)
    }

    inner class IncludedAppInfoViewHolder(private val b: ListItemWgIncludeAppsBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun update(mapping: ProxyApplicationMapping) {
            b.wgIncludeAppListApkLabelTv.text = mapping.appName
            Log.i(LOG_TAG_PROXY, "add ${mapping.appName} to ${mapping.proxyId} from $proxyId")

            if (mapping.proxyId == "") {
                b.wgIncludeAppAppDescTv.text = ""
                b.wgIncludeAppAppDescTv.visibility = View.GONE
                b.wgIncludeAppListCheckbox.isChecked = false
            } else if (mapping.proxyId != proxyId) {
                b.wgIncludeAppAppDescTv.text = "part of ${mapping.proxyName}"
                b.wgIncludeAppAppDescTv.visibility = View.VISIBLE
                b.wgIncludeAppListCheckbox.isChecked = false
            } else {
                b.wgIncludeAppAppDescTv.text = ""
                b.wgIncludeAppAppDescTv.visibility = View.GONE
                b.wgIncludeAppListCheckbox.isChecked = mapping.proxyId == proxyId
            }

            val isIncluded = mapping.proxyId == proxyId && mapping.proxyId != ""
            displayIcon(getIcon(context, mapping.packageName, mapping.appName))
            setupClickListeners(mapping, isIncluded)
        }

        private fun setupClickListeners(mapping: ProxyApplicationMapping, isIncluded: Boolean) {
            b.wgIncludeAppListContainer.setOnClickListener {
                Log.i(LOG_TAG_PROXY, "wgIncludeAppListContainer- ${mapping.appName}, $isIncluded")
                updateInterfaceDetails(mapping, !isIncluded)
            }

            b.wgIncludeAppListCheckbox.setOnCheckedChangeListener(null)
            b.wgIncludeAppListCheckbox.setOnClickListener {
                val isAdded = mapping.proxyId == proxyId
                Log.i(LOG_TAG_PROXY, "wgIncludeAppListCheckbox - ${mapping.appName}, $isAdded")
                updateInterfaceDetails(mapping, !isAdded)
            }
        }

        private fun displayIcon(drawable: Drawable?) {
            Glide.with(context)
                .load(drawable)
                .error(getDefaultIcon(context))
                .into(b.wgIncludeAppListApkIconIv)
        }

        private fun updateInterfaceDetails(mapping: ProxyApplicationMapping, include: Boolean) {
            val appUidList = FirewallManager.getAppNamesByUid(mapping.uid)

            if (appUidList.count() > 1) {
                showDialog(appUidList, mapping, include)
            } else {
                if (include) {
                    ProxyManager.updateProxyIdForApp(mapping.uid, proxyId, proxyName)
                    Log.i(LOG_TAG_PROXY, "App ${mapping.appName} included in $proxyId")
                } else {
                    ProxyManager.removeProxyIdForApp(mapping.uid)
                    b.wgIncludeAppListCheckbox.isChecked = false
                    Log.i(
                        LOG_TAG_PROXY,
                        "App ${mapping.appName} removed from wg, id ${mapping.proxyId}}"
                    )
                }
            }
        }

        private fun showDialog(
            packageList: List<String>,
            mapping: ProxyApplicationMapping,
            included: Boolean
        ) {
            val positiveTxt: String

            val builderSingle = MaterialAlertDialogBuilder(context)

            builderSingle.setIcon(R.drawable.ic_firewall_exclude_on)

            val count = packageList.count()
            positiveTxt =
                if (included) {
                    builderSingle.setTitle("Include apps - $count")
                    "Include"
                } else {
                    builderSingle.setTitle("Remove apps - $count")
                    "Remove"
                }
            val arrayAdapter =
                ArrayAdapter<String>(context, android.R.layout.simple_list_item_activated_1)
            arrayAdapter.addAll(packageList)
            builderSingle.setCancelable(false)

            builderSingle.setItems(packageList.toTypedArray(), null)

            builderSingle
                .setPositiveButton(positiveTxt) { _: DialogInterface, _: Int ->
                    if (included) {
                        ProxyManager.updateProxyIdForApp(mapping.uid, proxyId, proxyName)
                        Log.i(LOG_TAG_PROXY, "Included apps: ${mapping.uid}, $proxyId, $proxyName")
                    } else {
                        ProxyManager.removeProxyIdForApp(mapping.uid)
                        b.wgIncludeAppListCheckbox.isChecked = false
                        Log.i(LOG_TAG_PROXY, "Removed apps: ${mapping.uid}, $proxyId, $proxyName")
                    }
                }
                .setNeutralButton(context.getString(R.string.ctbs_dialog_negative_btn)) {
                    _: DialogInterface,
                    _: Int ->
                }

            val alertDialog: AlertDialog = builderSingle.show()
            alertDialog.listView.setOnItemClickListener { _, _, _, _ -> }
            alertDialog.setCancelable(false)
        }
    }
}

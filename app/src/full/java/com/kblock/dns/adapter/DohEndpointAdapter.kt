/*
Copyright 2020 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.kblock.dns.adapter

import android.content.Context
import android.content.DialogInterface
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kblock.dns.R
import com.kblock.dns.RethinkDnsApplication.Companion.DEBUG
import com.kblock.dns.data.AppConfig
import com.kblock.dns.database.DoHEndpoint
import com.kblock.dns.databinding.DohEndpointListItemBinding
import com.kblock.dns.util.LoggerConstants.Companion.LOG_TAG_DNS
import com.kblock.dns.util.UIUtils.clipboardCopy
import com.kblock.dns.util.UIUtils.getDnsStatus
import com.kblock.dns.util.Utilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DohEndpointAdapter(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val appConfig: AppConfig
) : PagingDataAdapter<DoHEndpoint, DohEndpointAdapter.DoHEndpointViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<DoHEndpoint>() {
                override fun areItemsTheSame(
                    oldConnection: DoHEndpoint,
                    newConnection: DoHEndpoint
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.isSelected == newConnection.isSelected)
                }

                override fun areContentsTheSame(
                    oldConnection: DoHEndpoint,
                    newConnection: DoHEndpoint
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.isSelected != newConnection.isSelected)
                }
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoHEndpointViewHolder {
        val itemBinding =
            DohEndpointListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DoHEndpointViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: DoHEndpointViewHolder, position: Int) {
        val doHEndpoint: DoHEndpoint = getItem(position) ?: return
        holder.update(doHEndpoint)
    }

    inner class DoHEndpointViewHolder(private val b: DohEndpointListItemBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun update(endpoint: DoHEndpoint) {
            displayDetails(endpoint)
            setupClickListeners(endpoint)
        }

        private fun setupClickListeners(endpoint: DoHEndpoint) {
            b.root.setOnClickListener { updateConnection(endpoint) }
            b.dohEndpointListActionImage.setOnClickListener {
                showExplanationOnImageClick(endpoint)
            }
            b.dohEndpointListCheckImage.setOnClickListener { updateConnection(endpoint) }
        }

        private fun displayDetails(endpoint: DoHEndpoint) {
            if (endpoint.isSecure) {
                b.dohEndpointListUrlName.text = endpoint.dohName
            } else {
                b.dohEndpointListUrlName.text =
                    context.getString(
                        R.string.ci_desc,
                        endpoint.dohName,
                        context.getString(R.string.lbl_insecure)
                    )
            }
            b.dohEndpointListUrlExplanation.text = ""
            b.dohEndpointListCheckImage.isChecked = endpoint.isSelected
            Log.i(
                LOG_TAG_DNS,
                "connected to doh: ${endpoint.dohName} isSelected? ${endpoint.isSelected}"
            )
            if (endpoint.isSelected) {
                b.dohEndpointListUrlExplanation.text =
                    context.getString(getDnsStatus()).replaceFirstChar(Char::titlecase)
            }

            // Shows either the info/delete icon for the DoH entries.
            showIcon(endpoint)
        }

        private fun showIcon(endpoint: DoHEndpoint) {
            if (endpoint.isDeletable()) {
                b.dohEndpointListActionImage.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_fab_uninstall)
                )
            } else {
                b.dohEndpointListActionImage.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_info)
                )
            }
        }

        private fun updateConnection(endpoint: DoHEndpoint) {
            if (DEBUG)
                Log.d(
                    LOG_TAG_DNS,
                    "on doh change - ${endpoint.dohName}, ${endpoint.dohURL}, ${endpoint.isSelected}"
                )
            io {
                endpoint.isSelected = true
                appConfig.handleDoHChanges(endpoint)
            }
        }

        private fun deleteEndpoint(id: Int) {
            io {
                appConfig.deleteDohEndpoint(id)
                uiCtx {
                    Toast.makeText(
                            context,
                            R.string.doh_custom_url_remove_success,
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }
            }
        }

        private fun showExplanationOnImageClick(endpoint: DoHEndpoint) {
            if (endpoint.isDeletable()) showDeleteDnsDialog(endpoint.id)
            else showDohMetadataDialog(endpoint.dohName, endpoint.dohURL, endpoint.dohExplanation)
        }

        private fun showDohMetadataDialog(title: String, url: String, message: String?) {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(title)
            builder.setMessage(url + "\n\n" + getDnsDesc(message))
            builder.setCancelable(true)
            builder.setPositiveButton(context.getString(R.string.dns_info_positive)) {
                dialogInterface,
                _ ->
                dialogInterface.dismiss()
            }
            builder.setNeutralButton(context.getString(R.string.dns_info_neutral)) {
                _: DialogInterface,
                _: Int ->
                clipboardCopy(context, url, context.getString(R.string.copy_clipboard_label))
                Utilities.showToastUiCentered(
                    context,
                    context.getString(R.string.info_dialog_url_copy_toast_msg),
                    Toast.LENGTH_SHORT
                )
            }
            builder.create().show()
        }

        private fun getDnsDesc(message: String?): String {
            if (message.isNullOrEmpty()) return ""

            return try {
                if (message.contains("R.string.")) {
                    val m = message.substringAfter("R.string.")
                    val resId: Int =
                        context.resources.getIdentifier(m, "string", context.packageName)
                    context.getString(resId)
                } else {
                    message
                }
            } catch (ignored: Exception) {
                ""
            }
        }

        private fun showDeleteDnsDialog(id: Int) {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(R.string.doh_custom_url_remove_dialog_title)
            builder.setMessage(R.string.doh_custom_url_remove_dialog_message)
            builder.setCancelable(true)
            builder.setPositiveButton(context.getString(R.string.lbl_delete)) { _, _ ->
                deleteEndpoint(id)
            }

            builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { _, _ ->
                // no-op
            }
            builder.create().show()
        }

        private suspend fun uiCtx(f: suspend () -> Unit) {
            withContext(Dispatchers.Main) { f() }
        }

        private fun io(f: suspend () -> Unit) {
            lifecycleOwner.lifecycleScope.launch { withContext(Dispatchers.IO) { f() } }
        }
    }
}

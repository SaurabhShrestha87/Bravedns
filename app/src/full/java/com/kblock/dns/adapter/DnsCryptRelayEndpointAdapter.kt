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
import com.kblock.dns.data.AppConfig
import com.kblock.dns.database.DnsCryptRelayEndpoint
import com.kblock.dns.databinding.DnsCryptEndpointListItemBinding
import com.kblock.dns.util.UIUtils
import com.kblock.dns.util.UIUtils.clipboardCopy
import com.kblock.dns.util.Utilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DnsCryptRelayEndpointAdapter(
    private val context: Context,
    val lifecycleOwner: LifecycleOwner,
    private val appConfig: AppConfig
) :
    PagingDataAdapter<
        DnsCryptRelayEndpoint, DnsCryptRelayEndpointAdapter.DnsCryptRelayEndpointViewHolder
    >(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<DnsCryptRelayEndpoint>() {
                override fun areItemsTheSame(
                    oldConnection: DnsCryptRelayEndpoint,
                    newConnection: DnsCryptRelayEndpoint
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.isSelected == newConnection.isSelected)
                }

                override fun areContentsTheSame(
                    oldConnection: DnsCryptRelayEndpoint,
                    newConnection: DnsCryptRelayEndpoint
                ): Boolean {
                    return (oldConnection.id == newConnection.id &&
                        oldConnection.isSelected != newConnection.isSelected)
                }
            }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): DnsCryptRelayEndpointViewHolder {
        val itemBinding =
            DnsCryptEndpointListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return DnsCryptRelayEndpointViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: DnsCryptRelayEndpointViewHolder, position: Int) {
        val dnsCryptRelayEndpoint: DnsCryptRelayEndpoint = getItem(position) ?: return
        holder.update(dnsCryptRelayEndpoint)
    }

    inner class DnsCryptRelayEndpointViewHolder(private val b: DnsCryptEndpointListItemBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun update(endpoint: DnsCryptRelayEndpoint) {
            displayDetails(endpoint)
            setupClickListener(endpoint)
        }

        private fun setupClickListener(endpoint: DnsCryptRelayEndpoint) {
            b.root.setOnClickListener {
                b.dnsCryptEndpointListActionImage.isChecked =
                    !b.dnsCryptEndpointListActionImage.isChecked
                updateDNSCryptRelayDetails(endpoint, b.dnsCryptEndpointListActionImage.isChecked)
            }

            b.dnsCryptEndpointListActionImage.setOnClickListener {
                updateDNSCryptRelayDetails(endpoint, b.dnsCryptEndpointListActionImage.isChecked)
            }

            b.dnsCryptEndpointListInfoImage.setOnClickListener { promptUser(endpoint) }
        }

        private fun displayDetails(endpoint: DnsCryptRelayEndpoint) {
            b.dnsCryptEndpointListUrlName.text = endpoint.dnsCryptRelayName
            if (endpoint.isSelected) {
                b.dnsCryptEndpointListUrlExplanation.text =
                    context.getString(UIUtils.getDnsStatus()).replaceFirstChar(Char::titlecase)
            } else {
                b.dnsCryptEndpointListUrlExplanation.text = ""
            }

            b.dnsCryptEndpointListActionImage.isChecked = endpoint.isSelected
            if (endpoint.isDeletable()) {
                b.dnsCryptEndpointListInfoImage.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_fab_uninstall)
                )
            } else {
                b.dnsCryptEndpointListInfoImage.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_info)
                )
            }
        }

        private fun promptUser(endpoint: DnsCryptRelayEndpoint) {
            if (endpoint.isDeletable()) showDeleteDialog(endpoint.id)
            else {
                showDialogExplanation(
                    endpoint.dnsCryptRelayName,
                    endpoint.dnsCryptRelayURL,
                    endpoint.dnsCryptRelayExplanation
                )
            }
        }

        private fun showDialogExplanation(title: String, url: String, message: String?) {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(title)
            if (message != null) builder.setMessage(url + "\n\n" + relayDesc(message))
            else builder.setMessage(url)
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

        private fun relayDesc(message: String?): String {
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

        private fun showDeleteDialog(id: Int) {
            val builder = MaterialAlertDialogBuilder(context)
            builder.setTitle(R.string.dns_crypt_relay_remove_dialog_title)
            builder.setMessage(R.string.dns_crypt_relay_remove_dialog_message)
            builder.setCancelable(true)
            builder.setPositiveButton(context.getString(R.string.lbl_delete)) { _, _ ->
                deleteEndpoint(id)
            }

            builder.setNegativeButton(context.getString(R.string.lbl_cancel)) { _, _ -> }
            builder.create().show()
        }

        private fun updateDNSCryptRelayDetails(
            endpoint: DnsCryptRelayEndpoint,
            isSelected: Boolean
        ) {

            io {
                if (isSelected && !appConfig.isDnscryptRelaySelectable()) {
                    uiCtx {
                        Toast.makeText(
                                context,
                                context.getString(R.string.dns_crypt_relay_error_toast),
                                Toast.LENGTH_LONG
                            )
                            .show()
                        b.dnsCryptEndpointListActionImage.isChecked = false
                    }
                    return@io
                }

                endpoint.isSelected = isSelected
                appConfig.handleDnsrelayChanges(endpoint)
            }
        }

        private fun deleteEndpoint(id: Int) {
            io {
                appConfig.deleteDnscryptRelayEndpoint(id)
                uiCtx {
                    Toast.makeText(
                            context,
                            R.string.dns_crypt_relay_remove_success,
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }
            }
        }

        private fun io(f: suspend () -> Unit) {
            lifecycleOwner.lifecycleScope.launch { withContext(Dispatchers.IO) { f() } }
        }

        private suspend fun uiCtx(f: suspend () -> Unit) {
            withContext(Dispatchers.Main) { f() }
        }
    }
}
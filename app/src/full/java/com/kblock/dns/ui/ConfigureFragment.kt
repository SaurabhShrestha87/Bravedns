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
package com.kblock.dns.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.kblock.dns.R
import com.kblock.dns.databinding.FragmentConfigureBinding

class ConfigureFragment : Fragment(R.layout.fragment_configure) {

    private val b by viewBinding(FragmentConfigureBinding::bind)

    enum class ScreenType {
        DNS,
        FIREWALL,
        PROXY,
        VPN,
        OTHERS
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun initView() {
        b.fsNetworkTv.text = getString(R.string.lbl_network).replaceFirstChar(Char::titlecase)

        b.fsDnsCard.setOnClickListener {
            // open dns configuration
            startActivity(ScreenType.DNS)
        }

        b.fsFirewallCard.setOnClickListener {
            // open firewall configuration
            startActivity(ScreenType.FIREWALL)
        }

        b.fsProxyCard.setOnClickListener {
            // open proxy configuration
            startActivity(ScreenType.PROXY)
        }

        b.fsNetworkCard.setOnClickListener {
            // open vpn configuration
            startActivity(ScreenType.VPN)
        }

        b.fsOthersCard.setOnClickListener {
            // open others configuration
            startActivity(ScreenType.OTHERS)
        }
    }

    private fun startActivity(type: ScreenType) {
        val intent =
            when (type) {
                ScreenType.DNS -> Intent(requireContext(), DnsDetailActivity::class.java)
                ScreenType.FIREWALL -> Intent(requireContext(), FirewallActivity::class.java)
                ScreenType.PROXY -> Intent(requireContext(), ProxySettingsActivity::class.java)
                ScreenType.VPN -> Intent(requireContext(), TunnelSettingsActivity::class.java)
                ScreenType.OTHERS -> Intent(requireContext(), MiscSettingsActivity::class.java)
            }
        intent.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        startActivity(intent)
    }
}

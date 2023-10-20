/*
 * Copyright 2022 RethinkDNS and its authors
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
package com.kblock.dns.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.kblock.dns.data.AppConfig
import com.kblock.dns.database.ConnectionTrackerDAO
import com.kblock.dns.database.DnsLogDAO
import com.kblock.dns.service.FirewallManager
import com.kblock.dns.util.Constants

class SummaryStatisticsViewModel(
    private val connectionTrackerDAO: ConnectionTrackerDAO,
    private val dnsLogDAO: DnsLogDAO,
    private val appConfig: AppConfig
) : ViewModel() {
    private var networkActivity: MutableLiveData<String> = MutableLiveData()
    private var countryActivities: MutableLiveData<String> = MutableLiveData()
    private var domains: MutableLiveData<String> = MutableLiveData()
    private var ips: MutableLiveData<String> = MutableLiveData()

    init {
        networkActivity.value = ""
        countryActivities.value = ""
        domains.value = ""
        ips.value = ""
    }

    val getAllowedAppNetworkActivity =
        networkActivity.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    // use dnsQuery as appName
                    connectionTrackerDAO.getAllowedAppNetworkActivity()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getBlockedAppNetworkActivity =
        networkActivity.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    // use dnsQuery as appName
                    connectionTrackerDAO.getBlockedAppNetworkActivity()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getMostContactedDomains =
        domains.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    if (appConfig.getBraveMode().isDnsMode()) {
                        dnsLogDAO.getMostContactedDomains()
                    } else {
                        connectionTrackerDAO.getMostContactedDomains()
                    }
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getMostBlockedDomains =
        domains.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    if (appConfig.getBraveMode().isDnsMode()) {
                        dnsLogDAO.getMostBlockedDomains()
                    } else {
                        // if any app bypasses the dns, then the decision made in flow() call
                        if (FirewallManager.isAnyAppBypassesDns()) {
                            connectionTrackerDAO.getMostBlockedDomains()
                        } else {
                            dnsLogDAO.getMostBlockedDomains()
                        }
                    }
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getMostContactedIps =
        ips.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getMostContactedIps()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getMostBlockedIps =
        ips.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getMostBlockedIps()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getMostContactedCountries =
        countryActivities.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getMostContactedCountries()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getMostBlockedCountries =
        countryActivities.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getMostBlockedCountries()
                }
                .liveData
                .cachedIn(viewModelScope)
        }
}

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
import com.kblock.dns.ui.SummaryStatisticsFragment
import com.kblock.dns.util.Constants

class DetailedStatisticsViewModel(
    private val connectionTrackerDAO: ConnectionTrackerDAO,
    private val dnsLogDAO: DnsLogDAO,
    appConfig: AppConfig
) : ViewModel() {
    private var allowedNetworkActivity: MutableLiveData<String> = MutableLiveData()
    private var blockedNetworkActivity: MutableLiveData<String> = MutableLiveData()
    private var allowedDomains: MutableLiveData<String> = MutableLiveData()
    private var blockedDomains: MutableLiveData<String> = MutableLiveData()
    private var allowedIps: MutableLiveData<String> = MutableLiveData()
    private var blockedIps: MutableLiveData<String> = MutableLiveData()
    private var allowedCountries: MutableLiveData<String> = MutableLiveData()
    private var blockedCountries: MutableLiveData<String> = MutableLiveData()

    fun setData(type: SummaryStatisticsFragment.SummaryStatisticsType) {
        when (type) {
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONNECTED_APPS -> {
                allowedNetworkActivity.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_APPS -> {
                blockedNetworkActivity.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONTACTED_DOMAINS -> {
                allowedDomains.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_DOMAINS -> {
                blockedDomains.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONTACTED_IPS -> {
                allowedIps.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_IPS -> {
                blockedIps.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_CONTACTED_COUNTRIES -> {
                allowedCountries.value = ""
            }
            SummaryStatisticsFragment.SummaryStatisticsType.MOST_BLOCKED_COUNTRIES -> {
                blockedCountries.value = ""
            }
        }
    }

    val getAllAllowedAppNetworkActivity =
        allowedNetworkActivity.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getAllAllowedAppNetworkActivity()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedAppNetworkActivity =
        blockedNetworkActivity.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getAllBlockedAppNetworkActivity()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllContactedDomains =
        allowedDomains.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    if (appConfig.getBraveMode().isDnsMode()) {
                        dnsLogDAO.getAllContactedDomains()
                    } else {
                        connectionTrackerDAO.getAllContactedDomains()
                    }
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedDomains =
        blockedDomains.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    if (appConfig.getBraveMode().isDnsMode()) {
                        dnsLogDAO.getAllBlockedDomains()
                    } else {
                        // if any app bypasses the dns, then the decision made in flow() call
                        if (FirewallManager.isAnyAppBypassesDns()) {
                            connectionTrackerDAO.getAllBlockedDomains()
                        } else {
                            dnsLogDAO.getAllBlockedDomains()
                        }
                    }
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllContactedIps =
        allowedIps.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getAllContactedIps()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedIps =
        blockedIps.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getAllBlockedIps()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllContactedCountries =
        allowedCountries.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getAllContactedCountries()
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    val getAllBlockedCountries =
        blockedCountries.switchMap { _ ->
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    connectionTrackerDAO.getAllBlockedCountries()
                }
                .liveData
                .cachedIn(viewModelScope)
        }
}

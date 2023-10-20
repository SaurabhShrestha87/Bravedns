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
package com.kblock.dns.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.kblock.dns.database.ProxyApplicationMappingDAO
import com.kblock.dns.ui.WgIncludeAppsDialog
import com.kblock.dns.util.Constants.Companion.LIVEDATA_PAGE_SIZE

class ProxyAppsMappingViewModel(private val mappingDAO: ProxyApplicationMappingDAO) : ViewModel() {

    private var filteredList: MutableLiveData<String> = MutableLiveData()
    private var filterType: WgIncludeAppsDialog.TopLevelFilter =
        WgIncludeAppsDialog.TopLevelFilter.ALL_APPS
    private var proxyId: String = ""

    init {
        filterType = WgIncludeAppsDialog.TopLevelFilter.ALL_APPS
        proxyId = ""
        filteredList.postValue("%%")
    }

    var apps =
        filteredList.switchMap { searchTxt ->
            Pager(PagingConfig(LIVEDATA_PAGE_SIZE)) {
                    if (filterType == WgIncludeAppsDialog.TopLevelFilter.SELECTED_APPS) {
                        mappingDAO.getSelectedAppsMapping(searchTxt, proxyId)
                    } else {
                        mappingDAO.getAllAppsMapping(searchTxt)
                    }
                }
                .liveData
                .cachedIn(viewModelScope)
        }

    fun setFilter(filter: String, type: WgIncludeAppsDialog.TopLevelFilter, pid: String) {
        filterType = type
        this.proxyId = pid
        filteredList.postValue("%$filter%")
    }

    fun getAppCountById(configId: String): LiveData<Int> {
        return mappingDAO.getAppCountByIdLiveData(configId)
    }
}
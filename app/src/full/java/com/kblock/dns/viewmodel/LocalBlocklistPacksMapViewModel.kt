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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.kblock.dns.database.LocalBlocklistPacksMapDao
import com.kblock.dns.util.Constants

class LocalBlocklistPacksMapViewModel(
    private val localBlocklistPacksMapDao: LocalBlocklistPacksMapDao
) : ViewModel() {

    private var filter: MutableLiveData<String> = MutableLiveData()

    init {
        filter.value = ""
    }

    val simpleTags =
        filter.switchMap {
            Pager(PagingConfig(Constants.LIVEDATA_PAGE_SIZE)) {
                    localBlocklistPacksMapDao.getTags()
                }
                .liveData
                .cachedIn(viewModelScope)
        }
}
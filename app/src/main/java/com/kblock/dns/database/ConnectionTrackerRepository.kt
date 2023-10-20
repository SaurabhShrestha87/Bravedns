/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.kblock.dns.database

import androidx.lifecycle.LiveData
import com.kblock.dns.data.ConnectionSummary
import com.kblock.dns.data.DataUsage

class ConnectionTrackerRepository(private val connectionTrackerDAO: ConnectionTrackerDAO) {

    suspend fun insert(connectionTracker: ConnectionTracker) {
        connectionTrackerDAO.insert(connectionTracker)
    }

    suspend fun insertBatch(conns: List<ConnectionTracker>) {
        connectionTrackerDAO.insertBatch(conns)
    }

    suspend fun updateBatch(summary: List<ConnectionSummary>) {
        summary.forEach {
            connectionTrackerDAO.updateSummary(
                it.connId,
                it.downloadBytes,
                it.uploadBytes,
                it.duration,
                it.synack,
                it.message
            )
        }
    }

    suspend fun purgeLogsByDate(date: Long) {
        connectionTrackerDAO.purgeLogsByDate(date)
    }

    suspend fun clearAllData() {
        connectionTrackerDAO.clearAllData()
    }

    fun logsCount(): LiveData<Long> {
        return connectionTrackerDAO.logsCount()
    }

    fun getLeastLoggedTime(): Long {
        return connectionTrackerDAO.getLeastLoggedTime()
    }

    suspend fun clearLogsByUid(uid: Int) {
        connectionTrackerDAO.clearLogsByUid(uid)
    }

    suspend fun getDataUsage(before: Long, current: Long): List<DataUsage> {
        return connectionTrackerDAO.getDataUsage(before, current)
    }
}

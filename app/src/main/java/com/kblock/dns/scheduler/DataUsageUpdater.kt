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
package com.kblock.dns.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kblock.dns.RethinkDnsApplication.Companion.DEBUG
import com.kblock.dns.database.AppInfoRepository
import com.kblock.dns.database.ConnectionTrackerRepository
import com.kblock.dns.service.PersistentState
import com.kblock.dns.util.Constants
import com.kblock.dns.util.LoggerConstants
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DataUsageUpdater(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams), KoinComponent {
    private val connTrackRepository by inject<ConnectionTrackerRepository>()
    private val appInfoRepository by inject<AppInfoRepository>()
    private val persistentState by inject<PersistentState>()

    override suspend fun doWork(): Result {
        updateDataUsage()
        return Result.success()
    }

    private suspend fun updateDataUsage() {
        // fetch the data usage from connection tracker and update the app info database
        // with the data usage.
        val currentTimestamp = System.currentTimeMillis()
        val previousTimestamp = persistentState.prevDataUsageCheck
        val dataUsageList = connTrackRepository.getDataUsage(previousTimestamp, currentTimestamp)
        dataUsageList.forEach {
            if (it.uid == Constants.INVALID_UID) return@forEach

            try {
                val currentDataUsage = appInfoRepository.getDataUsageByUid(it.uid)
                val upload = currentDataUsage.uploadBytes + it.uploadBytes
                val download = currentDataUsage.downloadBytes + it.downloadBytes
                if (DEBUG)
                    Log.d(
                        LoggerConstants.LOG_TAG_SCHEDULER,
                        "Data usage for ${it.uid}, $upload, $download"
                    )
                appInfoRepository.updateDataUsageByUid(it.uid, upload, download)
            } catch (e: Exception) {
                Log.e(
                    LoggerConstants.LOG_TAG_SCHEDULER,
                    "Exception in data usage updater: ${e.message}",
                    e
                )
            }
        }
        persistentState.prevDataUsageCheck = currentTimestamp
        Log.i(
            LoggerConstants.LOG_TAG_SCHEDULER,
            "Data usage updated for all apps at $currentTimestamp"
        )
    }
}

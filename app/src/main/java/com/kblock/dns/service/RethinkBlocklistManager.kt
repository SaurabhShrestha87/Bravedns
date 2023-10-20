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
package com.kblock.dns.service

import android.content.Context
import android.util.Log
import com.kblock.dns.R
import com.kblock.dns.data.FileTag
import com.kblock.dns.data.FileTagDeserializer
import com.kblock.dns.database.LocalBlocklistPacksMap
import com.kblock.dns.database.LocalBlocklistPacksMapRepository
import com.kblock.dns.database.RemoteBlocklistPacksMap
import com.kblock.dns.database.RemoteBlocklistPacksMapRepository
import com.kblock.dns.database.RethinkLocalFileTag
import com.kblock.dns.database.RethinkLocalFileTagRepository
import com.kblock.dns.database.RethinkRemoteFileTag
import com.kblock.dns.database.RethinkRemoteFileTagRepository
import com.kblock.dns.util.Constants.Companion.LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME
import com.kblock.dns.util.Constants.Companion.ONDEVICE_BLOCKLIST_FILE_TAG
import com.kblock.dns.util.Constants.Companion.REMOTE_BLOCKLIST_DOWNLOAD_FOLDER_NAME
import com.kblock.dns.util.LoggerConstants
import com.kblock.dns.util.Utilities
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import dnsx.BraveDNS
import dnsx.Dnsx
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException

object RethinkBlocklistManager : KoinComponent {

    private var braveDnsLocal: BraveDNS? = null
    private var braveDnsRemote: BraveDNS? = null

    private val remoteFileTagRepository by inject<RethinkRemoteFileTagRepository>()
    private val remoteBlocklistPacksMapRepository by inject<RemoteBlocklistPacksMapRepository>()
    private val localFileTagRepository by inject<RethinkLocalFileTagRepository>()
    private val localBlocklistPacksMapRepository by inject<LocalBlocklistPacksMapRepository>()
    private val persistentState by inject<PersistentState>()

    private const val EMPTY_SUBGROUP = "others"

    private const val PARENTAL_CONTROL_TAG = "ParentalControl"
    private const val SECURITY_TAG = "Security"
    private const val PRIVACY_TAG = "Privacy"

    data class RethinkBlockType(val name: String, val label: Int, val desc: Int)

    data class PacksMappingKey(val pack: String, val level: Int)

    enum class RethinkBlocklistType {
        LOCAL,
        REMOTE;

        companion object {
            fun getType(id: Int): RethinkBlocklistType {
                if (id == LOCAL.ordinal) return LOCAL

                return REMOTE
            }
        }

        fun isLocal(): Boolean {
            return this == LOCAL
        }

        fun isRemote(): Boolean {
            return this == REMOTE
        }
    }

    enum class DownloadType(val id: Int) {
        LOCAL(0),
        REMOTE(1);

        fun isLocal(): Boolean {
            return this == LOCAL
        }

        fun isRemote(): Boolean {
            return this == REMOTE
        }
    }

    // TODO: move this strings to strings.xml
    val PARENTAL_CONTROL =
        RethinkBlockType(
            PARENTAL_CONTROL_TAG,
            R.string.rbl_parental_control,
            R.string.rbl_parental_control_desc
        )
    val SECURITY = RethinkBlockType(SECURITY_TAG, R.string.rbl_security, R.string.rbl_security_desc)
    val PRIVACY = RethinkBlockType(PRIVACY_TAG, R.string.rbl_privacy, R.string.rbl_privacy_desc)

    // read and parse the json file, either remote or local blocklist
    // returns the parsed FileTag list, on error return empty array list
    suspend fun readJson(context: Context, type: DownloadType, timestamp: Long): Boolean {
        // TODO: merge both the remote and local json parsing into one
        return if (type.isRemote()) {
            readRemoteJson(context, timestamp)
        } else {
            readLocalJson(context, timestamp)
        }
    }

    private suspend fun readLocalJson(context: Context, timestamp: Long): Boolean {
        val packsBlocklistMapping: Multimap<PacksMappingKey, Int> = HashMultimap.create()
        try {
            val dbFileTagLocal: MutableList<RethinkLocalFileTag> = mutableListOf()
            val dir =
                Utilities.blocklistDownloadBasePath(
                    context,
                    LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME,
                    timestamp
                )

            val file = Utilities.blocklistFile(dir, ONDEVICE_BLOCKLIST_FILE_TAG) ?: return false

            val jsonString = file.bufferedReader().use { it.readText() }
            // register the type adapter to deserialize the class.
            // see FileTag.kt for more info (FileTagDeserializer)
            val gson =
                GsonBuilder()
                    .registerTypeAdapter(FileTag::class.java, FileTagDeserializer())
                    .create()
            val entries: JsonObject = Gson().fromJson(jsonString, JsonObject::class.java)
            entries.entrySet().forEach {
                val t = gson.fromJson(it.value, FileTag::class.java)
                // add subg tag as "others" if its empty
                if (t.subg.isEmpty()) {
                    t.subg = EMPTY_SUBGROUP
                }
                t.group = t.group.lowercase()
                val l = getRethinkLocalObj(t)

                if (l.pack?.isNotEmpty() == true) {
                    l.pack?.forEachIndexed { index, s ->
                        // if the pack is empty or level is empty, then skip
                        if (s.isEmpty() || l.level == null || l.level?.isEmpty() == true) {
                            l.level = arrayListOf()
                            packsBlocklistMapping.put(PacksMappingKey(s, 0), l.value)
                            return@forEachIndexed
                        }
                        val level = l.level?.elementAt(index) ?: 2
                        packsBlocklistMapping.put(PacksMappingKey(s, level), l.value)
                    }
                }

                dbFileTagLocal.add(l)
            }
            val selectedTags = localFileTagRepository.getSelectedTags()
            // edge case: found a residual block list entry still available in the database
            // during the insertion of new block list entries. This occurred when the number of
            // block lists in the preceding list is greater than the current list. Always
            // empty the data base entries before creating new entries.
            localFileTagRepository.deleteAll()
            localFileTagRepository.insertAll(dbFileTagLocal.toList())
            localFileTagRepository.updateTags(selectedTags.toSet(), 1)
            localBlocklistPacksMapRepository.deleteAll()
            // insert the packs and level mapping in the database
            localBlocklistPacksMapRepository.insertAll(
                packsBlocklistMapping.keySet().map { key ->
                    LocalBlocklistPacksMap(
                        key.pack,
                        key.level,
                        packsBlocklistMapping.get(key).toList(),
                        dbFileTagLocal.first { it.pack?.contains(key.pack) == true }.group
                    )
                }
            )
            Log.i(LoggerConstants.LOG_TAG_DNS, "New Local blocklist files inserted into database")
            return true
        } catch (ioException: IOException) {
            Log.e(
                LoggerConstants.LOG_TAG_DNS,
                "Failure reading json file, blocklist type: remote, timestamp: $timestamp",
                ioException
            )
        }
        return false
    }

    private suspend fun readRemoteJson(context: Context, timestamp: Long): Boolean {
        try {
            val packsBlocklistMapping: Multimap<PacksMappingKey, Int> = HashMultimap.create()
            val dbFileTagRemote: MutableList<RethinkRemoteFileTag> = mutableListOf()

            val dir =
                Utilities.blocklistDownloadBasePath(
                    context,
                    REMOTE_BLOCKLIST_DOWNLOAD_FOLDER_NAME,
                    timestamp
                )

            val file = Utilities.blocklistFile(dir, ONDEVICE_BLOCKLIST_FILE_TAG) ?: return false

            // register type-adapter to enable custom deserialization of the FileTag object.
            // see FileTag.kt for more info (FileTagDeserializer)
            val gson =
                GsonBuilder()
                    .registerTypeAdapter(FileTag::class.java, FileTagDeserializer())
                    .create()

            val jsonString = file.bufferedReader().use { it.readText() }
            val entries: JsonObject = Gson().fromJson(jsonString, JsonObject::class.java)
            entries.entrySet().forEach {
                val t = gson.fromJson(it.value, FileTag::class.java)
                // add subg tag as "others" if its empty
                if (t.subg.isEmpty()) {
                    t.subg = EMPTY_SUBGROUP
                }
                t.group = t.group.lowercase()
                val r = getRethinkRemoteObj(t)

                if (r.pack?.isNotEmpty() == true) {
                    r.pack?.forEachIndexed { index, s ->
                        // if the pack is empty or the level is null, skip the entry
                        if (s.isEmpty() || r.level == null || r.level?.isEmpty() == true) {
                            r.level = arrayListOf()
                            packsBlocklistMapping.put(PacksMappingKey(s, 0), r.value)
                            return@forEachIndexed
                        }
                        // if the level is empty, then set the level to 2 (assume highest) #756
                        val level = r.level?.elementAt(index) ?: 2
                        packsBlocklistMapping.put(PacksMappingKey(s, level), r.value)
                    }
                }
                dbFileTagRemote.add(r)
                // if (DEBUG) Log.d(LoggerConstants.LOG_TAG_DNS, "Remote file tag: $r")
                Log.i(
                    LoggerConstants.LOG_TAG_DNS,
                    "Remote file tag: ${r.group}, ${r.pack}, ${r.simpleTagId}, ${r.level}, ${r.value}, ${r.entries}, ${r.isSelected}, ${r.show}, ${r.subg}, ${r.uname}, ${r.url}, ${r.vname}"
                )
            }
            val selectedTags = remoteFileTagRepository.getSelectedTags()
            // edge case: found a residual block list entry still available in the database
            // during the insertion of new block list entries. This occurred when the number of
            // block lists in the preceding list is greater than the current list. Always
            // empty the data base entries before creating new entries.
            remoteFileTagRepository.deleteAll()
            remoteFileTagRepository.insertAll(dbFileTagRemote.toList())
            remoteFileTagRepository.updateTags(selectedTags.toSet(), 1)
            // insert the packs and level mapping in the database
            remoteBlocklistPacksMapRepository.deleteAll()
            remoteBlocklistPacksMapRepository.insertAll(
                packsBlocklistMapping.keySet().map { key ->
                    RemoteBlocklistPacksMap(
                        key.pack,
                        key.level,
                        packsBlocklistMapping.get(key).toList(),
                        dbFileTagRemote.first { it.pack?.contains(key.pack) == true }.group
                    )
                }
            )
            Log.i(LoggerConstants.LOG_TAG_DNS, "New Remote blocklist files inserted into database")
            return true
        } catch (ioException: IOException) {
            Log.e(
                LoggerConstants.LOG_TAG_DNS,
                "Failure reading json file, blocklist type: remote, timestamp: $timestamp",
                ioException
            )
        }
        return false
    }

    private fun getRethinkLocalObj(t: FileTag): RethinkLocalFileTag {
        return RethinkLocalFileTag(
            t.value,
            t.uname,
            t.vname,
            t.group,
            t.subg,
            t.pack,
            t.level,
            t.urls,
            t.show,
            t.entries,
            t.simpleTagId,
            t.isSelected
        )
    }

    private fun getRethinkRemoteObj(t: FileTag): RethinkRemoteFileTag {
        return RethinkRemoteFileTag(
            t.value,
            t.uname,
            t.vname,
            t.group,
            t.subg,
            t.pack,
            t.level,
            t.urls,
            t.show,
            t.entries,
            t.simpleTagId,
            t.isSelected
        )
    }

    suspend fun updateFiletagRemote(remote: RethinkRemoteFileTag) {
        remoteFileTagRepository.update(remote)
    }

    suspend fun updateFiletagLocal(local: RethinkLocalFileTag) {
        localFileTagRepository.update(local)
    }

    suspend fun updateFiletagsRemote(values: Set<Int>, isSelected: Int) {
        remoteFileTagRepository.updateTags(values, isSelected)
    }

    fun updateFiletagsLocal(values: Set<Int>, isSelected: Int) {
        localFileTagRepository.updateTags(values, isSelected)
    }

    suspend fun getSelectedFileTagsLocal(): List<Int> {
        return localFileTagRepository.getSelectedTags()
    }

    suspend fun getSelectedFileTagsRemote(): List<Int> {
        return remoteFileTagRepository.getSelectedTags()
    }

    suspend fun clearTagsSelectionRemote() {
        remoteFileTagRepository.clearSelectedTags()
    }

    suspend fun clearTagsSelectionLocal() {
        localFileTagRepository.clearSelectedTags()
    }

    fun cpSelectFileTag(context: Context, localFileTags: RethinkLocalFileTag): Int {
        val selectedTags =
            getTagsFromStamp(
                    context,
                    persistentState.localBlocklistStamp,
                    RethinkBlocklistType.LOCAL
                )
                .toMutableSet()

        // remove the tag from the local blocklist if it exists and current selection is 0
        if (selectedTags.contains(localFileTags.value) && !localFileTags.isSelected) {
            selectedTags.remove(localFileTags.value)
        } else if (!selectedTags.contains(localFileTags.value) && localFileTags.isSelected) {
            // only add the tag if it is not already present
            selectedTags.add(localFileTags.value)
        } else {
            // no-op
        }

        val stamp = getStamp(context, selectedTags, RethinkBlocklistType.LOCAL)
        persistentState.localBlocklistStamp = stamp
        return localFileTagRepository.contentUpdate(localFileTags)
    }

    fun getStamp(context: Context, fileValues: Set<Int>, type: RethinkBlocklistType): String {
        return try {
            val flags = convertListToCsv(fileValues)
            getBraveDns(context, blocklistTimestamp(type), type)?.flagsToStamp(flags) ?: ""
        } catch (e: java.lang.Exception) {
            Log.e(LoggerConstants.LOG_TAG_VPN, "err stamp2tags: ${e.message}, $e ")
            ""
        }
    }

    private fun blocklistTimestamp(type: RethinkBlocklistType): Long {
        return if (type.isLocal()) {
            persistentState.localBlocklistTimestamp
        } else {
            persistentState.remoteBlocklistTimestamp
        }
    }

    fun getTagsFromStamp(context: Context, stamp: String, type: RethinkBlocklistType): Set<Int> {
        return try {
            convertCsvToList(
                getBraveDns(context, blocklistTimestamp(type), type)?.stampToFlags(stamp)
            )
        } catch (e: Exception) {
            Log.e(LoggerConstants.LOG_TAG_VPN, "err tags2stamp: ${e.message}, $e ")
            setOf()
        }
    }

    private fun convertCsvToList(csv: String?): Set<Int> {
        if (csv == null) return setOf()

        return csv.split(",").map { it.toInt() }.toSet()
    }

    private fun convertListToCsv(s: Set<Int>): String {
        return s.joinToString(",")
    }

    private fun getBraveDnsRemote(context: Context, timestamp: Long): BraveDNS? {
        if (braveDnsRemote != null) {
            return braveDnsRemote
        }

        val dir =
            Utilities.blocklistDir(context, REMOTE_BLOCKLIST_DOWNLOAD_FOLDER_NAME, timestamp)
                ?: return null
        val file =
            Utilities.blocklistFile(dir.absolutePath, ONDEVICE_BLOCKLIST_FILE_TAG) ?: return null

        braveDnsRemote =
            try {
                if (file.exists()) {
                    Dnsx.newBraveDNSRemote(file.absolutePath)
                } else {
                    Log.e(
                        LoggerConstants.LOG_TAG_VPN,
                        "File does not exist in path: ${file.absolutePath}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e(
                    LoggerConstants.LOG_TAG_VPN,
                    "Exception creating BraveDNS object, ${e.message}, $e "
                )
                null
            }
        return braveDnsRemote
    }

    private fun getBraveDnsLocal(context: Context, timestamp: Long): BraveDNS? {
        if (braveDnsLocal != null) {
            return braveDnsLocal
        }

        val dir =
            Utilities.blocklistDir(context, LOCAL_BLOCKLIST_DOWNLOAD_FOLDER_NAME, timestamp)
                ?: return null
        val file =
            Utilities.blocklistFile(dir.absolutePath, ONDEVICE_BLOCKLIST_FILE_TAG) ?: return null
        braveDnsLocal =
            try {
                if (file.exists()) {
                    Dnsx.newBraveDNSRemote(file.absolutePath)
                } else {
                    Log.e(
                        LoggerConstants.LOG_TAG_VPN,
                        "File does not exist in path: ${file.absolutePath}"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e(
                    LoggerConstants.LOG_TAG_VPN,
                    "Exception creating BraveDNS object, ${e.message}, $e "
                )
                null
            }
        return braveDnsLocal
    }

    private fun getBraveDns(
        context: Context,
        timestamp: Long,
        type: RethinkBlocklistType
    ): BraveDNS? {
        if (type.isRemote()) {
            return getBraveDnsRemote(context, timestamp)
        }

        return getBraveDnsLocal(context, timestamp)
    }

    fun createBraveDns(context: Context, timestamp: Long, type: RethinkBlocklistType) {
        getBraveDns(context, timestamp, type)
    }
}

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
package com.kblock.dns.database

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kblock.dns.service.WireguardManager.SEC_WARP_ID
import com.kblock.dns.service.WireguardManager.WARP_ID

@Dao
interface WgConfigFilesDAO {

    @Update fun update(wgConfigFiles: WgConfigFiles)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(wgConfigFiles: List<WgConfigFiles>): LongArray

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insert(wgConfigFiles: WgConfigFiles): Long

    @Query(
        "select * from WgConfigFiles where id != $SEC_WARP_ID and id != $WARP_ID order by id desc"
    )
    fun getWgConfigsLiveData(): PagingSource<Int, WgConfigFiles>

    @Query(
        "select * from WgConfigFiles where id != $SEC_WARP_ID and id != $WARP_ID order by id desc"
    )
    fun getWgConfigs(): List<WgConfigFiles>

    @Query("select max(id) from WgConfigFiles") fun getLastAddedConfigId(): Int

    @Delete fun delete(wgConfigFiles: WgConfigFiles)

    @Query("delete from WgConfigFiles where id != $SEC_WARP_ID and id != $WARP_ID")
    fun deleteOnAppRestore(): Int

    @Query("delete from WgConfigFiles where id = :id") fun deleteConfig(id: Int)

    @Query("select * from WgConfigFiles where id = :id") fun isConfigAdded(id: Int): WgConfigFiles?

    @Query("select count(id) from WgConfigFiles where id != $SEC_WARP_ID and id != $WARP_ID")
    fun getConfigCount(): LiveData<Int>

    @Query("update WgConfigFiles set isActive = 0 where id = :id") fun disableConfig(id: Int)
}

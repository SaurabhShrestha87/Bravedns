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
package com.kblock.dns.service

import com.kblock.dns.database.AppInfo
import org.koin.core.component.KoinComponent

object ProxyManager : KoinComponent {
    const val ID_ORBOT_BASE = "ORBOT"
    const val ID_WG_BASE = "wg"
    const val ID_TCP_BASE = "TCP"
    const val ID_S5_BASE = "S5"
    const val ID_HTTP_BASE = "HTTP"
    const val ID_SYSTEM = "SYSTEM"

    suspend fun load() {}

    fun getProxyMapping(): MutableSet<FirewallManager.AppInfoTuple> {
        return mutableSetOf()
    }

    fun addNewApp(appInfo: AppInfo, proxyId: String = "", proxyName: String = "") {}

    fun deleteApp(appInfo: AppInfo, proxyId: String = "", proxyName: String = "") {}

    fun deleteApp(appInfoTuple: FirewallManager.AppInfoTuple) {}

    fun getProxyIdForApp(uid: Int): String {
        return ""
    }

    fun isProxyActive(proxyId: String): Boolean {
        return false
    }
}

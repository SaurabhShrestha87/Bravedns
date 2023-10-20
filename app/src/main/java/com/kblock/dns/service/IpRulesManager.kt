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
package com.kblock.dns.service

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.kblock.dns.R
import com.kblock.dns.database.CustomIp
import com.kblock.dns.database.CustomIpRepository
import com.kblock.dns.util.Constants
import com.kblock.dns.util.IPUtil
import com.kblock.dns.util.LoggerConstants.Companion.LOG_TAG_FIREWALL
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import inet.ipaddr.HostName
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

object IpRulesManager : KoinComponent {

    private val customIpRepository by inject<CustomIpRepository>()
    private val persistentState by inject<PersistentState>()
    private val lock = ReentrantReadWriteLock()

    // max size of ip request look-up cache
    private const val CACHE_MAX_SIZE = 10000L

    private var appIpRules: MutableMap<CacheKey, CustomIp> = hashMapOf()
    private var wildCards: MutableMap<CacheKey, CustomIp> = hashMapOf()

    // key-value object for ip look-up
    data class CacheKey(val hostName: HostName, val uid: Int)

    // stores the response for the look-up request from the BraveVpnService
    private val resultsCache: Cache<CacheKey, IpRuleStatus> =
        CacheBuilder.newBuilder().maximumSize(CACHE_MAX_SIZE).build()

    enum class IPRuleType(val id: Int) {
        IPV4(0),
        IPV6(2)
    }

    enum class IpRuleStatus(val id: Int) {
        NONE(0),
        BLOCK(1),
        TRUST(2),
        BYPASS_UNIVERSAL(3);

        fun isBlocked(): Boolean {
            return this.id == BLOCK.id
        }

        companion object {

            // labels for spinner / toggle ui
            fun getLabel(context: Context): Array<String> {
                return arrayOf(
                    context.getString(R.string.ci_no_rule),
                    context.getString(R.string.ci_block),
                    context.getString(R.string.ci_trust_rule)
                )
            }

            fun getStatus(id: Int): IpRuleStatus {
                return when (id) {
                    NONE.id -> NONE
                    BLOCK.id -> BLOCK
                    TRUST.id -> TRUST
                    BYPASS_UNIVERSAL.id -> BYPASS_UNIVERSAL
                    else -> NONE
                }
            }
        }
    }

    init {
        io { loadIpRules() }
    }

    // returns CustomIp object based on uid and IP address
    private suspend fun getObj(uid: Int, ipAddress: String, port: Int): CustomIp? {
        return customIpRepository.getCustomIpDetail(uid, ipAddress, port)
    }

    fun getCustomIpsLiveData(): LiveData<Int> {
        return customIpRepository.getCustomIpsLiveData()
    }

    fun removeIpRule(uid: Int, ipAddress: String, port: Int) {
        Log.i(LOG_TAG_FIREWALL, "IP Rules, remove/delete rule for ip: $ipAddress for uid: $uid")
        if (ipAddress.isEmpty()) {
            return
        }

        io {
            val customIp = getObj(uid, ipAddress, port)
            customIpRepository.deleteRule(uid, ipAddress, port)

            if (customIp == null) return@io

            // remove the ip address from local list
            removeLocalCache(customIp)
            resultsCache.invalidateAll()
        }
    }

    fun updateRule(uid: Int, ipAddress: String, port: Int, status: IpRuleStatus) {
        Log.i(
            LOG_TAG_FIREWALL,
            "IP Rules, update rule for ip: $ipAddress for uid: $uid with status: ${status.name}"
        )
        io {
            val customIpObj = constructCustomIpObject(uid, ipAddress, port, status)
            customIpRepository.insert(customIpObj)
            updateLocalCache(customIpObj)
            resultsCache.invalidateAll()
        }
    }

    fun byPassUniversal(customIp: CustomIp) {
        Log.i(
            LOG_TAG_FIREWALL,
            "IP Rules, by-pass univ rules, ip: ${customIp.ipAddress} for uid: ${customIp.uid} with previous status id: ${customIp.status}"
        )
        io {
            customIp.status = IpRuleStatus.BYPASS_UNIVERSAL.id
            customIp.modifiedDateTime = System.currentTimeMillis()
            customIpRepository.update(customIp)
            updateLocalCache(customIp)
            resultsCache.invalidateAll()
        }
    }

    fun trustIpRules(customIp: CustomIp) {
        Log.i(
            LOG_TAG_FIREWALL,
            "IP Rules, trust ip, ip: ${customIp.ipAddress} for uid: ${customIp.uid} with previous status id: ${customIp.status}"
        )
        io {
            customIp.status = IpRuleStatus.TRUST.id
            customIp.modifiedDateTime = System.currentTimeMillis()
            customIpRepository.update(customIp)
            updateLocalCache(customIp)
            resultsCache.invalidateAll()
        }
    }

    fun noRuleIp(customIp: CustomIp) {
        Log.i(
            LOG_TAG_FIREWALL,
            "IP Rules, remove(soft delete) rule for ip: ${customIp.ipAddress} for uid: ${customIp.uid} with previous status id: ${customIp.status}"
        )
        io {
            customIp.status = IpRuleStatus.NONE.id
            customIp.modifiedDateTime = System.currentTimeMillis()
            customIpRepository.update(customIp)
            updateLocalCache(customIp)
            resultsCache.invalidateAll()
        }
    }

    private fun updateLocalCache(ip: CustomIp) {
        lock.write {
            if (
                ip.getCustomIpAddress().asAddress().isMultiple ||
                    ip.getCustomIpAddress().asAddress().isAnyLocal
            ) {
                wildCards[CacheKey(ip.getCustomIpAddress(), ip.uid)] = ip
                return
            }
            appIpRules[CacheKey(ip.getCustomIpAddress(), ip.uid)] = ip
        }
    }

    private fun removeLocalCache(ip: CustomIp) {
        lock.write {
            if (
                ip.getCustomIpAddress().asAddress().isMultiple ||
                    ip.getCustomIpAddress().asAddress().isAnyLocal
            ) {
                wildCards.remove(CacheKey(ip.getCustomIpAddress(), ip.uid))
                return
            }
            appIpRules.remove(CacheKey(ip.getCustomIpAddress(), ip.uid))
        }
    }

    fun blockIp(customIp: CustomIp) {
        Log.i(
            LOG_TAG_FIREWALL,
            "IP Rules, block rule for ip: ${customIp.ipAddress} for uid: ${customIp.uid} with previous status id: ${customIp.status}"
        )
        io {
            customIp.status = IpRuleStatus.BLOCK.id
            customIp.modifiedDateTime = System.currentTimeMillis()
            customIpRepository.update(customIp)
            updateLocalCache(customIp)
            resultsCache.invalidateAll()
        }
    }

    fun hasRule(uid: Int, ipString: String, port: Int?): IpRuleStatus {
        val ip =
            if (port == null) {
                HostName(ipString)
            } else {
                HostName(IPAddressString(ipString).address, port)
            }

        // check if the ip address is already available in the cache
        val key = CacheKey(ip, uid)
        resultsCache.getIfPresent(key)?.let {
            // return only if both ip and app(uid) matches
            return it
        }

        if (appIpRules.contains(key)) {
            val customIp = appIpRules[key]

            if (customIp != null) {
                val status = IpRuleStatus.getStatus(customIp.status)
                resultsCache.put(key, status)
                return status
            }
        }

        var status = subnetMatch(uid, ip)

        // status is not NONE, return the obtained status
        if (status != IpRuleStatus.NONE) {
            resultsCache.put(key, status)
            return status
        }

        if (port != null) {
            status = hasRule(uid, ip.asAddress().toNormalizedString(), null)
            resultsCache.put(key, status)
            return status
        }

        // no need to carry out below checks if ip is not IPv6
        if (!IPUtil.isIpV6(ip.asAddress()) && !isIpv6ToV4FilterRequired()) {
            resultsCache.put(key, status)
            return status
        }

        // for IPv4 address in IPv6
        var ipv4: IPAddress? = null
        if (IPUtil.canMakeIpv4(ip.asAddress())) {
            ipv4 = IPUtil.toIpV4(ip.asAddress())
        }

        if (ipv4 != null) {
            status = hasRule(uid, ipv4.toNormalizedString(), ip.port)
            // status is not NONE, return the obtained status
            if (status != IpRuleStatus.NONE) {
                resultsCache.put(key, status)
                return status
            }
        } else {
            // no-op
        }

        resultsCache.put(key, status)
        return status
    }

    fun isIpRuleAvailable(uid: Int, ipString: String, port: Int?): IpRuleStatus {
        val ip =
            if (port == null) {
                HostName(ipString)
            } else {
                HostName(IPAddressString(ipString).address, port)
            }

        val key = CacheKey(ip, uid)
        val customIpObj =
            appIpRules.getOrElse(key) {
                return IpRuleStatus.NONE
            }

        return IpRuleStatus.getStatus(customIpObj.status)
    }

    private fun isIpv6ToV4FilterRequired(): Boolean {
        return persistentState.filterIpv4inIpv6
    }

    fun deleteRulesByUid(uid: Int) {
        io {
            customIpRepository.deleteRulesByUid(uid)
            appIpRules = appIpRules.filterKeys { it.uid != uid } as MutableMap<CacheKey, CustomIp>
            wildCards = wildCards.filterKeys { it.uid != uid } as MutableMap<CacheKey, CustomIp>
            resultsCache.invalidateAll()
        }
    }

    fun deleteAllAppsRules() {
        io {
            customIpRepository.deleteAllAppsRules()
            appIpRules.clear()
            wildCards.clear()
            resultsCache.invalidateAll()
        }
    }

    suspend fun loadIpRules() {
        if (appIpRules.isNotEmpty() || wildCards.isNotEmpty()) {
            return
        }
        io {
            val rules = customIpRepository.getIpRules()
            // sort the rules by the network prefix length
            val selector: (IPAddressString?) -> Int = { str -> str?.networkPrefixLength ?: 32 }
            val subnetMap =
                rules.sortedByDescending { selector(it.getCustomIpAddress().asAddressString()) }
            subnetMap.forEach {
                // isAnyLocal is true for rules like 0.0.0.0:443
                if (
                    it.getCustomIpAddress().asAddress().isMultiple ||
                        it.getCustomIpAddress().asAddress().isAnyLocal
                ) {
                    wildCards[CacheKey(it.getCustomIpAddress(), it.uid)] = it
                } else {
                    appIpRules[CacheKey(it.getCustomIpAddress(), it.uid)] = it
                }
            }
        }
    }

    fun constructCustomIpObject(
        uid: Int,
        ipAddress: String,
        port: Int?,
        status: IpRuleStatus,
        wildcard: Boolean = false
    ): CustomIp {
        val customIp = CustomIp()
        customIp.setCustomIpAddress(ipAddress)
        customIp.port = port ?: Constants.UNSPECIFIED_PORT
        customIp.protocol = ""
        customIp.isActive = true
        customIp.status = status.id
        customIp.wildcard = wildcard
        customIp.modifiedDateTime = System.currentTimeMillis()

        // TODO: is this needed in database?
        customIp.ruleType =
            if (customIp.getCustomIpAddress().asAddress()?.isIPv6 == true) {
                IPRuleType.IPV6.id
            } else {
                IPRuleType.IPV4.id
            }
        customIp.uid = uid
        return customIp
    }

    fun addIpRule(uid: Int, ipAddress: String, port: Int?, status: IpRuleStatus) {
        io {
            Log.i(
                LOG_TAG_FIREWALL,
                "IP Rules, add rule for ($uid) ip: $ipAddress, $port with status: ${status.name}"
            )
            val customIpObj = constructCustomIpObject(uid, ipAddress, port, status)
            customIpRepository.insert(customIpObj)
            updateLocalCache(customIpObj)
            resultsCache.invalidateAll()
        }
    }

    fun updateIpRule(prevRule: CustomIp, newRule: CustomIp) {
        io {
            Log.i(
                LOG_TAG_FIREWALL,
                "IP Rules, update rule for (${prevRule.uid}) ip: ${prevRule.getCustomIpAddress().asAddress().toNormalizedString()}, ${prevRule.port} with status: ${newRule.status}"
            )
            customIpRepository.deleteRule(
                prevRule.uid,
                prevRule.getCustomIpAddress().asAddress().toNormalizedString(),
                prevRule.port
            )
            customIpRepository.insert(newRule)
            updateLocalCache(newRule)
            resultsCache.invalidateAll()
        }
    }

    private fun subnetMatch(uid: Int, hostName: HostName): IpRuleStatus {
        // TODO: subnet precedence should be taken care of.
        // TODO: IP/16 and IP/24 is added to the rule.
        wildCards.keys.forEach { w ->
            val wc = w.hostName
            if (wc.asAddress().contains(hostName.asAddress())) {
                val rule = wildCards[w]
                if (rule?.uid == uid && (wc.port == null || wc.port == hostName.port)) {
                    return IpRuleStatus.getStatus(rule.status)
                } else {
                    // no-op
                }
            } else if (
                wc.asAddress().isAnyLocal &&
                    wc.asAddress().ipVersion == hostName.asAddress().ipVersion
            ) {
                // case where the default (0.0.0.0 / [::]) is treated as wildcard
                val rule = wildCards[w]
                if (uid == rule?.uid && wc.port == hostName.port) {
                    return IpRuleStatus.getStatus(rule.status)
                } else {
                    // no-op
                }
            } else {
                // no-op
            }
        }

        return IpRuleStatus.NONE
    }

    private fun io(f: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { f() }
    }
}

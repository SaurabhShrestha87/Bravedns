/*
 * Copyright 2023 RethinkDNS and its authors
 *
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
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
package com.kblock.dns.wireguard

import ipn.Ipn
import ipn.Key

/**
 * Represents a Curve25519 key pair as used by WireGuard.
 *
 * Instances of this class are immutable.
 */
@NonNullForAll
class KeyPair @JvmOverloads constructor(key: Key = Ipn.newPrivateKey()) {
    private val privateKey: Key
    private val publicKey: Key
    /**
     * Creates a key pair using an existing private key.
     *
     * @param key a private key, used to derive the public key
     */
    /** Creates a key pair using a newly-generated private key. */
    init {
        this.privateKey = key
        publicKey = key.mult()
    }

    /**
     * Returns the private key from the key pair.
     *
     * @return the private key
     */
    fun getPrivateKey(): Key {
        return privateKey
    }

    /**
     * Returns the public key from the key pair.
     *
     * @return the public key
     */
    fun getPublicKey(): Key {
        return publicKey
    }
}

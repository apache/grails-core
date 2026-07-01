/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.web.servlet.mvc

import jakarta.servlet.http.HttpSession

/**
 * A token used to handle double-submits.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
class SynchronizerTokensHolder implements Serializable {

    private static final long serialVersionUID = 1

    public static final String HOLDER = 'SYNCHRONIZER_TOKENS_HOLDER'
    public static final String TOKEN_KEY = 'SYNCHRONIZER_TOKEN'
    public static final String TOKEN_URI = 'SYNCHRONIZER_URI'
    public static final int DEFAULT_MAX_TOKEN_URLS = 100
    public static final int DEFAULT_MAX_TOKENS_PER_URL = 100

    Map<String, Set<UUID>> currentTokens = new LinkedHashMap<String, Set<UUID>>()

    synchronized boolean isValid(String url, String token) {
        UUID uuid = parseToken(token)
        uuid != null && getExistingTokens(url)?.contains(uuid)
    }

    synchronized String generateToken(String url) {
        final UUID uuid = UUID.randomUUID()
        Set<UUID> tokens = getTokens(url)
        tokens.add(uuid)
        removeEldestTokenIfNecessary(tokens)
        removeEldestTokenUrlIfNecessary()
        return uuid
    }

    synchronized void resetToken(String url) {
        currentTokens.remove(url)
    }

    synchronized void resetToken(String url, String token) {
        if (url && token) {
            final Set<UUID> set = getExistingTokens(url)
            UUID uuid = parseToken(token)
            if (uuid != null) {
                set?.remove(uuid)
            }
            if (set?.isEmpty()) {
                currentTokens.remove(url)
            }
        }
    }

    synchronized boolean isValidAndResetToken(String url, String token) {
        UUID uuid = parseToken(token)
        if (uuid == null) {
            return false
        }

        final Set<UUID> set = getExistingTokens(url)
        boolean valid = set != null && set.remove(uuid)
        if (set?.isEmpty()) {
            currentTokens.remove(url)
        }
        valid
    }

    synchronized boolean isEmpty() {
        return currentTokens.isEmpty() || currentTokens.every { String url, Set<UUID> uuids -> uuids.isEmpty() }
    }

    protected Set<UUID> getTokens(String url) {
        if (!currentTokens.containsKey(url)) {
            currentTokens[url] = new LinkedHashSet<UUID>()
        }

        currentTokens[url]
    }

    private Set<UUID> getExistingTokens(String url) {
        url ? currentTokens[url] : null
    }

    private UUID parseToken(String token) {
        if (!token) {
            return null
        }

        try {
            UUID.fromString(token)
        }
        catch (IllegalArgumentException ignored) {
            null
        }
    }

    private void removeEldestTokenUrlIfNecessary() {
        if (currentTokens.size() > DEFAULT_MAX_TOKEN_URLS) {
            currentTokens.remove(currentTokens.keySet().iterator().next())
        }
    }

    private void removeEldestTokenIfNecessary(Set<UUID> tokens) {
        if (tokens.size() > DEFAULT_MAX_TOKENS_PER_URL) {
            tokens.remove(tokens.iterator().next())
        }
    }

    static SynchronizerTokensHolder store(HttpSession session) {
        SynchronizerTokensHolder tokensHolder = session.getAttribute(HOLDER)
        if (!tokensHolder) {
            tokensHolder = new SynchronizerTokensHolder()
            session.setAttribute(HOLDER, tokensHolder)
        }
        return tokensHolder
    }
}

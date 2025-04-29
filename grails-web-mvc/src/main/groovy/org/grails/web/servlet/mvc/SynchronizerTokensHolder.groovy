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

import java.util.concurrent.CopyOnWriteArraySet

import jakarta.servlet.http.HttpSession

/**
 * A token used to handle double-submits.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
class SynchronizerTokensHolder implements Serializable {

    private static final long serialVersionUID = 1

    public static final String HOLDER = "SYNCHRONIZER_TOKENS_HOLDER"
    public static final String TOKEN_KEY = "SYNCHRONIZER_TOKEN"
    public static final String TOKEN_URI = "SYNCHRONIZER_URI"

    Map<String, Set<UUID>> currentTokens= [:]

    boolean isValid(String url, String token) {
        try {
            getTokens(url).contains UUID.fromString(token)
        }
        catch (IllegalArgumentException e) {
            false
        }
    }

    String generateToken(String url) {
        final UUID uuid = UUID.randomUUID()
        getTokens(url).add(uuid)
        return uuid
    }

    void resetToken(String url) {
        currentTokens.remove(url)
    }

    void resetToken(String url, String token) {
        if (url && token) {
            final Set set = getTokens(url)
            try {
                set.remove UUID.fromString(token)
            }
            catch (IllegalArgumentException ignored) {}
            if (set.isEmpty()) {
                currentTokens.remove(url)
            }
        }
    }

    boolean isEmpty() {
        return currentTokens.isEmpty() || currentTokens.every { String url, Set<UUID> uuids -> uuids.isEmpty() }
    }

    protected Set<UUID> getTokens(String url) {
        if (!currentTokens.containsKey(url)) {
            currentTokens[url] = new CopyOnWriteArraySet<UUID>()
        }

        currentTokens[url]
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

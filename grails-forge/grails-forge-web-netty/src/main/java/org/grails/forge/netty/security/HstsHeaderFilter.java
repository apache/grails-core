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
package org.grails.forge.netty.security;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

@Requires(property = HstsHeaderFilter.ENABLED_PROPERTY, value = "true")
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class HstsHeaderFilter {

    static final String ENABLED_PROPERTY = "grails.forge.security.hsts.enabled";
    static final String HEADER_NAME = "Strict-Transport-Security";

    private final String headerValue;

    public HstsHeaderFilter(@Value("${grails.forge.security.hsts.header-value}") @NonNull String headerValue) {
        this.headerValue = headerValue;
    }

    @ResponseFilter
    void filterResponse(@NonNull MutableHttpResponse<?> response) {
        response.getHeaders().set(HEADER_NAME, headerValue);
    }
}

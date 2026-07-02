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
package org.grails.forge.analytics.postgres;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.security.token.Claims;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;

import jakarta.inject.Singleton;

@Singleton
public class AnalyticsCallerJwtClaimsValidator<T> implements GenericJwtClaimsValidator<T> {

    static final String CALLER_SUBJECT_PROPERTY = "grails.forge.analytics.caller-subject";

    private final String callerSubject;

    public AnalyticsCallerJwtClaimsValidator(@Value("${" + CALLER_SUBJECT_PROPERTY + "}") String callerSubject) {
        this.callerSubject = callerSubject;
    }

    @Override
    public boolean validate(@NonNull Claims claims, T request) {
        Object subject = claims.get(Claims.SUBJECT);
        return callerSubject.equals(subject);
    }
}

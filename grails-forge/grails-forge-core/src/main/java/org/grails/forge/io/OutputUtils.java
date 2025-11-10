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
package org.grails.forge.io;

import java.time.Instant;

import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OutputUtils {

    private static final Logger LOG = LoggerFactory.getLogger(OutputUtils.class);
    private static final String ENV_SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH";

    private OutputUtils() {
    }

    public static Instant createLastModified() {
        return createLastModified(Instant.now());
    }

    @Nullable
    public static Instant createLastModified(@Nullable Instant fallback) {
        String raw = System.getenv(ENV_SOURCE_DATE_EPOCH);
        if (raw == null) {
            // reproducible timestamp not requested
            return fallback;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            LOG.warn("Environment variable {} is set but empty; using fallback.", ENV_SOURCE_DATE_EPOCH);
            return fallback;
        }

        try {
            long epochSeconds = Long.parseLong(trimmed);
            return Instant.ofEpochSecond(epochSeconds);
        } catch (Exception ex) {
            LOG.warn("Could not interpret {}=[{}]; using fallback. Reason: {}", ENV_SOURCE_DATE_EPOCH, raw, ex.toString());
            return fallback;
        }
    }
}

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

package org.grails.datastore.mapping.config;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * H2 compatibility modes and the native {@link DatabasePlatform} each mode emulates.
 *
 * @since 9.0
 */
public enum H2Mode {

    REGULAR(DatabasePlatform.H2),
    ORACLE(DatabasePlatform.ORACLE),
    MSSQLSERVER(DatabasePlatform.SQL_SERVER),
    DERBY(DatabasePlatform.DERBY),
    MYSQL(DatabasePlatform.MYSQL),
    POSTGRESQL(DatabasePlatform.POSTGRESQL),
    MARIADB(DatabasePlatform.MARIADB),
    DB2(DatabasePlatform.DB2_LUW),
    SYBASE(DatabasePlatform.SYBASE);

    private static final Pattern H2_MODE_PATTERN = Pattern.compile("(?i)[;?&]MODE=([a-zA-Z0-9_]+)");

    private final DatabasePlatform targetPlatform;

    H2Mode(DatabasePlatform targetPlatform) {
        this.targetPlatform = targetPlatform;
    }

    public DatabasePlatform getTargetPlatform() {
        return targetPlatform;
    }

    /**
     * Resolves the {@link H2Mode} from a JDBC URL (e.g. {@code jdbc:h2:mem:test;MODE=Oracle}) or mode name.
     *
     * @param jdbcUrlOrMode the JDBC URL or bare mode name
     * @return the matching {@link H2Mode}, or {@link #REGULAR} if unconfigured or unrecognized
     */
    public static H2Mode of(String jdbcUrlOrMode) {
        if (jdbcUrlOrMode == null) {
            return REGULAR;
        }
        String mode = jdbcUrlOrMode;
        Matcher matcher = H2_MODE_PATTERN.matcher(jdbcUrlOrMode);
        if (matcher.find()) {
            mode = matcher.group(1);
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        for (H2Mode candidate : values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        return REGULAR;
    }
}

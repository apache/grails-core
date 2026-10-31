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

/**
 * Supported database platforms and their canonical null uniqueness semantics.
 *
 * @since 9.0
 */
public enum DatabasePlatform {

    ORACLE(UniqueNulls.VALUE, true),
    DB2_LUW(UniqueNulls.VALUE, true),
    SQL_SERVER(UniqueNulls.VALUE, false),
    DERBY(UniqueNulls.VALUE, false),
    SYBASE(UniqueNulls.VALUE, false),

    POSTGRESQL(UniqueNulls.SKIP, false),
    MYSQL(UniqueNulls.SKIP, false),
    MARIADB(UniqueNulls.SKIP, false),
    DB2_IBM_I(UniqueNulls.SKIP, false),
    DB2_ZOS(UniqueNulls.SKIP, false),
    H2(UniqueNulls.SKIP, false),

    DEFAULT(UniqueNulls.SKIP, false);

    private final UniqueNulls uniqueNulls;
    private final boolean entirelyNullKeyExempt;

    DatabasePlatform(UniqueNulls uniqueNulls, boolean entirelyNullKeyExempt) {
        this.uniqueNulls = uniqueNulls;
        this.entirelyNullKeyExempt = entirelyNullKeyExempt;
    }

    public UniqueNulls getUniqueNulls() {
        return uniqueNulls;
    }

    public boolean isEntirelyNullKeyExempt() {
        return entirelyNullKeyExempt;
    }

    /**
     * Resolves the effective {@link DatabasePlatform} for H2 given its JDBC URL or compatibility mode name.
     *
     * @param jdbcUrlOrMode the JDBC URL (e.g. {@code jdbc:h2:mem:test;MODE=Oracle}) or bare mode name (e.g. {@code Oracle})
     * @return the resolved platform that H2 emulates, or {@link #H2} if no mode or standard mode
     */
    public static DatabasePlatform resolveForH2(String jdbcUrlOrMode) {
        return H2Mode.of(jdbcUrlOrMode).getTargetPlatform();
    }
}

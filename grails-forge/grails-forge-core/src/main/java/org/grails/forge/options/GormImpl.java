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
package org.grails.forge.options;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

public enum GormImpl {

    HIBERNATE5("gorm-hibernate5", "Hibernate 5"),
    HIBERNATE7("gorm-hibernate7", "Hibernate 7"),
    MONGODB("gorm-mongodb", "MongoDB");

    public static final GormImpl DEFAULT_OPTION = HIBERNATE5;

    // Selection value accepted before HIBERNATE5 replaced the HIBERNATE constant
    private static final String LEGACY_HIBERNATE_VALUE = "hibernate";

    private final String featureName;
    private final String label;

    GormImpl(String featureName, String label) {
        this.featureName = featureName;
        this.label = label;
    }

    @NonNull
    public String getName() {
        return featureName;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    /**
     * Resolves a user-supplied selection value to a {@link GormImpl}.
     *
     * @param value the selection value (case-insensitive), e.g. {@code hibernate5};
     *              the legacy value {@code hibernate} resolves to {@link #HIBERNATE5}
     * @return the matching implementation, or {@code null} when the value is unknown
     */
    @Nullable
    public static GormImpl parse(@Nullable String value) {
        if (value == null) {
            return null;
        }
        if (LEGACY_HIBERNATE_VALUE.equalsIgnoreCase(value)) {
            return HIBERNATE5;
        }
        for (GormImpl impl : values()) {
            if (value.equalsIgnoreCase(impl.name())) {
                return impl;
            }
        }
        return null;
    }

}

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

import java.util.Locale;

import io.micronaut.core.annotation.NonNull;

public enum DevelopmentReloading {

    DEVTOOLS("Spring Boot DevTools"),
    JREBEL("JRebel"),
    NONE("None");

    public static final DevelopmentReloading DEFAULT_OPTION = DEVTOOLS;
    private final String label;

    DevelopmentReloading(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }

    @NonNull
    public String getName() {
        return name().toLowerCase(Locale.ENGLISH);
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    public DevelopmentReloading toDevelopmentReloading() {
        return switch (this) {
            case DEVTOOLS -> DevelopmentReloading.DEVTOOLS;
            case JREBEL -> DevelopmentReloading.JREBEL;
            default -> DevelopmentReloading.NONE;
        };
    }
}

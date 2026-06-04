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

/**
 * The GSP layout decorator implementation. SiteMesh 3 ({@code grails-sitemesh3})
 * is the default; the legacy SiteMesh 2 based {@code grails-layout} plugin is the
 * opt-in alternative. The two are mutually exclusive, so the choice is modelled as
 * a single option rather than competing features.
 *
 * @since 8.0.0
 */
public enum GspLayoutImpl {

    SITEMESH3("sitemesh3", "SiteMesh 3"),
    GRAILS_LAYOUT("grails-layout", "SiteMesh 2");

    public static final GspLayoutImpl DEFAULT_OPTION = SITEMESH3;
    private final String featureName;
    private final String label;

    GspLayoutImpl(String featureName, String label) {
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
}

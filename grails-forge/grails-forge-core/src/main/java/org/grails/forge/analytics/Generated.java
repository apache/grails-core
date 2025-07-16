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
package org.grails.forge.analytics;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.options.TestFramework;
import org.grails.forge.util.VersionInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

@Introspected
public class Generated {
    private final ApplicationType type;
    private final GormImpl gorm;
    private final ServletImpl servlet;
    private final TestFramework testFramework;
    private final JdkVersion jdkVersion;
    private Collection<? extends SelectedFeature> features = new ArrayList<>();
    private final String grailsVersion;

    @Creator
    public Generated(
            @NonNull ApplicationType type,
            @NonNull GormImpl gorm,
            @NonNull ServletImpl servlet,
            @NonNull TestFramework testFramework,
            @NonNull JdkVersion jdkVersion) {
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.gorm = Objects.requireNonNull(gorm, "GORM cannot be null");
        this.servlet = Objects.requireNonNull(servlet, "Embedded Servlet cannot be null");
        this.testFramework = Objects.requireNonNull(testFramework, "Test framework cannot be null");
        this.jdkVersion = Objects.requireNonNull(jdkVersion, "JDK version cannot be null");
        this.grailsVersion = VersionInfo.getGrailsVersion();
    }

    /**
     * @return The selected features.
     */
    public Collection<? extends SelectedFeature> getSelectedFeatures() {
        return features;
    }

    public void setSelectedFeatures(Collection<? extends SelectedFeature> features) {
        if (features != null) {
            this.features = features;
        }
    }

    /**
     * @return The type
     */
    public @NonNull ApplicationType getType() {
        return type;
    }

    /**
     * @return The GORM Implementation
     */
    public @NonNull GormImpl getGorm() {
        return gorm;
    }
    
    /**
     * @return The Embedded Servlet Implementation
     */
    public @NonNull ServletImpl getServlet() {
        return servlet;
    }

    /**
     * @return The test framework
     */
    public @NonNull TestFramework getTestFramework() {
        return testFramework;
    }

    /**
     * @return The JDK version
     */
    public @NonNull JdkVersion getJdkVersion() {
        return jdkVersion;
    }

    /**
     * @return The Micronaut Version
     */
    public @NonNull String getGrailsVersion() {
        return grailsVersion;
    }

}

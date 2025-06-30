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
 * Servlet Implementation.
 *
 * @author puneetbehl
 * @since 6.0.0
 */
public enum ServletImpl {
    NONE("NONE"),
    TOMCAT("spring-boot-starter-tomcat"),
    JETTY("spring-boot-starter-jetty"),
    UNDERTOW("spring-boot-starter-undertow");

    public static final ServletImpl DEFAULT_OPTION = TOMCAT;
    private final String featureName;

    ServletImpl(String featureName) {
        this.featureName = featureName;
    }

    @NonNull
    public String getName() {
        return featureName;
    }
}

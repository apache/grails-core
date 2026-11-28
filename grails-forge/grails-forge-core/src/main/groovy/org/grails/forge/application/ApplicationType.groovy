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
package org.grails.forge.application

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull

@CompileStatic
enum ApplicationType {

    WEB('Web Application', 'A Grails® framework Web Application'),
    REST_API('Rest API', 'A Grails® framework Rest API'),
    WEB_PLUGIN('Web Plugin', 'A Grails® framework Web Plugin'),
    PLUGIN('Plugin', 'A Grails® framework Plugin'),
    REST_API_PLUGIN('Rest API Plugin', 'A Grails® framework Rest API Plugin')

    public static final ApplicationType DEFAULT_OPTION = WEB

    private final String title
    private final String description

    ApplicationType(String title,
                    String description) {
        this.title = title
        this.description = description
    }

    /**
     * @return The title.
     */
    String getTitle() {
        return title
    }

    /**
     * @return The description.
     */
    String getDescription() {
        return description
    }

    @Nonnull
    String getName() {
        return name().toLowerCase(Locale.ENGLISH)
    }

    /**
     * @return whether a project of the type is a Grails plugin: it has a plugin descriptor class and is built
     * with the Gradle plugin for Grails plugins
     */
    boolean isPlugin() {
        return this == PLUGIN || this == WEB_PLUGIN || this == REST_API_PLUGIN
    }

    /**
     * @return whether a project of the type has the content of a REST API: JSON views and the REST URL mappings,
     * and no GSP
     */
    boolean isRestApi() {
        return this == REST_API || this == REST_API_PLUGIN
    }
}

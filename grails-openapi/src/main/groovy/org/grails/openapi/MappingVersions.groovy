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
package org.grails.openapi

import groovy.transform.CompileStatic

import grails.plugins.VersionComparator
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingsHolder

/**
 * The versions URL mappings are declared for, which Grails matches on the {@code Accept-Version}
 * header a request sends.
 */
@CompileStatic
class MappingVersions {

    private static final VersionComparator VERSION_COMPARATOR = new VersionComparator()

    private final UrlMappingsHolder urlMappingsHolder
    private Map<String, String> latestVersions

    MappingVersions(UrlMappingsHolder urlMappingsHolder) {
        this.urlMappingsHolder = urlMappingsHolder
    }

    /**
     * @return the version a mapping is declared for, or {@code null} for a mapping that answers
     * any version
     */
    static String versionOf(UrlMapping mapping) {
        String version = mapping?.version
        version && version != UrlMapping.ANY_VERSION ? version : null
    }

    /**
     * Whether a version is the one Grails answers a request asking for none with: the highest
     * version mapped for the same pattern and method.
     */
    boolean isLatest(UrlMapping mapping, String version) {
        if (latestVersions == null) {
            latestVersions = [:]
            for (UrlMapping candidate : urlMappingsHolder.urlMappings) {
                String candidateVersion = versionOf(candidate)
                if (candidateVersion == null) {
                    continue
                }
                String route = routeOf(candidate)
                String latest = latestVersions[route]
                if (latest == null || VERSION_COMPARATOR.compare(candidateVersion, latest) > 0) {
                    latestVersions[route] = candidateVersion
                }
            }
        }
        latestVersions[routeOf(mapping)] == version
    }

    private static String routeOf(UrlMapping mapping) {
        "${mapping.httpMethod ?: UrlMapping.ANY_HTTP_METHOD} ${mapping.urlData?.urlPattern}".toString()
    }
}

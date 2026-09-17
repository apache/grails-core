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
package org.grails.forge.web

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.grails.forge.api.GrailsForgeConfiguration
import org.grails.forge.api.RequestInfo
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.BuildTool
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.FeatureFilter
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.ServletImpl

class ForgeRequestSupport {

    static final String CREATE_NAME_PATTERN = /[\w\d-_.]+/
    static final String ZIP_NAME_PATTERN = /[\w\d-_]+/

    private static final ObjectMapper JSON = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)

    static RequestInfo info(HttpServletRequest request, GrailsForgeConfiguration configuration) {
        String serverURL = resolveUrl(request, configuration)
        String path = request.requestURI + (request.queryString ? "?${request.queryString}" : '')
        new RequestInfo(serverURL, path, request.locale ?: Locale.ENGLISH, request.getHeader('User-Agent') ?: '')
    }

    static String toJson(Object body) {
        JSON.writeValueAsString(body)
    }

    static FeatureFilter featureFilter(Map params) {
        FeatureFilter filter = new FeatureFilter()
        filter.reloading = parseEnum(DevelopmentReloading, params.reloading)
        filter.gorm = GormImpl.parse(params.gorm?.toString())
        filter.servlet = parseEnum(ServletImpl, params.servlet)
        filter.javaVersion = parseJdk(params.javaVersion)
        filter
    }

    static ApplicationType parseType(Object raw) {
        String name = raw?.toString()
        ApplicationType.values().find { it.name.equalsIgnoreCase(name) || it.name().equalsIgnoreCase(name) }
    }

    static boolean validName(Object raw, String pattern) {
        String name = raw?.toString()
        name && name ==~ pattern
    }

    static List<String> featureList(Map params) {
        def features = params.list('features')
        features ?: []
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, Object raw) {
        if (!raw) {
            return null
        }
        try {
            Enum.valueOf(type, raw.toString().toUpperCase())
        } catch (IllegalArgumentException ignored) {
            null
        }
    }

    static JdkVersion parseJdk(Object raw) {
        if (!raw) {
            return null
        }
        JdkVersion named = parseEnum(JdkVersion, raw)
        if (named != null) {
            return named
        }
        try {
            JdkVersion.valueOf(Integer.parseInt(raw.toString()))
        } catch (Exception ignored) {
            null
        }
    }

    static BuildTool parseBuild(Object raw) {
        parseEnum(BuildTool, raw)
    }

    static String resolveUrl(HttpServletRequest request, GrailsForgeConfiguration configuration) {
        String configuredPath = configuration?.path?.orElse('') ?: ''
        Optional<URL> configuredUrl = configuration?.url ?: Optional.empty()
        if (configuredUrl.present) {
            String url = configuredUrl.get().toString()
            if (url.startsWith('https://') || url.startsWith('http://')) {
                return url + configuredPath
            }
            return 'https://' + url + configuredPath
        }

        String forwardedProto = request.getHeader('X-Forwarded-Proto')
        if (forwardedProto) {
            String forwardedHost = request.getHeader('X-Forwarded-Host') ?: request.getHeader('Host')
            if (forwardedHost) {
                return "${forwardedProto}://${forwardedHost}${configuredPath}"
            }
        }

        String scheme = request.scheme
        String host = request.serverName
        int port = request.serverPort
        boolean defaultPort = ('http'.equalsIgnoreCase(scheme) && port == 80) ||
                ('https'.equalsIgnoreCase(scheme) && port == 443)
        String authority = defaultPort ? host : "${host}:${port}"
        "${scheme}://${authority}${configuredPath}"
    }
}

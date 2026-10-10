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

import groovy.transform.CompileStatic
import jakarta.servlet.http.HttpServletRequest
import org.grails.forge.api.GrailsForgeConfiguration
import org.grails.forge.api.RequestInfo
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.options.BuildTool
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.FeatureFilter
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

/**
 * Reads what the requests of the API carry: the application type, name, features and options the
 * controllers hand to the generator, and the URL the links of a response start from.
 */
@CompileStatic
class ForgeRequestSupport {

    static final String CREATE_NAME_PATTERN = /[\w\d-_.]+/
    static final String ZIP_NAME_PATTERN = /[\w\d-_]+/

    static final String TYPE_PARAMETER = 'The application type, such as web or rest-api'
    static final String NAME_PARAMETER = 'The name of the application, optionally qualified by its package, such as com.example.demo'
    static final String FEATURES_PARAMETER = 'The features to include, repeated or comma separated'
    static final String BUILD_PARAMETER = 'The build tool; Gradle is the only one supported'
    static final String RELOADING_PARAMETER = 'The development reloading option'
    static final String GORM_PARAMETER = 'The GORM implementation'
    static final String SERVLET_PARAMETER = 'The servlet container'
    static final String JDK_PARAMETER = 'The JDK version, such as 21'
    static final String CLIENT_ERROR = 'Invalid application type, name, feature or option'

    static RequestInfo info(HttpServletRequest request, GrailsForgeConfiguration configuration) {
        String serverURL = resolveUrl(request, configuration)
        String path = request.requestURI + (request.queryString ? "?${request.queryString}" : '')
        new RequestInfo(serverURL, path, request.locale ?: Locale.ENGLISH, request.getHeader('User-Agent') ?: '')
    }

    /**
     * The filter of a feature listing: each option a request gives, or null where it gives none or
     * one that is not an option.
     */
    static FeatureFilter featureFilter(String reloading, String gorm, String servlet, String javaVersion) {
        FeatureFilter filter = new FeatureFilter()
        filter.reloading = parseEnum(DevelopmentReloading, reloading)
        filter.gorm = GormImpl.parse(gorm)
        filter.servlet = parseEnum(ServletImpl, servlet)
        filter.javaVersion = parseJdk(javaVersion)
        filter
    }

    /**
     * The options of a generation: each option a request gives, or its default.
     */
    static Options options(String reloading, String gorm, String servlet, String javaVersion, OperatingSystem operatingSystem) {
        new Options(
                parseEnum(DevelopmentReloading, reloading) ?: DevelopmentReloading.DEFAULT_OPTION,
                GormImpl.parse(gorm) ?: GormImpl.DEFAULT_OPTION,
                parseEnum(ServletImpl, servlet) ?: ServletImpl.DEFAULT_OPTION,
                parseJdk(javaVersion) ?: JdkVersion.DEFAULT_OPTION,
                operatingSystem
        )
    }

    /**
     * The application type a request names, by its id or its name in any case, hyphenated or not:
     * {@code rest-api}, {@code rest_api} and {@code REST_API} name the same type, as they did for
     * the Micronaut service.
     */
    static ApplicationType parseType(String raw) {
        if (!raw) {
            return null
        }
        String name = raw.replace('-', '_')
        ApplicationType.values().find { ApplicationType type ->
            type.name.equalsIgnoreCase(name) || type.name().equalsIgnoreCase(name)
        }
    }

    static boolean validName(String name, String pattern) {
        name && name ==~ pattern
    }

    /**
     * The features a request names: every value of its {@code features} parameter, each of which
     * may list several, comma separated, as the Micronaut service read them.
     */
    static List<String> featureList(List<String> values) {
        List<String> features = []
        for (String value : (values ?: [])) {
            for (String feature : value.tokenize(',')) {
                if (feature.trim()) {
                    features << feature.trim()
                }
            }
        }
        features
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (!raw) {
            return null
        }
        try {
            Enum.valueOf(type, raw.toUpperCase())
        } catch (IllegalArgumentException ignored) {
            null
        }
    }

    static JdkVersion parseJdk(String raw) {
        if (!raw) {
            return null
        }
        JdkVersion named = parseEnum(JdkVersion, raw)
        if (named != null) {
            return named
        }
        try {
            JdkVersion.valueOf(Integer.parseInt(raw))
        } catch (Exception ignored) {
            null
        }
    }

    static BuildTool parseBuild(String raw) {
        parseEnum(BuildTool, raw)
    }

    /**
     * The URL the links of a response start from: the configured URL and path, or else the scheme,
     * host and port of the request. The {@code X-Forwarded-*} headers of a proxy are already applied
     * to the request, by the forwarded-header filter {@code server.forward-headers-strategy} enables.
     */
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
        String scheme = request.scheme
        String host = request.serverName
        int port = request.serverPort
        boolean defaultPort = ('http'.equalsIgnoreCase(scheme) && port == 80) ||
                ('https'.equalsIgnoreCase(scheme) && port == 443)
        String authority = defaultPort ? host : "${host}:${port}"
        "${scheme}://${authority}${configuredPath}"
    }
}

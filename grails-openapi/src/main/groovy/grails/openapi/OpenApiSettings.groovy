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
package grails.openapi

import groovy.transform.CompileStatic

import io.swagger.v3.oas.models.SpecVersion
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertySource

/**
 * How the OpenAPI document is generated, read from the {@code grails.openapi} configuration.
 *
 * <p>The same settings drive a document generated at build time and one springdoc serves. Where
 * springdoc is present, its {@code springdoc.paths-to-match}, {@code springdoc.paths-to-exclude},
 * {@code springdoc.packages-to-scan} and {@code springdoc.packages-to-exclude} are honored for the
 * default document as well, as are {@code springdoc.produces-to-match},
 * {@code springdoc.consumes-to-match} and {@code springdoc.headers-to-match}, and
 * {@code springdoc.api-docs.version} chooses the OpenAPI version.</p>
 *
 * @since 8.0
 */
@CompileStatic
class OpenApiSettings {

    /**
     * The prefix of the settings.
     */
    static final String PREFIX = 'grails.openapi'

    private static final String GROUPS_PREFIX = PREFIX + '.groups.'
    private static final String SPRINGDOC_PREFIX = 'springdoc'

    /**
     * Whether the document is generated at all.
     */
    boolean enabled = true

    /**
     * Whether only an action that declares {@code @Operation}, or whose controller declares
     * {@code @Tag}, is described. Without it every reachable action is.
     */
    boolean annotatedOnly = false

    /**
     * Whether the {@code create} and {@code edit} actions of a {@code RestfulController} are
     * described. They answer the forms an HTML client renders rather than an API client.
     */
    boolean includeFormActions = false

    /**
     * A resource location, such as {@code classpath:openapi-base.yml}, of a YAML or JSON
     * document whose information, servers, security, tags, extensions, paths and components the
     * generated document starts from.
     */
    String baseDocument

    /**
     * Where the {@code generate-open-api} command writes the documents, relative to the
     * directory it runs in.
     */
    String outputDirectory = 'build/openapi'

    /**
     * The format the {@code generate-open-api} command writes: {@code yaml} or {@code json}.
     */
    String outputFormat = 'yaml'

    /**
     * The OpenAPI version described.
     */
    SpecVersion specVersion = SpecVersion.V31

    /**
     * Whether Grails renders the version of a domain class, which it does where
     * {@code grails.converters.json.domain.include.version} or
     * {@code grails.converters.domain.include.version} is set.
     */
    boolean includeVersion = false

    /**
     * What the default document selects.
     */
    OpenApiSelection defaultSelection = new OpenApiSelection()

    /**
     * The groups, each a document of its own.
     */
    List<OpenApiSelection> groups = []

    /**
     * @return the group of that name, or {@code null} if none is configured
     */
    OpenApiSelection group(String name) {
        groups.find { OpenApiSelection selection -> selection.group == name }
    }

    /**
     * Reads the settings from an environment.
     *
     * <p>The properties are read one by one rather than bound, because the configuration Grails
     * loads from {@code application.yml} exposes each nested block as a value of its own, which a
     * binder would try to convert into the bound type.</p>
     */
    static OpenApiSettings from(Environment environment) {
        OpenApiSettings settings = new OpenApiSettings()
        settings.enabled = environment.getProperty("${PREFIX}.enabled".toString(), Boolean, true)
        settings.annotatedOnly = environment.getProperty("${PREFIX}.annotated-only".toString(), Boolean, false)
        settings.includeFormActions = environment.getProperty("${PREFIX}.include-form-actions".toString(), Boolean, false)
        settings.baseDocument = environment.getProperty("${PREFIX}.base-document".toString())
        settings.outputDirectory = environment.getProperty("${PREFIX}.output-directory".toString(), settings.outputDirectory)
        settings.outputFormat = environment.getProperty("${PREFIX}.output-format".toString(), settings.outputFormat)

        settings.includeVersion = environment.getProperty('grails.converters.json.domain.include.version', Boolean,
                environment.getProperty('grails.converters.domain.include.version', Boolean, false))

        String version = environment.getProperty('springdoc.api-docs.version')
        if (version?.toLowerCase(Locale.ENGLISH)?.contains('3_0')) {
            settings.specVersion = SpecVersion.V30
        }

        OpenApiSelection defaults = selection(environment, PREFIX, null)
        OpenApiSelection springdoc = selection(environment, SPRINGDOC_PREFIX, null)
        defaults.pathsToMatch.addAll(springdoc.pathsToMatch)
        defaults.pathsToExclude.addAll(springdoc.pathsToExclude)
        defaults.packagesToScan.addAll(springdoc.packagesToScan)
        defaults.packagesToExclude.addAll(springdoc.packagesToExclude)
        defaults.producesToMatch.addAll(springdoc.producesToMatch)
        defaults.consumesToMatch.addAll(springdoc.consumesToMatch)
        defaults.headersToMatch.addAll(springdoc.headersToMatch)
        settings.defaultSelection = defaults

        settings.groups = groupNames(environment).collect { String name ->
            selection(environment, GROUPS_PREFIX + name, name)
        }
        settings
    }

    private static OpenApiSelection selection(Environment environment, String prefix, String group) {
        new OpenApiSelection(
                group: group,
                displayName: environment.getProperty("${prefix}.display-name".toString()),
                pathsToMatch: list(environment, "${prefix}.paths-to-match".toString()),
                pathsToExclude: list(environment, "${prefix}.paths-to-exclude".toString()),
                packagesToScan: list(environment, "${prefix}.packages-to-scan".toString()),
                packagesToExclude: list(environment, "${prefix}.packages-to-exclude".toString()),
                producesToMatch: list(environment, "${prefix}.produces-to-match".toString()),
                consumesToMatch: list(environment, "${prefix}.consumes-to-match".toString()),
                headersToMatch: list(environment, "${prefix}.headers-to-match".toString()))
    }

    private static List<String> list(Environment environment, String key) {
        String[] values = environment.getProperty(key, String[])
        if (values) {
            return values.collect { String value -> value.trim() }.findAll().toList()
        }
        // A list written in YAML is also exposed element by element.
        List<String> indexed = []
        for (int index = 0; ; index++) {
            String value = environment.getProperty("${key}[${index}]".toString())
            if (value == null) {
                break
            }
            indexed << value.trim()
        }
        indexed
    }

    /**
     * The names under {@code grails.openapi.groups}, in the order they are declared.
     */
    private static Set<String> groupNames(Environment environment) {
        Set<String> names = new LinkedHashSet<String>()
        if (!(environment instanceof ConfigurableEnvironment)) {
            return names
        }
        for (PropertySource<?> source : ((ConfigurableEnvironment) environment).propertySources) {
            if (!(source instanceof EnumerablePropertySource)) {
                continue
            }
            for (String property : ((EnumerablePropertySource<?>) source).propertyNames) {
                if (property.startsWith(GROUPS_PREFIX)) {
                    String name = property.substring(GROUPS_PREFIX.length()).split(/[.\[]/)[0]
                    if (name) {
                        names << name
                    }
                }
            }
        }
        names
    }
}

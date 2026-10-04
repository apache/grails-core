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
package org.apache.grails.openapi.cli

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import io.swagger.v3.oas.models.OpenAPI
import org.springframework.util.ClassUtils

import grails.openapi.GrailsOpenApiGenerator
import grails.openapi.OpenApiSelection
import grails.openapi.OpenApiSettings
import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ExecutionContext
import org.grails.openapi.springdoc.GroupedOpenApiContributor

/**
 * Writes the application's OpenAPI description to files, so the description can be packaged,
 * served as a static file, reviewed in a change, or handed to a code generator without running
 * the application.
 *
 * <p>The default document is written to {@code openapi.yaml}, and each group - configured under
 * {@code grails.openapi.groups}, or declared to springdoc as a {@code GroupedOpenApi} - to
 * {@code openapi-<group>.yaml}. Where springdoc is configured, its method filters and customizers
 * are applied as they are to the documents it serves. The directory and format come
 * from {@code grails.openapi.output-directory} and {@code grails.openapi.output-format}, and can be
 * overridden with the {@code --output-directory} and {@code --format} options.</p>
 */
@Slf4j
@CompileStatic
class GenerateOpenApiCommand implements ApplicationCommand {

    private static final String SPRINGDOC_GROUP = 'org.springdoc.core.models.GroupedOpenApi'
    private static final String SPRINGDOC_ENABLED = 'springdoc.api-docs.enabled'

    /**
     * The characters a file name cannot hold on some file system: a path separator, one Windows
     * reserves, or a control character.
     */
    private static final Pattern UNSAFE_FILE_NAME_CHARACTERS = Pattern.compile('[\\\\/:*?"<>|\\p{Cntrl}]')

    final String description = 'Writes the OpenAPI description of the application to files'

    @Override
    boolean handle(ExecutionContext executionContext) {
        GrailsOpenApiGenerator generator = applicationContext.getBeanProvider(GrailsOpenApiGenerator).getIfAvailable()
        if (generator == null) {
            log.error('Wrote no OpenAPI description: grails.openapi.enabled is false')
            return false
        }
        OpenApiSettings settings = generator.settings
        if (springdocPresent() && !applicationContext.environment.getProperty(SPRINGDOC_ENABLED, Boolean, true)) {
            log.warn('springdoc is disabled where the command runs, by {}: false, so neither the groups of ' +
                    'springdoc.group-configs nor springdoc\'s method filters and customizers are applied', SPRINGDOC_ENABLED)
        }

        String format = (option(executionContext, 'format') ?: settings.outputFormat).toLowerCase(Locale.ENGLISH)
        if (!(format in ['yaml', 'json'])) {
            log.error('Unsupported OpenAPI format [{}]: use yaml or json', format)
            return false
        }
        File directory = outputDirectory(executionContext, option(executionContext, 'output-directory') ?: settings.outputDirectory)
        if (!directory.directory && !directory.mkdirs()) {
            log.error('Could not create the OpenAPI output directory [{}]', directory)
            return false
        }

        Map<String, OpenApiSelection> files = groupFiles(groups(settings), format)
        if (files == null) {
            return false
        }
        write(customized(generator.generate(defaultSelection(settings)), null), new File(directory, "openapi.${format}"), format)
        files.each { String fileName, OpenApiSelection group ->
            write(customized(generator.generate(group), group.group), new File(directory, fileName), format)
        }
        true
    }

    /**
     * The file each group is written to, named for the group, with each character a file name
     * cannot hold, such as the {@code /} of {@code admin/v1}, written as {@code -}.
     *
     * @return the group written to each file, or {@code null}, and why logged, where two groups
     * would be written to one file
     */
    private Map<String, OpenApiSelection> groupFiles(Collection<OpenApiSelection> groups, String format) {
        Map<String, OpenApiSelection> files = [:]
        for (OpenApiSelection group : groups) {
            String fileName = "openapi-${group.group.replaceAll(UNSAFE_FILE_NAME_CHARACTERS, '-')}.${format}".toString()
            OpenApiSelection earlier = files.putIfAbsent(fileName, group)
            if (earlier != null) {
                log.error('Wrote no OpenAPI description: the groups [{}] and [{}] would both be written to {}; ' +
                        'rename one of them', earlier.group, group.group, fileName)
                return null
            }
            if (fileName != "openapi-${group.group}.${format}".toString()) {
                log.warn('Writing the OpenAPI group [{}] to {}, since a file name cannot hold its name', group.group, fileName)
            }
        }
        files
    }

    /**
     * The default document, with the method filters springdoc applies to it where springdoc is
     * configured, so the file describes what springdoc serves.
     */
    private OpenApiSelection defaultSelection(OpenApiSettings settings) {
        springdocPresent()
                ? GroupedOpenApiContributor.defaultSelection(applicationContext, settings.defaultSelection)
                : settings.defaultSelection
    }

    /**
     * The groups springdoc serves, which include those configured under
     * {@code grails.openapi.groups}, or those groups alone without springdoc.
     */
    private Collection<OpenApiSelection> groups(OpenApiSettings settings) {
        Map<String, OpenApiSelection> byName = [:]
        if (springdocPresent()) {
            GroupedOpenApiContributor.declaredGroups(applicationContext).each { OpenApiSelection group ->
                byName.putIfAbsent(group.group, group)
            }
        }
        settings.groups.each { OpenApiSelection group -> byName.putIfAbsent(group.group, group) }
        byName.values()
    }

    /**
     * The document with the customizers springdoc applies to the one it serves, where springdoc is
     * configured.
     */
    private OpenAPI customized(OpenAPI openApi, String group) {
        if (springdocPresent()) {
            GroupedOpenApiContributor.customize(applicationContext, group, openApi)
        }
        openApi
    }

    private static boolean springdocPresent() {
        ClassUtils.isPresent(SPRINGDOC_GROUP, GenerateOpenApiCommand.classLoader)
    }

    private static void write(OpenAPI openApi, File file, String format) {
        file.setText(GrailsOpenApiGenerator.serialize(openApi, format), 'UTF-8')
        println "Wrote ${file}"
    }

    private static File outputDirectory(ExecutionContext executionContext, String path) {
        File directory = new File(path)
        directory.absolute ? directory : new File(executionContext.baseDir ?: new File('.'), path)
    }

    private static String option(ExecutionContext executionContext, String name) {
        Object value = executionContext.commandLine?.optionValue(name)
        value instanceof CharSequence && value ? value.toString() : null
    }
}

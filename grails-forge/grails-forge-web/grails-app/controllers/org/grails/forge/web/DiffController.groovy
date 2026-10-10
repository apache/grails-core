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

import grails.compiler.GrailsCompileStatic
import grails.web.Controller
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.grails.forge.api.GrailsForgeConfiguration
import org.grails.forge.api.RequestInfo
import org.grails.forge.api.UserAgentParser
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.diff.FeatureDiffer
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.util.NameUtils
import org.springframework.beans.factory.annotation.Autowired

@Controller
@GrailsCompileStatic
@Tag(name = 'diff', description = 'What features change in a generated application')
class DiffController {

    static allowedMethods = [diffFeature: 'GET', diffApp: 'GET']

    private static final String DEFAULT_NAME = 'example'

    @Autowired
    ProjectGenerator projectGenerator

    @Autowired
    FeatureDiffer featureDiffer

    @Autowired
    GrailsForgeConfiguration grailsForgeConfiguration

    @Operation(summary = 'Diff a feature', description = 'What a feature adds to an application of the type given, as a unified diff.')
    @ApiResponse(responseCode = '200', description = 'The diff', content = @Content(mediaType = 'text/plain', schema = @Schema(type = 'string')))
    @ApiResponse(responseCode = '400', description = ForgeRequestSupport.CLIENT_ERROR)
    def diffFeature(@Parameter(description = ForgeRequestSupport.TYPE_PARAMETER) String type,
                    @Parameter(description = 'The feature') String feature,
                    @Parameter(description = 'The name of the application the diff is produced for; example unless given') String name,
                    @Parameter(description = ForgeRequestSupport.RELOADING_PARAMETER) String reloading,
                    @Parameter(description = ForgeRequestSupport.GORM_PARAMETER) String gorm,
                    @Parameter(description = ForgeRequestSupport.SERVLET_PARAMETER) String servlet,
                    @Parameter(description = ForgeRequestSupport.JDK_PARAMETER) String javaVersion) {
        if (name != null && !ForgeRequestSupport.validName(name, ForgeRequestSupport.CREATE_NAME_PATTERN)) {
            render status: 400
            return
        }
        renderDiff(produceDiff(type, name ?: DEFAULT_NAME, [feature], reloading, gorm, servlet, javaVersion))
    }

    @Operation(summary = 'Diff an application', description = 'What the features requested add to an application of the type and name given, as a unified diff.')
    @Parameter(name = 'features', in = ParameterIn.QUERY, description = ForgeRequestSupport.FEATURES_PARAMETER, array = @ArraySchema(schema = @Schema(type = 'string')))
    @ApiResponse(responseCode = '200', description = 'The diff', content = @Content(mediaType = 'text/plain', schema = @Schema(type = 'string')))
    @ApiResponse(responseCode = '400', description = ForgeRequestSupport.CLIENT_ERROR)
    def diffApp(@Parameter(description = ForgeRequestSupport.TYPE_PARAMETER) String type,
                @Parameter(description = ForgeRequestSupport.NAME_PARAMETER) String name,
                @Parameter(description = ForgeRequestSupport.RELOADING_PARAMETER) String reloading,
                @Parameter(description = ForgeRequestSupport.GORM_PARAMETER) String gorm,
                @Parameter(description = ForgeRequestSupport.SERVLET_PARAMETER) String servlet,
                @Parameter(description = ForgeRequestSupport.JDK_PARAMETER) String javaVersion) {
        if (!ForgeRequestSupport.validName(name, ForgeRequestSupport.CREATE_NAME_PATTERN)) {
            render status: 400
            return
        }
        List<String> features = ForgeRequestSupport.featureList((List<String>) params.list('features'))
        renderDiff(produceDiff(type, name, features, reloading, gorm, servlet, javaVersion))
    }

    private void renderDiff(String diff) {
        if (diff == null) {
            render status: 400
            return
        }
        render text: diff, contentType: 'text/plain'
    }

    private String produceDiff(String type, String name, List<String> features,
                               String reloading, String gorm, String servlet, String javaVersion) {
        ApplicationType applicationType = ForgeRequestSupport.parseType(type)
        if (applicationType == null) {
            return null
        }
        RequestInfo requestInfo = ForgeRequestSupport.info(request, grailsForgeConfiguration)
        try {
            GeneratorContext generatorContext = projectGenerator.createGeneratorContext(
                    applicationType,
                    NameUtils.parse(name),
                    ForgeRequestSupport.options(reloading, gorm, servlet, javaVersion, OperatingSystem.DEFAULT),
                    UserAgentParser.getOperatingSystem(requestInfo.userAgent),
                    features,
                    ConsoleOutput.NOOP
            )
            StringBuilder builder = new StringBuilder()
            featureDiffer.produceDiff(projectGenerator, generatorContext, new ConsoleOutput() {
                @Override
                void out(String message) { builder.append(message).append(System.lineSeparator()) }
                @Override
                void err(String message) { }
                @Override
                void warning(String message) { }
                @Override
                boolean showStacktrace() { false }
                @Override
                boolean verbose() { false }
            })
            return builder.toString()
        } catch (IllegalArgumentException ignored) {
            return null
        }
    }
}

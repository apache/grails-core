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
import org.grails.forge.api.create.CreateProjectService
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.io.ZipOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.ServletImpl
import org.springframework.beans.factory.annotation.Autowired

@Controller
@GrailsCompileStatic
@Tag(name = 'generation', description = 'Generating an application as a zip archive')
class ZipCreateController {

    static allowedMethods = [createApp: 'GET', createZip: 'GET']

    @Autowired
    CreateProjectService createProjectService

    @Operation(summary = 'Generate an application', description = 'The application, of the type and name given, generated with the features and options requested, as a zip archive.')
    @Parameter(name = 'features', in = ParameterIn.QUERY, description = ForgeRequestSupport.FEATURES_PARAMETER, array = @ArraySchema(schema = @Schema(type = 'string')))
    @ApiResponse(responseCode = '201', description = 'The generated application', content = @Content(mediaType = 'application/zip', schema = @Schema(type = 'string', format = 'binary')))
    @ApiResponse(responseCode = '400', description = ForgeRequestSupport.CLIENT_ERROR)
    def createApp(@Parameter(description = ForgeRequestSupport.TYPE_PARAMETER) String type,
                  @Parameter(description = ForgeRequestSupport.NAME_PARAMETER) String name,
                  @Parameter(description = ForgeRequestSupport.BUILD_PARAMETER) String build,
                  @Parameter(description = ForgeRequestSupport.RELOADING_PARAMETER) String reloading,
                  @Parameter(description = ForgeRequestSupport.GORM_PARAMETER) String gorm,
                  @Parameter(description = ForgeRequestSupport.SERVLET_PARAMETER) String servlet,
                  @Parameter(description = ForgeRequestSupport.JDK_PARAMETER) String javaVersion) {
        ApplicationType applicationType = ForgeRequestSupport.parseType(type)
        if (applicationType == null || !ForgeRequestSupport.validName(name, ForgeRequestSupport.CREATE_NAME_PATTERN)) {
            render status: 400
            return
        }
        writeZip(applicationType, name, build, reloading, gorm, servlet, javaVersion)
    }

    @Operation(summary = 'Generate an application by name', description = 'The application of the name given, a web application unless a type is given, generated with the features and options requested, as a zip archive.')
    @Parameter(name = 'features', in = ParameterIn.QUERY, description = ForgeRequestSupport.FEATURES_PARAMETER, array = @ArraySchema(schema = @Schema(type = 'string')))
    @ApiResponse(responseCode = '201', description = 'The generated application', content = @Content(mediaType = 'application/zip', schema = @Schema(type = 'string', format = 'binary')))
    @ApiResponse(responseCode = '400', description = ForgeRequestSupport.CLIENT_ERROR)
    def createZip(@Parameter(description = 'The name of the application') String name,
                  @Parameter(description = 'The application type, such as web or rest-api; web unless given') String type,
                  @Parameter(description = ForgeRequestSupport.BUILD_PARAMETER) String build,
                  @Parameter(description = ForgeRequestSupport.RELOADING_PARAMETER) String reloading,
                  @Parameter(description = ForgeRequestSupport.GORM_PARAMETER) String gorm,
                  @Parameter(description = ForgeRequestSupport.SERVLET_PARAMETER) String servlet,
                  @Parameter(description = ForgeRequestSupport.JDK_PARAMETER) String javaVersion) {
        ApplicationType applicationType = type ? ForgeRequestSupport.parseType(type) : ApplicationType.DEFAULT_OPTION
        if (applicationType == null || !ForgeRequestSupport.validName(name, ForgeRequestSupport.ZIP_NAME_PATTERN)) {
            render status: 400
            return
        }
        writeZip(applicationType, name, build, reloading, gorm, servlet, javaVersion)
    }

    private void writeZip(ApplicationType type, String name, String build, String reloading, String gorm, String servlet, String javaVersion) {
        GeneratorContext generatorContext
        try {
            generatorContext = createProjectService.createProjectGeneratorContext(
                    type,
                    name,
                    ForgeRequestSupport.featureList((List<String>) params.list('features')),
                    ForgeRequestSupport.parseBuild(build),
                    ForgeRequestSupport.parseEnum(DevelopmentReloading, reloading),
                    GormImpl.parse(gorm),
                    ForgeRequestSupport.parseEnum(ServletImpl, servlet),
                    ForgeRequestSupport.parseJdk(javaVersion),
                    request.getHeader('User-Agent')
            )
        } catch (IllegalArgumentException ignored) {
            render status: 400
            return
        }
        String filename = generatorContext.project.name + '.zip'
        response.status = 201
        response.contentType = 'application/zip'
        response.setHeader('Content-Disposition', "attachment; filename=${filename}")
        try {
            createProjectService.projectGenerator.generate(
                    type,
                    generatorContext.project,
                    new ZipOutputHandler(generatorContext.project.name, response.outputStream),
                    generatorContext
            )
            response.outputStream.flush()
        } catch (Exception e) {
            // the response is committed: the failure can be told to the operator, not to the client
            log.error("Error generating application ${generatorContext.project.name}: ${e.message}", e)
            throw e
        }
    }
}

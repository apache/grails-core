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
import org.grails.forge.api.Relationship
import org.grails.forge.api.RequestInfo
import org.grails.forge.api.UserAgentParser
import org.grails.forge.api.preview.PreviewDTO
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.util.NameUtils
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper

@Controller
@GrailsCompileStatic
@Tag(name = 'preview', description = 'Previewing the files of an application before generating it')
class PreviewController {

    static allowedMethods = [previewApp: 'GET']

    @Autowired
    ProjectGenerator projectGenerator

    @Autowired
    GrailsForgeConfiguration grailsForgeConfiguration

    @Autowired
    JsonMapper jsonMapper

    @Operation(summary = 'Preview an application', description = 'The files of the application, of the type and name given, as it would be generated with the features and options requested.')
    @Parameter(name = 'features', in = ParameterIn.QUERY, description = ForgeRequestSupport.FEATURES_PARAMETER, array = @ArraySchema(schema = @Schema(type = 'string')))
    @ApiResponse(responseCode = '200', description = 'The files of the application, by path', content = @Content(mediaType = 'application/json', schema = @Schema(implementation = PreviewDTO)))
    @ApiResponse(responseCode = '400', description = ForgeRequestSupport.CLIENT_ERROR)
    def previewApp(@Parameter(description = ForgeRequestSupport.TYPE_PARAMETER) String type,
                   @Parameter(description = ForgeRequestSupport.NAME_PARAMETER) String name,
                   @Parameter(description = ForgeRequestSupport.RELOADING_PARAMETER) String reloading,
                   @Parameter(description = ForgeRequestSupport.GORM_PARAMETER) String gorm,
                   @Parameter(description = ForgeRequestSupport.SERVLET_PARAMETER) String servlet,
                   @Parameter(description = ForgeRequestSupport.JDK_PARAMETER) String javaVersion) {
        ApplicationType applicationType = ForgeRequestSupport.parseType(type)
        if (applicationType == null || !ForgeRequestSupport.validName(name, ForgeRequestSupport.CREATE_NAME_PATTERN)) {
            render status: 400
            return
        }
        RequestInfo requestInfo = ForgeRequestSupport.info(request, grailsForgeConfiguration)
        OperatingSystem operatingSystem = UserAgentParser.getOperatingSystem(requestInfo.userAgent)
        MapOutputHandler outputHandler = new MapOutputHandler()
        try {
            projectGenerator.generate(
                    applicationType,
                    NameUtils.parse(name),
                    ForgeRequestSupport.options(reloading, gorm, servlet, javaVersion, operatingSystem),
                    operatingSystem,
                    ForgeRequestSupport.featureList((List<String>) params.list('features')),
                    outputHandler,
                    ConsoleOutput.NOOP
            )
        } catch (IllegalArgumentException ignored) {
            render status: 400
            return
        }
        PreviewDTO previewDTO = new PreviewDTO(outputHandler.project)
        previewDTO.addLink(Relationship.CREATE, requestInfo.link(Relationship.CREATE, applicationType))
        previewDTO.addLink(Relationship.SELF, requestInfo.self())
        render text: jsonMapper.writeValueAsString(previewDTO), contentType: 'application/json'
    }
}

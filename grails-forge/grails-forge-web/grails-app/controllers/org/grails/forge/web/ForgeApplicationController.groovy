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
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.grails.forge.api.ApplicationTypeDTO
import org.grails.forge.api.ApplicationTypeList
import org.grails.forge.api.FeatureList
import org.grails.forge.api.ForgeApplicationService
import org.grails.forge.api.GrailsForgeConfiguration
import org.grails.forge.api.RequestInfo
import org.grails.forge.api.VersionDTO
import org.grails.forge.application.ApplicationType
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper

@Controller
@GrailsCompileStatic
@Tag(name = 'application types', description = 'The application types Forge generates, their features, and the versions they are generated with')
class ForgeApplicationController {

    static allowedMethods = [
            home: 'GET',
            versions: 'GET',
            list: 'GET',
            getType: 'GET',
            features: 'GET',
            defaultFeatures: 'GET'
    ]

    @Autowired
    ForgeApplicationService forgeApplicationService

    @Autowired
    GrailsForgeConfiguration grailsForgeConfiguration

    @Autowired
    JsonMapper jsonMapper

    @Operation(summary = 'How to use the API', description = 'A plain-text description of the API. A browser, asking for text/html, is redirected to the Forge UI.')
    @ApiResponse(responseCode = '200', description = 'The plain-text description', content = @Content(mediaType = 'text/plain', schema = @Schema(type = 'string')))
    @ApiResponse(responseCode = '301', description = 'The Forge UI, for a request accepting text/html')
    def home() {
        RequestInfo info = requestInfo()
        String accept = request.getHeader('Accept') ?: ''
        URI redirectUri = grailsForgeConfiguration.redirectUri().orElse(null)
        if (accept.contains('text/html') && redirectUri != null) {
            redirect url: redirectUri.toString(), permanent: true
            return
        }
        render text: forgeApplicationService.homeText(info), contentType: 'text/plain'
    }

    @Operation(summary = 'The versions generated applications use', description = 'The versions of the framework, the JDK and the managed dependencies applications are generated with.')
    @ApiResponse(responseCode = '200', description = 'The versions, by name', content = @Content(mediaType = 'application/json', schema = @Schema(implementation = VersionDTO)))
    def versions() {
        renderJson(forgeApplicationService.getInfo(requestInfo()))
    }

    @Operation(summary = 'The application types', description = 'Every application type Forge generates, with the links to generate and preview one.')
    @ApiResponse(responseCode = '200', description = 'The application types', content = @Content(mediaType = 'application/json', schema = @Schema(implementation = ApplicationTypeList)))
    def list() {
        renderJson(forgeApplicationService.list(requestInfo()))
    }

    @Operation(summary = 'An application type', description = 'An application type and every feature it can be generated with.')
    @ApiResponse(responseCode = '200', description = 'The application type', content = @Content(mediaType = 'application/json', schema = @Schema(implementation = ApplicationTypeDTO)))
    @ApiResponse(responseCode = '400', description = 'Unknown application type')
    def getType(@Parameter(description = ForgeRequestSupport.TYPE_PARAMETER) String type) {
        ApplicationType applicationType = ForgeRequestSupport.parseType(type)
        if (applicationType == null) {
            render status: 400
            return
        }
        renderJson(forgeApplicationService.getType(applicationType, requestInfo()))
    }

    @Operation(summary = 'The features of an application type', description = 'The features an application of the type can be generated with, for the options given.')
    @ApiResponse(responseCode = '200', description = 'The features', content = @Content(mediaType = 'application/json', schema = @Schema(implementation = FeatureList)))
    @ApiResponse(responseCode = '400', description = 'Unknown application type')
    def features(@Parameter(description = ForgeRequestSupport.TYPE_PARAMETER) String type,
                 @Parameter(description = ForgeRequestSupport.RELOADING_PARAMETER) String reloading,
                 @Parameter(description = ForgeRequestSupport.GORM_PARAMETER) String gorm,
                 @Parameter(description = ForgeRequestSupport.SERVLET_PARAMETER) String servlet,
                 @Parameter(description = ForgeRequestSupport.JDK_PARAMETER) String javaVersion) {
        ApplicationType applicationType = ForgeRequestSupport.parseType(type)
        if (applicationType == null) {
            render status: 400
            return
        }
        renderJson(forgeApplicationService.features(applicationType, requestInfo(),
                ForgeRequestSupport.featureFilter(reloading, gorm, servlet, javaVersion)))
    }

    @Operation(summary = 'The default features of an application type', description = 'The features an application of the type is generated with unless others are chosen, for the options given.')
    @ApiResponse(responseCode = '200', description = 'The default features', content = @Content(mediaType = 'application/json', schema = @Schema(implementation = FeatureList)))
    @ApiResponse(responseCode = '400', description = 'Unknown application type')
    def defaultFeatures(@Parameter(description = ForgeRequestSupport.TYPE_PARAMETER) String type,
                        @Parameter(description = ForgeRequestSupport.RELOADING_PARAMETER) String reloading,
                        @Parameter(description = ForgeRequestSupport.GORM_PARAMETER) String gorm,
                        @Parameter(description = ForgeRequestSupport.SERVLET_PARAMETER) String servlet,
                        @Parameter(description = ForgeRequestSupport.JDK_PARAMETER) String javaVersion) {
        ApplicationType applicationType = ForgeRequestSupport.parseType(type)
        if (applicationType == null) {
            render status: 400
            return
        }
        renderJson(forgeApplicationService.defaultFeatures(applicationType, requestInfo(),
                ForgeRequestSupport.featureFilter(reloading, gorm, servlet, javaVersion)))
    }

    private RequestInfo requestInfo() {
        ForgeRequestSupport.info(request, grailsForgeConfiguration)
    }

    private void renderJson(Object body) {
        render text: jsonMapper.writeValueAsString(body), contentType: 'application/json'
    }
}

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

import grails.web.Controller
import org.grails.forge.api.GrailsForgeConfiguration
import org.grails.forge.api.Relationship
import org.grails.forge.api.RequestInfo
import org.grails.forge.api.UserAgentParser
import org.grails.forge.api.preview.PreviewDTO
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.springframework.beans.factory.annotation.Autowired

@Controller
class PreviewController {

    static allowedMethods = [previewApp: 'GET']

    @Autowired
    ProjectGenerator projectGenerator

    @Autowired
    GrailsForgeConfiguration grailsForgeConfiguration

    def previewApp() {
        ApplicationType type = ForgeRequestSupport.parseType(params.type)
        if (type == null || !ForgeRequestSupport.validName(params.name, ForgeRequestSupport.CREATE_NAME_PATTERN)) {
            render status: 400
            return
        }
        RequestInfo requestInfo = ForgeRequestSupport.info(request, grailsForgeConfiguration)
        Project project = NameUtils.parse(params.name.toString())
        MapOutputHandler outputHandler = new MapOutputHandler()
        try {
            projectGenerator.generate(
                    type,
                    project,
                    new Options(
                            ForgeRequestSupport.parseEnum(DevelopmentReloading, params.reloading) ?: DevelopmentReloading.DEFAULT_OPTION,
                            GormImpl.parse(params.gorm?.toString()) ?: GormImpl.DEFAULT_OPTION,
                            ForgeRequestSupport.parseEnum(ServletImpl, params.servlet) ?: ServletImpl.DEFAULT_OPTION,
                            ForgeRequestSupport.parseJdk(params.javaVersion) ?: JdkVersion.DEFAULT_OPTION,
                            UserAgentParser.getOperatingSystem(requestInfo.userAgent)
                    ),
                    UserAgentParser.getOperatingSystem(requestInfo.userAgent),
                    ForgeRequestSupport.featureList(params),
                    outputHandler,
                    ConsoleOutput.NOOP
            )
        } catch (IllegalArgumentException ignored) {
            render status: 400
            return
        }
        PreviewDTO previewDTO = new PreviewDTO(outputHandler.project)
        previewDTO.addLink(Relationship.CREATE, requestInfo.link(Relationship.CREATE, type))
        previewDTO.addLink(Relationship.SELF, requestInfo.self())
        render text: ForgeRequestSupport.toJson(previewDTO), contentType: 'application/json'
    }
}

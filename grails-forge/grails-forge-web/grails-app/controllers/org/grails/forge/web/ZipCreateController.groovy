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
import org.grails.forge.api.create.CreateProjectService
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.io.ZipOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.ServletImpl
import org.springframework.beans.factory.annotation.Autowired

@Controller
class ZipCreateController {

    static allowedMethods = [createApp: 'GET', createZip: 'GET']

    @Autowired
    CreateProjectService createProjectService

    def createApp() {
        ApplicationType type = ForgeRequestSupport.parseType(params.type)
        if (type == null || !ForgeRequestSupport.validName(params.name, ForgeRequestSupport.CREATE_NAME_PATTERN)) {
            render status: 400
            return
        }
        writeZip(type, params.name.toString())
    }

    def createZip() {
        ApplicationType type = params.type ? ForgeRequestSupport.parseType(params.type) : ApplicationType.DEFAULT_OPTION
        if (type == null || !ForgeRequestSupport.validName(params.name, ForgeRequestSupport.ZIP_NAME_PATTERN)) {
            render status: 400
            return
        }
        writeZip(type, params.name.toString())
    }

    private void writeZip(ApplicationType type, String name) {
        try {
            GeneratorContext generatorContext = createProjectService.createProjectGeneratorContext(
                    type,
                    name,
                    ForgeRequestSupport.featureList(params),
                    ForgeRequestSupport.parseBuild(params.build),
                    ForgeRequestSupport.parseEnum(DevelopmentReloading, params.reloading),
                    GormImpl.parse(params.gorm?.toString()),
                    ForgeRequestSupport.parseEnum(ServletImpl, params.servlet),
                    ForgeRequestSupport.parseJdk(params.javaVersion),
                    request.getHeader('User-Agent')
            )
            String filename = generatorContext.project.name + '.zip'
            response.status = 201
            response.contentType = 'application/zip'
            response.setHeader('Content-Disposition', "attachment; filename=${filename}")
            createProjectService.projectGenerator.generate(
                    type,
                    generatorContext.project,
                    new ZipOutputHandler(generatorContext.project.name, response.outputStream),
                    generatorContext
            )
            response.outputStream.flush()
        } catch (IllegalArgumentException ignored) {
            render status: 400
        }
    }
}

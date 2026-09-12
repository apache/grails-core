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
import org.grails.forge.api.RequestInfo
import org.grails.forge.api.UserAgentParser
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.diff.FeatureDiffer
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.springframework.beans.factory.annotation.Autowired

@Controller
class DiffController {

    static allowedMethods = [diffFeature: 'GET', diffApp: 'GET']

    @Autowired
    ProjectGenerator projectGenerator

    @Autowired
    FeatureDiffer featureDiffer

    @Autowired
    GrailsForgeConfiguration grailsForgeConfiguration

    def diffFeature() {
        String diff = produceDiff([params.feature])
        if (diff == null) {
            render status: 400
            return
        }
        render text: diff, contentType: 'text/plain'
    }

    def diffApp() {
        String diff = produceDiff(params.list('features') ?: [])
        if (diff == null) {
            render status: 400
            return
        }
        render text: diff, contentType: 'text/plain'
    }

    private String produceDiff(List<String> features) {
        ApplicationType type = ForgeRequestSupport.parseType(params.type)
        if (type == null) {
            return null
        }
        RequestInfo requestInfo = ForgeRequestSupport.info(request, grailsForgeConfiguration)
        Project project = NameUtils.parse((params.name ?: 'example').toString())
        Options options = new Options(
                ForgeRequestSupport.parseEnum(DevelopmentReloading, params.reloading) ?: DevelopmentReloading.DEFAULT_OPTION,
                GormImpl.parse(params.gorm?.toString()) ?: GormImpl.DEFAULT_OPTION,
                ForgeRequestSupport.parseEnum(ServletImpl, params.servlet) ?: ServletImpl.DEFAULT_OPTION,
                ForgeRequestSupport.parseJdk(params.javaVersion) ?: JdkVersion.DEFAULT_OPTION
        )
        try {
            GeneratorContext generatorContext = projectGenerator.createGeneratorContext(
                    type,
                    project,
                    options,
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

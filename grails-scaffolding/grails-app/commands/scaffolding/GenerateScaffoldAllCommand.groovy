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
package scaffolding

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import grails.codegen.model.Model
import grails.dev.commands.GrailsApplicationCommand
import grails.plugin.scaffolding.CommandLineHelper
import grails.plugin.scaffolding.SkipBootstrap
import org.grails.io.support.Resource

/**
 * Generates a scaffolded service and controller.
 * Usage: <code>./gradlew runCommand "-Pargs=generate-scaffold-all [DOMAIN_CLASS_NAME]"</code>
 *
 * @author Scott Murphy Heiberg
 * @since 7.1.0
 */
@CompileStatic
class GenerateScaffoldAllCommand extends GrailsApplicationCommand implements CommandLineHelper, SkipBootstrap {

    String description = 'Generates a scaffolded service and controller'

    @Delegate
    ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    boolean handle() {
        final String domainClassName = args[0]
        if (!domainClassName) {
            error('No domain-class specified')
            return false
        }
        final Resource sourceClass = source(domainClassName)
        if (!sourceClass) {
            error("No domain-class found for name: ${domainClassName}")
            return false
        }
        boolean overwrite = isFlagPresent('force')
        final Model model = model(sourceClass)

        String namespace = flag('namespace')
        String serviceExtends = flag('serviceExtends')
        String controllerExtends = flag('controllerExtends')

        // Generate scaffolded service
        Map<String, Object> serviceTemplateModel = model.asMap()
        serviceTemplateModel.put('extendsClass', serviceExtends ?: '')
        serviceTemplateModel.put('extendsClassName', serviceExtends ? serviceExtends.substring(serviceExtends.lastIndexOf('.') + 1) : '')

        render(template: template('scaffolding/ScaffoldedService.groovy'),
                destination: file("grails-app/services/${model.packagePath}/${model.convention('Service')}.groovy"),
                model: serviceTemplateModel,
                overwrite: overwrite)
        verbose('Scaffold service created for domain class')

        // Generate scaffolded controller with service reference
        Map<String, Object> templateModel = model.asMap()
        templateModel.put('useService', true)
        templateModel.put('namespace', namespace ?: '')
        templateModel.put('extendsClass', controllerExtends ?: '')
        templateModel.put('extendsClassName', controllerExtends ? controllerExtends.substring(controllerExtends.lastIndexOf('.') + 1) : '')

        String controllerDestinationPath = "grails-app/controllers/${model.packagePath}"

        if (namespace) {
            controllerDestinationPath = "${controllerDestinationPath}/${namespace}"
        }

        render(template: template('scaffolding/ScaffoldedController.groovy'),
                destination: file("${controllerDestinationPath}/${model.convention('Controller')}.groovy"),
                model: templateModel,
                overwrite: overwrite)
        verbose('Scaffold controller created for domain class with service reference')

        return true
    }
}

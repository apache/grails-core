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
package org.grails.cli.profile.steps

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import grails.build.logging.GrailsConsole
import grails.util.GrailsNameUtils
import org.grails.cli.interactive.completers.ClassNameCompleter
import org.grails.cli.profile.AbstractStep
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.Profile
import org.grails.cli.profile.commands.templates.SimpleTemplate
import org.grails.cli.profile.support.ArtefactVariableResolver
import org.grails.io.support.Resource

/**
 * A {@link org.grails.cli.profile.Step} that renders a template
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 *
 * @since 3.0
 */
@InheritConstructors
@CompileStatic
class RenderStep extends AbstractStep {

    public static final String NAME = "render"
    public static final String TEMPLATES_DIR = "templates/"

    @Override
    @CompileStatic
    String getName() { NAME }

    @Override
    public boolean handle(ExecutionContext context) {
        def commandLine = context.getCommandLine()
        String nameAsArgument = commandLine.getRemainingArgs()[0]
        String artifactName
        String artifactPackage
        def nameAndPackage = resolveNameAndPackage(context, nameAsArgument)
        artifactName = nameAndPackage[0]
        artifactPackage = nameAndPackage[1]
        def variableResolver = new ArtefactVariableResolver(artifactName, (String) parameters.convention,artifactPackage)
        File destination = variableResolver.resolveFile(parameters.destination.toString(), context)

        try {

            String relPath = relativePath(context.baseDir, destination)
            if(destination.exists() && !flag(commandLine, 'force')) {
                context.console.error("${relPath} already exists.")
                return false
            }

            renderToDestination(destination, variableResolver.variables)
            context.console.addStatus("Created $relPath")

            return true
        } catch (Throwable e) {
            GrailsConsole.instance.error("Failed to render template to destination: ${e.message}", e)
            return false
        }
    }

    protected Resource searchTemplateDepthFirst(Profile profile, String template) {
        if(template.startsWith(TEMPLATES_DIR)) {
            return searchTemplateDepthFirst(profile, template.substring(TEMPLATES_DIR.length()))
        }
        Resource templateFile = profile.getTemplate(template)
        if(templateFile.exists()) {
            return templateFile
        } else {
            for(parent in profile.extends) {
                templateFile = searchTemplateDepthFirst(parent, template)
                if(templateFile) {
                    return templateFile
                }
            }
        }
        null
    }

    protected void renderToDestination(File destination, Map variables) {
        Profile profile = command.profile
        Resource templateFile = searchTemplateDepthFirst(profile, parameters.template.toString())
        if(!templateFile) {
            throw new IOException("cannot find template " + parameters.template)
        }
        destination.setText(new SimpleTemplate(templateFile.inputStream.getText(StandardCharsets.UTF_8.name())).render(variables), StandardCharsets.UTF_8.name())
        ClassNameCompleter.refreshAll()
    }

    protected List<String> resolveNameAndPackage(ExecutionContext context, String nameAsArgument) {
        List<String> parts = nameAsArgument.split(/\./) as List

        String artifactName
        String artifactPackage

        if(parts.size() == 1) {
            artifactName = parts[0]
            artifactPackage = context.navigateConfig('grails', 'codegen', 'defaultPackage')?:''
        } else {
            artifactName = parts[-1]
            artifactPackage = parts[0..-2].join('.')
        }

        [GrailsNameUtils.getClassName(artifactName), artifactPackage]
    }

    protected String relativePath(File relbase, File file) {
        def pathParts = []
        def currentFile = file
        while (currentFile != null && currentFile != relbase) {
            pathParts += currentFile.name
            currentFile = currentFile.parentFile
        }
        pathParts.reverse().join('/')
    }


}

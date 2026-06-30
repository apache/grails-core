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
package org.apache.grails.buildsrc

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.process.ExecSpec
import org.gradle.process.ExecOperations
import javax.inject.Inject

@CompileStatic
class GrailsIJFormatterPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        registerGitHooks(project)
        registerFormattingTasks(project)
    }

    private static void registerGitHooks(Project project) {
        if (project == project.rootProject) {
            project.tasks.register('installGitHooks', Copy) {
                it.group = 'verification'
                it.description = 'Installs the git pre-commit hook for automatic code formatting'
                it.from(project.rootProject.layout.projectDirectory.file('etc/hooks/pre-commit'))
                it.into(project.rootProject.layout.projectDirectory.dir('.git/hooks'))
                it.fileMode = 0755
            }
        }
    }

    private static void registerFormattingTasks(Project project) {
        ExecOperationsSupport execSupport = project.objects.newInstance(ExecOperationsSupport)
        def ideaExecProvider = project.providers.gradleProperty('idea.exec').orElse('idea')
        def formatFilesProvider = project.providers.gradleProperty('formatFiles')
        def rootProjectDir = project.rootProject.projectDir
        def projectDir = project.projectDir

        project.tasks.register('formatCode') { task ->
            task.group = 'verification'
            task.description = 'Formats Java and Groovy source files using the IntelliJ command line formatter'

            task.doLast {
                String ideaExec = ideaExecProvider.get()
                def filesToFormat = formatFilesProvider.getOrNull()
                def settingsFile = new File(rootProjectDir, '.idea/codeStyles/Project.xml')

                if (!settingsFile.exists()) {
                    throw new RuntimeException("IntelliJ code style settings not found at ${settingsFile.absolutePath}")
                }

                try {
                    execSupport.execOperations.exec { ExecSpec exec ->
                        exec.commandLine ideaExec
                        exec.args 'format'
                        exec.args '-s', settingsFile.absolutePath
                        exec.args '-mask', '*.java,*.groovy'
                        exec.args '-r'
                        if (filesToFormat) {
                            exec.args((filesToFormat.toString()).split(','))
                        } else {
                            exec.args projectDir.absolutePath
                        }
                    }
                } catch (Exception e) {
                    task.logger.error('IntelliJ formatter failed to execute: {}', e.message)
                    task.logger.error('Please ensure IntelliJ command line tools are installed and available on your PATH.')
                    task.logger.error('See: https://www.jetbrains.com/help/idea/working-with-the-ide-features-from-command-line.html')
                    throw new RuntimeException('IntelliJ formatter failed. See logs for details.', e)
                }
            }
        }
    }
}

interface ExecOperationsSupport {
    @Inject
    ExecOperations getExecOperations()
}

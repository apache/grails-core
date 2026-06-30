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
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.process.ExecResult
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
                it.filePermissions {
                    it.unix('0755')
                }
            }
        }
    }

    private static void registerFormattingTasks(Project project) {
        project.tasks.register('formatCode') { task ->
            task.description = 'Formats Java and Groovy source files using the IntelliJ command line formatter'

            ExecOperationsSupport execSupport = project.objects.newInstance(ExecOperationsSupport)
            Provider<String> formatExecProvider = project.providers.gradleProperty('format.exec')
            Provider<String> formatFilesProvider = project.providers.gradleProperty('formatFiles')

            Directory rootProjectDir = project.rootProject.layout.projectDirectory
            Directory projectDir = project.layout.projectDirectory
            File formatterHome = new File(project.gradle.gradleUserHomeDir, 'grails-ij-formatter')

            task.doLast {
                String formatExec = resolveFormatExecutable(formatExecProvider.getOrNull())
                String filesToFormat = formatFilesProvider.getOrNull()
                File settingsFile = rootProjectDir.file('.idea/codeStyles/Project.xml').getAsFile()

                if (!settingsFile.exists()) {
                    throw new GradleException("IntelliJ code style settings not found at ${settingsFile.absolutePath}")
                }

                // The formatter is the full IDE in headless mode, and the platform allows only one instance per
                // config/system directory. Point it at a private set of directories so it can run even while the
                // developer has IntelliJ IDEA open against the default ones.
                File ideaProperties = writeIsolatedInstanceProperties(formatterHome)

                ByteArrayOutputStream output = new ByteArrayOutputStream()
                ExecResult result
                try {
                    result = execSupport.execOperations.exec { ExecSpec exec ->
                        exec.commandLine formatExec
                        exec.args '-s', settingsFile.absolutePath
                        exec.args '-mask', '*.java,*.groovy'
                        exec.args '-r'
                        if (filesToFormat) {
                            exec.args(filesToFormat.split(','))
                        } else {
                            exec.args projectDir.getAsFile().absolutePath
                        }
                        exec.environment 'IDEA_PROPERTIES', ideaProperties.absolutePath
                        exec.standardOutput = output
                        exec.errorOutput = output
                        exec.ignoreExitValue = true
                    }
                } catch (Exception e) {
                    throw new GradleException("Failed to start the IntelliJ formatter '${formatExec}': ${e.message}", e)
                }

                String formatterOutput = output.toString()
                task.logger.info(formatterOutput)
                // With the isolated instance above this should not happen, but fail loudly rather than let a commit
                // proceed with unformatted code if the formatter still could not acquire its instance.
                if (formatterOutput.contains('Only one instance')) {
                    task.logger.error(formatterOutput)
                    throw new GradleException('The IntelliJ formatter could not start its own instance. ' +
                            'Close IntelliJ IDEA and try again, or format the files from within the IDE.')
                }
                if (result.exitValue != 0) {
                    task.logger.error(formatterOutput)
                    throw new GradleException("IntelliJ formatter exited with code ${result.exitValue}. See output above.")
                }
            }
        }
    }

    private static final List<String> UNIX_LAUNCHERS = ['idea.sh', 'idea'].asImmutable()
    private static final List<String> WINDOWS_LAUNCHERS = ['idea64.exe', 'idea.exe', 'idea.bat', 'idea.cmd'].asImmutable()

    /**
     * Resolves the IntelliJ command line formatter ({@code format.sh}/{@code format.bat}).
     * An explicit {@code -Pformat.exec} override wins; otherwise the script is looked up on the
     * PATH directly and then as a sibling of an {@code idea} launcher on the PATH, which matches
     * the standard install layout on Windows, macOS and Linux.
     */
    private static String resolveFormatExecutable(String override) {
        if (override) {
            return override
        }

        boolean windows = Os.isFamily(Os.FAMILY_WINDOWS)
        String formatScript = windows ? 'format.bat' : 'format.sh'

        File onPath = findOnPath(formatScript)
        if (onPath) {
            return onPath.absolutePath
        }

        List<String> launchers = windows ? WINDOWS_LAUNCHERS : UNIX_LAUNCHERS
        for (String launcher : launchers) {
            File found = findOnPath(launcher)
            if (found) {
                File sibling = new File(found.canonicalFile.parentFile, formatScript)
                if (sibling.isFile()) {
                    return sibling.absolutePath
                }
            }
        }

        throw new GradleException("Could not locate the IntelliJ command line formatter (${formatScript}).\n" +
                "Add the IDE's bin directory to your PATH, or point 'format.exec' at it.\n" +
                'The format script lives in the IDE bin directory, for example:\n' +
                '  macOS:   <IDE>.app/Contents/bin/format.sh\n' +
                '  Linux:   <IDE>/bin/format.sh\n' +
                '  Windows: <IDE>\\bin\\format.bat\n' +
                'Set it per invocation with -Pformat.exec=/path/to/' + formatScript + ', or persist it (recommended,\n' +
                "and required for the git pre-commit hook) by adding 'format.exec=/path/to/${formatScript}' to\n" +
                '~/.gradle/gradle.properties.\n' +
                'See: https://www.jetbrains.com/help/idea/command-line-formatter.html')
    }

    /**
     * Creates a private set of IntelliJ config/system/plugins/log directories and writes an
     * {@code idea.properties} file pointing at them. Passing this file via the {@code IDEA_PROPERTIES}
     * environment variable lets the formatter run as an isolated instance that does not collide with a
     * running IDE. The directories are reused between runs to avoid re-initialising on every commit.
     */
    private static File writeIsolatedInstanceProperties(File home) {
        File config = new File(home, 'config')
        File system = new File(home, 'system')
        File plugins = new File(home, 'plugins')
        File log = new File(home, 'log')
        for (File dir : [config, system, plugins, log]) {
            dir.mkdirs()
        }
        Properties props = new Properties()
        props.setProperty('idea.config.path', config.absolutePath)
        props.setProperty('idea.system.path', system.absolutePath)
        props.setProperty('idea.plugins.path', plugins.absolutePath)
        props.setProperty('idea.log.path', log.absolutePath)
        File propsFile = new File(home, 'idea.properties')
        OutputStream out = new FileOutputStream(propsFile)
        try {
            props.store(out, 'Isolated IntelliJ instance for command line formatting')
        } finally {
            out.close()
        }
        return propsFile
    }

    private static File findOnPath(String name) {
        String pathEnv = System.getenv('PATH')
        if (!pathEnv) {
            return null
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (!dir) {
                continue
            }
            File candidate = new File(dir, name)
            if (candidate.isFile()) {
                return candidate
            }
        }
        return null
    }
}

interface ExecOperationsSupport {
    @Inject
    ExecOperations getExecOperations()
}

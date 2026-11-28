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
package org.grails.forge.cli.command

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ConsoleProject
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.console.ProjectCommand
import org.apache.grails.forge.cli.console.UsageHelp
import org.apache.grails.forge.cli.health.HealthContext
import org.apache.grails.forge.cli.health.HealthDocument
import org.apache.grails.forge.cli.health.HealthFinding
import org.apache.grails.forge.cli.health.HealthLevel
import org.apache.grails.forge.cli.health.ProjectHealth
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.util.VersionInfo
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.function.Function

/**
 * Says what the files of the project in the current directory show of its state: what makes its
 * build fail, what the upgrade notes ask for and is not done, and what the guide recommends. It reads
 * the project as text and changes nothing; it runs no Gradle, no generator and nothing of the network.
 * It is run in a project that is still on Grails 7 or 8 as well, where it says what Grails 9 asks of it.
 *
 * @since 9.0
 */
@CommandLine.Command(name = ProjectHealthCommand.NAME, description = [
        'Says what the files of the project in the current directory show of its state: what makes its build fail (error), what the upgrade notes ask for ' +
                'and is not done or what is deprecated (warning), and what the guide recommends (advice), each with what to do.',
        'The project is read as text and nothing is changed: no Gradle, no generator and nothing of the network is run. A project still on Grails 7 or 8 ' +
                'is checked for what Grails 9 asks of it.',
        'The command ends with 1 when there is an error, and with --strict when there is a warning too; advice never fails it.'])
@Component
@Scope('prototype')
@CompileStatic
class ProjectHealthCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    public static final String NAME = 'health'

    @CommandLine.Option(names = ['--json'], description = 'Write every finding as one JSON document for a tool to read, with the exit code the command has without it.')
    boolean json

    @CommandLine.Option(names = ['--strict'], description = 'End with 1 when there is a warning, as when there is an error: for a job that holds a project to what the upgrade notes ask.')
    boolean strict

    @Override
    Integer call() throws Exception {
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject.Search search = ConsoleProject.search(current)
        String typed = "${ProjectGroupCommand.NAME} ${NAME}"
        if (search.project == null) {
            if (json) {
                return written(HealthDocument.refused(search.untrusted, search.none(typed), strict), 1)
            }
            err(search.none(typed))
            return 1
        }
        HealthContext context = new HealthContext(search.project, Path.of(System.getProperty('user.home')), File.separatorChar == ('\\' as char),
                { String command -> UsageHelp.toType(spec, command) } as Function<String, String>)
        ProjectHealth health
        try {
            health = ProjectHealth.of(context)
        } catch (IOException | RuntimeException e) {
            String message = GradleRunner.line(e.getMessage() ?: e.getClass().getSimpleName())
            if (showStacktrace()) {
                e.printStackTrace(spec.commandLine().getErr())
            }
            if (json) {
                return written(HealthDocument.failed(search.project.directory, message, strict), 1)
            }
            err(message)
            return 1
        }
        List<HealthFinding> failing = health.failing(strict)
        if (json) {
            return written(HealthDocument.checked(search.project.directory, context.projectVersion, health, strict), failing.isEmpty() ? 0 : 1)
        }
        report(context, health)
        if (!failing.isEmpty()) {
            // what fails the command, by the ids of the findings
            List<String> counts = []
            if (health.count(HealthLevel.ERROR) > 0) {
                counts << counted(health.count(HealthLevel.ERROR), 'error')
            }
            if (strict && health.count(HealthLevel.WARNING) > 0) {
                counts << counted(health.count(HealthLevel.WARNING), 'warning')
            }
            err("${typed}${strict ? ' --strict' : ''} found ${counts.join(' and ')}: ${failing*.id.unique().join(', ')}")
            return 1
        }
        return 0
    }

    /**
     * @param spec the command that says it
     * @return the line that names this command, for the commands that end a step of an upgrade
     */
    static String advice(CommandLine.Model.CommandSpec spec) {
        return "${UsageHelp.toType(spec, "${ProjectGroupCommand.NAME} ${NAME}")} says what else the upgrade notes ask of the project, as far as its files show it"
    }

    private void report(HealthContext context, ProjectHealth health) {
        String version = context.projectVersion == null ? 'a version of Grails gradle.properties does not name' : "Grails ${context.projectVersion} (gradle.properties)"
        out("@|blue ||@ Project ${shown(projectName(context))} on ${shown(version)}, checked by the command line of Grails ${shown(VersionInfo.getGrailsVersion())}, offline")
        List<HealthFinding> shown = health.findings.findAll { HealthFinding each -> each.level != HealthLevel.OK || verbose() }
        int width = (shown.collect { HealthFinding each -> each.id.length() }.max() ?: 0) as int
        for (HealthFinding finding : shown) {
            out("@|${finding.level.style} | ${finding.level.label.padRight(7)}|@ ${finding.id.padRight(width)}  ${finding.message}" +
                    (finding.fix == null ? '' : ". Fix: ${finding.fix}"))
        }
        int ok = health.count(HealthLevel.OK)
        out("@|blue ||@ ${counted(ok, 'check')} OK${ok > 0 && !verbose() ? ' (-v lists them)' : ''}. ${counted(health.count(HealthLevel.ERROR), 'error')}, " +
                "${counted(health.count(HealthLevel.WARNING), 'warning')}, ${health.count(HealthLevel.ADVICE)} advice, ${health.count(HealthLevel.SKIPPED)} skipped")
    }

    // the name of the application as its record has it, or as its settings name the build, or the name of its directory
    private static String projectName(HealthContext context) {
        return context.selection?.manifest?.name ?: context.evidence.rootProjectName ?: context.directory.getFileName()?.toString() ?: context.directory.toString()
    }

    private static String counted(int count, String what) {
        return "${count} ${what}${count == 1 ? '' : 's'}"
    }

    private static String shown(String text) {
        return GradleRunner.line(text)
    }

    private int written(HealthDocument document, int exitCode) {
        // written as it is: a document is not markup of the command line
        spec.commandLine().getOut().println(document.toJson())
        return exitCode
    }
}

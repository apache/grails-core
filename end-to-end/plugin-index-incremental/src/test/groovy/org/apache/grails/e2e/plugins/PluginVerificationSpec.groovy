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
package org.apache.grails.e2e.plugins

import org.apache.grails.e2e.beans.PluginIndexFixture

/**
 * The build of an application fails on the plugins that would stop it from starting, with the reason the
 * application would give, and passes once they would not.
 */
class PluginVerificationSpec extends PluginIndexFixture {

    void 'check fails on a plugin whose dependency is missing, and passes once it only comes after it'() {
        given: 'a plugin of the application that depends on a plugin no dependency provides'
        File sources = new File(projectDir, 'src/main/groovy/demo')
        sources.mkdirs()
        File plugin = new File(sources, 'ReportingGrailsPlugin.groovy')
        plugin.text = descriptor("dependsOn = ['absentReports']")

        when:
        def failed = run('check', '-x', 'test')

        then: 'the build names the plugin, the dependency and what to do, as startup would'
        failed.exitCode != 0
        failed.output.contains('The Grails plugins of this project would stop its application from starting:')
        failed.output.contains('In the development, test, production environments: ')
        failed.output.contains('Grails plug-in [reporting]')
        failed.output.contains('dependency [absentReports] with required version [*] is missing')
        failed.output.contains('name it in after instead of dependsOn')

        when: 'the plugin only has to come after it when it is present'
        plugin.text = descriptor("after = ['absentReports']")
        def passed = run('check', '-x', 'test')

        then:
        passed.exitCode == 0
        File report = new File(projectDir, 'build/grails/plugin-order.txt')
        report.readLines()*.replaceAll(':.*', '') == ['development', 'test', 'production']
        report.readLines().every { it.contains('core') && it.contains('reporting') }
    }

    private static String descriptor(String attributes) {
        """
            package demo
            import grails.plugins.Plugin
            import grails.plugins.metadata.GrailsPluginDescriptor

            @GrailsPluginDescriptor(name = 'reporting', ${attributes})
            class ReportingGrailsPlugin extends Plugin { }
        """
    }

    private Map run(String... tasks) {
        List<String> args = [System.getProperty('grails.e2e.gradlew'), '-p', projectDir.absolutePath, '--max-workers=1']
        if (Boolean.getBoolean('grails.e2e.offline')) {
            args.add('--offline')
        }
        args.addAll(tasks)
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start()
        String output = process.inputStream.getText('UTF-8')
        [exitCode: process.waitFor(), output: output]
    }
}

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
package org.grails.forge.build.gradle

import org.grails.forge.template.Writable
import spock.lang.Specification

/**
 * What the plugins of a build add to the build file beside their own line is the same text whichever
 * plugin was added first and whatever the platform ends a line with.
 */
class GradleBuildPluginLinesSpec extends Specification {

    void "the import lines of the plugins are in the order of the lines, each once, and end with a line feed"() {
        given:
        List<String> imports = (1..40).collect { int number -> "import com.acme.build.Type${number}".toString() }
        List<GradlePlugin> plugins = [
                GradlePlugin.builder().id('com.acme.second').buildImports(imports.reverse().take(25) as String[]).build(),
                GradlePlugin.builder().id('com.acme.first').buildImports(imports.take(25) as String[]).build()
        ]

        when:
        Set<String> lines = new GradleBuild(GradleDsl.GROOVY, [], [], plugins, [], []).getPluginsImports()
        Set<String> reversed = new GradleBuild(GradleDsl.GROOVY, [], [], plugins.reverse(), [], []).getPluginsImports()

        then:
        lines.toList() == imports.toSorted().collect { String line -> line + '\n' }
        reversed.toList() == lines.toList()
    }

    void "what a plugin adds to the build file and to the settings file ends with a line feed"() {
        given:
        Writable block = { OutputStream out -> out.write('acme {\n}'.getBytes('UTF-8')) } as Writable
        Writable settings = { OutputStream out -> out.write('// acme'.getBytes('UTF-8')) } as Writable
        GradleBuild build = new GradleBuild(GradleDsl.GROOVY, [], [],
                [GradlePlugin.builder().id('com.acme.first').extension(block).settingsExtension(settings).build(),
                 GradlePlugin.builder().id('com.acme.second').extension(block).build()], [], [])

        expect:
        build.renderExtensions() == 'acme {\n}\nacme {\n}\n'
        build.renderSettingsExtensions() == '// acme\n'
    }
}

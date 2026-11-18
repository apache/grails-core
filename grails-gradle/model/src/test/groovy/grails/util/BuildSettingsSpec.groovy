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
package grails.util

import spock.lang.Specification
import spock.lang.TempDir

class BuildSettingsSpec extends Specification {

    @TempDir
    File tmp

    void 'the resources directory is #description, whatever the working directory'() {
        given: 'an application, and a working directory next to it, as a build that sets workingDir has'
        File app = new File(tmp, 'app')
        new File(app, 'grails-app').mkdirs()
        new File(app, 'build-parent/build-8070/resources/main').mkdirs()
        new File(app, 'build/resources/main').mkdirs()
        File elsewhere = new File(tmp, 'elsewhere')
        elsewhere.mkdirs()
        File absolute = new File(tmp, 'resources')
        absolute.mkdirs()
        String fromSystem = property == null ? null : "-Dgrails.project.resource.dir=${property.replace('<absolute>', absolute.absolutePath)}"

        when:
        Map<String, String> printed = printBuildSettings(elsewhere, "-Dbase.dir=${app.absolutePath}", fromSystem)

        then:
        printed.RESOURCES_DIR == (expected == '<absolute>' ? absolute : new File(app, expected)).canonicalPath

        where:
        description                                        | property                                 | expected
        'the application\'s when the property is relative' | 'build-parent/build-8070/resources/main' | 'build-parent/build-8070/resources/main'
        'the property when it is absolute'                 | '<absolute>'                             | '<absolute>'
        'the application\'s build/ without the property'   | null                                     | 'build/resources/main'
    }

    void 'the target directory is #description, whatever the working directory'() {
        given:
        File app = new File(tmp, 'app')
        new File(app, 'grails-app').mkdirs()
        File elsewhere = new File(tmp, 'elsewhere')
        elsewhere.mkdirs()
        File absolute = new File(tmp, 'out')
        List<String> fromSystem = properties.collect { it.replace('<absolute>', absolute.absolutePath) }

        when:
        Map<String, String> printed = printBuildSettings(elsewhere, "-Dbase.dir=${app.absolutePath}", *fromSystem)

        then:
        printed.TARGET_DIR == (expected == '<absolute>' ? absolute : new File(app, expected)).canonicalPath

        where:
        description                                          | properties                                                                       | expected
        'the application\'s when the property is relative'    | ['-Dgrails.project.target.dir=build-parent/build-8070']                          | 'build-parent/build-8070'
        'the property when it is absolute'                   | ['-Dgrails.project.target.dir=<absolute>']                                       | '<absolute>'
        'project.target.dir without the property'            | ['-Dproject.target.dir=out']                                                     | 'out'
        'project.target.dir, set by hand, over the property' | ['-Dgrails.project.target.dir=build-parent/build-8070', '-Dproject.target.dir=out'] | 'out'
        'the application\'s build/ without either'            | []                                                                               | 'build'
    }

    void 'development keeps its restart marker in the build directory it is given, not build/'() {
        given: 'an application with its build directory moved and no build/'
        File app = new File(tmp, 'app')
        new File(app, 'grails-app').mkdirs()
        File buildDir = new File(app, 'build-parent/build-8070')
        buildDir.mkdirs()

        when:
        String output = runBuildSettings(app, ["-Dbase.dir=${app.absolutePath}".toString(), '-Dgrails.env=development',
                '-Dgrails.project.target.dir=build-parent/build-8070'], ['devtools'])

        then:
        output.contains('DEVTOOLS_RESTART=false')
        new File(buildDir, '.grailspid').text.contains('@')
        !new File(app, 'build').exists()
        !output.contains('unable to write pid file')
    }

    private static Map<String, String> printBuildSettings(File workingDir, String... properties) {
        runBuildSettings(workingDir, properties.findAll() as List<String>, []).readLines()
                .findAll { it.contains('=') }
                .collectEntries { String line ->
                    int split = line.indexOf('=')
                    [(line.substring(0, split)): line.substring(split + 1)]
                }
    }

    /** Runs {@link BuildSettingsPrinter} in a JVM of its own, as {@code BuildSettings} resolves once, when it loads. */
    private static String runBuildSettings(File workingDir, List<String> properties, List<String> args) {
        String java = new File(System.getProperty('java.home'), 'bin/java').absolutePath
        List<String> command = [java, '-cp', System.getProperty('java.class.path')]
        command.addAll(properties)
        command << BuildSettingsPrinter.name
        command.addAll(args)
        Process process = new ProcessBuilder(command).directory(workingDir).redirectErrorStream(true).start()
        String output = process.inputStream.text
        assert process.waitFor() == 0: output
        output
    }
}

/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file to You under
 *  the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.ui.script

import grails.ui.command.GrailsApplicationContextCommandRunner
import spock.lang.Specification

/**
 * Tests verifying that {@link GrailsApplicationScriptRunner} filters command options
 * before passing args to Spring Boot, using the same
 * {@link GrailsApplicationContextCommandRunner#filterCommandOptions(String[])} method.
 *
 * <p>Detailed filter behavior tests are in
 * {@link grails.ui.command.GrailsApplicationContextCommandRunnerSpec}.</p>
 */
class GrailsApplicationScriptRunnerSpec extends Specification {

    def "filterCommandOptions is accessible from GrailsApplicationContextCommandRunner"() {
        given: 'args as they might arrive from runScript Gradle task'
        String[] args = ['myscript.groovy', '--someOption=value', 'com.example.Application'] as String[]

        when:
        String[] filtered = GrailsApplicationContextCommandRunner.filterCommandOptions(args)

        then: 'script names and application class preserved, options removed'
        filtered == ['myscript.groovy', 'com.example.Application'] as String[]
    }

    def "filterCommandOptions handles script args with no options"() {
        given:
        String[] args = ['myscript.groovy', 'com.example.Application'] as String[]

        when:
        String[] filtered = GrailsApplicationContextCommandRunner.filterCommandOptions(args)

        then:
        filtered == ['myscript.groovy', 'com.example.Application'] as String[]
    }
}

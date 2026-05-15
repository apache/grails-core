/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the 'License'); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an 'AS IS' BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.ui.command

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for {@link GrailsApplicationContextCommandRunner#filterCommandOptions(String[])}.
 *
 * Verifies that command-specific options (--key=value, --flag) are filtered out
 * before being passed to Spring Boot's SpringApplication.run(), while preserving
 * non-option arguments like the command name.
 */
class GrailsApplicationContextCommandRunnerSpec extends Specification {

    @Unroll
    def "filterCommandOptions filters '#description'"() {
        expect:
        GrailsApplicationContextCommandRunner.filterCommandOptions(input as String[]) == expected as String[]

        where:
        description                              | input                                                    | expected
        'single --dataSource option'             | ['dbm-status', '--dataSource=analytics']                 | ['dbm-status']
        'multiple command options'               | ['dbm-status', '--dataSource=analytics', '--contexts=dev'] | ['dbm-status']
        'no options (passthrough)'               | ['dbm-status']                                           | ['dbm-status']
        'empty args'                             | []                                                       | []
        'only options (no command)'              | ['--dataSource=analytics']                               | []
        'mixed options and positional args'      | ['dbm-gorm-diff', '--dataSource=analytics', 'output.groovy'] | ['dbm-gorm-diff', 'output.groovy']
        'flag without value'                     | ['dbm-update', '--verbose']                              | ['dbm-update']
        'single dash args preserved'             | ['dbm-status', '-v', '--dataSource=analytics']           | ['dbm-status', '-v']
        // Database Migration Plugin: --contexts and --defaultSchema
        'database migration --contexts option'   | ['dbm-update', '--contexts=production']                  | ['dbm-update']
        'database migration --defaultSchema'     | ['dbm-update', '--defaultSchema=myschema']               | ['dbm-update']
        'all three dbm options together'         | ['dbm-gorm-diff', '--dataSource=analytics', '--contexts=dev', '--defaultSchema=public'] | ['dbm-gorm-diff']
        'dbm command with positional + options'  | ['dbm-gorm-diff', 'output.groovy', '--dataSource=analytics', '--add'] | ['dbm-gorm-diff', 'output.groovy']
        // Spring Security Plugin: --groupClassName
        'spring security --groupClassName'       | ['s2-quickstart', 'com.example', 'User', 'Role', '--groupClassName=RoleGroup'] | ['s2-quickstart', 'com.example', 'User', 'Role']
        // Script runner: --option in script args
        'script runner with --option'            | ['myscript.groovy', '--someOption=value']                | ['myscript.groovy']
    }

    def 'filterCommandOptions preserves argument order'() {
        given:
        String[] input = ['first', 'second', '--removed', 'third'] as String[]

        when:
        String[] result = GrailsApplicationContextCommandRunner.filterCommandOptions(input)

        then:
        result == ['first', 'second', 'third'] as String[]
    }

    def 'filterCommandOptions returns new array instance'() {
        given:
        String[] input = ['dbm-status'] as String[]

        when:
        String[] result = GrailsApplicationContextCommandRunner.filterCommandOptions(input)

        then:
        !result.is(input)
        result == input
    }

    def 'filterCommandOptions handles typical Gradle dbm task args'() {
        given: 'args as they arrive from ApplicationContextCommandTask via GrailsGradlePlugin'
        // configureApplicationCommands() passes: [commandName, ...userArgs, applicationClassName]
        // main() extracts: commandName = args[0], appClass = args.last()
        // run() receives: args.init() = [commandName, ...userArgs]
        String[] runArgs = ['dbm-status', '--dataSource=analytics'] as String[]

        when:
        String[] springBootArgs = GrailsApplicationContextCommandRunner.filterCommandOptions(runArgs)

        then: 'only the command name reaches Spring Boot'
        springBootArgs == ['dbm-status'] as String[]
        springBootArgs.length == 1
    }

    def 'filterCommandOptions prevents dataSource property corruption'() {
        given: 'the problematic args that caused the original bug'
        String[] args = ['dbm-gorm-diff', '--dataSource=analytics', '--contexts=production'] as String[]

        when:
        String[] filtered = GrailsApplicationContextCommandRunner.filterCommandOptions(args)

        then: 'no --key=value args remain to corrupt Spring Boot properties'
        filtered.every { !it.startsWith('--') }
        filtered == ['dbm-gorm-diff'] as String[]
    }

    def 'filterCommandOptions handles null elements in args'() {
        given:
        String[] input = ['dbm-status', null, '--dataSource=analytics'] as String[]

        when:
        String[] result = GrailsApplicationContextCommandRunner.filterCommandOptions(input)

        then:
        noExceptionThrown()
        result == ['dbm-status'] as String[]
    }

    def 'filterCommandOptions protects all known Grails ecosystem command options'() {
        expect: 'every known --option from Grails plugins is filtered'
        GrailsApplicationContextCommandRunner.filterCommandOptions(input as String[]) == expected as String[]

        where:
        input                                                                | expected
        // Database Migration Plugin (30+ commands accept these)
        ['dbm-status', '--dataSource=analytics']                             | ['dbm-status']
        ['dbm-update', '--contexts=production,staging']                      | ['dbm-update']
        ['dbm-gorm-diff', '--defaultSchema=public']                          | ['dbm-gorm-diff']
        ['dbm-status', '--verbose']                                          | ['dbm-status']
        ['dbm-diff', '--add']                                                | ['dbm-diff']
        // Spring Security Plugin (s2-quickstart)
        ['s2-quickstart', 'com.example', 'User', 'Role', '--groupClassName=RoleGroup'] | ['s2-quickstart', 'com.example', 'User', 'Role']
        // Custom ApplicationCommand with arbitrary --options
        ['my-command', '--customFlag', '--anotherOption=value']              | ['my-command']
    }
}

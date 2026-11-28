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
package org.grails.forge.cli

import org.grails.forge.application.ApplicationType
import spock.lang.Unroll

/**
 * What the command line does in a directory whose {@code grails-forge-cli.yml} is not one Forge wrote:
 * the file is whatever a clone holds, and every command that has to do with a project reads it.
 */
class CodeGenConfigEarlierRecordSpec extends CommandSpec implements CommandFixture {

    static final String RECORD = 'applicationType: web\ndefaultPackage: com.example\nreloading: devtools\nsourceLanguage: groovy\n' +
            'features: [gorm-hibernate5, grails-quartz]\n'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(out))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "a record as Forge writes it gives the directory its commands, without a word"() {
        given:
        new File(dir, 'grails-forge-cli.yml').setText(RECORD, 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, '--help')

        then:
        exit == 0
        out.toString().contains('create-controller')
        out.toString().contains('create-job')
        !out.toString().contains('Warning')
    }

    @Unroll
    void "a record that cannot be used is said to be in one line, and the directory is one without a project: #what"() {
        given:
        new File(dir, 'grails-forge-cli.yml').setText(content(), 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, '--help')
        List<String> warnings = out.toString().readLines().findAll { it.contains('Warning') }

        then: 'help works, and says why the commands of a project are not there'
        exit == 0
        out.toString().contains('create-app')
        !out.toString().contains('create-controller')
        warnings.size() == 1
        warnings[0].contains(message)
        warnings[0].endsWith('; the commands that create the classes of an application are not there without it')
        warnings[0].length() < 600

        and: 'nothing of a stack trace, and nothing the file says that a terminal or the command line would act on'
        !out.toString().contains('Exception')
        !out.toString().contains('\tat ')
        !out.toString().contains('\u001b[35;5m')
        !out.toString().contains('@|red')

        when: 'a command of a project is asked for'
        out.reset()
        int command = ForgeCli.execute(beanContext, 'create-controller', 'Book')

        then:
        command != 0
        !out.toString().contains('Exception')
        !new File(dir, 'grails-app').exists()

        where:
        what | content | message
        'a type of application there is not' | { RECORD.replace('applicationType: web', 'applicationType: nonsense') } |
                "grails-forge-cli.yml: applicationType is not a type of application, but 'nonsense'"
        'not YAML' | { 'features: [a\n' } | 'grails-forge-cli.yml is not YAML that can be read: '
        'larger than a record is' | { RECORD + ('# ' + ('x' * 100) + '\n') * 40_000 } |
                'grails-forge-cli.yml is larger than it can be: it has '
        'nothing of a record' | { 'format: 1\n' } | 'grails-forge-cli.yml: defaultPackage is missing'
        'no type of application' | { 'defaultPackage: com.example\nfeatures: []\n' } | 'grails-forge-cli.yml: applicationType is missing'
        'no features' | { 'applicationType: web\ndefaultPackage: com.example\n' } | 'grails-forge-cli.yml: features is missing'
        'a value with an escape sequence and markup' | { RECORD.replace('reloading: devtools', 'reloading: "@|red r|@\\e[35;5mX"') } |
                'grails-forge-cli.yml: reloading must be one line of at most 255 characters, none of them a control character or one that is not shown'
        'a way of reloading there is not' | { RECORD.replace('reloading: devtools', 'reloading: hotswap') } |
                "grails-forge-cli.yml: reloading is not a way an application reloads, but 'hotswap'"
        'a language there is not' | { RECORD.replace('sourceLanguage: groovy', 'sourceLanguage: cobol') } |
                "grails-forge-cli.yml: sourceLanguage is not a language of an application, but 'cobol'"
        'a package that is a path' | { RECORD.replace('defaultPackage: com.example', 'defaultPackage: ../../etc') } |
                'grails-forge-cli.yml: defaultPackage is not the name of a package'
        'a feature that is no name' | { RECORD.replace('grails-quartz]', "'@|red x|@']") } |
                'grails-forge-cli.yml: features has an entry that is not the name of a feature'
        'features that are no list' | { RECORD.replace('[gorm-hibernate5, grails-quartz]', 'all') } |
                'grails-forge-cli.yml: features must be a list of at most 1000 names of features'
        'an anchor and an alias' | { RECORD + 'a: &x b\nc: *x\n' } |
                'grails-forge-cli.yml names a value with an anchor (&), which the state of a project does not use'
        'a tag' | { RECORD.replace('defaultPackage: com.example', 'defaultPackage: !!java.net.URL com.example') } |
                "grails-forge-cli.yml gives a value the tag 'tag:yaml.org,2002:java.net.URL'"
        'one line longer than a record has' | { RECORD + 'x: ' + ('c' * 70_000) + '\n' } |
                'grails-forge-cli.yml has a line of more than 65536 characters'
        'more lines than a record has' | { RECORD + ('a:\n' * 9000) } | 'grails-forge-cli.yml has more than 8192 lines'
    }

    void "a project whose state is refused and whose earlier record cannot be used is said to be both, and help works"() {
        given:
        generateProject(ApplicationType.WEB)
        File lock = new File(dir, '.grails/forge.lock.yml')
        lock.append('\n', 'UTF-8')
        new File(dir, 'grails-forge-cli.yml').setText('applicationType: nonsense\n', 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, '--help')
        List<String> warnings = out.toString().readLines().findAll { it.contains('Warning') }

        then:
        exit == 0
        warnings.size() == 2
        warnings[0].contains('.grails/forge.lock.yml was changed after it was written: it does not have the digest it ends with')
        warnings[1].contains("grails-forge-cli.yml: applicationType is not a type of application, but 'nonsense'")
        !out.toString().contains('Exception')
        !out.toString().contains('create-controller')
    }

    void "a project whose state is refused goes by the earlier record it has beside it"() {
        given: 'a generated project, which has no earlier record, and one put beside its state'
        generateProject(ApplicationType.WEB)
        assert !new File(dir, 'grails-forge-cli.yml').exists()
        new File(dir, 'grails-forge-cli.yml').setText(RECORD, 'UTF-8')
        new File(dir, '.grails/forge.lock.yml').append('\n', 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, '--help')

        then:
        exit == 0
        out.toString().readLines().findAll { it.contains('Warning') }.size() == 1
        out.toString().contains('create-controller')
    }
}

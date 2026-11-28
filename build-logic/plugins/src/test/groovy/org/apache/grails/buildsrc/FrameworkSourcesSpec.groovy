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

import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

@Requires({ GitCheckout.available })
class FrameworkSourcesSpec extends Specification {

    @TempDir
    File tmp

    GitCheckout checkout

    def setup() {
        checkout = new GitCheckout(new File(tmp, 'checkout'))
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A {}')
        checkout.write('grails-gradle/plugins/P.groovy', 'class P {}')
        checkout.write('grails-forge/grails-forge-cli/C.groovy', 'class C {}')
        checkout.write('dependencies.gradle', 'ext {}')
        checkout.write('README.md', 'read me')
        checkout.write('.gitignore', 'build/\n')
        checkout.commit('first')
    }

    void "the sources are the entries of the top directory the framework is built from, as Git names them"() {
        when:
        FrameworkSources sources = FrameworkSources.of(checkout.directory)

        then: 'Forge and the Markdown files are no part of them'
        sources.entries.keySet() as List == ['.gitignore', 'dependencies.gradle', 'grails-core', 'grails-gradle']
        sources.commit == checkout.head()
        sources.uncommitted == 0
        sources.fingerprint ==~ /[0-9a-f]{64}/

        and: 'another checkout of the same commit has the same'
        FrameworkSources.of(checkout.cloneTo(new File(tmp, 'clone')).directory).fingerprint == sources.fingerprint
    }

    void "a change in Forge, in the docs or in what Git ignores leaves the sources as they are, committed or not"() {
        given:
        String before = FrameworkSources.of(checkout.directory).fingerprint

        when: 'not committed'
        checkout.write('grails-forge/grails-forge-cli/C.groovy', 'class C { int changed }')
        checkout.write('grails-forge/grails-forge-cli/New.groovy', 'class New {}')
        checkout.write('README.md', 'read me again')
        checkout.write('grails-core/build/classes/A.class', 'compiled')

        then:
        FrameworkSources.of(checkout.directory).fingerprint == before
        FrameworkSources.of(checkout.directory).uncommitted == 0

        when: 'committed, so that the commit is another one'
        checkout.commit('forge only')

        then:
        FrameworkSources.of(checkout.directory).fingerprint == before
    }

    void "a change of the framework is told, and where: #change"() {
        given:
        FrameworkSources before = FrameworkSources.of(checkout.directory)

        when:
        if (content == null) {
            assert new File(checkout.directory, path).delete()
        } else {
            checkout.write(path, content)
        }
        FrameworkSources after = FrameworkSources.of(checkout.directory)

        then:
        after.fingerprint != before.fingerprint
        after.changedSince(before) == [entry]
        after.uncommitted == 1

        when: 'the change is committed'
        checkout.commit('changed')
        FrameworkSources committed = FrameworkSources.of(checkout.directory)

        then: 'committing identical content does not invalidate the published artifacts'
        committed.fingerprint != before.fingerprint
        committed.fingerprint == after.fingerprint
        committed.changedSince(before) == [entry]
        committed.uncommitted == 0

        where:
        change                         | path                                     | content               | entry
        'a changed file'               | 'grails-core/src/main/groovy/A.groovy'   | 'class A { int b }'   | 'grails-core'
        'a new file'                   | 'grails-core/src/main/groovy/B.groovy'   | 'class B {}'          | 'grails-core'
        'a deleted file'               | 'grails-gradle/plugins/P.groovy'         | null                  | 'grails-gradle'
        'a file of the top directory'  | 'dependencies.gradle'                    | 'ext { a = 1 }'       | 'dependencies.gradle'
        'a new module'                 | 'grails-new/build.gradle'                | 'plugins {}'          | 'grails-new'
    }

    void "a file that is changed again without a commit is another state of the sources"() {
        given:
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A { int one }')
        String first = FrameworkSources.of(checkout.directory).fingerprint

        when:
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A { int two }')

        then:
        FrameworkSources.of(checkout.directory).fingerprint != first

        when: 'and back'
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A { int one }')

        then:
        FrameworkSources.of(checkout.directory).fingerprint == first
    }

    void "the sources are written and read again as they were"() {
        given:
        checkout.write('grails-core/src/main/groovy/B.groovy', 'class B {}')
        FrameworkSources sources = FrameworkSources.of(checkout.directory)

        when:
        FrameworkSources read = FrameworkSources.parse(sources.toText())

        then:
        read.fingerprint == sources.fingerprint
        read.commit == sources.commit
        read.uncommitted == 1
        read.entries == sources.entries
        FrameworkSources.parse('something else') == null
    }

    void "staging, checkout line endings and a shallow detached checkout do not change the fingerprint"() {
        given:
        checkout.write('.gitattributes', '*.groovy text\n')
        checkout.commit('normalize sources')
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A {\n    int value\n}\n')
        String dirty = FrameworkSources.of(checkout.directory).fingerprint

        when:
        checkout.git('add', '--all')

        then:
        FrameworkSources.of(checkout.directory).fingerprint == dirty

        when:
        checkout.write('grails-core/src/main/groovy/A.groovy', 'class A {\r\n    int value\r\n}\r\n')

        then:
        FrameworkSources.of(checkout.directory).fingerprint == dirty

        when:
        checkout.commit('changed')
        GitCheckout clone = checkout.cloneTo(new File(tmp, 'checkout with spaces'))
        clone.git('checkout', '--detach', 'HEAD')

        then:
        FrameworkSources.of(clone.directory).fingerprint == dirty
    }

    void "where there is no repository of Git the sources are not known"() {
        given:
        File plain = new File(tmp, 'plain')
        new File(plain, 'grails-core').mkdirs()

        expect:
        FrameworkSources.of(plain) == null
    }
}

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
package org.apache.grails.e2e.beans

import org.grails.datastore.mapping.services.Service

class GormIndexIncrementalSpec extends PluginIndexFixture {

    void 'packaged service providers match clean builds after a rename, annotation removal and deletion'() {
        given:
        File sources = new File(projectDir, 'src/main/groovy/demo')
        sources.mkdirs()
        File manual = new File(sources, 'Manual.groovy')
        manual.text = 'package demo; class Manual implements org.grails.datastore.mapping.services.Service<Object> {}'
        File descriptor = new File(projectDir, 'src/main/resources/META-INF/services/' + Service.name)
        descriptor.parentFile.mkdirs()
        descriptor.text = '# application registration\n\ndemo.Manual # manual provider\n'
        File first = writeService(sources, 'First')
        File second = writeService(sources, 'Second')

        when:
        build('jar')

        then:
        providers() == ['demo.First', 'demo.Manual', 'demo.Second']

        when:
        assert first.delete()
        File renamed = writeService(sources, 'Renamed')

        then:
        matchesClean(['demo.Manual', 'demo.Renamed', 'demo.Second'])

        when:
        second.text = 'package demo; class Second {}'

        then:
        matchesClean(['demo.Manual', 'demo.Renamed'])

        when:
        assert renamed.delete()

        then:
        matchesClean(['demo.Manual'])
    }

    private boolean matchesClean(List<String> expected) {
        build('jar')
        assert providers() == expected
        build('clean', 'jar')
        assert providers() == expected
        true
    }

    private static File writeService(File sources, String name) {
        new File(sources, name + '.groovy').tap {
            text = "package demo; @grails.gorm.services.Service(Object) class ${name} {}"
        }
    }

    private List<String> providers() {
        new URLClassLoader([snapshot().toURI().toURL()] as URL[], getClass().classLoader).withCloseable { loader ->
            ServiceLoader.load(Service, loader).stream().filter { it.type().name.startsWith('demo.') }
                    .map { it.get().class.name }.sorted().toList()
        }
    }
}

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
package org.grails.gsp.compiler

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers the view registry {@link GroovyPageCompiler} writes beside the classes it compiles. The
 * registry is what resolves a view to its compiled class at runtime, so an entry that outlives the
 * page it names sends the locator after a class that is no longer there.
 */
class GroovyPageCompilerSpec extends Specification {

    @TempDir
    File tempDir

    private File viewsDir
    private File targetDir

    void setup() {
        viewsDir = new File(tempDir, 'views')
        viewsDir.mkdirs()
        targetDir = new File(tempDir, 'classes')
        targetDir.mkdirs()
    }

    void 'the registry names every compiled page'() {
        given:
        writeView('index.gsp')
        writeView('layouts/main.gsp')

        when:
        compile()

        then:
        registry().keySet() == ['/index.gsp', '/layouts/main.gsp'] as Set
    }

    void 'a page removed since the last compile leaves the registry'() {
        given: 'a registry written for two pages'
        writeView('index.gsp')
        File removed = writeView('obsolete.gsp')
        compile()

        when: 'one of them is gone and the pages are compiled again'
        removed.delete()
        compile()

        then: 'the registry names only the page that is still there'
        registry().keySet() == ['/index.gsp'] as Set
    }

    void 'a page recompiled under a different prefix is registered only under the new one'() {
        given: 'a registry written under the prefix a Grails application looks views up by'
        writeView('index.gsp')
        compile('/WEB-INF/grails-app/views/')

        when: 'the pages are compiled again under the prefix a standalone application looks them up by'
        compile('/')

        then:
        registry().keySet() == ['/index.gsp'] as Set
    }

    private File writeView(String path) {
        File view = new File(viewsDir, path)
        view.parentFile.mkdirs()
        view.text = "<html><body>${path}</body></html>"
        view
    }

    private void compile(String viewPrefix = '/') {
        GroovyPageCompiler compiler = new GroovyPageCompiler()
        compiler.viewsDir = viewsDir
        compiler.targetDir = targetDir
        compiler.viewPrefix = viewPrefix
        compiler.packagePrefix = 'probe'
        compiler.srcFiles = []
        viewsDir.eachFileRecurse { File file ->
            if (file.name.endsWith('.gsp')) {
                compiler.srcFiles << file
            }
        }
        compiler.compile()
    }

    private Properties registry() {
        Properties views = new Properties()
        new File(targetDir, 'gsp/views.properties').withInputStream { views.load(it) }
        views
    }

}

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
package org.grails.gsp

import java.security.PrivilegedAction

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Reload-staleness behaviour of {@link GroovyPageMetaInfo}.
 *
 * A precompiled page records the source timestamp as a {@code LAST_MODIFIED} constant. That value is
 * fixed at zero by {@code GroovyPageCompiler} so precompiled pages are byte-reproducible, which means
 * the staleness check has no timestamp to compare against and must not report the page as changed.
 */
class GroovyPageMetaInfoReloadSpec extends Specification {

    @TempDir
    File tempDir

    private Resource sourcePage() {
        File page = new File(tempDir, 'index.gsp')
        page.text = '<html><body>hi</body></html>'
        new FileSystemResource(page)
    }

    private static PrivilegedAction<Resource> callableFor(Resource resource) {
        { -> resource } as PrivilegedAction
    }

    void 'a page with no recorded timestamp is not reported as stale'() {
        given: 'a precompiled page whose LAST_MODIFIED was fixed at zero for reproducibility'
        Resource resource = sourcePage()
        GroovyPageMetaInfo metaInfo = new GroovyPageMetaInfo()
        metaInfo.lastModified = 0L

        expect: 'the live source is not treated as newer, so precompilation is not defeated'
        !metaInfo.shouldReload(callableFor(resource))
    }

    void 'a page whose source is newer than the recorded timestamp is reported as stale'() {
        given: 'a page recorded well before the source file was last written'
        Resource resource = sourcePage()
        GroovyPageMetaInfo metaInfo = new GroovyPageMetaInfo()
        metaInfo.lastModified = resource.getFile().lastModified() - 60_000L

        expect: 'the existing staleness detection still fires for real timestamps'
        metaInfo.shouldReload(callableFor(resource))
    }

    void 'a page recorded at the same time as its source is not reported as stale'() {
        given: 'a page whose recorded timestamp matches the source file'
        Resource resource = sourcePage()
        GroovyPageMetaInfo metaInfo = new GroovyPageMetaInfo()
        metaInfo.lastModified = resource.getFile().lastModified()

        expect:
        !metaInfo.shouldReload(callableFor(resource))
    }

    void 'a missing source resource never triggers a reload'() {
        given:
        GroovyPageMetaInfo metaInfo = new GroovyPageMetaInfo()
        metaInfo.lastModified = 0L

        expect:
        !metaInfo.shouldReload(callableFor(new FileSystemResource(new File(tempDir, 'absent.gsp'))))
    }
}

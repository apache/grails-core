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
package org.grails.testing.runtime.support

import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import spock.lang.Specification

import grails.config.Config
import grails.config.Settings
import grails.core.GrailsApplication
import grails.util.BuildSettings
import org.grails.io.support.GrailsResourceUtils

class GroovyPageUnitTestResourceLoaderSpec extends Specification {

    void 'in memory pages are served for plain and WEB-INF prefixed locations'() {
        given:
        GroovyPageUnitTestResourceLoader loader = new GroovyPageUnitTestResourceLoader(['/book/index.gsp': '<h1>hi</h1>'])

        when:
        Resource plain = loader.getResource('/book/index.gsp')
        Resource prefixed = loader.getResource(GroovyPageUnitTestResourceLoader.WEB_INF_PREFIX + '/book/index.gsp')

        then:
        GroovyPageUnitTestResourceLoader.WEB_INF_PREFIX == '/WEB-INF/grails-app/views'
        plain instanceof ByteArrayResource
        plain.inputStream.text == '<h1>hi</h1>'
        prefixed.inputStream.text == '<h1>hi</h1>'
        plain.description.contains('/book/index.gsp')
    }

    void 'unknown pages resolve to the project views directory'() {
        given:
        GroovyPageUnitTestResourceLoader loader = new GroovyPageUnitTestResourceLoader([:])

        when:
        Resource resource = loader.getResource('/book/missing.gsp')
        String expected = new File(BuildSettings.BASE_DIR.absolutePath + File.separatorChar + GrailsResourceUtils.VIEWS_DIR_PATH + '/book/missing.gsp').canonicalPath

        then:
        resource instanceof FileSystemResource
        ((FileSystemResource) resource).path == expected
        !resource.exists()
    }

    void 'a configured views directory overrides the base path'() {
        given:
        File viewsDir = File.createTempDir()
        Config config = Stub(Config) { getProperty(Settings.GSP_VIEWS_DIR) >> viewsDir.absolutePath }
        GrailsApplication application = Stub(GrailsApplication) { getConfig() >> config }
        GroovyPageUnitTestResourceLoader loader = new GroovyPageUnitTestResourceLoader([:])
        loader.grailsApplication = application
        loader.afterPropertiesSet()

        when:
        Resource resource = loader.getResource('/book/list.gsp')

        then:
        ((FileSystemResource) resource).path == new File(viewsDir, 'book/list.gsp').canonicalPath

        cleanup:
        viewsDir.deleteDir()
    }

    void 'a missing application or views setting keeps the default base path'() {
        given:
        Config config = Stub(Config) { getProperty(Settings.GSP_VIEWS_DIR) >> null }
        GrailsApplication application = Stub(GrailsApplication) { getConfig() >> config }
        GroovyPageUnitTestResourceLoader withoutApp = new GroovyPageUnitTestResourceLoader([:])
        GroovyPageUnitTestResourceLoader withoutSetting = new GroovyPageUnitTestResourceLoader([:])
        withoutSetting.grailsApplication = application

        when:
        withoutApp.afterPropertiesSet()
        withoutSetting.afterPropertiesSet()

        then:
        ((FileSystemResource) withoutApp.getResource('/a.gsp')).path == ((FileSystemResource) withoutSetting.getResource('/a.gsp')).path
    }

}

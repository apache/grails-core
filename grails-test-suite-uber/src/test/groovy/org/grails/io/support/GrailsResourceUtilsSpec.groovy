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
package org.grails.io.support

import grails.util.BuildSettings
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import spock.lang.IgnoreIf
import spock.lang.Specification

class GrailsResourceUtilsSpec extends Specification {

    private static final String TEST_URL = "file:///test/grails/app/grails-app/domain/Test.groovy"
    private static final String TEST_PACKAGE_URL = "file:///test/grails/app/grails-app/domain/mycompany/Test.groovy"
    private static final String TEST_CONTROLLER_URL = "file:///test/grails/app/grails-app/controllers/TestController.groovy"
    private static final String TEST_PLUGIN_CTRL = "file:///test/grails/app/plugins/myplugin/grails-app/controllers/TestController.groovy"

    private static final String WEBINF_CONTROLLER = "file:///test/grails/app/WEB-INF/grails-app/controllers/TestController.groovy"
    private static final String WEBINF_PLUGIN_CTRL = "file:///test/grails/app/WEB-INF/plugins/myplugin/grails-app/controllers/TestController.groovy"

    private static final String UNIT_TESTS_URL = "file:///test/grails/app/grails-tests/SomeTests.groovy"

    void testGetArtifactDirectory() {
        expect:
        "controllers" == GrailsResourceUtils.getArtefactDirectory(TEST_CONTROLLER_URL)
        "domain" == GrailsResourceUtils.getArtefactDirectory(TEST_PACKAGE_URL)
    }

    void testJavaAndGroovySources() {
        expect:
        "mycompany.Test" == GrailsResourceUtils.getClassName(TEST_PACKAGE_URL)
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/domain/mycompany/Test.java").getPath())
        "Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blahblah/Test.java").getPath())
        "Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blah-blah/Test.java").getPath())
        "Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blah--blah/Test.java").getPath())
        "Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blah_blah/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blahblah/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blah-blah/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blah--blah/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/grails-app/blah_blah/mycompany/Test.java").getPath())

        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/main/groovy/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/test/groovy/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/main/java/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/test/java/mycompany/Test.java").getPath())

        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/main/groovy/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/test/groovy/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/main/java/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/test/java/mycompany/Test.java").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/main/groovy/mycompany/Test.groovy").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/test/groovy/mycompany/Test.groovy").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/main/java/mycompany/Test.groovy").getPath())
        "mycompany.Test" == GrailsResourceUtils.getClassName(new File("/test/grails/app/src/test/java/mycompany/Test.groovy").getPath())
    }

    void testIsDomainClass() {
        when:
        URL testUrl = new URL(TEST_URL)

        then:
        GrailsResourceUtils.isDomainClass(testUrl)
    }

    void testGetPathFromRoot() {
        expect:
        "mycompany/Test.groovy" == GrailsResourceUtils.getPathFromRoot(TEST_PACKAGE_URL)
        "Test.groovy" == GrailsResourceUtils.getPathFromRoot(TEST_URL)
    }

    void testGetClassNameResource() {
        when:
        Resource r = new UrlResource(new URL(TEST_URL))

        then:
        "Test" == GrailsResourceUtils.getClassName(r)
    }

    void testGetClassNameString() {
        expect:
        "Test" == GrailsResourceUtils.getClassName(TEST_URL)
    }

    void testIsGrailsPath() {
        expect:
        GrailsResourceUtils.isGrailsPath(TEST_URL)
    }

    void testIsTestPath() {
        expect:
        GrailsResourceUtils.isGrailsPath(UNIT_TESTS_URL)
    }

    void testGetTestNameResource() {
        when:
        Resource r = new UrlResource(new URL(UNIT_TESTS_URL))

        then:
        "SomeTests" == GrailsResourceUtils.getClassName(r)
    }

    void testGetTestNameString() {
        expect:
        "SomeTests" == GrailsResourceUtils.getClassName(UNIT_TESTS_URL)
    }

    void testGetViewsDirForURL() {
        when:
        Resource viewsDir = GrailsResourceUtils.getViewsDir(new UrlResource(TEST_CONTROLLER_URL))

        then:
        toFileUrl("/test/grails/app/grails-app/views") == viewsDir.getURL().toString()

        viewsDir == GrailsResourceUtils.getViewsDir(new UrlResource(TEST_URL))
        toFileUrl("/test/grails/app/grails-app/views") == viewsDir.getURL().toString()
    }

    void testGetAppDir() {
        when:
        Resource appDir = GrailsResourceUtils.getAppDir(new UrlResource(TEST_CONTROLLER_URL))

        then:
        toFileUrl("/test/grails/app/grails-app") == appDir.getURL().toString()

        appDir == GrailsResourceUtils.getAppDir(new UrlResource(TEST_URL))
        toFileUrl("/test/grails/app/grails-app") == appDir.getURL().toString()
    }

    void testGetDirWithinWebInf() {
        when:
        Resource viewsDir = GrailsResourceUtils.getViewsDir(new UrlResource(TEST_CONTROLLER_URL))
        Resource pluginViews = GrailsResourceUtils.getViewsDir(new UrlResource(TEST_PLUGIN_CTRL))

        Resource webInfViews = GrailsResourceUtils.getViewsDir(new UrlResource(WEBINF_CONTROLLER))
        Resource webInfPluginViews = GrailsResourceUtils.getViewsDir(new UrlResource(WEBINF_PLUGIN_CTRL))

        then:
        toFileUrl("/test/grails/app/grails-app/views") == viewsDir.getURL().toString()
        toFileUrl("/test/grails/app/plugins/myplugin/grails-app/views") == pluginViews.getURL().toString()
        toFileUrl("/test/grails/app/WEB-INF/grails-app/views") == webInfViews.getURL().toString()
        toFileUrl("/test/grails/app/WEB-INF/plugins/myplugin/grails-app/views") == webInfPluginViews.getURL().toString()

        "/WEB-INF/grails-app/views" == GrailsResourceUtils.getRelativeInsideWebInf(webInfViews)
        "/WEB-INF/plugins/myplugin/grails-app/views" == GrailsResourceUtils.getRelativeInsideWebInf(webInfPluginViews)

        "/WEB-INF/plugins/myplugin/grails-app/views" == GrailsResourceUtils.getRelativeInsideWebInf(pluginViews)
        "/WEB-INF/grails-app/views" == GrailsResourceUtils.getRelativeInsideWebInf(viewsDir)
    }

    void testGetPluginContextPath() {
        given:
        MockServletContext servletContext = new MockServletContext("/myapp")
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext)
        request.setContextPath("/myapp")

        expect:
        "" == GrailsResourceUtils.getStaticResourcePathForResource(new UrlResource(TEST_CONTROLLER_URL), null)
        "plugins/myplugin" == GrailsResourceUtils.getStaticResourcePathForResource(new UrlResource(TEST_PLUGIN_CTRL), null)
        "" == GrailsResourceUtils.getStaticResourcePathForResource(new UrlResource(WEBINF_CONTROLLER), null)
        "plugins/myplugin" == GrailsResourceUtils.getStaticResourcePathForResource(new UrlResource(WEBINF_PLUGIN_CTRL), null)
        "/myapp" == GrailsResourceUtils.getStaticResourcePathForResource(new UrlResource(WEBINF_CONTROLLER), request.getContextPath())
        "/myapp/plugins/myplugin" == GrailsResourceUtils.getStaticResourcePathForResource(new UrlResource(WEBINF_PLUGIN_CTRL), request.getContextPath())
    }

    void testAppendPiecesForUri() {
        expect:
        "" == GrailsResourceUtils.appendPiecesForUri("")
        "/alpha/beta/gamma" == GrailsResourceUtils.appendPiecesForUri("/alpha", "/beta", "/gamma")
        "/alpha/beta/gamma" == GrailsResourceUtils.appendPiecesForUri("/alpha/", "/beta/", "/gamma")
        "/alpha/beta/gamma/" == GrailsResourceUtils.appendPiecesForUri("/alpha/", "/beta/", "/gamma/")
        "alpha/beta/gamma" == GrailsResourceUtils.appendPiecesForUri("alpha", "beta", "gamma")
    }

    @IgnoreIf({ os.windows })
    void testGetPathFromBaseDir() {
        expect:
        "views/demo/index.gsp" == GrailsResourceUtils.getPathFromBaseDir("${BuildSettings.BASE_DIR.absolutePath}/grails-app/views/demo/index.gsp")
        "src/main/demo/index.gsp" == GrailsResourceUtils.getPathFromBaseDir("${BuildSettings.BASE_DIR.absolutePath}/src/main/demo/index.gsp")
        "/alpha/index.gsp" == GrailsResourceUtils.getPathFromBaseDir("/alpha/index.gsp")
    }

    private String toFileUrl(String path) {
        if (path == null) return path
        String url = null
        try {
            url = new File(path).toURI().toURL().toString()
        } catch (MalformedURLException e) {
            url = path
        }
        return url
    }
}

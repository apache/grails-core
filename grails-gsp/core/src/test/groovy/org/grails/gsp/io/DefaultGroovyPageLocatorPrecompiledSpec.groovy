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
package org.grails.gsp.io

import spock.lang.Specification

/**
 * Covers which pages are used when the application looks like a project on disk.
 *
 * <p>Development skips the pages compiled at build time so that editing one takes effect without a
 * restart. An ahead-of-time image can look the same -- it is a single executable, and it may be run
 * from the directory it was built in -- but it cannot compile a page at run time, so reading the
 * sources renders nothing.</p>
 */
class DefaultGroovyPageLocatorPrecompiledSpec extends Specification {

    private static final String VIEW = '/views/index.gsp'

    DefaultGroovyPageLocator locator = new DefaultGroovyPageLocator()

    void setup() {
        locator.setPrecompiledGspMap([(VIEW): CompiledPage.name])
    }

    private static final String AOT_KEY = 'spring.aot.enabled'

    /** Restores the property, so the environment other specs observe is unchanged. */
    private void withAot(boolean aot, Closure body) {
        String previous = System.getProperty(AOT_KEY)
        try {
            aot ? System.setProperty(AOT_KEY, 'true') : System.clearProperty(AOT_KEY)
            body.call()
        }
        finally {
            previous == null ? System.clearProperty(AOT_KEY) : System.setProperty(AOT_KEY, previous)
        }
    }

    /** A locator that believes it is looking at a project on disk, which the filesystem decides. */
    private DefaultGroovyPageLocator developmentLocator() {
        def developing = new DefaultGroovyPageLocator() {
            @Override
            protected boolean isDevelopmentMode() { true }
        }
        developing.setPrecompiledGspMap([(VIEW): CompiledPage.name])
        developing
    }

    void 'a compiled page is used when the application is not a project on disk'() {
        when:
            def source = null
            withAot(false) { source = locator.findPage(VIEW) }

        then:
            source instanceof GroovyPageCompiledScriptSource
    }

    void 'a compiled page is used in an ahead-of-time image even though it looks like development'() {
        when: 'the executable is run from the directory it was built in'
            def source = null
            withAot(true) { source = developmentLocator().findPage(VIEW) }

        then: 'reading the sources would render nothing, because no class can be defined'
            source instanceof GroovyPageCompiledScriptSource
    }

    void 'development still prefers the sources so that an edit takes effect'() {
        when:
            def source = null
            withAot(false) { source = developmentLocator().findPage(VIEW) }

        then: 'no source file exists here, so nothing is found rather than the compiled page'
            !(source instanceof GroovyPageCompiledScriptSource)
    }

    /**
     * Stands in for a page the build compiled. The constants are the ones the compiler emits and the
     * page's metadata is read from, so the locator can treat this like any other compiled page.
     */
    static class CompiledPage extends org.grails.gsp.GroovyPage {

        public static final String CONTENT_TYPE = 'text/html;charset=UTF-8'

        public static final Map JSP_TAGS = [:]

        public static final String EXPRESSION_CODEC = 'html'

        public static final String STATIC_CODEC = 'none'

        public static final String OUT_CODEC = 'none'

        public static final String TAGLIB_CODEC = 'none'

        @Override
        String getGroovyPageFileName() {
            'index.gsp'
        }

        @Override
        Object run() {
            null
        }
    }
}

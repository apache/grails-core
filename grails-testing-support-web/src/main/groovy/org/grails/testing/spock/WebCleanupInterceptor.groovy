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

package org.grails.testing.spock

import grails.testing.web.GrailsWebUnitTest
import groovy.transform.CompileStatic
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.web.gsp.GroovyPagesTemplateRenderer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.springframework.web.context.request.RequestContextHolder

@CompileStatic
class WebCleanupInterceptor implements IMethodInterceptor {

    public static final String GROOVY_PAGES_TEMPLATE_ENGINE = "groovyPagesTemplateEngine"
    public static final String GROOVY_PAGES_TEMPLATE_RENDERER = "groovyPagesTemplateRenderer"

    @Override
    void intercept(IMethodInvocation invocation) throws Throwable {
        GrailsWebUnitTest test = (GrailsWebUnitTest)invocation.instance
        cleanup(test)
        invocation.proceed()
    }

    void cleanup(GrailsWebUnitTest test) {
        test.views.clear()
        RequestContextHolder.resetRequestAttributes()
        GrailsWebRequest webRequest = test.webRequest
        def ctx = webRequest?.applicationContext
        if (ctx?.containsBean(GROOVY_PAGES_TEMPLATE_ENGINE)) {
            ctx.getBean(GROOVY_PAGES_TEMPLATE_ENGINE, GroovyPagesTemplateEngine).clearPageCache()
        }
        if (ctx?.containsBean(GROOVY_PAGES_TEMPLATE_RENDERER)) {
            ctx.getBean(GROOVY_PAGES_TEMPLATE_RENDERER, GroovyPagesTemplateRenderer).clearCache()
        }
        test.webRequest = null
    }
}

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
package org.grails.plugins.services

import grails.spring.WebBeanBuilder
import grails.util.GrailsWebMockUtil

import org.grails.commons.test.AbstractGrailsMockTests
import org.grails.spring.aop.autoproxy.GroovyAwareAspectJAwareAdvisorAutoProxyCreator
import org.springframework.aop.scope.ScopedProxyFactoryBean
import org.springframework.web.context.request.RequestContextHolder

class ScopedProxyAndServiceClassTests extends AbstractGrailsMockTests {

    // test for http://jira.codehaus.org/browse/GRAILS-6278
    void testScopedProxy() {
        def bb = new WebBeanBuilder()

        GrailsWebMockUtil.bindMockWebRequest()

        bb.beans {
            "org.springframework.aop.config.internalAutoProxyCreator"(GroovyAwareAspectJAwareAdvisorAutoProxyCreator)
            testService(TestService) { bean ->
                bean.scope = "session"

            }
            testScopeProxy(ScopedProxyFactoryBean) {
                targetBeanName="testService"
                proxyTargetClass=true
            }
        }

        def appCtx = bb.createApplicationContext()

        def testService = appCtx.getBean("testScopeProxy")

        assert testService != null
        assert "foo" == testService.myProperty
        assert "bar" == testService.serviceMethod()
        assert "bar" == testService.indirectServiceMethod()
    }

    protected void onTearDown() {
        RequestContextHolder.resetRequestAttributes()
    }
}

class TestService {

    def myProperty = "foo"

    def serviceMethod() {
        'bar'
    }

    def indirectServiceMethod() {
        serviceMethod()
    }

// - This was the workaround for the bug -
//
//    private MetaClass metaClass
//
//    TestService() {
//        this.metaClass = GroovySystem.metaClassRegistry.getMetaClass(TestService)
//    }
//
//    void setMetaClass(MetaClass mc) {
//        this.metaClass = mc
//    }
//
//    MetaClass getMetaClass() { metaClass }
}

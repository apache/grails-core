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
package org.grails.reload

import grails.spring.BeanBuilder
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.junit.Ignore
import org.junit.jupiter.api.Test
import org.springframework.aop.framework.ProxyFactoryBean

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Ignore //TODO Ignore for JDK 11
class SpringProxiedBeanReloadTests {

    @Test
    void testReloadCGLibProxiedBean() {
        def gcl = new GroovyClassLoader()
        def cls = gcl.parseClass("class Book { String title = 'The Stand'; String author }")

        def bb = new BeanBuilder(gcl)
        bb.beans {
            interceptor(DummyInterceptor)
            target(cls) {
               author = "Stephen King"
            }
            myBean(ProxyFactoryBean) {
                targetName = 'target'
                autodetectInterfaces = false
                interceptorNames = 'interceptor'
            }
        }

        def appCtx = bb.createApplicationContext()

        assertEquals "The Stand", appCtx.getBean('myBean').title
        assertEquals "Stephen King", appCtx.getBean('myBean').author

        gcl = new GroovyClassLoader()
        cls = gcl.parseClass("class Book { String title = 'The Shining'; String author }")

        bb = new BeanBuilder(gcl)
        bb.beans {
            interceptor(DummyInterceptor)
            target(cls) {
               author = "Stephen King"
            }
            myBean(ProxyFactoryBean) {
                targetName = 'target'
                autodetectInterfaces = false
                interceptorNames = 'interceptor'
            }
        }

        bb.registerBeans(appCtx)

        assertEquals "The Shining", appCtx.getBean('myBean').title
        assertEquals "Stephen King", appCtx.getBean('myBean').author
    }

}
class DummyInterceptor implements MethodInterceptor {
    Object invoke(MethodInvocation methodInvocation) {
        methodInvocation.proceed()
    }
}

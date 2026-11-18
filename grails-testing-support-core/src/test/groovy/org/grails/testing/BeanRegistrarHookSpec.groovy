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
package org.grails.testing

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import spock.lang.Specification

class BeanRegistrarHookSpec extends Specification implements GrailsUnitTest {

    Closure doWithSpring() {
        { ->
            dslOnlyBean(StringBuilder)
            sharedBean(StringBuilder)
        }
    }

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('registrarOnlyBean', StringBuffer)
            registry.registerBean('sharedBean', StringBuffer)
            registry.registerBean('dependentBean', StringBuilder) { BeanRegistry.Spec<StringBuilder> spec ->
                spec.supplier { BeanRegistry.SupplierContext context ->
                    new StringBuilder(context.bean('registrarOnlyBean', StringBuffer).append('wired').toString())
                }
            }
        } as BeanRegistrar
    }

    void "the test's beanRegistrar() registers beans in its application context"() {
        expect:
        applicationContext.getBean('registrarOnlyBean') instanceof StringBuffer

        and: 'a registrar bean can depend on another'
        applicationContext.getBean('dependentBean').toString() == 'wired'
    }

    void "beanRegistrar() drains after doWithSpring(), as an application's does at boot"() {
        expect: 'both hooks contribute'
        applicationContext.getBean('dslOnlyBean') instanceof StringBuilder

        and: 'the registrar wins a name conflict with the deprecated DSL'
        applicationContext.getBean('sharedBean') instanceof StringBuffer
    }
}

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

class DefineBeansPluginHooksSpec extends Specification implements GrailsUnitTest {

    static class BothHooksPlugin {

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
            } as BeanRegistrar
        }
    }

    void "defineBeans applies both plugin hooks, mirroring the boot order"() {
        when: 'a plugin defining both doWithSpring() and beanRegistrar() is passed to defineBeans'
        defineBeans(new BothHooksPlugin())

        then: 'the DSL beans and the registrar beans are both registered'
        applicationContext.getBean('dslOnlyBean') instanceof StringBuilder
        applicationContext.getBean('registrarOnlyBean') instanceof StringBuffer

        and: 'the registrar wins a name conflict with the deprecated DSL, as at boot'
        applicationContext.getBean('sharedBean') instanceof StringBuffer
    }
}

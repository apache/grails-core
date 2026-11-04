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
package org.apache.grails.core.aot

import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RegisteredBean
import org.springframework.beans.factory.support.RootBeanDefinition
import spock.lang.Specification

/**
 * Covers a bean's autowire mode reaching the code generated for it.
 *
 * <p>Grails registers much of what it contributes as autowired by name, and the generator writes out
 * most of a definition but not that, so a bean rebuilt from generated code would arrive with those
 * collaborators unset. Nothing fails at start-up; the first request that reaches one does.</p>
 */
class AutowireModeBeanRegistrationAotProcessorSpec extends Specification {

    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory()

    AutowireModeBeanRegistrationAotProcessor processor = new AutowireModeBeanRegistrationAotProcessor()

    private RegisteredBean register(String name, int autowireMode) {
        RootBeanDefinition definition = new RootBeanDefinition(Collaborating)
        definition.autowireMode = autowireMode
        beanFactory.registerBeanDefinition(name, definition)
        RegisteredBean.of(beanFactory, name)
    }

    void 'a bean autowired by name is contributed to'() {
        expect:
            processor.processAheadOfTime(register('byName', AbstractBeanDefinition.AUTOWIRE_BY_NAME)) != null
    }

    void 'a bean autowired by type is contributed to'() {
        expect:
            processor.processAheadOfTime(register('byType', AbstractBeanDefinition.AUTOWIRE_BY_TYPE)) != null
    }

    void 'a bean that is not autowired is left alone'() {
        expect: 'contributing to every bean would put a redundant assignment in every definition'
            processor.processAheadOfTime(register('plain', AbstractBeanDefinition.AUTOWIRE_NO)) == null
    }

    static class Collaborating {

        String collaborator

        void setCollaborator(String collaborator) {
            this.collaborator = collaborator
        }
    }
}

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
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

import org.grails.encoder.CodecLookup
import org.grails.plugins.codecs.DefaultCodecLookup
import spock.lang.Specification

/**
 * {@code codecLookup}, which {@code CodecsConfiguration} declares unconditionally, from each hook. As
 * with an application's {@code @Bean}, a framework configuration's unconditional bean wins over the
 * test's configuration; {@code doWithSpring()} and {@code beanRegistrar()}, applied afterwards,
 * replace it.
 */
class ReplaceFrameworkBeanSpec extends Specification implements GrailsUnitTest {

    static class ReplacementCodecLookup extends DefaultCodecLookup {
    }

    def beans = {
        bean('codecLookup', CodecLookup) {
            new ReplacementCodecLookup()
        }
    }

    void "a beans block does not replace an unconditional framework bean"() {
        expect:
        applicationContext.getBean('codecLookup').getClass() == DefaultCodecLookup
    }
}

class ReplaceFrameworkBeanWithConfigurationClassSpec extends Specification implements GrailsUnitTest {

    @Configuration
    static class ReplacementConfiguration {

        @Bean('codecLookup')
        CodecLookup codecLookup() {
            new ReplaceFrameworkBeanSpec.ReplacementCodecLookup()
        }
    }

    void "a nested configuration class does not replace an unconditional framework bean"() {
        expect:
        applicationContext.getBean('codecLookup').getClass() == DefaultCodecLookup
    }
}

class ReplaceFrameworkBeanWithDoWithSpringSpec extends Specification implements GrailsUnitTest {

    Closure doWithSpring() {
        { ->
            codecLookup(ReplaceFrameworkBeanSpec.ReplacementCodecLookup)
        }
    }

    void "doWithSpring() replaces an unconditional framework bean"() {
        expect:
        applicationContext.getBean('codecLookup') instanceof ReplaceFrameworkBeanSpec.ReplacementCodecLookup
    }
}

class ReplaceFrameworkBeanWithBeanRegistrarSpec extends Specification implements GrailsUnitTest {

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('codecLookup', ReplaceFrameworkBeanSpec.ReplacementCodecLookup)
        } as BeanRegistrar
    }

    void "beanRegistrar() replaces an unconditional framework bean"() {
        expect:
        applicationContext.getBean('codecLookup') instanceof ReplaceFrameworkBeanSpec.ReplacementCodecLookup
    }
}

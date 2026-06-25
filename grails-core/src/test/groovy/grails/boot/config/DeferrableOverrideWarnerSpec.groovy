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
package grails.boot.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

/**
 * Verifies the deprecation warning is precise: it fires only when a late doWithSpring bean would
 * override a name-guarded {@code @ConditionalOnMissingBean} auto-config bean (one that would have
 * deferred), and stays silent on the legitimate late-registration uses.
 */
class DeferrableOverrideWarnerSpec extends Specification {

    AnnotationConfigApplicationContext ctx

    def cleanup() { ctx?.close() }

    void "only a name-guarded @ConditionalOnMissingBean override is flagged"() {
        given: "an auto-config-like @Configuration whose bean defs carry the real condition metadata"
            ctx = new AnnotationConfigApplicationContext()
            ctx.register(SampleAutoConfig)
            ctx.refresh()
            def registry = ctx.defaultListableBeanFactory

        expect: "name-guarded conditional -> would have deferred -> FLAGGED"
            DeferrableOverrideWarner.overridesDeferrableConditional(registry.getBeanDefinition('nameGuarded'), 'nameGuarded')

        and: "type-guarded conditional (no name) -> conservatively NOT flagged"
            !DeferrableOverrideWarner.overridesDeferrableConditional(registry.getBeanDefinition('typeGuarded'), 'typeGuarded')

        and: "unconditional bean -> intentional-override territory -> NOT flagged"
            !DeferrableOverrideWarner.overridesDeferrableConditional(registry.getBeanDefinition('unconditional'), 'unconditional')
    }

    @Configuration
    static class SampleAutoConfig {
        @Bean
        @ConditionalOnMissingBean(name = 'nameGuarded')
        MarkerA nameGuarded() { new MarkerA() }

        @Bean
        @ConditionalOnMissingBean(MarkerB)
        MarkerB typeGuarded() { new MarkerB() }

        @Bean
        MarkerC unconditional() { new MarkerC() }
    }

    static class MarkerA {}
    static class MarkerB {}
    static class MarkerC {}
}

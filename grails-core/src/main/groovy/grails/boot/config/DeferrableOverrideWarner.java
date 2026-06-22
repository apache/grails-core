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
package grails.boot.config;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.type.MethodMetadata;

import org.grails.spring.RuntimeSpringConfiguration;

/**
 * Emits a one-line warning when a bean registered in the late {@code doWithSpring} phase overrides a
 * Spring Boot auto-configuration bean that is {@code @ConditionalOnMissingBean} on that very name —
 * i.e. a bean that would have <em>deferred</em> had the plugin/app registered it in the modern
 * {@link grails.core.GrailsApplicationLifeCycle#doWithSpringBeforeAutoConfiguration()} phase instead,
 * avoiding both the override and the wasted bean creation.
 *
 * <p>The check is deliberately narrow. {@code doWithSpring} (after auto-configuration) has permanent,
 * legitimate uses that this MUST stay silent on:
 * <ul>
 *   <li>decorating/wrapping a bean that auto-config created (needs it to exist first);</li>
 *   <li>aggregating or inspecting the fully-populated registry;</li>
 *   <li>artefact-driven beans (one per controller/service/taglib) that need a loaded GrailsApplication;</li>
 *   <li>intentionally overriding an <em>unconditional</em> Boot bean (the only way to replace it).</li>
 * </ul>
 * So it fires only on the one anti-pattern the modern phase removes: overriding a <em>name-guarded</em>
 * {@code @ConditionalOnMissingBean} bean. Type-guarded conditionals are left silent — overriding by name
 * does not reliably prove the type condition would have matched, and a false nudge is worse than none.
 */
final class DeferrableOverrideWarner {

    private static final Logger LOG = LoggerFactory.getLogger(DeferrableOverrideWarner.class);

    private static final String CONDITIONAL_ON_MISSING_BEAN =
            "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean";

    private DeferrableOverrideWarner() {
    }

    static void warnOnDeferrableOverrides(RuntimeSpringConfiguration springConfig, BeanDefinitionRegistry registry) {
        for (String name : springConfig.getBeanNames()) {
            if (!registry.containsBeanDefinition(name)) {
                continue; // a fresh bean, not an override
            }
            if (overridesDeferrableConditional(registry.getBeanDefinition(name), name)) {
                LOG.warn("Bean '{}' registered in doWithSpring overrides a Spring Boot auto-configuration "
                        + "bean of the same name that is @ConditionalOnMissingBean(name=\"{}\") and would have "
                        + "deferred. Register it in doWithSpringBeforeAutoConfiguration() instead: the "
                        + "auto-configuration bean then backs off cleanly, with no override and no wasted bean "
                        + "creation. (doWithSpring stays correct for decoration, aggregation, artefact-driven "
                        + "beans, and intentional overrides of unconditional beans.)", name, name);
            }
        }
    }

    /**
     * True only when {@code existing} is an auto-configuration {@code @Bean} method annotated
     * {@code @ConditionalOnMissingBean} whose {@code name} attribute names {@code beanName}.
     */
    static boolean overridesDeferrableConditional(BeanDefinition existing, String beanName) {
        if (!(existing instanceof AnnotatedBeanDefinition)) {
            return false;
        }
        MethodMetadata factoryMethod = ((AnnotatedBeanDefinition) existing).getFactoryMethodMetadata();
        if (factoryMethod == null || !factoryMethod.isAnnotated(CONDITIONAL_ON_MISSING_BEAN)) {
            return false;
        }
        Map<String, Object> attributes = factoryMethod.getAnnotationAttributes(CONDITIONAL_ON_MISSING_BEAN);
        if (attributes == null) {
            return false;
        }
        Object names = attributes.get("name");
        // Only the name-guarded case is unambiguous: the conditional explicitly guards this bean name,
        // so registering it before auto-config would certainly make the conditional bean defer.
        return names instanceof String[] && Arrays.asList((String[]) names).contains(beanName);
    }
}

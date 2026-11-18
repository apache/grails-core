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
package org.apache.grails.core.aot;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.javapoet.CodeBlock;

/**
 * Carries a bean's autowire mode into the code generated for it ahead of time.
 *
 * <p>Grails registers much of what it contributes as autowired by name: a tag library, a controller
 * or an interceptor takes its collaborators from beans of the same name, without annotating them.
 * The generator writes out most of a definition but not this, so a bean rebuilt from generated code
 * arrives with those collaborators unset -- a tag library holding null where it expects a message
 * source, and a link generator holding null where it expects the URL mappings. Nothing fails at
 * start-up; the first page that reaches one of them does.</p>
 *
 * <p>The annotated injection is unaffected either way, because the generator resolves that itself
 * and writes the field and method access into the instance supplier. This only restores what
 * convention supplies.</p>
 *
 * @since 8.0
 */
public class AutowireModeBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    @Nullable
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        int autowireMode = autowireModeOf(registeredBean);
        if (autowireMode == AbstractBeanDefinition.AUTOWIRE_NO) {
            return null;
        }
        return BeanRegistrationAotContribution.withCustomCodeFragments(
                codeFragments -> new AutowireModeCodeFragments(codeFragments, autowireMode));
    }

    private int autowireModeOf(RegisteredBean registeredBean) {
        RootBeanDefinition definition = registeredBean.getMergedBeanDefinition();
        return definition.getAutowireMode();
    }

    /** Appends the assignment the generator leaves out to the properties it does write. */
    private static final class AutowireModeCodeFragments extends BeanRegistrationCodeFragmentsDecorator {

        private final int autowireMode;

        private AutowireModeCodeFragments(BeanRegistrationCodeFragments delegate, int autowireMode) {
            super(delegate);
            this.autowireMode = autowireMode;
        }

        @Override
        public CodeBlock generateSetBeanDefinitionPropertiesCode(GenerationContext generationContext,
                BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition,
                Predicate<String> attributeFilter) {
            CodeBlock properties = super.generateSetBeanDefinitionPropertiesCode(generationContext,
                    beanRegistrationCode, beanDefinition, attributeFilter);
            return CodeBlock.builder()
                    .add(properties)
                    .addStatement("$L.setAutowireMode($L)", BEAN_DEFINITION_VARIABLE, this.autowireMode)
                    .build();
        }
    }
}

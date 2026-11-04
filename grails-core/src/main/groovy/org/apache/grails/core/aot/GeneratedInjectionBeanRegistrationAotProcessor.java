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

import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.javapoet.CodeBlock;

/**
 * Has the code generated ahead of time for each bean definition, an inner bean's included, mark the definition
 * as one it contributed, naming the type whose annotated members it injects.
 *
 * <p>That type is the one Spring's own processors inject through when they generate the code, and the marks
 * let the processors registered again on generated artifacts leave to the generated code what it injected.
 * See {@link GeneratedBeanDefinitions}.</p>
 *
 * @since 8.0.1
 */
public class GeneratedInjectionBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        String declaredType = registeredBean.getBeanClass().getName();
        return BeanRegistrationAotContribution.withCustomCodeFragments(
                codeFragments -> new MarkingCodeFragments(codeFragments, declaredType));
    }

    /** Marks the definition once its instance supplier is set, as it is returned. */
    private static final class MarkingCodeFragments extends BeanRegistrationCodeFragmentsDecorator {

        private final String declaredType;

        private MarkingCodeFragments(BeanRegistrationCodeFragments delegate, String declaredType) {
            super(delegate);
            this.declaredType = declaredType;
        }

        @Override
        public CodeBlock generateReturnCode(GenerationContext generationContext,
                BeanRegistrationCode beanRegistrationCode) {
            return CodeBlock.builder()
                    .addStatement("$T.markGenerated($L, $S)", GeneratedBeanDefinitions.class,
                            BEAN_DEFINITION_VARIABLE, this.declaredType)
                    .add(super.generateReturnCode(generationContext, beanRegistrationCode))
                    .build();
        }
    }
}

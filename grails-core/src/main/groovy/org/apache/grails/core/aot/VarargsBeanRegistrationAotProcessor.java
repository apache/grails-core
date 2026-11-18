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

import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.javapoet.CodeBlock;
import org.springframework.util.ClassUtils;

/**
 * Gathers a variable-argument constructor argument into the array it feeds, ahead of time.
 *
 * <p>A bean declared through the plugin DSL passes its arguments positionally, and a constructor
 * that ends in a variable-argument parameter is called the way the language allows: one value where
 * the parameter is an array, or a collection where it is an array of that element type. Building the
 * bean, Spring adapts the argument to the parameter. Reading the definition to generate code for it,
 * Spring does not: it looks the argument up by the parameter's type, and a lone {@code String} does
 * not answer to {@code String[]}.</p>
 *
 * <p>The argument is then missed and resolved as a dependency instead, and an array of a type nobody
 * publishes as a bean resolves to an empty array rather than failing. So the bean is built, and
 * built wrong: a datastore that maps no classes, or a servlet registration with no URL mapping,
 * which then falls back to mapping everything. Nothing is logged, and the bean that goes wrong is
 * rarely the one that reports it -- the first symptom is a page that 404s or a domain class that
 * says it is not one.</p>
 *
 * <p>Gathering the argument into an array here means the generator writes out {@code new String[]
 * {"*.gsp"}}, which the lookup does find. Only an argument that is already usable as the array is
 * left alone, and an argument that would need its elements converted is left to the resolution that
 * exists today rather than guessed at here.</p>
 *
 * @since 8.0
 */
public class VarargsBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

    private static final Log logger = LogFactory.getLog(VarargsBeanRegistrationAotProcessor.class);

    @Override
    @Nullable
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        Executable executable = resolveExecutable(registeredBean);
        if (executable == null || !executable.isVarArgs()) {
            return null;
        }
        Class<?>[] parameterTypes = executable.getParameterTypes();
        RootBeanDefinition beanDefinition = registeredBean.getMergedBeanDefinition();
        Object gathered = gatherTrailingArgument(beanDefinition.getConstructorArgumentValues(), parameterTypes);
        if (gathered == null) {
            return null;
        }
        return BeanRegistrationAotContribution.withCustomCodeFragments(
                codeFragments -> new VarargsCodeFragments(codeFragments, gathered));
    }

    /**
     * The constructor or factory method the generator will write the call to.
     *
     * <p>Resolution reads the bean class and its members, so a bean whose class cannot be resolved
     * fails here rather than at the point of use. It is not this processor's place to report that:
     * generation carries on and fails where it means something, and a line is left behind so that
     * a bean which is genuinely broken can be traced to here rather than only to what followed.</p>
     *
     * <p>An {@code Error} is not caught. This says only that a bean has no variable-argument
     * constructor to gather, and a JVM that is out of memory or a class that will not link is
     * neither that nor something to carry on generating through.</p>
     */
    @Nullable
    private Executable resolveExecutable(RegisteredBean registeredBean) {
        try {
            return registeredBean.resolveConstructorOrFactoryMethod();
        }
        catch (Exception ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Not gathering arguments for bean '" + registeredBean.getBeanName() +
                        "', whose constructor could not be resolved", ex);
            }
            return null;
        }
    }

    /**
     * The trailing argument as the array its parameter takes, or {@code null} to leave it alone.
     *
     * <p>Only the straightforward reading is handled: one argument per parameter, the last of them
     * standing for the variable-argument array. An argument list that spreads several values across
     * that parameter, or that is indexed rather than positional, is left as it is.</p>
     */
    @Nullable
    private Object gatherTrailingArgument(ConstructorArgumentValues arguments, Class<?>[] parameterTypes) {
        if (!arguments.getIndexedArgumentValues().isEmpty()) {
            return null;
        }
        List<ValueHolder> supplied = arguments.getGenericArgumentValues();
        if (supplied.size() != parameterTypes.length) {
            return null;
        }
        Class<?> arrayType = parameterTypes[parameterTypes.length - 1];
        if (!arrayType.isArray()) {
            return null;
        }
        Object value = supplied.get(supplied.size() - 1).getValue();
        if (value == null || ClassUtils.isAssignableValue(arrayType, value)) {
            return null;
        }
        Class<?> componentType = arrayType.getComponentType();
        Collection<?> elements = value instanceof Collection<?> collection ? collection : List.of(value);
        return toArray(elements, componentType);
    }

    /**
     * The elements as an array of the component type, or {@code null} if any of them is not already
     * one. Converting an element is the resolution step's job, and guessing at it here would turn a
     * missed argument into a wrong one.
     */
    @Nullable
    private Object toArray(Collection<?> elements, Class<?> componentType) {
        for (Object element : elements) {
            if (element == null || !ClassUtils.isAssignableValue(componentType, element)) {
                return null;
            }
        }
        Object array = Array.newInstance(componentType, elements.size());
        int index = 0;
        for (Object element : elements) {
            Array.set(array, index++, element);
        }
        return array;
    }

    /** Writes the definition out with the gathered argument in place of the one supplied. */
    private static final class VarargsCodeFragments extends BeanRegistrationCodeFragmentsDecorator {

        private final Object gathered;

        private VarargsCodeFragments(BeanRegistrationCodeFragments delegate, Object gathered) {
            super(delegate);
            this.gathered = gathered;
        }

        @Override
        public CodeBlock generateSetBeanDefinitionPropertiesCode(GenerationContext generationContext,
                BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition,
                Predicate<String> attributeFilter) {
            return super.generateSetBeanDefinitionPropertiesCode(generationContext, beanRegistrationCode,
                    withGatheredArgument(beanDefinition), attributeFilter);
        }

        /**
         * A copy carrying the gathered argument, so the definition the context is running on keeps
         * the argument it was given and only the generated code differs.
         */
        private RootBeanDefinition withGatheredArgument(RootBeanDefinition beanDefinition) {
            RootBeanDefinition copy = new RootBeanDefinition(beanDefinition);
            List<ValueHolder> supplied = copy.getConstructorArgumentValues().getGenericArgumentValues();
            supplied.get(supplied.size() - 1).setValue(this.gathered);
            return copy;
        }
    }
}

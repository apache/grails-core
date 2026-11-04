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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Registers what Spring reaches reflectively when it creates and wires the beans the framework declares.
 *
 * <p>Spring generates the code that creates a bean only for the definitions it is shown, and the plugins
 * declare theirs as the application starts: a {@code BeanRegistrar}'s beans are left out of generation and
 * declared again, and the bean DSL's are declared again by the plugins at run time. Those are created the
 * ordinary way -- through a constructor, given their properties through their setters, and given their
 * {@code @Autowired}, {@code @Value} and {@code @Inject} members by the post-processor that handles them --
 * and all of it is reflective. An image keeps none of it unless asked, so an image built without these
 * stopped at the first plugin bean declared by class, with "No default constructor found", and then at the
 * first one with an autowired setter.</p>
 *
 * <p>Every bean definition with a class is covered, so it does not matter how a plugin declared it.</p>
 *
 * <p>The injection points of every bean are registered as well, Spring's included. An application running
 * on generated artifacts has the processors that inject through annotations registered again, because a bean
 * built by a supplier and declared as an interface hides the class whose members need injecting; those
 * processors then inject every bean, including the ones the generated code already injected, and do it
 * reflectively. Only the annotated members of Spring's beans are registered: creating them is the generated
 * code's business, except for the ones a registrar declares, which the generated code leaves out.</p>
 *
 * @since 9.0
 */
public class BeanReflectionBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    private static final String SPRING_PACKAGE = "org.springframework.";

    /** The annotations Spring injects a member through, or calls a method for. */
    private static final List<Class<? extends Annotation>> INJECTION_ANNOTATIONS = List.of(
            Autowired.class, Value.class, Inject.class, Resource.class, PostConstruct.class, PreDestroy.class);

    /** Only its annotated members are reached: a Spring bean, or the product of a factory method. */
    private static final int INJECTED = 0;

    /** Also given its properties through its setters, after a supplier created it. */
    private static final int WIRED = 1;

    /** Also created through its constructor. */
    private static final int CREATED = 2;

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Map<Class<?>, Integer> beanClasses = beanClassesOf(beanFactory);
        if (beanClasses.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) -> {
            ReflectionHints reflection = generationContext.getRuntimeHints().reflection();
            beanClasses.forEach((beanClass, access) -> register(reflection, beanClass, access));
        };
    }

    /**
     * The class of every bean, and how much of it is reached reflectively: a bean is created through its
     * constructor unless a supplier or a factory method creates it, and the product of a factory method, like
     * a Spring bean the generated code creates, only has its annotated members reached.
     */
    private Map<Class<?>, Integer> beanClassesOf(ConfigurableListableBeanFactory beanFactory) {
        Map<Class<?>, Integer> beanClasses = new LinkedHashMap<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition definition;
            try {
                definition = beanFactory.getMergedBeanDefinition(name);
            }
            catch (NoSuchBeanDefinitionException ex) {
                continue;
            }
            if (!(definition instanceof AbstractBeanDefinition beanDefinition) || beanDefinition.isAbstract()) {
                continue;
            }
            Class<?> beanClass;
            int access;
            if (beanDefinition.getFactoryMethodName() != null) {
                beanClass = productOf(beanFactory, name);
                access = INJECTED;
            }
            else {
                beanClass = beanClassOf(beanDefinition, beanFactory.getBeanClassLoader());
                access = (beanDefinition.getInstanceSupplier() == null) ? CREATED : WIRED;
            }
            if (beanClass == null) {
                continue;
            }
            if (beanClass.getName().startsWith(SPRING_PACKAGE) && !isLeftOutOfGeneration(beanDefinition)) {
                access = INJECTED;
            }
            beanClasses.merge(beanClass, access, Math::max);
        }
        return beanClasses;
    }

    /** A registrar's bean: the generated code does not create it, and the registrar declares it again. */
    private static boolean isLeftOutOfGeneration(AbstractBeanDefinition beanDefinition) {
        return Boolean.TRUE.equals(beanDefinition.getAttribute(BeanRegistrationAotProcessor.IGNORE_REGISTRATION_ATTRIBUTE));
    }

    /** What a factory method creates, as far as it can be told without calling it. */
    @Nullable
    private static Class<?> productOf(ConfigurableListableBeanFactory beanFactory, String name) {
        try {
            return beanFactory.getType(name, false);
        }
        catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    /**
     * The class a definition creates, whether it holds the class or only its name: the bean DSL declares a
     * bean by name as often as by class, and the name is resolved only when the bean is created.
     */
    @Nullable
    private static Class<?> beanClassOf(AbstractBeanDefinition beanDefinition, @Nullable ClassLoader classLoader) {
        if (beanDefinition.hasBeanClass()) {
            return beanDefinition.getBeanClass();
        }
        String className = beanDefinition.getBeanClassName();
        if (className == null) {
            return null;
        }
        try {
            return ClassUtils.forName(className, classLoader);
        }
        catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }

    private static void register(ReflectionHints reflection, Class<?> beanClass, int access) {
        if (access == CREATED) {
            reflection.registerType(beanClass, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        }
        else if (access == WIRED) {
            reflection.registerType(beanClass, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
        try {
            ReflectionUtils.doWithMethods(beanClass, method -> reflection.registerMethod(method, ExecutableMode.INVOKE),
                    BeanReflectionBeanFactoryInitializationAotProcessor::isInjectionPoint);
            ReflectionUtils.doWithFields(beanClass, reflection::registerField,
                    BeanReflectionBeanFactoryInitializationAotProcessor::isInjectionPoint);
        }
        catch (LinkageError ex) {
            // a member naming a class that is absent here cannot be injected here either
        }
    }

    private static boolean isInjectionPoint(AnnotatedElement element) {
        for (Class<? extends Annotation> annotation : INJECTION_ANNOTATIONS) {
            if (AnnotatedElementUtils.hasAnnotation(element, annotation)) {
                return true;
            }
        }
        return false;
    }

}

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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.util.ClassUtils;

/**
 * What the code generated ahead of time did for the beans it contributed, so that the processors injecting
 * through annotations do not do it a second time.
 *
 * <p>An application running on generated artifacts has those processors registered again, for the annotated
 * members the generated code could not see. The generated code injects a bean through the annotated members of
 * the type its definition declares, and records that type's initialization and destruction methods on the
 * definition; injected a second time, a bean runs a side-effecting {@code @Autowired} method twice, which
 * registered every executor's metrics twice.</p>
 *
 * <p>The code generated for each definition, an inner bean's included, calls {@link #markGenerated} as it
 * returns the definition. That names on the definition the type whose members the code injects, and records
 * each instance its supplier creates. A definition declared any other way carries no such name, wherever and
 * whenever it is declared, so it is processed as usual.</p>
 *
 * <p>For a bean whose definition names that type, the processors first mark the type's members as configured
 * from outside, with the methods of the bean's class overriding them, which are what the generated code
 * called. That is how Spring keeps two processors from injecting one member, so an instance the generated code
 * created is injected only through the members its class adds. Any other instance of the bean, such as one
 * handed to {@code configureBean}, was not injected by that code, and is processed in full.</p>
 *
 * <p>The generated code also records on each definition the initialization and destruction methods it found
 * annotated. Those are taken off again, since the processor that calls them is registered again and calls them
 * for every instance: see {@link #leaveAnnotatedLifecycleMethodsToProcessor}.</p>
 *
 * @since 8.0.1
 */
public final class GeneratedBeanDefinitions {

    /** The attribute that names, on a definition the generated code contributed, the type that code injects. */
    public static final String DECLARED_TYPE_ATTRIBUTE = GeneratedBeanDefinitions.class.getName() + ".declaredType";

    /** The instances the suppliers of generated definitions created. */
    private static final WeakIdentitySet CREATED = new WeakIdentitySet();

    private GeneratedBeanDefinitions() {
    }

    /**
     * Marks a definition the generated code contributed: called by that code as it returns the definition.
     *
     * @param beanDefinition the definition, with the instance supplier the generated code gave it
     * @param declaredType the name of the type whose annotated members the generated code injects
     */
    @SuppressWarnings("unchecked")
    public static void markGenerated(RootBeanDefinition beanDefinition, String declaredType) {
        beanDefinition.setAttribute(DECLARED_TYPE_ATTRIBUTE, declaredType);
        Supplier<?> supplier = beanDefinition.getInstanceSupplier();
        if (supplier == null) {
            return;
        }
        InstanceSupplier<Object> instanceSupplier = (supplier instanceof InstanceSupplier<?> generated) ?
                (InstanceSupplier<Object>) generated : InstanceSupplier.using(supplier::get);
        beanDefinition.setInstanceSupplier(instanceSupplier.andThen((registeredBean, instance) -> {
            if (instance != null) {
                CREATED.add(instance);
            }
            return instance;
        }));
    }

    /**
     * Whether an instance is not one the generated code created, although its bean's definition is a generated
     * one: an instance handed to {@code configureBean} or {@code initializeBean} under that bean's name, which
     * the generated code injected nothing into.
     *
     * @param beanFactory the bean factory the bean is in
     * @param beanName the bean's name
     * @param bean the instance
     * @return {@code true} when the bean's definition was contributed by the generated code and the instance
     *         was not created by it
     */
    public static boolean isAnotherInstanceOfGeneratedBean(@Nullable BeanFactory beanFactory, String beanName,
            Object bean) {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory listable) ||
                !listable.containsBeanDefinition(beanName)) {
            return false;
        }
        return listable.getMergedBeanDefinition(beanName).hasAttribute(DECLARED_TYPE_ATTRIBUTE) &&
                !CREATED.contains(bean);
    }

    /**
     * Marks as configured from outside, on the merged definition of a bean the generated code contributed, the
     * members that code injects: those the given processor finds on the type the definition names, and the
     * methods of the bean's class overriding them, which are the ones the generated code called. An override of
     * a generic method is the method declared with the actual type, not the bridge the compiler adds for the
     * erased one. A processor of that kind then injects an instance the generated code created only through the
     * members its class adds, which is none when the class is the type the definition names.
     *
     * @param beanDefinition the bean's merged definition, before the processor of that kind processes it
     * @param beanType the class of the bean
     * @param generatingProcessor a processor of the kind and configuration the code was generated with, which
     *        is given a definition of its own to process
     */
    public static void markMembersInjectedByGeneratedCode(RootBeanDefinition beanDefinition, Class<?> beanType,
            MergedBeanDefinitionPostProcessor generatingProcessor) {
        if (!(beanDefinition.getAttribute(DECLARED_TYPE_ATTRIBUTE) instanceof String declaredTypeName)) {
            return;
        }
        Class<?> beanClass = ClassUtils.getUserClass(beanType);
        Class<?> declaredType = findType(beanClass, declaredTypeName);
        if (declaredType == null) {
            return;
        }
        RootBeanDefinition generated = new RootBeanDefinition(declaredType);
        generatingProcessor.postProcessMergedBeanDefinition(generated, declaredType, "");
        for (Member member : generated.getExternallyManagedConfigMembers()) {
            beanDefinition.registerExternallyManagedConfigMember(member);
            if (member instanceof Method method) {
                beanDefinition.registerExternallyManagedConfigMember(
                        BridgeMethodResolver.getMostSpecificMethod(method, beanClass));
            }
        }
    }

    /**
     * Takes off the definitions the generated code contributed, inner beans' included, the initialization and
     * destruction methods that code recorded from annotations, once the processor that calls annotated ones is
     * registered again. That processor calls them for every instance, as on a context without generated code.
     *
     * <p>Left on, a recorded method ran twice for an instance handed to {@code configureBean}. The container
     * skips a recorded method that a processor has called, but {@code configureBean} works on a copy of the
     * definition, which keeps the recorded names and loses what the processor called.</p>
     *
     * @param registry the registry the generated code contributed its definitions to
     */
    public static void leaveAnnotatedLifecycleMethodsToProcessor(BeanDefinitionRegistry registry) {
        CommonAnnotationBeanPostProcessor generatingProcessor = new CommonAnnotationBeanPostProcessor();
        for (String beanName : registry.getBeanDefinitionNames()) {
            leaveAnnotatedLifecycleMethodsToProcessor(registry.getBeanDefinition(beanName), generatingProcessor);
        }
    }

    private static void leaveAnnotatedLifecycleMethodsToProcessor(BeanDefinition beanDefinition,
            MergedBeanDefinitionPostProcessor generatingProcessor) {
        if (beanDefinition instanceof AbstractBeanDefinition definition &&
                definition.getAttribute(DECLARED_TYPE_ATTRIBUTE) instanceof String declaredTypeName &&
                (definition.getInitMethodNames() != null || definition.getDestroyMethodNames() != null)) {
            Class<?> definedType = definition.getResolvableType().resolve();
            Class<?> declaredType = (definedType != null) ?
                    findType(ClassUtils.getUserClass(definedType), declaredTypeName) : null;
            if (declaredType != null) {
                RootBeanDefinition annotated = new RootBeanDefinition(declaredType);
                generatingProcessor.postProcessMergedBeanDefinition(annotated, declaredType, "");
                definition.setInitMethodNames(
                        without(definition.getInitMethodNames(), annotated.getExternallyManagedInitMethods()));
                definition.setDestroyMethodNames(
                        without(definition.getDestroyMethodNames(), annotated.getExternallyManagedDestroyMethods()));
            }
        }
        if (beanDefinition.hasPropertyValues()) {
            beanDefinition.getPropertyValues().forEach(value -> leaveToProcessorIn(value.getValue(), generatingProcessor));
        }
        if (beanDefinition.hasConstructorArgumentValues()) {
            ConstructorArgumentValues arguments = beanDefinition.getConstructorArgumentValues();
            arguments.getIndexedArgumentValues().values()
                    .forEach(argument -> leaveToProcessorIn(argument.getValue(), generatingProcessor));
            arguments.getGenericArgumentValues()
                    .forEach(argument -> leaveToProcessorIn(argument.getValue(), generatingProcessor));
        }
    }

    /** Looks for inner beans in a property value or constructor argument, however deeply they are held. */
    private static void leaveToProcessorIn(@Nullable Object value, MergedBeanDefinitionPostProcessor generatingProcessor) {
        if (value instanceof BeanDefinitionHolder holder) {
            leaveAnnotatedLifecycleMethodsToProcessor(holder.getBeanDefinition(), generatingProcessor);
        }
        else if (value instanceof BeanDefinition definition) {
            leaveAnnotatedLifecycleMethodsToProcessor(definition, generatingProcessor);
        }
        else if (value instanceof Iterable<?> values) {
            values.forEach(element -> leaveToProcessorIn(element, generatingProcessor));
        }
        else if (value instanceof Map<?, ?> map) {
            map.forEach((key, element) -> {
                leaveToProcessorIn(key, generatingProcessor);
                leaveToProcessorIn(element, generatingProcessor);
            });
        }
        else if (value instanceof Object[] values) {
            Arrays.stream(values).forEach(element -> leaveToProcessorIn(element, generatingProcessor));
        }
    }

    private static String @Nullable [] without(String @Nullable [] methodNames, Set<String> annotated) {
        if (methodNames == null) {
            return null;
        }
        String[] remaining = Arrays.stream(methodNames).filter(name -> !annotated.contains(name)).toArray(String[]::new);
        return (remaining.length > 0) ? remaining : null;
    }

    /** The class, superclass or interface of the bean's class with the given name. */
    @Nullable
    private static Class<?> findType(Class<?> beanClass, String name) {
        for (Class<?> type = beanClass; type != null; type = type.getSuperclass()) {
            if (type.getName().equals(name)) {
                return type;
            }
        }
        for (Class<?> type : ClassUtils.getAllInterfacesForClassAsSet(beanClass)) {
            if (type.getName().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Instances held weakly and told apart by identity, since a bean's own {@code equals} and {@code hashCode}
     * may change as it is injected.
     */
    private static final class WeakIdentitySet {

        private final ReferenceQueue<Object> collected = new ReferenceQueue<>();

        private final Set<Element> elements = new HashSet<>();

        synchronized void add(Object instance) {
            expunge();
            elements.add(new Element(instance, collected));
        }

        synchronized boolean contains(Object instance) {
            expunge();
            return elements.contains(new Element(instance, null));
        }

        private void expunge() {
            Reference<?> reference = collected.poll();
            while (reference != null) {
                elements.remove(reference);
                reference = collected.poll();
            }
        }

        private static final class Element extends WeakReference<Object> {

            private final int hash;

            Element(Object instance, @Nullable ReferenceQueue<Object> queue) {
                super(instance, queue);
                this.hash = System.identityHashCode(instance);
            }

            @Override
            public int hashCode() {
                return hash;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) {
                    return true;
                }
                Object instance = get();
                return other instanceof Element element && instance != null && instance == element.get();
            }
        }
    }

}

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
package org.apache.grails.openapi.aot

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Array
import java.lang.reflect.Method
import java.lang.reflect.Type

import groovy.transform.CompileStatic

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.converter.ModelConverterContextImpl
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema
import org.jspecify.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aot.generate.GenerationContext
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.ReflectionHints
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.core.GenericTypeResolver

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import grails.rest.RestfulController
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.openapi.ActionAnnotations
import org.grails.openapi.GrailsModelConverter
import org.grails.plugins.openapi.OpenApiGrailsPlugin

/**
 * Keeps, in an image compiled ahead of time, what the description is derived from at runtime.
 *
 * <p>The description reads the application's controllers, their actions and the OpenAPI
 * annotations on them, and has swagger-core introspect every type they serve and bind. An image
 * keeps only the members something asks for, so without these hints those classes are described
 * as having nothing.</p>
 *
 * <p>The types are found the way swagger-core finds them at runtime: each domain class, and each
 * type the controllers serve or bind or the OpenAPI annotations on them name, is resolved through
 * swagger-core with the Grails converter, in one context, and every type the resolution reaches is
 * kept, with the classes it extends, rather than every type reachable from their properties. What
 * Grails declares of a type, such as its constraints, is not read, since the application is not
 * running. A controller or an annotation that cannot be read is skipped, and logged, as the
 * description skips it at runtime.</p>
 *
 * @since 8.0
 */
@CompileStatic
class OpenApiBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(OpenApiBeanFactoryInitializationAotProcessor)

    private static final String SWAGGER_ANNOTATIONS = 'io.swagger.v3.oas.annotations.'

    /**
     * What a controller, and each class it extends, is kept with: the actions each declares, which
     * the description reads with their annotations and parameters from the most derived class
     * declaring them, and the static properties Grails reads its formats and allowed methods from.
     */
    private static final MemberCategory[] CONTROLLER_MEMBERS = [
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.ACCESS_DECLARED_FIELDS
    ] as MemberCategory[]

    /**
     * What a described type is kept with: the constraints a validateable type declares, read
     * through a static method, the properties Grails binds, read from a static field, and the
     * fields Groovy puts the annotations of a property on, such as its {@code @Schema}.
     */
    private static final MemberCategory[] DESCRIBED_MEMBERS = [
            MemberCategory.INVOKE_PUBLIC_METHODS,
            MemberCategory.ACCESS_PUBLIC_FIELDS,
            MemberCategory.ACCESS_DECLARED_FIELDS
    ] as MemberCategory[]

    @Override
    @Nullable
    BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        if (!beanFactory.containsBeanDefinition(OpenApiGrailsPlugin.GENERATOR_BEAN_NAME)) {
            return null
        }
        Object application = beanFactory.getSingleton(GrailsApplication.APPLICATION_ID)
        if (!(application instanceof GrailsApplication)) {
            return null
        }
        GrailsApplication grailsApplication = (GrailsApplication) application
        List<Class<?>> controllers = artefactClasses(grailsApplication, ControllerArtefactHandler.TYPE)
        Set<Class<?>> described = describedTypes(grailsApplication)
        if (controllers.empty && described.empty) {
            return null
        }
        return { GenerationContext generationContext, BeanFactoryInitializationCode code ->
            ReflectionHints reflection = generationContext.runtimeHints.reflection()
            for (Class<?> controller : controllers) {
                for (Class<?> current = controller; current != null && isApplicationType(current); current = current.superclass) {
                    reflection.registerType(current, CONTROLLER_MEMBERS)
                }
            }
            for (Class<?> type : described) {
                reflection.registerType(type, DESCRIBED_MEMBERS)
            }
        } as BeanFactoryInitializationAotContribution
    }

    /**
     * Every type swagger-core reaches from the types the application's controllers serve and bind,
     * the OpenAPI annotations on them name, and its domain classes.
     */
    private static Set<Class<?>> describedTypes(GrailsApplication grailsApplication) {
        Set<Class<?>> roots = new LinkedHashSet<>(artefactClasses(grailsApplication, DomainClassArtefactHandler.TYPE))
        for (GrailsClass artefact : grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE)) {
            if (!(artefact instanceof GrailsControllerClass)) {
                continue
            }
            try {
                collectRoots((GrailsControllerClass) artefact, roots)
            }
            catch (Exception | LinkageError e) {
                // Described without what it cannot be read for at runtime too.
                LOG.warn('Could not read [{}] to keep what it is described from: {}', artefact.clazz.name, e.message)
                LOG.debug("Could not read [${artefact.clazz.name}]", e)
            }
        }
        reached(roots)
    }

    private static void collectRoots(GrailsControllerClass controller, Set<Class<?>> roots) {
        Class<?> type = controller.clazz
        if (RestfulController.isAssignableFrom(type)) {
            Class<?> resource = GenericTypeResolver.resolveTypeArgument(type, RestfulController)
            if (resource != null && resource != Object) {
                roots << resource
            }
        }
        collectNamedClasses(type, roots)
        for (String actionName : controller.actions) {
            Class<?> command = ActionAnnotations.commandObjectType(type, actionName)
            if (command != null) {
                roots << command
            }
            Method action = ActionAnnotations.actionMethod(type, actionName)
            if (action != null) {
                collectNamedClasses(action, roots)
                action.parameters.each { collectNamedClasses(it, roots) }
            }
        }
    }

    /**
     * The types swagger-core resolves to describe the roots, through the converter the
     * description uses, with the classes they extend, whose members Jackson reads too.
     */
    private static Set<Class<?>> reached(Set<Class<?>> roots) {
        Recorder recorder = new Recorder()
        ModelConverters converters = new ModelConverters(true)
        // behind the Grails converter, which answers for what is not introspected, such as the
        // metaClass of a Groovy object, so only what swagger-core introspects is recorded
        converters.addConverter(recorder)
        converters.addConverter(GrailsModelConverter.INSTANCE)
        // One context for every root, as a document has one, so a type many reach is resolved once.
        ModelConverterContextImpl context = new ModelConverterContextImpl(converters.converters)
        GrailsModelConverter.withTypesOnly {
            for (Class<?> root : roots) {
                try {
                    context.resolve(new AnnotatedType(root).resolveAsRef(true))
                }
                catch (RuntimeException | LinkageError e) {
                    // Described without it at runtime too.
                    LOG.debug("Could not resolve [${root.name}] to keep what it is described from", e)
                }
            }
        }
        Set<Class<?>> kept = new LinkedHashSet<>()
        for (Class<?> type : roots + recorder.reached) {
            for (Class<?> current = type; current != null && isApplicationType(current); current = current.superclass) {
                kept << current
            }
        }
        kept
    }

    private static boolean isApplicationType(Class<?> type) {
        type != Object && !type.primitive && !type.array && !type.name.startsWith('java.') && !type.name.startsWith('javax.')
    }

    private static List<Class<?>> artefactClasses(GrailsApplication grailsApplication, String artefactType) {
        (grailsApplication.getArtefacts(artefactType) ?: new GrailsClass[0])*.clazz.findAll { Class<?> it -> it != null }.toList()
    }

    /**
     * The classes the OpenAPI annotations on an element name, such as the implementation of a
     * response's schema.
     */
    private static void collectNamedClasses(AnnotatedElement element, Set<Class<?>> into) {
        for (Annotation annotation : element.annotations) {
            collectNamedClasses(annotation, into)
        }
    }

    private static void collectNamedClasses(Annotation annotation, Set<Class<?>> into) {
        Class<? extends Annotation> annotationType = annotation.annotationType()
        if (!annotationType.name.startsWith(SWAGGER_ANNOTATIONS)) {
            return
        }
        for (Method attribute : annotationType.declaredMethods) {
            if (attribute.parameterCount != 0) {
                continue
            }
            try {
                collectValue(attribute.invoke(annotation), into)
            }
            catch (Exception | LinkageError e) {
                // A class the application does not have, such as one only compiled against, which
                // the description cannot read at runtime either.
                LOG.warn('Could not read {} of {}: {}', attribute.name, annotationType.simpleName, e.cause?.message ?: e.message)
            }
        }
    }

    private static void collectValue(Object value, Set<Class<?>> into) {
        if (value instanceof Class) {
            Class<?> type = (Class<?>) value
            if (type != Void && !type.annotation && isApplicationType(type)) {
                into << type
            }
        }
        else if (value instanceof Annotation) {
            collectNamedClasses((Annotation) value, into)
        }
        else if (value != null && value.getClass().array) {
            int length = Array.getLength(value)
            for (int i = 0; i < length; i++) {
                collectValue(Array.get(value, i), into)
            }
        }
    }

    /**
     * Notes every type swagger-core resolves, and passes it on.
     */
    private static class Recorder implements ModelConverter {

        final Set<Class<?>> reached = new LinkedHashSet<>()

        @Override
        Schema resolve(AnnotatedType annotatedType, ModelConverterContext context, Iterator<ModelConverter> chain) {
            Type type = annotatedType.type
            if (type != null) {
                Class<?> raw = Json.mapper().constructType(type).rawClass
                if (raw != null) {
                    reached << raw
                }
            }
            chain.hasNext() ? chain.next().resolve(annotatedType, context, chain) : null
        }
    }
}

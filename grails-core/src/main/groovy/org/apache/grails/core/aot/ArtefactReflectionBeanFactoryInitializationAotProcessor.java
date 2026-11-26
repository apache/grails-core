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

import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import grails.core.ArtefactHandler;
import grails.core.ArtefactHandlerAdapter;
import grails.core.GrailsApplication;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;

/**
 * Registers what the framework reaches reflectively as it registers artefacts: the class that represents
 * each kind of artefact, and the artefacts themselves -- the application's and the ones the plugins provide.
 *
 * <p>Every artefact is represented by a {@code GrailsClass} its handler creates through a constructor
 * taking the artefact's class -- a codec by {@code DefaultGrailsCodecClass}, a tag library by
 * {@code DefaultGrailsTagLibClass}. And an artefact is read the way its kind needs: a codec created and
 * given its collaborators through its setters, a URL mappings class asked for the {@code mappings} closure
 * it declares, a controller for its actions. An image keeps none of those members unless asked, so an image
 * built without these stopped while the plugins registered their artefacts, unable to find the constructor
 * of the first codec's {@code GrailsClass}, and then while the URL mappings were evaluated, unable to find
 * the closure the default mappings declare.</p>
 *
 * <p>The build registers the application's own classes from its output, but not the artefacts that come
 * from the framework -- the default URL mappings are one the application holds without having written
 * it -- nor what the plugins provide.</p>
 *
 * <p>Both are read from the application as it was assembled while the hints are written, which is the only
 * place they are all known: a handler names its {@code GrailsClass} in code, and a plugin lists what it
 * provides in a property rather than in its descriptor.</p>
 *
 * @since 9.0
 */
public class ArtefactReflectionBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> representations = representationsOf(beanFactory);
        Set<Class<?>> artefacts = artefactsOf(beanFactory);
        artefacts.addAll(providedByPlugins(beanFactory));
        if (representations.isEmpty() && artefacts.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) -> {
            ReflectionHints reflection = generationContext.getRuntimeHints().reflection();
            for (Class<?> representation : representations) {
                reflection.registerType(representation, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            }
            for (Class<?> artefact : artefacts) {
                reflection.registerType(artefact, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);
            }
        };
    }

    /**
     * The {@code GrailsClass} each handler creates, or none if this is not a Grails application context -- a
     * plain Spring one being generated has no {@code grailsApplication}.
     */
    private Set<Class<?>> representationsOf(ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> representations = new LinkedHashSet<>();
        Object application = beanFactory.getSingleton(GrailsApplication.APPLICATION_ID);
        if (!(application instanceof GrailsApplication grailsApplication)) {
            return representations;
        }
        ArtefactHandler[] handlers = grailsApplication.getArtefactHandlers();
        if (handlers == null) {
            return representations;
        }
        for (ArtefactHandler handler : handlers) {
            if (handler instanceof ArtefactHandlerAdapter adapter && adapter.getGrailsClassImpl() != null) {
                representations.add(adapter.getGrailsClassImpl());
            }
        }
        return representations;
    }

    /** The artefacts the application holds, whichever module they came from. */
    private Set<Class<?>> artefactsOf(ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> artefacts = new LinkedHashSet<>();
        Object application = beanFactory.getSingleton(GrailsApplication.APPLICATION_ID);
        if (!(application instanceof GrailsApplication grailsApplication) || grailsApplication.getAllClasses() == null) {
            return artefacts;
        }
        for (Class<?> artefact : grailsApplication.getAllClasses()) {
            if (artefact != null) {
                artefacts.add(artefact);
            }
        }
        return artefacts;
    }

    /** The artefacts the plugins provide, which the application declares nowhere. */
    private Set<Class<?>> providedByPlugins(ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> provided = new LinkedHashSet<>();
        Object manager = beanFactory.getSingleton(GrailsPluginManager.BEAN_NAME);
        if (!(manager instanceof GrailsPluginManager pluginManager)) {
            return provided;
        }
        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            Class<?>[] artefacts = plugin.getProvidedArtefacts();
            if (artefacts != null) {
                for (Class<?> artefact : artefacts) {
                    if (artefact != null) {
                        provided.add(artefact);
                    }
                }
            }
        }
        return provided;
    }

}

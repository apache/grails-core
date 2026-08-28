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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.javapoet.CodeBlock;

import grails.core.GrailsApplication;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;

/**
 * Writes down the artefacts an application is made of, while they can still be found.
 *
 * <p>They are found two ways, and an image has neither. The classpath is scanned for the classes
 * under the application's own packages, which needs a classpath to walk; and a list the compile-time
 * transform builds as it goes is consulted, which lives in a static field of the transform and is
 * therefore empty in anything the transform did not itself compile.</p>
 *
 * <p>So an image found no controllers, no domain classes and no URL mappings, and the application
 * failed to start on the first bean that wanted one. The only way an application could start was to
 * override {@code classes()} and list its own artefacts by hand, which is a thing to keep in step
 * with the application forever after.</p>
 *
 * <p>Generation runs on an ordinary JVM with the full classpath, where both ways work. What they
 * found is written into the generated code here, and read back by {@link
 * grails.boot.config.GrailsAutoConfiguration#classes()}.</p>
 *
 * @since 8.0
 */
public class ArtefactClassesBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    /**
     * Where the classes are left for {@code classes()} to find. A singleton rather than a bean
     * definition, because it is read while the definitions are still being contributed.
     */
    public static final String BEAN_NAME = "grailsArtefactClasses";

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        List<Class<?>> artefacts = artefactsOf(beanFactory);
        if (artefacts.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) ->
                contribute(artefacts, beanFactoryInitializationCode);
    }

    /**
     * The artefacts the application was found to be made of, or none if this is not a Grails
     * application context -- a plain Spring one being generated has no {@code grailsApplication}.
     */
    private List<Class<?>> artefactsOf(ConfigurableListableBeanFactory beanFactory) {
        List<Class<?>> artefacts = new ArrayList<>();
        Object application = beanFactory.getSingleton(GrailsApplication.APPLICATION_ID);
        if (!(application instanceof GrailsApplication grailsApplication)) {
            return artefacts;
        }
        Class<?>[] allClasses = grailsApplication.getAllClasses();
        if (allClasses == null) {
            return artefacts;
        }
        Set<Class<?>> providedByPlugins = providedByPlugins(beanFactory);
        for (Class<?> artefact : allClasses) {
            if (artefact != null && isNamed(artefact) && !providedByPlugins.contains(artefact)) {
                artefacts.add(artefact);
            }
        }
        return artefacts;
    }

    /**
     * The artefacts the plugins bring with them, which are not the application's to declare.
     *
     * <p>They are registered from the plugins on every start, so writing them down here would have
     * the application claim them as its own -- and a codec or a tag library registered twice, once
     * as a plugin's and once as the application's, is not the same as registered once.</p>
     */
    private Set<Class<?>> providedByPlugins(ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> provided = new LinkedHashSet<>();
        Object manager = beanFactory.getSingleton(GrailsPluginManager.BEAN_NAME);
        if (!(manager instanceof GrailsPluginManager pluginManager)) {
            return provided;
        }
        for (GrailsPlugin plugin : pluginManager.getAllPlugins()) {
            Class<?>[] artefacts = plugin.getProvidedArtefacts();
            if (artefacts != null) {
                provided.addAll(Arrays.asList(artefacts));
            }
        }
        return provided;
    }

    /**
     * Whether the class can be written down and read back. One generated as the application ran --
     * a proxy, or a script compiled from a string -- has a name that resolves to nothing next time.
     */
    private boolean isNamed(Class<?> artefact) {
        return !artefact.isSynthetic() && !artefact.isAnonymousClass() &&
                !artefact.isLocalClass() && artefact.getCanonicalName() != null;
    }

    private void contribute(List<Class<?>> artefacts, BeanFactoryInitializationCode beanFactoryInitializationCode) {
        GeneratedMethod method = beanFactoryInitializationCode.getMethods()
                .add("registerArtefactClasses", builder -> {
                    builder.addJavadoc("Register the artefacts this application is made of.");
                    builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
                    builder.addParameter(ConfigurableListableBeanFactory.class,
                            BeanFactoryInitializationCode.BEAN_FACTORY_VARIABLE);
                    builder.addStatement("$L.registerSingleton($S, $L)",
                            BeanFactoryInitializationCode.BEAN_FACTORY_VARIABLE, BEAN_NAME, arrayOf(artefacts));
                });
        beanFactoryInitializationCode.addInitializer(method.toMethodReference());
    }

    /** The classes as an array literal, in the order they were found, so a run reads as a build did. */
    private CodeBlock arrayOf(List<Class<?>> artefacts) {
        CodeBlock.Builder array = CodeBlock.builder().add("new $T[] {", Class.class);
        for (int i = 0; i < artefacts.size(); i++) {
            array.add(i == 0 ? "$T.class" : ", $T.class", artefacts.get(i));
        }
        return array.add("}").build();
    }
}

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

package org.grails.web.pages;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotationUtils;

import grails.artefact.Artefact;
import grails.core.gsp.GrailsTagLibClass;
import grails.gsp.TagLib;
import org.grails.core.gsp.DefaultGrailsTagLibClass;
import org.grails.taglib.TagLibraryLookup;

/**
 * GSP TagLibraryLookup class that's used for standalone GSP
 *
 * @author Lari Hotari
 * @since 2.4.0
 */
public class StandaloneTagLibraryLookup extends TagLibraryLookup
        implements SmartInitializingSingleton, ApplicationListener<ContextRefreshedEvent> {

    /** What {@link Artefact} marks a tag library with, the way a Grails plugin declares one. */
    private static final String TAG_LIB_ARTEFACT = "TagLib";

    Set<Object> tagLibInstancesSet;

    private StandaloneTagLibraryLookup() {

    }

    public void afterPropertiesSet() {
        registerTagLibraries();
        registerTemplateNamespace();
    }

    protected void registerTagLibraries() {
        if (tagLibInstancesSet != null) {
            for (Object tagLibInstance : tagLibInstancesSet) {
                registerTagLib(new DefaultGrailsTagLibClass(tagLibInstance.getClass()));
            }
        }
    }

    @Override
    protected void putTagLib(Map<String, Object> tags, String name, GrailsTagLibClass taglib) {
        for (Object tagLibInstance : tagLibInstancesSet) {
            if (tagLibInstance.getClass() == taglib.getClazz()) {
                tags.put(name, tagLibInstance);
                break;
            }
        }
    }

    public void setTagLibInstances(List<Object> tagLibInstances) {
        this.tagLibInstancesSet = new LinkedHashSet<>();
        tagLibInstancesSet.addAll(tagLibInstances);
    }

    /**
     * Registers the tag library beans of the context once they all exist, which is before the web
     * server starts accepting requests. A context refreshed event would arrive after it already had,
     * leaving the first render of a page racing the tag libraries it uses.
     */
    @Override
    public void afterSingletonsInstantiated() {
        detectAndRegisterTabLibBeans();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        detectAndRegisterTabLibBeans();
    }

    public void detectAndRegisterTabLibBeans() {
        if (tagLibInstancesSet == null) {
            tagLibInstancesSet = new LinkedHashSet<>();
        }
        register(applicationContext.getBeansWithAnnotation(TagLib.class).values());
        register(tagLibraryArtefacts());
    }

    /**
     * The tag libraries of a Grails plugin, which are marked {@code @Artefact("TagLib")} rather than
     * {@code @TagLib} - the asset pipeline's {@code <asset:...>} library among them. An application
     * that declares one as a bean of its context can use it here as it would in a Grails
     * application, where the same class is found by scanning artefacts instead.
     */
    private Collection<Object> tagLibraryArtefacts() {
        Collection<Object> artefacts = new LinkedHashSet<>();
        for (Object bean : applicationContext.getBeansWithAnnotation(Artefact.class).values()) {
            Artefact artefact = AnnotationUtils.findAnnotation(bean.getClass(), Artefact.class);
            if (artefact != null && TAG_LIB_ARTEFACT.equals(artefact.value())) {
                artefacts.add(bean);
            }
        }
        return artefacts;
    }

    private void register(Collection<Object> detectedInstances) {
        for (Object instance : detectedInstances) {
            if (!tagLibInstancesSet.contains(instance)) {
                tagLibInstancesSet.add(instance);
                registerTagLib(new DefaultGrailsTagLibClass(instance.getClass()));
            }
        }
    }
}

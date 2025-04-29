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

import grails.core.gsp.GrailsTagLibClass;
import grails.gsp.TagLib;
import org.grails.core.gsp.DefaultGrailsTagLibClass;
import org.grails.taglib.TagLibraryLookup;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.*;

/**
 * GSP TagLibraryLookup class that's used for standalone GSP 
 * 
 * @author Lari Hotari
 * @since 2.4.0
 */
public class StandaloneTagLibraryLookup extends TagLibraryLookup implements ApplicationListener<ContextRefreshedEvent> {
    Set<Object> tagLibInstancesSet;
    
    private StandaloneTagLibraryLookup() {
        
    }

    public void afterPropertiesSet() {
        registerTagLibraries();
        registerTemplateNamespace();
    }

    protected void registerTagLibraries() {
        if(tagLibInstancesSet != null) {
            for(Object tagLibInstance : tagLibInstancesSet) {
                registerTagLib(new DefaultGrailsTagLibClass(tagLibInstance.getClass()));
            }
        }
    }

    @Override
    protected void putTagLib(Map<String, Object> tags, String name, GrailsTagLibClass taglib) {
        for(Object tagLibInstance : tagLibInstancesSet) {
            if(tagLibInstance.getClass() == taglib.getClazz()) {
                tags.put(name, tagLibInstance);
                break;
            }
        }
    }

    public void setTagLibInstances(List<Object> tagLibInstances) {
        this.tagLibInstancesSet = new LinkedHashSet<Object>();
        tagLibInstancesSet.addAll(tagLibInstances);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        detectAndRegisterTabLibBeans();
    }

    public void detectAndRegisterTabLibBeans() {
        if(tagLibInstancesSet==null) {
            tagLibInstancesSet = new LinkedHashSet<Object>();
        }
        Collection<Object> detectedInstances = applicationContext.getBeansWithAnnotation(TagLib.class).values();
        for(Object instance : detectedInstances) {
            if(!tagLibInstancesSet.contains(instance)) {
                tagLibInstancesSet.add(instance);
                registerTagLib(new DefaultGrailsTagLibClass(instance.getClass()));
            }
        }
    }
}
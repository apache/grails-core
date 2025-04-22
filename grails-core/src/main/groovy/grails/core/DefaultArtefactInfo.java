/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.core;

import grails.core.ArtefactInfo;
import grails.core.GrailsClass;
import grails.core.InjectableGrailsClass;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Mutable holder of artefact info.
 *
 * @author Marc Palmer (marc@anyware.co.uk)
 * @author Graeme Rocher
 */
public class DefaultArtefactInfo implements ArtefactInfo {

    private LinkedList<GrailsClass> grailsClasses = new LinkedList<GrailsClass>();
    private Class<?>[] classes;
    private Map<String, GrailsClass> grailsClassesByName = new LinkedHashMap<String, GrailsClass>();
    private Map<String, Class<?>> classesByName = new LinkedHashMap<String, Class<?>>();
    private Map<String, GrailsClass> logicalPropertyNameToClassMap = new HashMap<String, GrailsClass>();
    @SuppressWarnings("rawtypes")
    public Map handlerData = new HashMap();
    private GrailsClass[] grailsClassesArray;

    /**
     * <p>Call to add a new class to this info object.</p>
     * <p>You <b>must</b> call refresh() later to update the arrays</p>
     * @param artefactClass
     */
    public synchronized void addGrailsClass(GrailsClass artefactClass) {
        addGrailsClassInternal(artefactClass, false);
    }

    private void addGrailsClassInternal(GrailsClass artefactClass, boolean atStart) {
        grailsClassesByName = new LinkedHashMap<String, GrailsClass>(grailsClassesByName);
        classesByName = new LinkedHashMap<String, Class<?>>(classesByName);

        Class<?> actualClass = artefactClass.getClazz();
        boolean addToGrailsClasses = true;
        if (artefactClass instanceof InjectableGrailsClass) {
            addToGrailsClasses = ((InjectableGrailsClass)artefactClass).getAvailable();
        }
        if (addToGrailsClasses) {
            GrailsClass oldVersion = grailsClassesByName.put(actualClass.getName(), artefactClass);
            grailsClasses.remove(oldVersion);
        }
        classesByName.put(actualClass.getName(), actualClass);
        logicalPropertyNameToClassMap.put(artefactClass.getLogicalPropertyName(), artefactClass);

        if (!grailsClasses.contains(artefactClass)) {
            if (atStart) {
                grailsClasses.addFirst(artefactClass);
            }
            else {
                grailsClasses.addLast(artefactClass);
            }
        }
    }

    /**
     * Refresh the arrays generated from the maps.
     */
    public synchronized void updateComplete() {
        grailsClassesByName = Collections.unmodifiableMap(grailsClassesByName);
        classesByName = Collections.unmodifiableMap(classesByName);

        grailsClassesArray = grailsClasses.toArray(new GrailsClass[grailsClasses.size()]);
        // Make classes array
        classes = classesByName.values().toArray(new Class[classesByName.size()]);
    }

    public Class<?>[] getClasses() {
        return classes;
    }

    public GrailsClass[] getGrailsClasses() {
        return grailsClassesArray;
    }

    public Map<String, Class<?>> getClassesByName() {
        return classesByName;
    }

    public Map<String, GrailsClass> getGrailsClassesByName() {
        return grailsClassesByName;
    }

    public GrailsClass getGrailsClass(String name) {
        return grailsClassesByName.get(name);
    }

    public GrailsClass getGrailsClassByLogicalPropertyName(String logicalName) {
        return logicalPropertyNameToClassMap.get(logicalName);
    }

    public void addOverridableGrailsClass(GrailsClass artefactGrailsClass) {
        addGrailsClassInternal(artefactGrailsClass, true);
    }
}

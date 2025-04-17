/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.core.artefact.gsp;

import java.util.HashMap;
import java.util.Map;

import grails.core.ArtefactHandlerAdapter;
import grails.core.ArtefactInfo;
import grails.core.GrailsClass;
import grails.core.gsp.GrailsTagLibClass;
import org.apache.commons.logging.LogFactory;
import org.grails.core.gsp.DefaultGrailsTagLibClass;

/**
 * Configures tag libraries within namespaces in Grails.
 *
 * @author Marc Palmer (marc@anyware.co.uk)
 * @author Graeme Rocher
 * @author a.shneyderman
 *
 * @since 3.3
 */
public class TagLibArtefactHandler extends ArtefactHandlerAdapter {

    public static final String PLUGIN_NAME = "groovyPages";
    public static final String TYPE = "TagLib";

    private Map<String, GrailsTagLibClass> tag2libMap = new HashMap<String, GrailsTagLibClass>();
    private Map<String, GrailsTagLibClass> namespace2tagLibMap = new HashMap<String, GrailsTagLibClass>();

    public TagLibArtefactHandler() {
        super(TYPE, GrailsTagLibClass.class, DefaultGrailsTagLibClass.class, TYPE);
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    /**
     * Creates a map of tags (keyed on "${namespace}:${tagName}") to tag libraries.
     */
    @Override
    public void initialize(ArtefactInfo artefacts) {
        tag2libMap = new HashMap<String, GrailsTagLibClass>();
        for (GrailsClass aClass : artefacts.getGrailsClasses()) {
            GrailsTagLibClass taglibClass = (GrailsTagLibClass) aClass;
            String namespace = taglibClass.getNamespace();
            namespace2tagLibMap.put(namespace, taglibClass);
            for (Object o : taglibClass.getTagNames()) {
                String tagName = namespace + ":" + o;
                if (!tag2libMap.containsKey(tagName)) {
                    tag2libMap.put(tagName, taglibClass);
                }
                else {
                    GrailsTagLibClass current = tag2libMap.get(tagName);
                    if (!taglibClass.equals(current)) {
                        LogFactory.getLog(TagLibArtefactHandler.class).info("There are conflicting tags: " + taglibClass.getFullName() + "." +
                                tagName + " vs. " + current.getFullName() + "." + tagName +
                                ". The former will take precedence.");
                        tag2libMap.put(tagName, taglibClass);
                    }
                }
            }
        }
    }

    /**
     * Looks up a tag library by using either a full qualified tag name such as g:link or
     * via namespace such as "g".
     *
     * @param feature The tag name or namespace
     * @return A GrailsClass instance representing the tag library
     */
    @Override
    public GrailsClass getArtefactForFeature(Object feature) {
        final Object tagLib = tag2libMap.get(feature);
        if (tagLib!= null) {
            return (GrailsClass) tagLib;
        }

        return namespace2tagLibMap.get(feature);
    }
}

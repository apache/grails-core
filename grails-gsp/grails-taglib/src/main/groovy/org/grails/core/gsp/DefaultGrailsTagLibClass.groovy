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
package org.grails.core.gsp

import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO

import grails.core.gsp.GrailsTagLibClass
import org.grails.core.AbstractInjectableGrailsClass
import org.grails.core.artefact.gsp.TagLibArtefactHandler
import org.grails.taglib.TagMethodInvoker
import org.grails.taglib.discovery.ReflectedTagLibraryView
import org.grails.taglib.discovery.TagDiscoveryRules

/**
 * Default implementation of a tag lib class.
 *
 * @author Graeme Rocher
 *
 */
@CompileStatic
@POJO
class DefaultGrailsTagLibClass extends AbstractInjectableGrailsClass implements GrailsTagLibClass {

    protected static final String TAG_LIB = TagLibArtefactHandler.TYPE

    private Set<String> tags = new HashSet<>()
    private String namespace = GrailsTagLibClass.DEFAULT_NAMESPACE
    private Set<String> returnObjectForTagsSet = new HashSet<>()
    private Object defaultEncodeAs = null
    private Map<String, Object> encodeAsForTags = new HashMap<>()

    /**
     * Default constructor.
     *
     * @param clazz        the tag library class
     */
    @SuppressWarnings('rawtypes')
    DefaultGrailsTagLibClass(Class<?> clazz) {
        super(clazz, TagLibArtefactHandler.TYPE)

        for (MetaProperty prop in GroovySystem.getMetaClassRegistry().getMetaClass(clazz).getProperties()) {
            int modifiers = prop.getModifiers()
            if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
                continue
            }

            if (Closure.isAssignableFrom(prop.getType())) {
                tags.add(prop.getName())
            }
        }
        tags.addAll(TagMethodInvoker.getInvokableTagMethodNames(clazz))

        // Closure-typed tags are also read directly from the class, because the metaclass does not
        // always report them as properties (with @CompileStatic at the class level, a Closure
        // property may not be compiled as one). Read through the shared rules rather than walking
        // the hierarchy here, so that the set a build records and the set registered here are
        // produced by the same code and cannot describe different tags.
        tags.addAll(TagDiscoveryRules.findTags(new ReflectedTagLibraryView(clazz)))

        String ns = getStaticPropertyValue(NAMESPACE_FIELD_NAME, String)
        if (ns != null && !''.equals(ns.trim())) {
            namespace = ns.trim()
        }

        List returnObjectForTagsList = getStaticPropertyValue(RETURN_OBJECT_FOR_TAGS_FIELD_NAME, List)
        if (returnObjectForTagsList != null) {
            for (Object tagName in returnObjectForTagsList) {
                returnObjectForTagsSet.add(String.valueOf(tagName))
            }
        }

        defaultEncodeAs = getStaticPropertyValue(DEFAULT_ENCODE_AS_FIELD_NAME, Object)

        Map encodeAsForTagsMap = getStaticPropertyValue(ENCODE_AS_FOR_TAGS_FIELD_NAME, Map)
        if (encodeAsForTagsMap != null) {
            for (Map.Entry entry in encodeAsForTagsMap.entrySet()) {
                encodeAsForTags.put(entry.getKey().toString(), entry.getValue())
            }
        }
    }

    boolean hasTag(String tagName) {
        return tags.contains(tagName)
    }

    Set<String> getTagNames() {
        return tags
    }

    String getNamespace() {
        return namespace
    }

    Set<String> getTagNamesThatReturnObject() {
        return returnObjectForTagsSet
    }

    Object getEncodeAsForTag(String tagName) {
        return encodeAsForTags.get(tagName)
    }

    Object getDefaultEncodeAs() {
        return defaultEncodeAs
    }
}

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
package org.grails.gsp.compiler.tags

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import org.grails.taglib.GrailsTagException

/**
 * A registry for holding all Grails tag implementations.
 *
 * @author Graeme Rocher
 */
@CompileStatic
class GrailsTagRegistry {

    private static GrailsTagRegistry instance = new GrailsTagRegistry()

    private static Map<String, Class<?>> tagRegistry = new ConcurrentHashMap<>()

    static {
        instance.registerTag(GroovyEachTag.TAG_NAME, GroovyEachTag)
        instance.registerTag(GroovyIfTag.TAG_NAME, GroovyIfTag)
        instance.registerTag(GroovyUnlessTag.TAG_NAME, GroovyUnlessTag)
        instance.registerTag(GroovyElseTag.TAG_NAME, GroovyElseTag)
        instance.registerTag(GroovyElseIfTag.TAG_NAME, GroovyElseIfTag)
        instance.registerTag(GroovyFindAllTag.TAG_NAME, GroovyFindAllTag)
        instance.registerTag(GroovyCollectTag.TAG_NAME, GroovyCollectTag)
        instance.registerTag(GroovyGrepTag.TAG_NAME, GroovyGrepTag)
        instance.registerTag(GroovyWhileTag.TAG_NAME, GroovyWhileTag)
        instance.registerTag(GroovyDefTag.TAG_NAME, GroovyDefTag)
    }

    private GrailsTagRegistry() {
        // singleton
    }

    static GrailsTagRegistry getInstance() {
        return instance
    }

    void registerTag(String tagName, Class<?> tag) {
        tagRegistry.put(tagName, tag)
    }

    boolean tagSupported(String tagName) {
        return tagRegistry.containsKey(tagName)
    }

    boolean isSyntaxTag(String tagName) {
        if (tagRegistry.containsKey(tagName)) {
            return GroovySyntaxTag.isAssignableFrom(tagRegistry.get(tagName))
        }
        return false
    }

    GrailsTag newTag(String tagName) {
        if (!tagRegistry.containsKey(tagName)) {
            throw new GrailsTagException('Tag [' + tagName + '] is not a a valid grails tag')
        }

        Class<?> tagClass = tagRegistry.get(tagName)

        try {
            return (GrailsTag) tagClass.getDeclaredConstructor().newInstance()
        }
        catch (InstantiationException | InvocationTargetException | NoSuchMethodException e) {
            throw new GrailsTagException('Instantiation error loading tag [' + tagName + ']: ' + e.getMessage(), e)
        }
        catch (IllegalAccessException e) {
            throw new GrailsTagException('Illegal access error loading tag [' + tagName + ']: ' + e.getMessage(), e)
        }
    }

}

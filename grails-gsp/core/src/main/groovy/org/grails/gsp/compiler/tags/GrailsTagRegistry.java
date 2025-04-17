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
package org.grails.gsp.compiler.tags;

import org.grails.taglib.GrailsTagException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry for holding all Grails tag implementations.
 *
 * @author Graeme Rocher
 */
public class GrailsTagRegistry {

    private static GrailsTagRegistry instance = new GrailsTagRegistry();

    private static Map<String, Class<?>> tagRegistry = new ConcurrentHashMap<String, Class<?>>();

    static {
        instance.registerTag(GroovyEachTag.TAG_NAME,    GroovyEachTag.class);
        instance.registerTag(GroovyIfTag.TAG_NAME,      GroovyIfTag.class);
        instance.registerTag(GroovyUnlessTag.TAG_NAME,  GroovyUnlessTag.class);
        instance.registerTag(GroovyElseTag.TAG_NAME,    GroovyElseTag.class);
        instance.registerTag(GroovyElseIfTag.TAG_NAME,  GroovyElseIfTag.class);
        instance.registerTag(GroovyFindAllTag.TAG_NAME, GroovyFindAllTag.class);
        instance.registerTag(GroovyCollectTag.TAG_NAME, GroovyCollectTag.class);
        instance.registerTag(GroovyGrepTag.TAG_NAME,    GroovyGrepTag.class);
        instance.registerTag(GroovyWhileTag.TAG_NAME,   GroovyWhileTag.class);
        instance.registerTag(GroovyDefTag.TAG_NAME,     GroovyDefTag.class);
    }

    private GrailsTagRegistry() {
        // singleton
    }

    public static GrailsTagRegistry getInstance() {
        return instance;
    }

    public void registerTag(String tagName, Class<?> tag) {
        tagRegistry.put(tagName, tag);
    }

    public boolean tagSupported(String tagName) {
        return tagRegistry.containsKey(tagName);
    }

    public boolean isSyntaxTag(String tagName) {
        if (tagRegistry.containsKey(tagName)) {
            return GroovySyntaxTag.class.isAssignableFrom(tagRegistry.get(tagName));
        }
        return false;
    }

    public GrailsTag newTag(String tagName) {
        if (!tagRegistry.containsKey(tagName)) {
            throw new GrailsTagException("Tag [" + tagName + "] is not a a valid grails tag");
        }

        Class<?> tagClass = tagRegistry.get(tagName);

        try {
            return (GrailsTag)tagClass.newInstance();
        }
        catch (InstantiationException e) {
            throw new GrailsTagException("Instantiation error loading tag ["+tagName+"]: " + e.getMessage(), e);
        }
        catch (IllegalAccessException e) {
            throw new GrailsTagException("Illegal access error loading tag ["+tagName+"]: " + e.getMessage(), e);
        }
    }
}

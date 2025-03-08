/* Copyright 2004-2005 the original author or authors.
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
package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Handles the binding Grails domain classes and properties to the Hibernate runtime meta model.
 * Based on the HbmBinder code in Hibernate core and influenced by AnnotationsBinder.
 *
 * @author Graeme Rocher
 * @since 0.1
 */
public abstract class AbstractGrailsDomainBinder {
    protected static final Map<Class<?>, Mapping> MAPPING_CACHE = new HashMap<>();


    /**
     * Obtains a mapping object for the given domain class nam
     *
     * @param theClass The domain class in question
     * @return A Mapping object or null
     */
    public static Mapping getMapping(Class<?> theClass) {
        return theClass == null ? null : MAPPING_CACHE.get(theClass);
    }

    /**
     * Obtains a mapping object for the given domain class nam
     *
     * @param theClass The domain class in question
     * @return A Mapping object or null
     */
    static void cacheMapping(Class<?> theClass, Mapping mapping) {
        MAPPING_CACHE.put(theClass, mapping);
    }

    /**
     * Obtains a mapping object for the given domain class nam
     *
     * @param domainClass The domain class in question
     * @return A Mapping object or null
     */
    public static Mapping getMapping(PersistentEntity domainClass) {
        return domainClass == null ? null : MAPPING_CACHE.get(domainClass.getJavaClass());
    }

    public static void clearMappingCache() {
        MAPPING_CACHE.clear();
    }

    public static void clearMappingCache(Class<?> theClass) {
        String className = theClass.getName();
        for(Iterator<Map.Entry<Class<?>, Mapping>> it = MAPPING_CACHE.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Class<?>, Mapping> entry = it.next();
            if (className.equals(entry.getKey().getName())) {
                it.remove();
            }
        }
    }
}


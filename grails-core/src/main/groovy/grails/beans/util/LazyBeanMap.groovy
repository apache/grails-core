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
package grails.beans.util

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher


/**
 * <p>A map that backs onto a bean. The map is not initialized from the beans property values on creation, but instead lazily reads the values on demand.
 * No caching is applied, so each read results in a reflective call to retrieve the property. Users of this API should apply their own caching strategy if necessary.
 * </p>
 * <p>
 * Note that the {@link Map#values()} and {@link Map#entrySet()} implementations are expensive operations as they will read all properties of the target bean and should be used with caution.
 * </p>
 * <p>
 * Additionally the {@link Map#remove(java.lang.Object)} and {@link Map#clear()} methods are not implemented and will throw {@link UnsupportedOperationException}.
 * </p>
 * @since 2.4
 * @author Graeme Rocher
 * @deprecated use {@link LazyMetaPropertyMap} instead
 */
@CompileStatic
class LazyBeanMap implements Map<String,Object>{
    final private ClassPropertyFetcher cpf
    final Object target

    /**
     * Creates a bean map
     *
     * @param target The target bean
     */
    LazyBeanMap(Object target) {
        this.target = target
        if(target != null) {
            this.cpf = ClassPropertyFetcher.forClass(target.getClass())
        }
    }

    @Override
    int size() {
        return cpf ? cpf.metaProperties.size() : 0
    }

    @Override
    boolean isEmpty() { cpf ? false : true}

    @Override
    boolean containsKey(Object key) {
        cpf != null && cpf.getPropertyType(key.toString(), true) != null
    }

    @Override
    boolean containsValue(Object value) {
        cpf != null && values().contains(value)
    }

    @Override
    def get(Object key) {
        if(!cpf) return null
        def property = key.toString()
        if(cpf.isReadableProperty(property))
            cpf.getPropertyValue target, property
        else
            return null
    }

    @Override
    def put(String key, def value) {
        if(!cpf) return null
        def old = get(key)
        def mc = GroovySystem.metaClassRegistry.getMetaClass(target.getClass())
        mc.setProperty(target, key, value)
        return old
    }

    @Override
    Object remove(Object key) {
        throw new UnsupportedOperationException("Method remove(key) not implemented")
    }

    @Override
    void putAll(Map<? extends String, ?> m) {
        if(!cpf) return
        for(String property in m.keySet()) {
            put(property, m.get(property))
        }
    }

    @Override
    void clear() {
        throw new UnsupportedOperationException("Method clear() not implemented")
    }

    @Override
    Set<String> keySet() {
        if(!cpf) return [] as Set<String>
        else {
            return new HashSet<String>( cpf.metaProperties.collect { MetaProperty pd -> pd.name} )
        }
    }

    @Override
    Collection<Object> values() {
        if(!cpf) return []
        else {
            keySet().collect() { String property -> ((ClassPropertyFetcher) /*Cast needed after upgrade to Groovy 4.0.22*/ cpf).getPropertyValue(property) }
        }
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        if(!cpf) return [] as Set<Map.Entry<String, Object>>
        else {
            return new HashSet<Map.Entry<String, Object>>(
                    keySet().collect() { String property -> new AbstractMap.SimpleEntry<String, Object>(property, ((ClassPropertyFetcher) /*Cast needed after upgrade to Groovy 4.0.22*/ cpf).getPropertyValue(property)) }
            )
        }
    }
}

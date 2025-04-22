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
package grails.beans.util;

import groovy.lang.GroovySystem;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaClass;
import groovy.lang.MetaProperty;
import groovy.transform.CompileStatic;
import groovy.util.MapEntry;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.reflect.NameUtils;
import org.springframework.util.Assert;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * A map implementation that reads an objects properties lazily using Groovy's MetaClass.
 *
 * @author Graeme Rocher
 */
@SuppressWarnings({"unchecked","rawtypes"})
@CompileStatic
public class LazyMetaPropertyMap implements Map {

    private MetaClass metaClass;
    private Object instance;
    private static List<String> EXCLUDES = Arrays.asList("class", "constraints", "hasMany", "mapping", "properties", GormProperties.IDENTITY, GormProperties.VERSION, "domainClass", "dirty", GormProperties.ERRORS, "dirtyPropertyNames");

    /**
     * Constructs the map
     * @param o The object to inspect
     */
    public LazyMetaPropertyMap(Object o) {
        Assert.notNull(o, "Object cannot be null");

        instance = o;
        metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(o.getClass());
    }

    /**
     * {@inheritDoc}
     * @see java.util.Map#size()
     */
    public int size() {
        return keySet().size();
    }

    /**
     * {@inheritDoc}
     * @see java.util.Map#isEmpty()
     */
    public boolean isEmpty() {
        return false; // will never be empty
    }

    /**
     * {@inheritDoc}
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object propertyName) {
        if (propertyName instanceof CharSequence) propertyName = propertyName.toString();
        Assert.isInstanceOf(String.class, propertyName, "This map implementation only supports String based keys!");

        String pn = propertyName.toString();
        return !NameUtils.isConfigurational(pn) && metaClass.getMetaProperty(pn) != null;
    }

    /**
     * Checks whether the specified value is contained within the Map. Note that because this implementation
     * lazily initialises property values the behaviour may not be consistent with the actual values of the
     * contained object unless they have already been initialised by calling get(Object)
     *
     * @see java.util.Map#containsValue(Object)
     */
    public boolean containsValue(Object o) {
        return values().contains(o);
    }

    /**
     * Obtains the value of an object's properties on demand using Groovy's MOP.
     *
     * @param propertyName The name of the property
     * @return The property value or null
     */
    public Object get(Object propertyName) {
        if (propertyName instanceof CharSequence) {
            propertyName = propertyName.toString();
        }

        if (propertyName instanceof List) {
            Map submap = new HashMap();
            List propertyNames = (List)propertyName;
            for (Object currentName : propertyNames) {
                if (currentName != null) {
                    currentName = currentName.toString();
                    if (containsKey(currentName)) {
                        submap.put(currentName, get(currentName));
                    }
                }
            }
            return submap;
        }

        if (NameUtils.isConfigurational(propertyName.toString())) {
            return null;
        }

        Object val = null;
        MetaProperty mp = metaClass.getMetaProperty(propertyName.toString());
        if (mp != null) {
            val = mp.getProperty(instance);
        }
        return val;
    }

    public Object put(Object propertyName, Object propertyValue) {
        if (propertyName instanceof CharSequence) {
            propertyName = propertyName.toString();
        }

        Object old = null;
        MetaProperty mp = metaClass.getMetaProperty((String)propertyName);
        if (mp != null && !isExcluded(mp)) {
            old = mp.getProperty(instance);
            if (propertyValue instanceof Map) {
                propertyValue = ((Map)propertyValue).get(propertyName);
            }
            mp.setProperty(instance, propertyValue);
        }
        return old;
    }

    /**
     * @throws UnsupportedOperationException
     */
    public Object remove(Object o) {
        throw new UnsupportedOperationException("Method remove(Object o) is not supported by this implementation");
    }

    public void putAll(Map map) {
        for (Object key : map.keySet()) {
            put(key, map.get(key));
        }
    }

    /**
     * @throws UnsupportedOperationException
     */
    public void clear() {
        throw new UnsupportedOperationException("Method clear() is not supported by this implementation");
    }

    public Set<String> keySet() {
        Set<String> names = new HashSet<>();
        for (MetaProperty mp : metaClass.getProperties()) {
            if (isExcluded(mp)) continue;
            names.add(mp.getName());
        }
        return names;
    }

    public Collection<Object> values() {
        Collection<Object> values = new ArrayList<>();
        for (MetaProperty mp : metaClass.getProperties()) {
            if (isExcluded(mp)) continue;
            values.add(mp.getProperty(instance));
        }
        return values;
    }

    @Override
    public int hashCode() {
        return instance.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LazyMetaPropertyMap) {
            LazyMetaPropertyMap other = (LazyMetaPropertyMap)o;
            return instance.equals(other.getInstance());
        }
        return false;
    }

    /**
     * Returns the wrapped instance.
     *
     * @return The wrapped instance
     */
    public Object getInstance() {
        return instance;
    }

    public Set<MapEntry> entrySet() {
        Set<MapEntry> entries = new HashSet<>();
        for (MetaProperty mp : metaClass.getProperties()) {
            if (isExcluded(mp)) continue;

            entries.add(new MapEntry(mp.getName(), mp.getProperty(instance)));
        }
        return entries;
    }

    private boolean isExcluded(MetaProperty mp) {
        return
                Modifier.isStatic(mp.getModifiers()) ||
                        EXCLUDES.contains(mp.getName()) ||
                        NameUtils.isConfigurational(mp.getName()) ||
                        (mp instanceof MetaBeanProperty) && (((MetaBeanProperty) mp).getGetter()) == null;
    }
}

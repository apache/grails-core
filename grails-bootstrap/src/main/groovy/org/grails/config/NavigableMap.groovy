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
package org.grails.config

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.runtime.DefaultGroovyMethods

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @deprecated This class is deprecated to reduce complexity, improve performance, and increase maintainability. Use {@code config.getProperty(String key, Class<T> targetType)} instead.
 */
@Deprecated
@EqualsAndHashCode
@CompileStatic
class NavigableMap implements Map<String, Object>, Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(NavigableMap)
    private static final int MAX_DOTTED_BRACKETS = 8

    final NavigableMap rootConfig
    final List<String> path
    final Map<String, Object> delegateMap
    final String dottedPath

    NavigableMap() {
        rootConfig = this
        path = []
        dottedPath = ''
        delegateMap = new LinkedHashMap<>()
    }

    NavigableMap(NavigableMap rootConfig, List<String> path) {
        super()
        this.rootConfig = rootConfig
        this.path = path
        dottedPath = path.join('.')
        delegateMap = new LinkedHashMap<>()
    }

    private NavigableMap(NavigableMap rootConfig, List<String> path, Map<String, Object> delegateMap) {
        this.rootConfig = rootConfig
        this.path = path
        dottedPath = path.join('.')
        this.delegateMap = delegateMap
    }

    @Override
    String toString() {
        delegateMap.toString()
    }

    @Override
    NavigableMap clone() {
        new NavigableMap(getRootConfig(), getPath(), new LinkedHashMap<>(getDelegateMap()))
    }

    @Override
    int size() {
        delegateMap.size()
    }

    @Override
    boolean isEmpty() {
        delegateMap.isEmpty()
    }

    @Override
    boolean containsKey(Object key) {
        delegateMap.containsKey(key)
    }

    @Override
    boolean containsValue(Object value) {
        delegateMap.containsValue(value)
    }

    @CompileDynamic
    @Override
    Object get(Object key) {
        Object result = delegateMap.get(key)
        if (result != null) {
            return result
        }
        null
    }

    @Override
    Object put(String key, Object value) {
        delegateMap.put(key, value)
    }

    @Override
    Object remove(Object key) {
        delegateMap.remove(key)
    }

    @Override
    void putAll(Map<? extends String, ? extends Object> m) {
        delegateMap.putAll(m)
    }

    @Override
    void clear() {
        delegateMap.clear()
    }

    @Override
    Set<String> keySet() {
        delegateMap.keySet()
    }

    @Override
    Collection<Object> values() {
        delegateMap.values()
    }

    @Override
    Set<Map.Entry<String, Object>> entrySet() {
        delegateMap.entrySet()
    }

    void merge(Map sourceMap, boolean parseFlatKeys = false) {
        mergeMaps(this, '', this, sourceMap, parseFlatKeys)
    }

    private void mergeMaps(NavigableMap rootMap,
                           String path,
                           NavigableMap targetMap,
                           Map sourceMap,
                           boolean parseFlatKeys) {

        for (Entry entry in sourceMap) {
            Object sourceKeyObject = entry.key
            Object sourceValue = entry.value
            String sourceKey = String.valueOf(sourceKeyObject)
            if (sourceValue instanceof HeldList && !targetMap.is(rootMap) && holdsIndexedEntries(sourceMap, sourceKey, (List) sourceValue)) {
                // the indexed entries beside it, rows[0] and on, write the list element by element
                continue
            }
            if (parseFlatKeys) {
                String[] keyParts = sourceKey.split(/\./)
                if (keyParts.length > 1) {
                    mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
                    def pathParts = keyParts[0..-2]
                    Map actualTarget = targetMap.navigateSubMap(pathParts as List, true)
                    String indexedKey = sourceKey
                    sourceKey = keyParts[-1]
                    // the flat key of the entry is its full path: that of the map holding the dotted key, then the key
                    String nestedPath = path ? "${path}.${pathParts.join('.')}" : pathParts.join('.')
                    mergeMapEntry(rootMap, nestedPath, actualTarget, sourceKey, sourceValue, parseFlatKeys)
                    holdIndexedContainer(targetMap, indexedKey)
                } else {
                    mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
                }
            } else {
                mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
            }
        }
    }

    /**
     * Holds the list or map that an indexed key with a property after the index, such as {@code a.rows[0].name}, writes
     * in the nested map that owns it, so that {@code navigate('a', 'rows')} and {@code config.a.rows} read it. The list
     * the owner already holds is the one held, as written element by element from application.yml or by a profile's
     * list over it; only when it holds none is it built from the list under the flat key. An element that is a map is
     * held as a nested map, which the owner also holds under the indexed name, {@code rows[0]}, and which holds the
     * element as application.yml gives it. Only the element just written is updated, so each indexed key costs the same
     * however long the list is. The flat keys are left as they are, so the environment presents the same names and
     * values as before.
     */
    private void holdIndexedContainer(NavigableMap targetMap, String indexedKey) {
        int subscriptStart = indexedKey.indexOf('[')
        int subscriptEnd = subscriptStart > -1 ? indexedKey.indexOf(']', subscriptStart) : -1
        if (subscriptEnd < 0 || indexedKey.indexOf('.', subscriptEnd) < 0) {
            return
        }
        String containerKey = indexedKey.substring(0, subscriptStart)
        int lastDot = containerKey.lastIndexOf('.')
        if (lastDot < 0 && targetMap.getPath().isEmpty()) {
            // the root holds the container under its own key, as the subscript wrote it, as 7.0.x always did
            return
        }
        // a map below the root, such as an element of a list, owns a container it holds itself, cols of rows[0].cols[0]
        NavigableMap owner = lastDot < 0 ? targetMap : targetMap.navigateSubMap(containerKey.substring(0, lastDot).tokenize('.'), false)
        if (owner == null) {
            return
        }
        String name = containerKey.substring(lastDot + 1)
        String written = indexedKey.substring(subscriptStart + 1, subscriptEnd)
        String writtenField = indexedKey.substring(subscriptEnd + 2)
        Object container = targetMap.get(containerKey)
        Object current = owner.get(name)
        if (container instanceof List) {
            if (!written.isInteger()) {
                return
            }
            HeldList held
            if (current instanceof HeldList) {
                held = (HeldList) current
            } else {
                boolean fromFlatKey = !(current instanceof List)
                List source = fromFlatKey ? (List) container : (List) current
                held = new HeldList()
                for (int i = 0; i < source.size(); i++) {
                    String index = String.valueOf(i)
                    Object element = fromFlatKey ? nestedOrElement(owner, "${name}[${index}]".toString(), source.get(i)) : source.get(i)
                    held.add(index == written ? element : heldElement(targetMap, owner, containerKey, name, index, element, null))
                }
            }
            int index = written.toInteger()
            Object element = index < held.size() ? held.get(index) : null
            if (element == null && index < ((List) container).size()) {
                element = ((List) container).get(index)
            }
            fill(held, index, null)
            held.set(index, heldElement(targetMap, owner, containerKey, name, written, element, writtenField))
            owner.put(name, held)
        } else if (container instanceof Map) {
            NavigableMap held
            if (current instanceof NavigableMap) {
                held = (NavigableMap) current
            } else {
                boolean fromFlatKey = !(current instanceof Map)
                Map source = fromFlatKey ? (Map) container : (Map) current
                held = new NavigableMap(owner.getRootConfig(), (owner.getPath() + [name]).asImmutable())
                for (Map.Entry entry in ((Map) source).entrySet()) {
                    String key = String.valueOf(entry.key)
                    Object element = fromFlatKey ? nestedOrElement(owner, "${name}[${key}]".toString(), entry.value) : entry.value
                    held.put(key, key == written ? element : heldElement(targetMap, owner, containerKey, name, key, element, null))
                }
            }
            Object element = held.containsKey(written) ? held.get(written) : ((Map) container).get(written)
            held.put(written, heldElement(targetMap, owner, containerKey, name, written, element, writtenField))
            owner.put(name, held)
        }
    }

    /**
     * The element to hold, which the owner also holds under its indexed name, {@code rows[0]}. An element that is a
     * nested map is kept, and so is the nested map the owner holds for the element just written when it is that same
     * map, since the merge has written the field into it or, for a key in brackets with a dot in it,
     * {@code [/api/v1.0/**]}, the map takes the field from where the merge wrote it. An element that is a value that is
     * not a map, or none, is replaced by the map the indexed key wrote, as {@code rows[1].name} replaces {@code 'two'},
     * rebuilt from the element under the flat key, so that a field written before the list was replaced or cleared does
     * not come back; a map the owner holds under the indexed name of any other element that is not a map is removed.
     * Otherwise an element that is a map is held as a nested map built from it, so that it holds every field the
     * element holds, such as {@code db.url} beside the {@code db.port}
     * just written below it, and none of an element the list held before it was replaced. The field just written then
     * takes the value the merge stored for it, {@link #storedValue(NavigableMap, String, String, String)}, so a
     * placeholder in it is resolved as in every other entry and a null written over it is held as null, as the flat
     * key holds it, and the keys of the map the indexed key was merged into
     * that held the element, or a map inside it, such as {@code a.rows[0]} and {@code a.rows[0].db}, then hold the
     * rebuilt element and its maps.
     */
    private static Object heldElement(NavigableMap targetMap, NavigableMap owner, String containerKey, String name, String index,
                                      Object element, String writtenField) {
        String indexedName = "${name}[${index}]".toString()
        Object nested = owner.get(indexedName)
        Object written = writtenField != null ? nested : null
        Object stored = writtenField != null ? storedValue(targetMap, containerKey, index, writtenField) : null
        if (written instanceof NavigableMap && element != null && element.is(written)) {
            if (index.indexOf('.') > -1 || writtenField.indexOf('.') > -1) {
                // the merge wrote the field of a key in brackets with a dot in it, [/api/v1.0/**], at another path, and
                // a field with a dot in it, db.url, is also held as one key, as application.yml gives it; a null
                // written over the field is written here too, so the held map does not keep the value before it
                ((NavigableMap) written).merge([(writtenField): stored], true)
            }
            return written
        }
        if (written instanceof NavigableMap && !(element instanceof Map)) {
            Object flat = elementOf(targetMap.get(containerKey), index)
            if (!(flat instanceof Map)) {
                return written
            }
            NavigableMap held = rebuilt(owner, indexedName, (Map) flat, written)
            held.merge([(writtenField): stored], true)
            owner.put(indexedName, held)
            replaceKeysOf(targetMap, "${containerKey}[${index}]".toString(), held)
            return held
        }
        if (!(element instanceof Map)) {
            if (nested instanceof NavigableMap) {
                // the map an indexed key wrote before the list was replaced holds no element of it
                owner.remove(indexedName)
            }
            return element
        }
        if (element instanceof NavigableMap && writtenField == null) {
            owner.put(indexedName, element)
            return element
        }
        NavigableMap held = new NavigableMap(owner.getRootConfig(), (owner.getPath() + [indexedName]).asImmutable())
        held.merge((Map) element, true)
        if (writtenField != null) {
            held.merge([(writtenField): stored], true)
        }
        owner.put(indexedName, held)
        replaceKeysOf(targetMap, "${containerKey}[${index}]".toString(), held)
        held
    }

    /**
     * The value the merge stored for the field just written, through {@link #mergeMapEntry(NavigableMap, String, Object)},
     * so that a placeholder in it is resolved as in every other entry. The merge splits the key at every dot, so the
     * field is in the nested map the owner holds for the element, {@code rows[0]}, or, for a key in brackets with a dot
     * in it, {@code [/api/v1.0/**]}, in the maps named by the parts of the key, and not in the map held for the element.
     */
    private static Object storedValue(NavigableMap targetMap, String containerKey, String index, String writtenField) {
        List<String> parts = "${containerKey}[${index}]".toString().split(/\./) as List<String>
        valueAt(targetMap.navigateSubMap(parts, false), writtenField)
    }

    /**
     * The nested map the owner holds for an element under its indexed name, which the merge wrote through
     * {@link #mergeMapEntry(NavigableMap, String, Object)}, or else the element of the list under the flat key, whose
     * values the subscript wrote as they were given.
     */
    private static Object nestedOrElement(NavigableMap owner, String indexedName, Object element) {
        Object nested = owner.get(indexedName)
        if (!(nested instanceof NavigableMap)) {
            return element
        }
        element instanceof Map ? rebuilt(owner, indexedName, (Map) element, nested) : element
    }

    /**
     * An element as the flat key holds it, with the value the owner's nested map holds for each of its fields, which
     * the merge stored through {@link #mergeMapEntry(NavigableMap, String, Object)}. A field the nested map holds and
     * the flat element does not was written before the list was replaced or cleared, and is left out.
     */
    private static NavigableMap rebuilt(NavigableMap owner, String indexedName, Map flat, Object nested) {
        NavigableMap held = new NavigableMap(owner.getRootConfig(), (owner.getPath() + [indexedName]).asImmutable())
        copyFields(held, flat, nested, '')
        held
    }

    private static void copyFields(NavigableMap held, Map flat, Object nested, String prefix) {
        for (Map.Entry entry in flat.entrySet()) {
            String field = prefix + String.valueOf(entry.key)
            if (entry.value instanceof Map && !((Map) entry.value).isEmpty()) {
                copyFields(held, (Map) entry.value, nested, field + '.')
                continue
            }
            Object value = valueAt(nested, field)
            held.merge([(field): value != null && !(value instanceof Map) ? value : entry.value], true)
        }
    }

    private static Object elementOf(Object container, String index) {
        if (container instanceof List) {
            List list = (List) container
            return index.isInteger() && index.toInteger() < list.size() ? list.get(index.toInteger()) : null
        }
        container instanceof Map ? ((Map) container).get(index) : null
    }

    private static Object valueAt(Object map, String field) {
        Object value = map
        for (String part in field.tokenize('.')) {
            if (!(value instanceof Map)) {
                return null
            }
            value = ((Map) value).get(part)
        }
        value
    }

    /**
     * Makes the keys of the map that hold an element or a map inside it, {@code a.rows[0]} and {@code a.rows[0].db},
     * hold the rebuilt element and its maps. Only keys the map holds already are written.
     */
    private static void replaceKeysOf(NavigableMap map, String key, NavigableMap held) {
        if (map.containsKey(key)) {
            map.put(key, held)
        }
        for (Map.Entry<String, Object> entry in held.entrySet()) {
            if (entry.value instanceof NavigableMap) {
                replaceKeysOf(map, "${key}.${entry.key}".toString(), (NavigableMap) entry.value)
            }
        }
    }

    /**
     * Whether the map holds an indexed entry beside the list for each of its elements, {@code rows[0]} and on, which
     * write the list element by element when the map is merged. A held list merged without them, as the value of a
     * map built from {@code config.app.rows}, is merged as a list.
     */
    private static boolean holdsIndexedEntries(Map map, String key, List list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) != null && !map.containsKey("${key}[${i}]".toString())) {
                return false
            }
        }
        true
    }

    /**
     * A list held for indexed keys in the nested map that owns it, beside the entries the map holds for its elements,
     * {@code rows[0]} and on. Merging the map writes the list element by element through those entries, as a list of
     * maps from application.yml always was, so a list that a source of higher precedence holds replaces the elements
     * it holds and keeps the others, and the held list itself is skipped.
     */
    @CompileStatic
    private static class HeldList extends ArrayList<Object> {
    }

    /**
     * Writes a value under the rest of a key after its subscript, {@code c.d} of {@code a[b].c.d}, into the map the
     * subscript names. A nested map is merged into, through {@link #mergeMapEntry(NavigableMap, String, Object)} as every
     * other entry is, so that its nested map {@code c} takes {@code d} as a dotted key merged into it would, and a flat
     * key of the root under the dotted name of the entry, {@code a.b.c.d}, takes the value it stored. Any other map
     * takes the rest of the key as one entry.
     */
    private void putBelowSubscript(NavigableMap rootMap, Map map, String remainder, Object value) {
        if (!(map instanceof NavigableMap) || remainder.indexOf('.') < 0) {
            map.put(remainder, value)
            return
        }
        NavigableMap nested = (NavigableMap) map
        mergeMaps(nested, '', nested, [(remainder): value], true)
        String dottedName = (nested.getPath() + [remainder]).join('.')
        if (rootMap.containsKey(dottedName)) {
            rootMap.put(dottedName, nested.get(remainder))
        }
    }

    protected void mergeMapEntry(NavigableMap rootMap, String path, NavigableMap targetMap, String sourceKey, Object sourceValue, boolean parseFlatKeys, boolean isNestedSet = false) {
        int subscriptStart = sourceKey.indexOf('[')
        int subscriptEnd = sourceKey.indexOf(']')
        if (subscriptEnd > subscriptStart) {
            if (subscriptStart > -1) {
                String k = sourceKey[0..<subscriptStart]
                String index = sourceKey[subscriptStart + 1..<subscriptEnd]
                String remainder = subscriptEnd != sourceKey.length() - 1 ? sourceKey[subscriptEnd + 2..-1] : null
                if (remainder) {

                    boolean isNumber = index.isNumber()
                    if (isNumber) {
                        int i = index.toInteger()
                        def currentValue = targetMap.get(k)
                        List list = currentValue instanceof List ? currentValue : []
                        if (list.size() > i) {
                            def v = list.get(i)
                            if (v instanceof Map) {
                                putBelowSubscript(rootMap, (Map) v, remainder, sourceValue)
                            } else {
                                Map newMap = [:]
                                newMap.put(remainder, sourceValue)
                                fill(list, i, null)
                                list.set(i, newMap)
                            }
                        } else {
                            Map newMap = [:]
                            newMap.put(remainder, sourceValue)
                            fill(list, i, null)
                            list.set(i, newMap)
                        }
                        targetMap.put(k, list)
                    } else {
                        def currentValue = targetMap.get(k)
                        Map nestedMap = currentValue instanceof Map ? currentValue : [:]
                        targetMap.put(k, nestedMap)

                        def v = nestedMap.get(index)
                        if (v instanceof Map) {
                            putBelowSubscript(rootMap, (Map) v, remainder, sourceValue)
                        } else {
                            Map newMap = [:]
                            newMap.put(remainder, sourceValue)
                            nestedMap.put(index, newMap)
                        }
                    }
                } else {
                    def currentValue = targetMap.get(k)
                    if (index.isNumber()) {
                        List list = currentValue instanceof List ? currentValue : []
                        int i = index.toInteger()
                        fill(list, i, null)
                        list.set(i, sourceValue)
                        targetMap.put(k, list)
                    } else {
                        Map nestedMap = currentValue instanceof Map ? currentValue : [:]
                        targetMap.put(k, nestedMap)
                        nestedMap.put(index, sourceValue)
                    }
                    targetMap.put(sourceKey, sourceValue)
                }

            }
        } else {
            Object currentValue = targetMap.containsKey(sourceKey) ? targetMap.get(sourceKey) : null
            Object newValue
            if (sourceValue instanceof Map) {
                List<String> newPathList = []
                newPathList.addAll(targetMap.getPath())
                newPathList.add(sourceKey)
                NavigableMap subMap
                if (currentValue instanceof NavigableMap) {
                    subMap = (NavigableMap) currentValue
                }
                else {
                    subMap = new NavigableMap(targetMap.getRootConfig(), newPathList.asImmutable())
                    if (currentValue instanceof Map) {
                        subMap.putAll((Map) currentValue)
                    }
                }
                String newPath = path ? "${path}.${sourceKey}" : sourceKey
                mergeMaps(rootMap, newPath , subMap, (Map) sourceValue, parseFlatKeys)
                newValue = subMap
            } else {
                newValue = sourceValue
            }
            if (isNestedSet && newValue == null) {
                if (path) {

                    def subMap = rootMap.get(path)
                    if (subMap instanceof Map) {
                        subMap.remove(sourceKey)
                    }
                    def keysToRemove = rootMap.keySet().findAll() { String key ->
                        key.startsWith("${path}.")
                    }
                    for (key in keysToRemove) {
                        rootMap.remove(key)
                    }
                }
                targetMap.remove(sourceKey)
            } else {
                mergeMapEntry(targetMap, sourceKey, newValue)
                if (path) {
                    // the flat key holds the value the nested entry holds, as the hook stored it, so a placeholder in it
                    // is resolved once, for both
                    rootMap.put("${path}.${sourceKey}".toString(), targetMap.get(sourceKey))
                }
            }
        }
    }

    protected Object mergeMapEntry(NavigableMap targetMap, String sourceKey, newValue) {
        targetMap.put(sourceKey, newValue)
    }

    Object getAt(Object key) {
        getProperty(String.valueOf(key))
    }

    void setAt(Object key, Object value) {
        setProperty(String.valueOf(key), value)
    }

    Object getProperty(String name) {
        if (!containsKey(name)) {
            return new NullSafeNavigator(this, [name].asImmutable())
        }
        Object result = get(name)
        return result
    }

    void setProperty(String name, Object value) {
        mergeMapEntry(rootConfig, dottedPath, this, name, value, false, true)
    }

    Object navigate(String... path) {
        return navigateMap(this, path)
    }

    private Object navigateMap(Map<String, Object> map, String... path) {
        if (map == null || path == null) return null
        if (path.length == 0) {
            return map
        } else if (path.length == 1) {
            return map.get(path[0])
        } else {
            def submap = map.get(path[0])
            if (submap instanceof Map) {
                return navigateMap((Map<String, Object>) submap, path.tail())
            }
            return submap
        }
    }

    private void fill(List list, Integer toIndex, Object value) {
        if (toIndex >= list.size()) {
            for (int i = list.size(); i <= toIndex; i++) {
                list.add(i, value)
            }
        }
    }

    NavigableMap navigateSubMap(List<String> path, boolean createMissing) {
        NavigableMap rootMap = this
        NavigableMap currentMap = this
        StringBuilder accumulatedPath = new StringBuilder()
        boolean isFirst = true
        for (String pathElement : path) {
            if (!isFirst) {
                accumulatedPath.append('.').append(pathElement)
            }
            else {
                isFirst = false
                accumulatedPath.append(pathElement)
            }

            Object currentItem = currentMap.get(pathElement)
            if (currentItem instanceof NavigableMap) {
                currentMap = (NavigableMap) currentItem
            } else if (createMissing) {
                List<String> newPathList = []
                newPathList.addAll(currentMap.getPath())
                newPathList.add(pathElement)

                Map<String, Object> newMap = new NavigableMap(currentMap.getRootConfig(), newPathList.asImmutable())
                currentMap.put(pathElement, newMap)

                def fullPath = accumulatedPath.toString()
                if (!rootMap.containsKey(fullPath)) {
                    rootMap.put(fullPath, newMap)
                }
                currentMap = newMap
            } else {
                return null
            }
        }
        currentMap
    }

    Map<String, Object> toFlatConfig() {
        Map<String, Object> flatConfig = [:]
        flattenKeys(flatConfig, this, [], false)
        removeDottedDuplicates(flatConfig)
        flatConfig
    }

    Properties toProperties() {
        Properties properties = new Properties()
        flattenKeys((Map<Object, Object>) properties, this, [], true)
        removeDottedDuplicates((Map<Object, Object>) properties)
        properties
    }

    /**
     * Removes each name that only writes a bracketed name with dots, such as {@code app.routes./api/**} beside
     * {@code app.routes[/api/**]}, which Spring Boot would bind as the key {@code api}. A map written by map subscripts
     * holds its entries under their bracketed names and is walked under their dotted ones too, so each entry is
     * flattened under both. A dotted name is removed only when every bracketed name that it writes with dots holds the
     * same value; where they differ, as when a later write changed one of them, every name stays, as it was before. A
     * bracketed key with a dot in it, {@code a[b.c]}, names a key of {@code a}, so the dotted name {@code a.b.c} is
     * a property of its own, and it stays when a key of the map that spells all of it, as a put of {@code a.b.c}
     * does, or the start of it, as a put of a map at {@code a.b} does, holds a value under the rest. A map put at
     * {@code a} with the key {@code b.c} in it is stored as merging {@code a[b.c]} stores it, so nothing tells the
     * two apart, and the dotted name is removed after both.
     */
    private void removeDottedDuplicates(Map<? extends Object, Object> flatConfig) {
        Map<String, List<Object>> bracketedValues = [:]
        Set<String> pathsToCheck = [] as Set<String>
        for (Map.Entry<? extends Object, Object> entry in flatConfig.entrySet()) {
            String name = String.valueOf(entry.key)
            if (name.indexOf('[') > -1) {
                for (Map.Entry<String, Boolean> dottedName in dottedNamesOf(name).entrySet()) {
                    List<Object> values = bracketedValues.get(dottedName.key)
                    if (values == null) {
                        values = []
                        bracketedValues.put(dottedName.key, values)
                    }
                    values.add(entry.value)
                    if (dottedName.value) {
                        pathsToCheck.add(dottedName.key)
                    }
                }
            }
        }
        for (Map.Entry<String, List<Object>> candidate in bracketedValues.entrySet()) {
            if (!flatConfig.containsKey(candidate.key)) {
                continue
            }
            Object value = flatConfig.get(candidate.key)
            if (!candidate.value.every { Object bracketedValue -> bracketedValue == value }) {
                continue
            }
            if (candidate.key in pathsToCheck && holdsPath(candidate.key.tokenize('.'))) {
                continue
            }
            flatConfig.remove(candidate.key)
        }
    }

    /**
     * Whether a key of this map that spells the whole path, or the start of it, holds a value under the rest of the
     * path. The rest is walked only through maps, so that a scalar part way down, such as {@code /v1} under
     * {@code routes} for {@code routes./v1.0/**}, holds no value below it.
     */
    private boolean holdsPath(List<String> path) {
        for (int end = path.size(); end > 0; end--) {
            String key = path.subList(0, end).join('.')
            if (containsKey(key) && holdsPath(get(key), path.subList(end, path.size()))) {
                return true
            }
        }
        false
    }

    private static boolean holdsPath(Object value, List<String> path) {
        for (String segment in path) {
            if (!(value instanceof Map) || !((Map) value).containsKey(segment)) {
                return false
            }
            value = ((Map) value).get(segment)
        }
        value != null
    }

    /**
     * The names that write some or all of the bracketed map keys of a name with dots, {@code app.routes./api/**} for
     * {@code app.routes[/api/**]}, each with whether one of the keys it writes so has a dot in it. A list index,
     * {@code rows[0]}, is not a map key and stays as it is.
     */
    private static Map<String, Boolean> dottedNamesOf(String name) {
        List<int[]> brackets = []
        int start = name.indexOf('[')
        while (start > -1 && brackets.size() < MAX_DOTTED_BRACKETS) {
            int end = name.indexOf(']', start)
            if (end < 0) {
                break
            }
            if (!name.substring(start + 1, end).isNumber()) {
                brackets.add([start, end] as int[])
            }
            start = name.indexOf('[', end)
        }
        Map<String, Boolean> dottedNames = [:]
        int combinations = 1 << brackets.size()
        for (int mask = 1; mask < combinations; mask++) {
            StringBuilder dottedName = new StringBuilder()
            boolean keyWithDot = false
            int from = 0
            for (int i = 0; i < brackets.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    int[] bracket = brackets.get(i)
                    dottedName.append(name, from, bracket[0])
                    if (bracket[0] > 0) {
                        dottedName.append('.')
                    }
                    String key = name.substring(bracket[0] + 1, bracket[1])
                    keyWithDot = keyWithDot || key.indexOf('.') > -1
                    dottedName.append(key)
                    from = bracket[1] + 1
                }
            }
            dottedName.append(name, from, name.length())
            dottedNames.put(dottedName.toString(), keyWithDot)
        }
        dottedNames
    }

    private void flattenKeys(Map<? extends Object, Object> flatConfig, Map currentMap, List<String> path, boolean forceStrings) {
        currentMap.each { key, value ->
            String stringKey = String.valueOf(key)
            if (value != null) {
                if (value instanceof Map) {
                    List<String> newPathList = []
                    newPathList.addAll(path)
                    newPathList.add(stringKey)

                    flattenKeys(flatConfig, (Map) value, newPathList.asImmutable(), forceStrings)
                } else {
                    String fullKey
                    if (path) {
                        fullKey = path.join('.') + '.' + stringKey
                    } else {
                        fullKey = stringKey
                    }
                    if (value instanceof Collection) {
                        if (forceStrings) {
                            flatConfig.put(fullKey, ((Collection) value).join(','))
                        } else {
                            flatConfig.put(fullKey, value)
                        }
                        int index = 0
                        for (Object item: (Collection) value) {
                            String collectionKey = "${fullKey}[${index}]".toString()
                            flatConfig.put(collectionKey, forceStrings ? String.valueOf(item) : item)
                            index++
                        }
                    } else {
                        flatConfig.put(fullKey, forceStrings ? String.valueOf(value) : value)
                    }
                }
            }
        }
    }

    @Override
    int hashCode() {
        return delegateMap.hashCode()
    }

    @Override
    boolean equals(Object obj) {
        return delegateMap.equals(obj)
    }

    /**
     * @deprecated This class should be avoided due to known performance reasons. Use {@code config.getProperty(String key, Class<T> targetType)} instead of dot based navigation.
     */
    @Deprecated
    @CompileStatic
    static class NullSafeNavigator implements Map<String, Object> {
        final NavigableMap parent
        final List<String> path

        NullSafeNavigator(NavigableMap parent, List<String> path) {
            this.parent = parent
            this.path = path
            if (LOG.isWarnEnabled()) {
                LOG.warn("Accessing config key '{}' through dot notation has known performance issues, consider using 'config.getProperty(key, targetClass)' instead.", path)
            }
        }

        Object getAt(Object key) {
            getProperty(String.valueOf(key))
        }

        void setAt(Object key, Object value) {
            setProperty(String.valueOf(key), value)
        }

        @Override
        int size() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap != null) {
                return parentMap.size()
            }
            return 0
        }

        @Override
        boolean isEmpty() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap != null) {
                return parentMap.isEmpty()
            }
            return true
        }

        boolean containsKey(Object key) {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap == null) return false
            else {
                return parentMap.containsKey(key)
            }
        }

        @Override
        boolean containsValue(Object value) {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap != null) {
                return parentMap.containsValue(value)
            }
            return false
        }

        @Override
        Object get(Object key) {
            return getAt(key)
        }

        @Override
        Object put(String key, Object value) {
            throw new UnsupportedOperationException('Configuration cannot be modified')
        }

        @Override
        Object remove(Object key) {
            throw new UnsupportedOperationException('Configuration cannot be modified')
        }

        @Override
        void putAll(Map<? extends String, ?> m) {
            throw new UnsupportedOperationException('Configuration cannot be modified')
        }

        @Override
        void clear() {
            throw new UnsupportedOperationException('Configuration cannot be modified')
        }

        @Override
        Set<String> keySet() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap != null) {
                return parentMap.keySet()
            }
            return Collections.emptySet()
        }

        @Override
        Collection<Object> values() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap != null) {
                return parentMap.values()
            }
            return Collections.emptySet()
        }

        @Override
        Set<Entry<String, Object>> entrySet() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap != null) {
                return parentMap.entrySet()
            }
            return Collections.emptySet()
        }

        Object getProperty(String name) {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if (parentMap == null) {
                return new NullSafeNavigator(parent, ((path + [name]) as List<String>).asImmutable())
            } else {
                return parentMap.get(name)
            }
        }

        void setProperty(String name, Object value) {
            NavigableMap parentMap = parent.navigateSubMap(path, true)
            parentMap.setProperty(name, value)
        }

        boolean asBoolean() {
            false
        }

        Object invokeMethod(String name, Object args) {
            throw new NullPointerException('Cannot invoke method ' + name + '() on NullSafeNavigator')
        }

        boolean equals(Object to) {
            return to == null || DefaultGroovyMethods.is(this, to)
        }

        Iterator iterator() {
            return Collections.EMPTY_LIST.iterator()
        }

        Object plus(String s) {
            return toString() + s
        }

        Object plus(Object o) {
            throw new NullPointerException('Cannot invoke method plus on NullSafeNavigator')
        }

        boolean is(Object other) {
            return other == null || DefaultGroovyMethods.is(this, other)
        }

        Object asType(Class c) {
            if (c == Boolean || c == boolean) return false
            return null
        }

        String toString() {
            return null
        }

//        public int hashCode() {
//            throw new NullPointerException('Cannot invoke method hashCode() on NullSafeNavigator')
//        }
    }
}

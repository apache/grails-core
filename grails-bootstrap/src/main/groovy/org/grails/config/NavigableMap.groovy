/*
 * Copyright 2014-2024 the original author or authors.
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
package org.grails.config

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Pattern

/**
 * @deprecated This class is deprecated to reduce complexity, improve performance, and increase maintainability. Use {@code config.getProperty(String key, Class<T> targetType)} instead.
 */
@Deprecated
@EqualsAndHashCode
@CompileStatic
class NavigableMap implements Map<String, Object>, Cloneable {

    private static final Logger LOG = LoggerFactory.getLogger(NavigableMap.class);

    private static final Pattern SPLIT_PATTERN = ~/\./
    private static final String SPRING_PROFILES = 'spring.profiles.active'
    private static final String SPRING = 'spring'
    private static final String CONFIG = 'config'
    private static final String ACTIVATE = 'activate'
    private static final String ON_PROFILE = 'on-profile'
    private static final String PROFILES = 'profiles'
    private static final String SUBSCRIPT_REGEX = /((.*)\[(\d+)\]).*/

    final NavigableMap rootConfig
    final List<String> path
    final Map<String, Object> delegateMap
    final String dottedPath

    NavigableMap() {
        rootConfig = this
        path = []
        dottedPath = ""
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
        this.delegateMap= delegateMap
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
        delegateMap.containsKey key
    }

    @Override
    boolean containsValue(Object value) {
        delegateMap.containsValue value
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
        delegateMap.remove key
    }

    @Override
    void putAll(Map<? extends String, ? extends Object> m) {
        delegateMap.putAll m
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

    void merge(Map sourceMap, boolean parseFlatKeys=false) {
        mergeMaps(this, "", this, sourceMap, parseFlatKeys)
    }

    private void mergeMaps(NavigableMap rootMap,
                           String path,
                           NavigableMap targetMap,
                           Map sourceMap,
                           boolean parseFlatKeys) {

        if(springProfileExclude(sourceMap, path)) {
            return
        }

        for (Entry entry in sourceMap) {
            Object sourceKeyObject = entry.key
            Object sourceValue = entry.value
            String sourceKey = String.valueOf(sourceKeyObject)
            if (parseFlatKeys) {
                String[] keyParts = sourceKey.split(/\./)
                if (keyParts.length > 1) {
                    mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
                    def pathParts = keyParts[0..-2]
                    Map actualTarget = targetMap.navigateSubMap(pathParts as List, true)
                    sourceKey = keyParts[-1]
                    mergeMapEntry(rootMap, pathParts.join('.'), actualTarget, sourceKey, sourceValue, parseFlatKeys)
                } else {
                    mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
                }
            } else {
                mergeMapEntry(rootMap, path, targetMap, sourceKey, sourceValue, parseFlatKeys)
            }
        }
    }

    private static boolean springProfileExclude(Map sourceMap, String path) {

        // Is there an active Spring profile?
        def activeSpringProfile = System.getProperty(SPRING_PROFILES)

        // Is there a 'spring.config.activate.on-profile' property defined in the source map?
        def sourceMapProfile1 = ((Map)((Map)((Map)sourceMap?.get(SPRING))?.get(CONFIG))?.get(ACTIVATE))?.get(ON_PROFILE)
        if (!sourceMapProfile1 && path == "$SPRING.$CONFIG.$ACTIVATE") {
            sourceMapProfile1 = sourceMap?.get(ON_PROFILE)
        }
        if (!sourceMapProfile1) {
            sourceMapProfile1 = sourceMap.get("$SPRING.$CONFIG.$ACTIVATE.$ON_PROFILE" as String)
        }
        if (sourceMapProfile1 && !activeSpringProfile) {
            // There is a spring.config.activate.on-profile property defined in this sourceMap, but there is no active spring profile
            return true
        }
        if (sourceMapProfile1 == activeSpringProfile) {
            // The active spring profile matches the spring.config.activate.on-profile property in this sourceMap
            return false
        }

        // Is there a 'spring.profiles' property defined in the source map? (Old way of Spring profiles activation)
        def sourceMapProfile2 = ((Map)sourceMap?.get(SPRING))?.get(PROFILES)
        if (!sourceMapProfile2 && path == SPRING) {
            sourceMapProfile2 = sourceMap?.get(PROFILES)
        }
        if (!sourceMapProfile2) {
            sourceMapProfile2 = sourceMap.get("$SPRING.$PROFILES" as String)
        }
        if (sourceMapProfile1 && !activeSpringProfile) {
            // There is a spring.config.activate.on-profile property defined in this sourceMap, but there is no active spring profile
            return true
        }
        if (sourceMapProfile2 == activeSpringProfile) {
            // The active spring profile matches the spring.profiles property in this sourceMap
            return false
        }

        if (activeSpringProfile && !sourceMapProfile1 && !sourceMapProfile2) {
            // There is no spring profile defined in this sourceMap, it should always be included
            return false
        }

        // We can skip this sourceMap as it defines a spring profile that is not active
        return true
    }

    protected void mergeMapEntry(NavigableMap rootMap, String path, NavigableMap targetMap, String sourceKey, Object sourceValue, boolean parseFlatKeys, boolean isNestedSet = false) {
        int subscriptStart = sourceKey.indexOf('[')
        int subscriptEnd = sourceKey.indexOf(']')
        if (subscriptEnd > subscriptStart) {
           if(subscriptStart > -1) {
               String k = sourceKey[0..<subscriptStart]
               String index = sourceKey[subscriptStart+1..<subscriptEnd]
               String remainder = subscriptEnd != sourceKey.length() -1 ? sourceKey[subscriptEnd+2..-1] : null
               if (remainder) {

                   boolean isNumber = index.isNumber()
                   if (isNumber) {
                       int i = index.toInteger()
                       def currentValue = targetMap.get(k)
                       List list = currentValue instanceof List ? currentValue : []
                       if (list.size() > i) {
                           def v = list.get(i)
                           if (v instanceof Map) {
                               ((Map)v).put(remainder, sourceValue)
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
                           ((Map)v).put(remainder, sourceValue)
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
            if(sourceValue instanceof Map) {
                List<String> newPathList = []
                newPathList.addAll( targetMap.getPath() )
                newPathList.add(sourceKey)
                NavigableMap subMap
                if(currentValue instanceof NavigableMap) {
                    subMap = (NavigableMap)currentValue
                }
                else {
                    subMap = new NavigableMap(targetMap.getRootConfig(), newPathList.asImmutable())
                    if(currentValue instanceof Map) {
                        subMap.putAll((Map)currentValue)
                    }
                }
                String newPath = path ? "${path}.${sourceKey}" : sourceKey
                mergeMaps(rootMap, newPath , subMap, (Map)sourceValue, parseFlatKeys)
                newValue = subMap
            } else {
                newValue = sourceValue
            }
            if (isNestedSet && newValue == null) {
                if(path) {

                    def subMap = rootMap.get(path)
                    if(subMap instanceof Map) {
                        subMap.remove(sourceKey)
                    }
                    def keysToRemove = rootMap.keySet().findAll() { String key ->
                        key.startsWith("${path}.")
                    }
                    for(key in keysToRemove) {
                        rootMap.remove(key)
                    }
                }
                targetMap.remove(sourceKey)
            } else {
                if(path) {
                    rootMap.put( "${path}.${sourceKey}".toString(), newValue )
                }
                mergeMapEntry(targetMap, sourceKey, newValue)
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
        if (!(result instanceof NavigableMap)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Accessing config key '{}' through dot notation is deprecated, and it will be removed in a future release. Use 'config.getProperty(key, targetClass)' instead.", name)
            }
        }
        return result
    }

    void setProperty(String name, Object value) {
        mergeMapEntry(rootConfig, dottedPath, this, name, value, false, true)
    }

    Object navigate(String... path) {
        return navigateMap(this, path)
    }
    
    private Object navigateMap(Map<String, Object> map, String... path) {
        if(map==null || path == null) return null
        if(path.length == 0) {
            return map
        } else if (path.length == 1) {
            return map.get(path[0])
        } else {
            def submap = map.get(path[0])
            if(submap instanceof Map) {
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
        for(String pathElement : path) {
            if(!isFirst) {
                accumulatedPath.append(".").append(pathElement)
            }
            else {
                isFirst = false
                accumulatedPath.append(pathElement)
            }

            Object currentItem = currentMap.get(pathElement) 
            if(currentItem instanceof NavigableMap) {
                currentMap = (NavigableMap)currentItem
            } else if (createMissing) {
                List<String> newPathList = []
                newPathList.addAll( currentMap.getPath() )
                newPathList.add(pathElement)

                Map<String, Object> newMap = new NavigableMap(currentMap.getRootConfig(), newPathList.asImmutable())
                currentMap.put(pathElement, newMap)

                def fullPath = accumulatedPath.toString()
                if(!rootMap.containsKey(fullPath)) {
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
        flatConfig
    }
    
    Properties toProperties() {
        Properties properties = new Properties()
        flattenKeys((Map<Object, Object>) properties, this, [], true)
        properties
    }
    
    private void flattenKeys(Map<? extends Object, Object> flatConfig, Map currentMap, List<String> path, boolean forceStrings) {
        currentMap.each { key, value ->
            String stringKey = String.valueOf(key)
            if(value != null) {
                if(value instanceof Map) {
                    List<String> newPathList = []
                    newPathList.addAll( path )
                    newPathList.add( stringKey )

                    flattenKeys(flatConfig, (Map)value, newPathList.asImmutable(), forceStrings)
                } else {
                    String fullKey
                    if(path) {
                        fullKey = path.join('.') + '.' + stringKey
                    } else {
                        fullKey = stringKey
                    }
                    if(value instanceof Collection) {
                        if(forceStrings) {
                            flatConfig.put(fullKey, ((Collection)value).join(","))
                        } else {
                            flatConfig.put(fullKey, value)
                        }
                        int index = 0
                        for(Object item: (Collection)value) {
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
    static class NullSafeNavigator implements Map<String, Object>{
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
            if(parentMap != null) {
                return parentMap.size()
            }
            return 0
        }

        @Override
        boolean isEmpty() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.isEmpty()
            }
            return true
        }

        boolean containsKey(Object key) {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap == null) return false
            else {
                return parentMap.containsKey(key)
            }
        }

        @Override
        boolean containsValue(Object value) {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
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
            throw new UnsupportedOperationException("Configuration cannot be modified");
        }

        @Override
        Object remove(Object key) {
            throw new UnsupportedOperationException("Configuration cannot be modified");
        }

        @Override
        void putAll(Map<? extends String, ?> m) {
            throw new UnsupportedOperationException("Configuration cannot be modified");
        }

        @Override
        void clear() {
            throw new UnsupportedOperationException("Configuration cannot be modified");
        }

        @Override
        Set<String> keySet() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.keySet()
            }
            return Collections.emptySet()
        }

        @Override
        Collection<Object> values() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.values()
            }
            return Collections.emptySet()
        }

        @Override
        Set<Entry<String, Object>> entrySet() {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap != null) {
                return parentMap.entrySet()
            }
            return Collections.emptySet()
        }

        Object getProperty(String name) {
            NavigableMap parentMap = parent.navigateSubMap(path, false)
            if(parentMap == null) {
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
            throw new NullPointerException("Cannot invoke method " + name + "() on NullSafeNavigator");
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
            throw new NullPointerException("Cannot invoke method plus on NullSafeNavigator")
        }

        boolean is(Object other) {
            return other == null || DefaultGroovyMethods.is(this, other)
        }

        Object asType(Class c) {
            if(c==Boolean || c==boolean) return false
            return null
        }

        String toString() {
            return null
        }

//        public int hashCode() {
//            throw new NullPointerException("Cannot invoke method hashCode() on NullSafeNavigator");
//        }
    }
}

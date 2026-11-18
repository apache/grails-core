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
package grails.web.databinding;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaProperty;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import grails.databinding.DataBindingSource;
import grails.databinding.FrameworkPropertyNames;

import static grails.web.databinding.DataBindingUtils.addUnbindablePropertyNames;
import static grails.web.databinding.DataBindingUtils.getBindingIncludeList;

/**
 * Clears explicitly included properties omitted from a binding source.
 *
 * @since 8.0
 */
final class MissingPropertyClearer {

    private static final String BLANK = "";
    private static final ThreadLocal<BindingResult> BINDING_RESULT = new ThreadLocal<>();
    private static final ThreadLocal<String> CLEAR_PATH = new ThreadLocal<>();

    private MissingPropertyClearer() {
    }

    static void clearMissingIncludedProperties(Object object, DataBindingSource bindingSource, List include, List exclude,
                                               String filter, BindingResult bindingResult) {
        List expandedInclude = expandWildcardIncludes(object, include);
        BindingResult previousBindingResult = BINDING_RESULT.get();
        BINDING_RESULT.set(bindingResult);
        try {
            for (Object includedProperty : expandedInclude) {
                if (includedProperty instanceof CharSequence) {
                    String propertyName = includedProperty.toString();
                    if (isClearMissingPropertyBindable(object, propertyName, expandedInclude, exclude)) {
                        if (assignNullToMissingIndexedProperties(object, bindingSource, propertyName, filter)) {
                            continue;
                        }
                        if (!bindingSourceContainsProperty(bindingSource, propertyName, filter)) {
                            setPropertyToNull(object, propertyName);
                        }
                    }
                }
            }
        }
        finally {
            if (previousBindingResult == null) {
                BINDING_RESULT.remove();
            }
            else {
                BINDING_RESULT.set(previousBindingResult);
            }
        }
    }

    private static List expandWildcardIncludes(Object object, List include) {
        List expandedInclude = new ArrayList();
        for (Object includedProperty : include) {
            if (!(includedProperty instanceof CharSequence)) {
                expandedInclude.add(includedProperty);
                continue;
            }
            String propertyName = includedProperty.toString();
            if (propertyName.endsWith(".*")) {
                expandNestedWildcard(object, propertyName.substring(0, propertyName.length() - 2), expandedInclude);
            }
            else if (propertyName.endsWith("_*")) {
                expandSiblingWildcard(object, propertyName.substring(0, propertyName.length() - 2), expandedInclude);
            }
            else {
                expandedInclude.add(propertyName);
            }
        }
        return expandedInclude;
    }

    private static void expandNestedWildcard(Object object, String propertyPath, List expandedInclude) {
        Object nestedObject = getPropertyValue(object, propertyPath);
        if (nestedObject instanceof Collection) {
            int index = 0;
            for (Object item : (Collection) nestedObject) {
                expandObjectProperties(item, propertyPath + "[" + index + "]", expandedInclude);
                index++;
            }
        }
        else if (nestedObject instanceof Map) {
            for (Object entryObject : ((Map) nestedObject).entrySet()) {
                Map.Entry entry = (Map.Entry) entryObject;
                expandObjectProperties(entry.getValue(), propertyPath + "[" + entry.getKey() + "]", expandedInclude);
            }
        }
        else {
            expandObjectProperties(nestedObject, propertyPath, expandedInclude);
        }
    }

    private static void expandSiblingWildcard(Object object, String propertyPrefix, List expandedInclude) {
        for (Object property : getMetaProperties(object)) {
            String propertyName = ((MetaProperty) property).getName();
            if (propertyName.startsWith(propertyPrefix + "_")) {
                expandedInclude.add(propertyName);
            }
        }
    }

    private static void expandObjectProperties(Object object, String propertyPath, List expandedInclude) {
        if (object == null) {
            return;
        }
        for (Object property : getMetaProperties(object)) {
            expandedInclude.add(propertyPath + "." + ((MetaProperty) property).getName());
        }
    }

    private static List getMetaProperties(Object object) {
        if (object == null) {
            return Collections.emptyList();
        }
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
        return metaClass.getProperties();
    }

    private static boolean isClearMissingPropertyBindable(Object object, String propertyName, List include, List exclude) {
        if (object == null) {
            return false;
        }
        String allowlistPropertyName = removePropertyIndexes(propertyName);
        List bindingIncludeList = getBindingIncludeList(object);
        List bindingExcludeList = normalizePropertyIndexes(addUnbindablePropertyNames(object, exclude));
        if (!isClearMissingPropertyPathAllowed(allowlistPropertyName, bindingIncludeList, include, bindingExcludeList)) {
            return false;
        }
        int separator = propertyPathSeparator(propertyName);
        if (separator == -1) {
            return true;
        }

        Object nestedObject = getPropertyValue(object, propertyName.substring(0, separator));
        String nestedPropertyName = propertyName.substring(separator + 1);
        if (nestedObject instanceof Collection) {
            for (Object item : (Collection) nestedObject) {
                if (item != null && !isClearMissingPropertyBindable(item, nestedPropertyName, getNestedIncludeList(include, propertyName), null)) {
                    return false;
                }
            }
            return true;
        }
        if (nestedObject instanceof Map) {
            for (Object value : ((Map) nestedObject).values()) {
                if (value != null && !isClearMissingPropertyBindable(value, nestedPropertyName, getNestedIncludeList(include, propertyName), null)) {
                    return false;
                }
            }
            return true;
        }
        return nestedObject == null || isClearMissingPropertyBindable(nestedObject, nestedPropertyName, getNestedIncludeList(include, propertyName), null);
    }

    private static String removePropertyIndexes(String propertyName) {
        return propertyName.replaceAll("\\[[^]]*]", "");
    }

    private static List normalizePropertyIndexes(List propertyNames) {
        if (propertyNames == null) {
            return Collections.emptyList();
        }
        List normalizedPropertyNames = new ArrayList(propertyNames.size());
        for (Object propertyName : propertyNames) {
            normalizedPropertyNames.add(propertyName instanceof CharSequence ? removePropertyIndexes(propertyName.toString()) : propertyName);
        }
        return normalizedPropertyNames;
    }

    private static List getNestedIncludeList(List include, String propertyName) {
        if (include == null || include.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedPropertyName = removePropertyIndexes(propertyName);
        int separator = propertyPathSeparator(normalizedPropertyName);
        if (separator == -1) {
            return Collections.emptyList();
        }
        String rootPropertyName = normalizedPropertyName.substring(0, separator);
        List nestedIncludeList = new ArrayList();
        for (Object includedProperty : include) {
            if (includedProperty instanceof CharSequence) {
                String includedPropertyName = removePropertyIndexes(includedProperty.toString());
                int includedPropertySeparator = propertyPathSeparator(includedPropertyName);
                if (includedPropertySeparator != -1 && rootPropertyName.equals(includedPropertyName.substring(0, includedPropertySeparator))) {
                    nestedIncludeList.add(includedPropertyName.substring(includedPropertySeparator + 1));
                }
            }
        }
        return nestedIncludeList;
    }

    private static boolean isPropertyExcluded(String propertyName, List excludeList) {
        if (excludeList == null) {
            return false;
        }
        for (Object item : excludeList) {
            String excludeName = item == null ? null : item.toString();
            if (excludeName != null && (excludeName.equals(propertyName) || propertyName.startsWith(excludeName + ".") ||
                    (excludeName.endsWith(".*") && propertyName.startsWith(excludeName.substring(0, excludeName.length() - 1))) ||
                    (excludeName.endsWith("_*") && propertyName.startsWith(excludeName.substring(0, excludeName.length() - 1))))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClearMissingPropertyPathAllowed(String propertyName, List generatedIncludeList, List explicitIncludeList, List excludeList) {
        if (isIntrinsicRuntimeProperty(propertyName) || isPropertyExcluded(propertyName, excludeList)) {
            return false;
        }
        return isClearMissingPropertyIncluded(propertyName, generatedIncludeList) ||
                isClearMissingPropertyIncluded(propertyName, explicitIncludeList);
    }

    private static boolean isIntrinsicRuntimeProperty(String propertyName) {
        int separator = propertyPathSeparator(propertyName);
        String rootPropertyName = separator == -1 ? propertyName : propertyName.substring(0, separator);
        return FrameworkPropertyNames.INTRINSIC_RUNTIME_PROPERTIES.contains(rootPropertyName);
    }

    private static boolean isClearMissingPropertyIncluded(String propertyName, List includeList) {
        if (includeList == null) {
            return false;
        }
        for (Object includedProperty : includeList) {
            if (includedProperty instanceof CharSequence) {
                String includedPropertyName = removePropertyIndexes(includedProperty.toString());
                if (includedPropertyName.equals(propertyName)) {
                    return true;
                }
                if (includedPropertyName.endsWith(".*")) {
                    String prefix = includedPropertyName.substring(0, includedPropertyName.length() - 2);
                    if (propertyName.startsWith(prefix + ".")) {
                        return true;
                    }
                }
                if (includedPropertyName.endsWith("_*")) {
                    String prefix = includedPropertyName.substring(0, includedPropertyName.length() - 2);
                    if (propertyName.startsWith(prefix + ".") || propertyName.startsWith(prefix + "_")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean assignNullToMissingIndexedProperties(Object object, DataBindingSource bindingSource, String propertyName, String filter) {
        String sourcePropertyName = filter == null ? propertyName : filter + "." + propertyName;
        return assignNullToMissingIndexedProperties(object, bindingSource, BLANK, sourcePropertyName, propertyName);
    }

    private static boolean assignNullToMissingIndexedProperties(Object object, Object source, String targetPathPrefix, String sourcePropertyName, String targetPropertyName) {
        int sourceSeparator = propertyPathSeparator(sourcePropertyName);
        int targetSeparator = propertyPathSeparator(targetPropertyName);
        if (sourceSeparator == -1 || targetSeparator == -1) {
            return false;
        }

        String sourceRootPropertyName = sourcePropertyName.substring(0, sourceSeparator);
        String targetRootPropertyName = targetPropertyName.substring(0, targetSeparator);
        String nestedSourcePropertyName = sourcePropertyName.substring(sourceSeparator + 1);
        String nestedTargetPropertyName = targetPropertyName.substring(targetSeparator + 1);
        String[] sourceSegments = splitPropertyPath(sourcePropertyName);
        String[] targetSegments = splitPropertyPath(targetPropertyName);
        if (sourceSegments.length > targetSegments.length) {
            if (containsSourceProperty(source, sourceRootPropertyName)) {
                return assignNullToMissingIndexedProperties(object, getSourcePropertyValue(source, sourceRootPropertyName), targetPathPrefix, nestedSourcePropertyName, targetPropertyName);
            }
            int sourceRootSegmentCount = sourceSegments.length - targetSegments.length + 1;
            sourceRootPropertyName = joinPropertyPath(sourceSegments, 0, sourceRootSegmentCount);
            nestedSourcePropertyName = joinPropertyPath(sourceSegments, sourceRootSegmentCount, sourceSegments.length);
            targetRootPropertyName = targetSegments[0];
            nestedTargetPropertyName = joinPropertyPath(targetSegments, 1, targetSegments.length);
        }

        if (containsSourceProperty(source, sourceRootPropertyName)) {
            Object nestedSource = getSourcePropertyValue(source, sourceRootPropertyName);
            if (nestedSource instanceof Collection) {
                return assignNullToMissingCollectionProperties(object, (Collection) nestedSource, targetPathPrefix, targetRootPropertyName, nestedSourcePropertyName, nestedTargetPropertyName);
            }
            Object targetObject = getTargetObject(object, targetPathPrefix);
            if (nestedSource instanceof Map && hasNestedSourceEntries((Map) nestedSource) && shouldExpandMapEntries(targetObject, targetObject == null ? null : targetObject.getClass(), targetRootPropertyName)) {
                return assignNullToMissingMapProperties(object, (Map) nestedSource, targetPathPrefix, targetRootPropertyName, nestedSourcePropertyName, nestedTargetPropertyName);
            }
        }

        boolean indexed = false;
        String indexedSourcePropertyPrefix = sourceRootPropertyName + "[";
        for (String indexedSourcePropertyName : getIndexedSourcePropertyNames(source, indexedSourcePropertyPrefix)) {
            indexed = true;
            String targetIndexedPropertyName = appendPropertyPath(targetPathPrefix, targetRootPropertyName + indexedSourcePropertyName.substring(sourceRootPropertyName.length()));
            if (containsSourceProperty(source, indexedSourcePropertyName)) {
                Object nestedSource = getSourcePropertyValue(source, indexedSourcePropertyName);
                if (!assignNullToMissingIndexedProperties(object, nestedSource, targetIndexedPropertyName, nestedSourcePropertyName, nestedTargetPropertyName) && !containsPropertyPath(nestedSource, nestedSourcePropertyName)) {
                    setPropertyToNull(object, targetIndexedPropertyName + "." + nestedTargetPropertyName);
                }
            }
            else if (!containsPropertyPath(source, indexedSourcePropertyName + "." + nestedSourcePropertyName)) {
                setPropertyToNull(object, targetIndexedPropertyName + "." + nestedTargetPropertyName);
            }
        }
        return indexed;
    }

    private static boolean assignNullToMissingCollectionProperties(Object object, Collection collection, String targetPathPrefix, String targetRootPropertyName, String nestedSourcePropertyName, String nestedTargetPropertyName) {
        int index = 0;
        for (Object item : collection) {
            String targetIndexedPropertyName = appendPropertyPath(targetPathPrefix, targetRootPropertyName + "[" + index + "]");
            assignNullToMissingNestedProperty(object, item, targetIndexedPropertyName, nestedSourcePropertyName, nestedTargetPropertyName);
            index++;
        }
        return true;
    }

    private static boolean assignNullToMissingMapProperties(Object object, Map map, String targetPathPrefix, String targetRootPropertyName, String nestedSourcePropertyName, String nestedTargetPropertyName) {
        for (Object entryObject : map.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            String targetIndexedPropertyName = appendPropertyPath(targetPathPrefix, targetRootPropertyName + "[" + entry.getKey() + "]");
            assignNullToMissingNestedProperty(object, entry.getValue(), targetIndexedPropertyName, nestedSourcePropertyName, nestedTargetPropertyName);
        }
        return true;
    }

    private static void assignNullToMissingNestedProperty(Object object, Object nestedSource, String targetIndexedPropertyName, String nestedSourcePropertyName, String nestedTargetPropertyName) {
        if (!assignNullToMissingIndexedProperties(object, nestedSource, targetIndexedPropertyName, nestedSourcePropertyName, nestedTargetPropertyName) && !containsPropertyPath(nestedSource, nestedSourcePropertyName)) {
            setPropertyToNull(object, targetIndexedPropertyName + "." + nestedTargetPropertyName);
        }
    }

    private static String joinPropertyPath(String[] segments, int start, int end) {
        StringBuilder propertyPath = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (propertyPath.length() > 0) {
                propertyPath.append('.');
            }
            propertyPath.append(segments[i]);
        }
        return propertyPath.toString();
    }

    private static boolean bindingSourceContainsProperty(DataBindingSource bindingSource, String propertyName, String filter) {
        String sourcePropertyName = filter == null ? propertyName : filter + "." + propertyName;
        int exactPrefixSegments = filter == null ? 0 : splitPropertyPath(filter).length;
        return containsPropertyPath(bindingSource, sourcePropertyName, exactPrefixSegments) || containsPropertyPath(bindingSource, checkboxMarkerPropertyName(sourcePropertyName), exactPrefixSegments);
    }

    private static boolean containsPropertyPath(Object source, String propertyName) {
        return containsPropertyPath(source, propertyName, 0);
    }

    private static boolean containsPropertyPath(Object source, String propertyName, int exactPrefixSegments) {
        if (containsSourceProperty(source, propertyName)) {
            return true;
        }
        if (containsIndexedPropertyPath(source, propertyName, exactPrefixSegments)) {
            return true;
        }
        int separator = propertyPathSeparator(propertyName);
        if (separator == -1) {
            return false;
        }
        String rootPropertyName = propertyName.substring(0, separator);
        if (!containsSourceProperty(source, rootPropertyName)) {
            return containsIndexedNestedPropertyPath(source, rootPropertyName, propertyName.substring(separator + 1));
        }
        Object nestedSource = getSourcePropertyValue(source, rootPropertyName);
        String nestedPropertyName = propertyName.substring(separator + 1);
        if (nestedSource instanceof Collection) {
            for (Object item : (Collection) nestedSource) {
                if (containsPropertyPath(item, nestedPropertyName)) {
                    return true;
                }
            }
            return false;
        }
        return containsPropertyPath(nestedSource, nestedPropertyName);
    }

    private static boolean containsIndexedNestedPropertyPath(Object source, String rootPropertyName, String nestedPropertyName) {
        String indexedSourcePropertyPrefix = rootPropertyName + "[";
        for (String indexedSourcePropertyName : getIndexedSourcePropertyNames(source, indexedSourcePropertyPrefix)) {
            if (containsSourceProperty(source, indexedSourcePropertyName) && containsPropertyPath(getSourcePropertyValue(source, indexedSourcePropertyName), nestedPropertyName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIndexedPropertyPath(Object source, String propertyName, int exactPrefixSegments) {
        for (String indexedPropertyName : getSourcePropertyNames(source)) {
            if (indexedPropertyPathMatches(indexedPropertyName, propertyName, exactPrefixSegments)) {
                return true;
            }
        }
        return false;
    }

    private static int propertyPathSeparator(String propertyName) {
        return propertyPathSeparator(propertyName, false);
    }

    private static int propertyPathSeparator(String propertyName, boolean last) {
        int separator = -1;
        int bracketDepth = 0;
        for (int i = 0; i < propertyName.length(); i++) {
            char character = propertyName.charAt(i);
            if (character == '[') {
                bracketDepth++;
            }
            else if (character == ']' && bracketDepth > 0) {
                bracketDepth--;
            }
            else if (character == '.' && bracketDepth == 0) {
                if (!last) {
                    return i;
                }
                separator = i;
            }
        }
        return separator;
    }

    private static String[] splitPropertyPath(String propertyName) {
        List<String> segments = new ArrayList<>();
        StringBuilder segment = new StringBuilder();
        int bracketDepth = 0;
        for (int i = 0; i < propertyName.length(); i++) {
            char character = propertyName.charAt(i);
            if (character == '.' && bracketDepth == 0) {
                segments.add(segment.toString());
                segment.setLength(0);
            }
            else {
                if (character == '[') {
                    bracketDepth++;
                }
                else if (character == ']' && bracketDepth > 0) {
                    bracketDepth--;
                }
                segment.append(character);
            }
        }
        segments.add(segment.toString());
        return segments.toArray(new String[0]);
    }

    private static boolean indexedPropertyPathMatches(String indexedPropertyName, String propertyName, int exactPrefixSegments) {
        String[] indexedPropertySegments = splitPropertyPath(indexedPropertyName);
        String[] propertySegments = splitPropertyPath(propertyName);
        if (indexedPropertySegments.length != propertySegments.length) {
            return false;
        }
        for (int i = 0; i < propertySegments.length; i++) {
            if (indexedPropertySegments[i].equals(propertySegments[i])) {
                continue;
            }
            if (i < exactPrefixSegments) {
                return false;
            }
            if (!indexedSegmentMatches(indexedPropertySegments[i], propertySegments[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean indexedSegmentMatches(String indexedSegment, String segment) {
        return indexedSegment.startsWith(segment + "[") && indexedSegment.endsWith("]");
    }

    private static Set<String> getIndexedSourcePropertyNames(Object source, String indexedSourcePropertyPrefix) {
        Set<String> indexedSourcePropertyNames = new LinkedHashSet<>();
        for (String propertyName : getSourcePropertyNames(source)) {
            if (propertyName.startsWith(indexedSourcePropertyPrefix)) {
                int closingIndex = propertyName.indexOf(']', indexedSourcePropertyPrefix.length());
                if (closingIndex > -1) {
                    indexedSourcePropertyNames.add(propertyName.substring(0, closingIndex + 1));
                }
            }
        }
        return indexedSourcePropertyNames;
    }

    private static boolean containsSourceProperty(Object source, String propertyName) {
        if (source instanceof DataBindingSource) {
            return ((DataBindingSource) source).containsProperty(propertyName);
        }
        if (source instanceof Map) {
            return ((Map) source).containsKey(propertyName);
        }
        return false;
    }

    private static Set<String> getSourcePropertyNames(Object source) {
        Set<String> propertyNames = new LinkedHashSet<>();
        if (source instanceof DataBindingSource) {
            propertyNames.addAll(((DataBindingSource) source).getPropertyNames());
        }
        else if (source instanceof Map) {
            for (Object key : ((Map) source).keySet()) {
                propertyNames.add(key.toString());
            }
        }
        return propertyNames;
    }

    private static Object getSourcePropertyValue(Object source, String propertyName) {
        if (source instanceof DataBindingSource) {
            return ((DataBindingSource) source).getPropertyValue(propertyName);
        }
        return ((Map) source).get(propertyName);
    }

    private static String checkboxMarkerPropertyName(String propertyName) {
        int separator = propertyPathSeparator(propertyName, true);
        if (separator == -1) {
            return "_" + propertyName;
        }
        return propertyName.substring(0, separator + 1) + "_" + propertyName.substring(separator + 1);
    }

    private static boolean hasNestedSourceEntries(Map map) {
        for (Object value : map.values()) {
            if (value instanceof Map || value instanceof Collection || value instanceof DataBindingSource) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldExpandMapEntries(Object target, Class targetType, String propertyName) {
        Object value = getTargetPropertyValue(target, propertyName);
        if (value instanceof Map && hasStructuredTargetMapValues((Map) value)) {
            return true;
        }

        Class mapValueType = getMapValueType(target, targetType, propertyName);
        return mapValueType != null && isStructuredMapValueType(mapValueType);
    }

    private static boolean hasStructuredTargetMapValues(Map map) {
        for (Object value : map.values()) {
            if (value != null && isStructuredMapValueType(value.getClass())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStructuredMapValueType(Class valueType) {
        Package valuePackage = valueType.getPackage();
        return !valueType.isPrimitive() &&
            (valuePackage == null || !valuePackage.getName().startsWith("java.")) &&
            !CharSequence.class.isAssignableFrom(valueType) &&
            !Number.class.isAssignableFrom(valueType) &&
            !Boolean.class.isAssignableFrom(valueType) &&
            !Enum.class.isAssignableFrom(valueType) &&
            !Map.class.isAssignableFrom(valueType) &&
            !Collection.class.isAssignableFrom(valueType) &&
            !Object.class.equals(valueType);
    }

    private static Class getMapValueType(Object target, Class targetType, String propertyName) {
        Class resolvedTargetType = target == null ? targetType : target.getClass();
        if (resolvedTargetType == null) {
            return null;
        }

        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(resolvedTargetType);
        MetaProperty metaProperty = mc.getMetaProperty(propertyName);
        if (metaProperty == null || !Map.class.isAssignableFrom(metaProperty.getType())) {
            return null;
        }

        Field field = findField(resolvedTargetType, propertyName);
        if (field == null) {
            return null;
        }
        return getMapValueType(field.getGenericType());
    }

    private static Class getMapValueType(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
        if (typeArguments.length < 2) {
            return null;
        }
        Type valueType = typeArguments[1];
        if (valueType instanceof Class) {
            return (Class) valueType;
        }
        if (valueType instanceof ParameterizedType && ((ParameterizedType) valueType).getRawType() instanceof Class) {
            return (Class) ((ParameterizedType) valueType).getRawType();
        }
        return null;
    }

    private static Field findField(Class type, String propertyName) {
        Class currentType = type;
        while (currentType != null) {
            try {
                return currentType.getDeclaredField(propertyName);
            }
            catch (NoSuchFieldException e) {
                currentType = currentType.getSuperclass();
            }
        }
        return null;
    }

    private static Object getTargetObject(Object object, String targetPathPrefix) {
        if (targetPathPrefix == null || targetPathPrefix.length() == 0) {
            return object;
        }

        Object targetObject = object;
        for (String propertyName : splitPropertyPath(targetPathPrefix)) {
            if (targetObject == null) {
                return null;
            }
            targetObject = getPropertyValue(targetObject, propertyName);
        }
        return targetObject;
    }

    private static Object getTargetPropertyValue(Object target, String propertyName) {
        if (target == null) {
            return null;
        }

        try {
            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(target.getClass());
            return mc.getProperty(target, propertyName);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String appendPropertyPath(String parentPath, String propertyName) {
        if (parentPath == null || parentPath.length() == 0) {
            return propertyName;
        }
        return parentPath + "." + propertyName;
    }

    private static void setPropertyToNull(Object object, String propertyName) {
        String previousClearPath = CLEAR_PATH.get();
        CLEAR_PATH.set(propertyName);
        try {
            String[] propertyNames = splitPropertyPath(propertyName);
            Object currentObject = object;
            for (int i = 0; i < propertyNames.length - 1 && currentObject != null; i++) {
                currentObject = getPropertyValue(currentObject, propertyNames[i]);
            }
            if (currentObject != null) {
                setPropertyValueToNull(currentObject, propertyNames[propertyNames.length - 1]);
            }
        }
        finally {
            if (previousClearPath == null) {
                CLEAR_PATH.remove();
            }
            else {
                CLEAR_PATH.set(previousClearPath);
            }
        }
    }

    private static Object getPropertyValue(Object object, String propertyName) {
        int bracket = propertyName.indexOf('[');
        try {
            if (bracket == -1) {
                MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
                return mc.getProperty(object, propertyName);
            }

            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
            Object indexedProperty = mc.getProperty(object, propertyName.substring(0, bracket));
            return getIndexedValue(indexedProperty, propertyName.substring(bracket + 1, propertyName.indexOf(']', bracket)));
        }
        catch (RuntimeException e) {
            addClearError(propertyName, e);
            return null;
        }
    }

    private static Object getIndexedValue(Object indexedProperty, String index) {
        if (indexedProperty instanceof List) {
            List list = (List) indexedProperty;
            Integer parsedIndex = parseIndex(index);
            return parsedIndex != null && parsedIndex >= 0 && parsedIndex < list.size() ? list.get(parsedIndex) : null;
        }
        if (indexedProperty instanceof Map) {
            return ((Map) indexedProperty).get(index);
        }
        return null;
    }

    private static void setPropertyValueToNull(Object object, String propertyName) {
        int bracket = propertyName.indexOf('[');
        try {
            if (bracket == -1) {
                MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
                MetaProperty metaProperty = mc.hasProperty(object, propertyName);
                if (metaProperty != null) {
                    mc.setProperty(object, propertyName,
                            metaProperty.getType().isPrimitive() ? primitiveDefault(metaProperty.getType()) : null);
                }
                return;
            }

            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
            Object indexedProperty = mc.getProperty(object, propertyName.substring(0, bracket));
            String index = propertyName.substring(bracket + 1, propertyName.indexOf(']', bracket));
            if (indexedProperty instanceof List) {
                List list = (List) indexedProperty;
                Integer parsedIndex = parseIndex(index);
                if (parsedIndex != null && parsedIndex >= 0 && parsedIndex < list.size()) {
                    list.set(parsedIndex, null);
                }
            }
            else if (indexedProperty instanceof Map) {
                ((Map) indexedProperty).put(index, null);
            }
        }
        catch (RuntimeException e) {
            addClearError(propertyName, e);
        }
    }

    private static void addClearError(String propertyName, RuntimeException exception) {
        BindingResult bindingResult = BINDING_RESULT.get();
        if (bindingResult == null) {
            return;
        }
        String clearPath = CLEAR_PATH.get();
        String field = clearPath == null ? propertyName : clearPath;
        bindingResult.addError(new FieldError(bindingResult.getObjectName(), field, null, true,
                new String[] { "typeMismatch." + field, "typeMismatch" }, null,
                "Failed to clear omitted included property: " + exception.getMessage()));
    }

    private static Object primitiveDefault(Class primitiveType) {
        if (Boolean.TYPE.equals(primitiveType)) {
            return false;
        }
        if (Character.TYPE.equals(primitiveType)) {
            return Character.valueOf('\0');
        }
        if (Byte.TYPE.equals(primitiveType)) {
            return Byte.valueOf((byte) 0);
        }
        if (Short.TYPE.equals(primitiveType)) {
            return Short.valueOf((short) 0);
        }
        if (Integer.TYPE.equals(primitiveType)) {
            return Integer.valueOf(0);
        }
        if (Long.TYPE.equals(primitiveType)) {
            return Long.valueOf(0L);
        }
        if (Float.TYPE.equals(primitiveType)) {
            return Float.valueOf(0F);
        }
        if (Double.TYPE.equals(primitiveType)) {
            return Double.valueOf(0D);
        }
        return null;
    }

    private static Integer parseIndex(String index) {
        try {
            return Integer.valueOf(index);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

}

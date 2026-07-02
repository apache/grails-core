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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaProperty;

import jakarta.servlet.ServletRequest;

import org.springframework.context.ApplicationContext;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import grails.core.GrailsApplication;
import grails.databinding.CollectionDataBindingSource;
import grails.databinding.DataBinder;
import grails.databinding.DataBindingSource;
import grails.databinding.SimpleMapDataBindingSource;
import grails.util.Environment;
import grails.util.Holders;
import grails.validation.ValidationErrors;
import grails.web.mime.MimeType;
import grails.web.mime.MimeTypeResolver;
import grails.web.mime.MimeTypeUtils;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.OneToOne;
import org.grails.web.databinding.DefaultASTDatabindingHelper;
import org.grails.web.databinding.bindingsource.DataBindingSourceRegistry;
import org.grails.web.databinding.bindingsource.DefaultDataBindingSourceRegistry;
import org.grails.web.databinding.bindingsource.InvalidRequestBodyException;

/**
 * Utility methods to perform data binding from Grails objects.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class DataBindingUtils {

    public static final String DATA_BINDER_BEAN_NAME = "grailsWebDataBinder";
    private static final String BLANK = "";
    private static final Map<Class, List> CLASS_TO_BINDING_INCLUDE_LIST = new ConcurrentHashMap<>();

    /**
     * Associations both sides of any bidirectional relationships found in the object and source map to bind
     *
     * @param object The object
     * @param source The source map
     * @param persistentEntity The PersistentEntity for the object
     */
    public static void assignBidirectionalAssociations(Object object, Map source, PersistentEntity persistentEntity) {
        if (source == null) {
            return;
        }

        for (Object key : source.keySet()) {
            String propertyName = key.toString();
            if (propertyName.indexOf('.') > -1) {
                propertyName = propertyName.substring(0, propertyName.indexOf('.'));
            }
            PersistentProperty prop = persistentEntity.getPropertyByName(propertyName);

            if (prop != null && prop instanceof OneToOne && ((OneToOne) prop).isBidirectional()) {
                Object val = source.get(key);
                PersistentProperty otherSide = ((OneToOne) prop).getInverseSide();
                if (val != null && otherSide != null) {
                    MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(val.getClass());
                    try {
                        mc.setProperty(val, otherSide.getName(), object);
                    }
                    catch (Exception e) {
                        // ignore
                    }
                }
            }

        }
    }

    /**
     * Binds the given source object to the given target object performing type conversion if necessary
     *
     * @param object The object to bind to
     * @param source The source object
     * @return A BindingResult if there were errors or null if it was successful
     */
    public static BindingResult bindObjectToInstance(Object object, Object source) {
        return bindObjectToInstance(object, source, getBindingIncludeList(object), Collections.emptyList(), null);
    }

    protected static List getBindingIncludeList(final Object object) {
        List includeList = Collections.emptyList();
        try {
            final Class<? extends Object> objectClass = object.getClass();
            if (CLASS_TO_BINDING_INCLUDE_LIST.containsKey(objectClass)) {
                includeList = CLASS_TO_BINDING_INCLUDE_LIST.get(objectClass);
            } else {
                final Field whiteListField = objectClass.getDeclaredField(DefaultASTDatabindingHelper.DEFAULT_DATABINDING_WHITELIST);
                if (whiteListField != null) {
                    if ((whiteListField.getModifiers() & Modifier.STATIC) != 0) {
                        final Object whiteListValue = whiteListField.get(objectClass);
                        if (whiteListValue instanceof List) {
                            includeList = (List) whiteListValue;
                        }
                    }
                }
                if (!Environment.getCurrent().isReloadEnabled()) {
                    CLASS_TO_BINDING_INCLUDE_LIST.put(objectClass, includeList);
                }
            }
        } catch (Exception e) {
        }
        return includeList;
    }

    /**
     * Binds the given source object to the given target object performing type conversion if necessary
     *
     * @param entity The PersistentEntity instance
     * @param object The object to bind to
     * @param source The source object
     *
     * @see org.grails.datastore.mapping.model.PersistentEntity
     *
     * @return A BindingResult if there were errors or null if it was successful
     */
    public static BindingResult bindObjectToDomainInstance(PersistentEntity entity, Object object, Object source) {
        return bindObjectToDomainInstance(entity, object, source, getBindingIncludeList(object), Collections.emptyList(), null);
    }

    /**
     * For each DataBindingSource provided by collectionBindingSource a new instance of targetType is created,
     * data binding is imposed on that instance with the DataBindingSource and the instance is added to the end of
     * collectionToPopulate
     *
     * @param targetType The type of objects to create, must be a concrete class
     * @param collectionToPopulate A collection to populate with new instances of targetType
     * @param collectionBindingSource A CollectionDataBindingSource
     * @since 2.3
     */
    public static <T> void bindToCollection(final Class<T> targetType, final Collection<T> collectionToPopulate, final CollectionDataBindingSource collectionBindingSource) throws InstantiationException, IllegalAccessException {
        bindToCollection(targetType, collectionToPopulate, collectionBindingSource, null);
    }

    public static <T> void bindToCollection(final Class<T> targetType, final Collection<T> collectionToPopulate, final CollectionDataBindingSource collectionBindingSource, final List include) throws InstantiationException, IllegalAccessException {
        final GrailsApplication application = Holders.findApplication();
        PersistentEntity entity = null;
        if (application != null) {
            try {
                entity = application.getMappingContext().getPersistentEntity(targetType.getName());
            } catch (GrailsConfigurationException e) {
                //no-op
            }
        }
        final List<DataBindingSource> dataBindingSources = collectionBindingSource.getDataBindingSources();
        for (final DataBindingSource dataBindingSource : dataBindingSources) {
            final T newObject;
            try {
                newObject = targetType.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException | InvocationTargetException ex) {
                throw new InstantiationException(
                    "Could not instantiate class [" + targetType.getName() + "]: " + ex.getMessage()
                );
            }

            DataBindingSource sourceToBind = dataBindingSource;
            if (include != null) {
                sourceToBind = createSecureDataBindingSource(newObject, dataBindingSource, include, null);
            }
            bindObjectToDomainInstance(entity, newObject, sourceToBind, include == null ? getBindingIncludeList(newObject) : include, Collections.emptyList(), null);
            collectionToPopulate.add(newObject);
        }
    }

    public static <T> void bindToCollection(final Class<T> targetType, final Collection<T> collectionToPopulate, final ServletRequest request) throws InstantiationException, IllegalAccessException {
        bindToCollection(targetType, collectionToPopulate, request, null);
    }

    public static <T> void bindToCollection(final Class<T> targetType, final Collection<T> collectionToPopulate, final ServletRequest request, final List include) throws InstantiationException, IllegalAccessException {
        final GrailsApplication grailsApplication = Holders.findApplication();
        final CollectionDataBindingSource collectionDataBindingSource = createCollectionDataBindingSource(grailsApplication, targetType, request);
        bindToCollection(targetType, collectionToPopulate, collectionDataBindingSource, include);
    }

    /**
     * Binds the given source object to the given target object performing type conversion if necessary
     *
     * @param object The object to bind to
     * @param source The source object
     * @param include The list of properties to include
     * @param exclude The list of properties to exclude
     * @param filter The prefix to filter by
     *
     * @return A BindingResult if there were errors or null if it was successful
     */
    public static BindingResult bindObjectToInstance(Object object, Object source, List include, List exclude, String filter) {
        if (include == null && exclude == null) {
            include = getBindingIncludeList(object);
        }
        GrailsApplication application = Holders.findApplication();
        PersistentEntity entity = findPersistentEntity(application, object);
        return bindObjectToDomainInstance(entity, object, source, include, exclude, filter);
    }

    private static PersistentEntity findPersistentEntity(GrailsApplication application, Object object) {
        if (application != null) {
            try {
                return application.getMappingContext().getPersistentEntity(object.getClass().getName());
            } catch (GrailsConfigurationException e) {
                return null;
            }
        }
        return null;
    }

    public static DataBindingSource createSecureDataBindingSource(Object object, Object source, List allowedParams, String filter) {
        GrailsApplication application = Holders.findApplication();
        DataBindingSource bindingSource = createDataBindingSource(application, object.getClass(), source);
        return createSecureDataBindingSource(object, bindingSource, allowedParams, filter);
    }

    public static BindingResult secureBindObjectToInstance(Object object, Object source, List allowedParams, String filter, boolean nullMissing) {
        BindingResult bindingResult = null;
        GrailsApplication grailsApplication = Holders.findApplication();
        PersistentEntity entity = findPersistentEntity(grailsApplication, object);

        try {
            final DataBindingSource bindingSource = createDataBindingSource(grailsApplication, object.getClass(), source);
            final DataBindingSource secureBindingSource = createSecureDataBindingSource(object, bindingSource, allowedParams, filter);
            final DataBinder grailsWebDataBinder = getGrailsWebDataBinder(grailsApplication);
            grailsWebDataBinder.bind(object, secureBindingSource, null, allowedParams, Collections.emptyList());
            if (nullMissing) {
                assignNullToMissingAllowedProperties(object, secureBindingSource, allowedParams);
            }
        } catch (InvalidRequestBodyException e) {
            String messageCode = "invalidRequestBody";
            Class objectType = object.getClass();
            String defaultMessage = "An error occurred parsing the body of the request";
            String[] codes = getMessageCodes(messageCode, objectType);
            bindingResult = new BeanPropertyBindingResult(object, objectType.getName());
            bindingResult.addError(new ObjectError(bindingResult.getObjectName(), codes, null, defaultMessage));
        } catch (Exception e) {
            bindingResult = new BeanPropertyBindingResult(object, object.getClass().getName());
            bindingResult.addError(new ObjectError(bindingResult.getObjectName(), resolveBindingErrorMessage(e)));
        }

        return processBindingResult(entity, object, bindingResult);
    }

    public static DataBindingSource createSecureDataBindingSource(DataBindingSource bindingSource, List allowedParams, String filter) {
        return createSecureDataBindingSource(null, bindingSource, allowedParams, filter);
    }

    private static DataBindingSource createSecureDataBindingSource(Object object, DataBindingSource bindingSource, List allowedParams, String filter) {
        Map secureSource = new LinkedHashMap();
        for (Object allowedParam : allowedParams) {
            if (allowedParam instanceof CharSequence) {
                String propertyName = allowedParam.toString();
                String sourcePropertyName = filter == null ? propertyName : filter + "." + propertyName;
                copyAllowedProperty(secureSource, propertyName, propertyName, object, object == null ? null : object.getClass(), bindingSource, sourcePropertyName);
                copyAllowedCheckboxMarker(secureSource, propertyName, bindingSource, sourcePropertyName);
            }
        }
        return new SimpleMapDataBindingSource(secureSource);
    }

    private static void copyAllowedCheckboxMarker(Map secureSource, String targetPropertyName, Object source, String sourcePropertyName) {
        String targetMarkerPropertyName = checkboxMarkerPropertyName(targetPropertyName);
        String sourceMarkerPropertyName = checkboxMarkerPropertyName(sourcePropertyName);
        copyAllowedProperty(secureSource, targetMarkerPropertyName, source, sourceMarkerPropertyName);
    }

    private static String checkboxMarkerPropertyName(String propertyName) {
        int separator = lastPropertyPathSeparator(propertyName);
        if (separator == -1) {
            return "_" + propertyName;
        }
        return propertyName.substring(0, separator + 1) + "_" + propertyName.substring(separator + 1);
    }

    private static boolean copyAllowedProperty(Map secureSource, String targetPropertyName, Object source, String sourcePropertyName) {
        return copyAllowedProperty(secureSource, targetPropertyName, targetPropertyName, null, null, source, sourcePropertyName);
    }

    private static boolean copyAllowedProperty(Map secureSource, String targetPropertyName, String targetLookupPropertyName, Object target, Class targetType, Object source, String sourcePropertyName) {
        if (containsSourceProperty(source, sourcePropertyName)) {
            if (shouldCopyExactSourceProperty(target, targetType, targetLookupPropertyName, sourcePropertyName)) {
                putNestedValue(secureSource, targetPropertyName, getSourcePropertyValue(source, sourcePropertyName));
                return true;
            }
        }
        int separator = propertyPathSeparator(sourcePropertyName);
        if (separator == -1) {
            return false;
        }
        if (copyAllowedIndexedPathProperty(secureSource, targetPropertyName, source, sourcePropertyName)) {
            return true;
        }
        if (copyAllowedIndexedRootProperty(secureSource, targetPropertyName, source, sourcePropertyName)) {
            return true;
        }
        String sourceRootPropertyName = sourcePropertyName.substring(0, separator);
        String nestedSourcePropertyName = sourcePropertyName.substring(separator + 1);
        if (!containsSourceProperty(source, sourceRootPropertyName)) {
            return copyAllowedIndexedProperty(secureSource, targetPropertyName, source, sourceRootPropertyName, nestedSourcePropertyName);
        }
        Object nestedSource = getSourcePropertyValue(source, sourceRootPropertyName);
        String nestedTargetLookupPropertyName = targetLookupPropertyName;
        Object nestedTarget = target;
        Class nestedTargetType = targetType;
        Class collectionElementType = null;
        Class mapValueType = null;
        boolean expandMapEntries = false;
        if (splitPropertyPath(sourcePropertyName).length <= splitPropertyPath(targetLookupPropertyName).length) {
            int targetSeparator = propertyPathSeparator(targetLookupPropertyName);
            if (targetSeparator == -1) {
                return false;
            }
            String targetRootPropertyName = targetLookupPropertyName.substring(0, targetSeparator);
            nestedTargetLookupPropertyName = targetLookupPropertyName.substring(targetSeparator + 1);
            nestedTargetType = getTargetPropertyType(target, targetType, targetRootPropertyName);
            collectionElementType = getCollectionElementType(target, targetType, targetRootPropertyName);
            mapValueType = getMapValueType(target, targetType, targetRootPropertyName);
            expandMapEntries = shouldExpandMapEntries(target, targetType, targetRootPropertyName);
            nestedTarget = getTargetPropertyValue(target, targetRootPropertyName);
        }
        if (nestedSource instanceof Collection) {
            return copyAllowedCollectionProperty(secureSource, targetPropertyName, (Collection) nestedSource, nestedSourcePropertyName, nestedTargetLookupPropertyName, nestedTarget, collectionElementType);
        }
        if (nestedSource instanceof Map) {
            if (expandMapEntries) {
                return copyAllowedMapProperty(secureSource, targetPropertyName, (Map) nestedSource, nestedSourcePropertyName, mapValueType);
            }
            if (copyAllowedDirectScalarMapProperty(secureSource, targetPropertyName, (Map) nestedSource, nestedSourcePropertyName)) {
                return true;
            }
            if (copyAllowedDirectMapProperty(secureSource, targetPropertyName, (Map) nestedSource, nestedSourcePropertyName)) {
                return true;
            }
        }
        return copyAllowedProperty(secureSource, targetPropertyName, nestedTargetLookupPropertyName, nestedTarget, nestedTargetType, nestedSource, nestedSourcePropertyName);
    }

    private static boolean shouldCopyExactSourceProperty(Object target, Class targetType, String targetLookupPropertyName, String sourcePropertyName) {
        if (propertyPathSeparator(sourcePropertyName) == -1) {
            return true;
        }
        int targetSeparator = propertyPathSeparator(targetLookupPropertyName);
        if (targetSeparator == -1) {
            return true;
        }
        String targetRootPropertyName = targetLookupPropertyName.substring(0, targetSeparator);
        return !shouldExpandMapEntries(target, targetType, targetRootPropertyName);
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
        Class resolvedTargetType = getTargetType(target, targetType);
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

    private static Class getCollectionElementType(Object target, Class targetType, String propertyName) {
        Object value = getTargetPropertyValue(target, propertyName);
        if (value instanceof Collection && !((Collection) value).isEmpty()) {
            Object firstValue = ((Collection) value).iterator().next();
            if (firstValue != null) {
                return firstValue.getClass();
            }
        }

        Class resolvedTargetType = getTargetType(target, targetType);
        if (resolvedTargetType == null) {
            return null;
        }
        Field field = findField(resolvedTargetType, propertyName);
        if (field == null) {
            return null;
        }
        return getCollectionElementType(field.getGenericType());
    }

    private static Class getCollectionElementType(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return null;
        }

        Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
        if (typeArguments.length == 0) {
            return null;
        }
        Type valueType = typeArguments[0];
        if (valueType instanceof Class) {
            return (Class) valueType;
        }
        if (valueType instanceof ParameterizedType && ((ParameterizedType) valueType).getRawType() instanceof Class) {
            return (Class) ((ParameterizedType) valueType).getRawType();
        }
        return null;
    }

    private static Class getTargetPropertyType(Object target, Class targetType, String propertyName) {
        Object value = getTargetPropertyValue(target, propertyName);
        if (value != null) {
            return value.getClass();
        }

        Class resolvedTargetType = getTargetType(target, targetType);
        if (resolvedTargetType == null) {
            return null;
        }
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(resolvedTargetType);
        MetaProperty metaProperty = mc.getMetaProperty(propertyName);
        if (metaProperty != null) {
            return metaProperty.getType();
        }
        Field field = findField(resolvedTargetType, propertyName);
        return field == null ? null : field.getType();
    }

    private static Class getTargetType(Object target, Class targetType) {
        if (target != null) {
            return target.getClass();
        }
        return targetType;
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

    private static boolean copyAllowedIndexedProperty(Map secureSource, String targetPropertyName, Object source, String sourceRootPropertyName, String nestedSourcePropertyName) {
        int separator = propertyPathSeparator(targetPropertyName);
        if (separator == -1) {
            return false;
        }

        String targetRootPropertyName = targetPropertyName.substring(0, separator);
        if (!sourceRootPropertyName.equals(targetRootPropertyName)) {
            return false;
        }
        String nestedTargetPropertyName = targetPropertyName.substring(separator + 1);
        boolean copied = false;
        String indexedSourcePropertyPrefix = sourceRootPropertyName + "[";
        for (String indexedSourcePropertyName : getIndexedSourcePropertyNames(source, indexedSourcePropertyPrefix)) {
            String targetIndexedPropertyName = targetRootPropertyName + indexedSourcePropertyName.substring(sourceRootPropertyName.length());
            String targetIndexedNestedPropertyName = targetIndexedPropertyName + "." + nestedTargetPropertyName;
            if (containsSourceProperty(source, indexedSourcePropertyName)) {
                Object nestedSource = getSourcePropertyValue(source, indexedSourcePropertyName);
                if (copyAllowedProperty(secureSource, targetIndexedNestedPropertyName, nestedSource, nestedSourcePropertyName)) {
                    copied = true;
                }
            }
            else if (copyAllowedProperty(secureSource, targetIndexedNestedPropertyName, source, indexedSourcePropertyName + "." + nestedSourcePropertyName)) {
                copied = true;
            }
        }
        return copied;
    }

    private static boolean copyAllowedIndexedPathProperty(Map secureSource, String targetPropertyName, Object source, String sourcePropertyName) {
        boolean copied = false;
        String[] targetSegments = splitPropertyPath(targetPropertyName);
        String[] sourceSegments = splitPropertyPath(sourcePropertyName);
        int exactPrefixSegments = sourceSegments.length - targetSegments.length;
        for (String indexedSourcePropertyName : getSourcePropertyNames(source)) {
            if (indexedPropertyPathMatches(indexedSourcePropertyName, sourcePropertyName, exactPrefixSegments)) {
                putNestedValue(secureSource, indexedTargetPropertyName(targetPropertyName, sourcePropertyName, indexedSourcePropertyName), getSourcePropertyValue(source, indexedSourcePropertyName));
                copied = true;
            }
        }
        return copied;
    }

    private static boolean copyAllowedIndexedRootProperty(Map secureSource, String targetPropertyName, Object source, String sourcePropertyName) {
        int sourceSeparator = lastPropertyPathSeparator(sourcePropertyName);
        int targetSeparator = lastPropertyPathSeparator(targetPropertyName);
        if (sourceSeparator == -1 || targetSeparator == -1) {
            return false;
        }

        String sourceRootPropertyName = sourcePropertyName.substring(0, sourceSeparator);
        String targetRootPropertyName = targetPropertyName.substring(0, targetSeparator);
        String nestedSourcePropertyName = sourcePropertyName.substring(sourceSeparator + 1);
        String nestedTargetPropertyName = targetPropertyName.substring(targetSeparator + 1);
        String[] targetSegments = splitPropertyPath(targetRootPropertyName);
        String[] sourceSegments = splitPropertyPath(sourceRootPropertyName);
        int exactPrefixSegments = sourceSegments.length - targetSegments.length;
        boolean copied = false;
        for (String indexedSourcePropertyName : getSourcePropertyNames(source)) {
            if (indexedPropertyPathMatches(indexedSourcePropertyName, sourceRootPropertyName, exactPrefixSegments) && containsSourceProperty(source, indexedSourcePropertyName)) {
                String targetIndexedPropertyName = indexedTargetPropertyName(targetRootPropertyName, sourceRootPropertyName, indexedSourcePropertyName);
                Object nestedSource = getSourcePropertyValue(source, indexedSourcePropertyName);
                if (copyAllowedProperty(secureSource, targetIndexedPropertyName + "." + nestedTargetPropertyName, nestedSource, nestedSourcePropertyName)) {
                    copied = true;
                }
            }
        }
        return copied;
    }

    private static int propertyPathSeparator(String propertyName) {
        return propertyPathSeparator(propertyName, false);
    }

    private static int lastPropertyPathSeparator(String propertyName) {
        return propertyPathSeparator(propertyName, true);
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

    private static String indexedTargetPropertyName(String targetPropertyName, String sourcePropertyName, String indexedSourcePropertyName) {
        String[] targetSegments = splitPropertyPath(targetPropertyName);
        String[] sourceSegments = splitPropertyPath(sourcePropertyName);
        String[] indexedSourceSegments = splitPropertyPath(indexedSourcePropertyName);
        int sourceOffset = Math.max(0, sourceSegments.length - targetSegments.length);
        int targetOffset = Math.max(0, targetSegments.length - sourceSegments.length);
        StringBuilder indexedTargetPropertyName = new StringBuilder();
        for (int i = 0; i < targetSegments.length; i++) {
            if (i > 0) {
                indexedTargetPropertyName.append('.');
            }
            String targetSegment = targetSegments[i];
            int sourceIndex = i - targetOffset + sourceOffset;
            if (sourceIndex < 0 || sourceIndex >= indexedSourceSegments.length || sourceIndex >= sourceSegments.length) {
                indexedTargetPropertyName.append(targetSegment);
                continue;
            }

            String indexedSourceSegment = indexedSourceSegments[sourceIndex];
            String sourceSegment = sourceSegments[sourceIndex];
            if (indexedSegmentMatches(indexedSourceSegment, sourceSegment)) {
                indexedTargetPropertyName.append(targetSegment).append(indexedSourceSegment.substring(sourceSegment.length()));
            }
            else {
                indexedTargetPropertyName.append(targetSegment);
            }
        }
        return indexedTargetPropertyName.toString();
    }

    private static boolean indexedPropertyPathMatches(String indexedPropertyName, String propertyName, int exactPrefixSegments) {
        String[] indexedPropertySegments = splitPropertyPath(indexedPropertyName);
        String[] propertySegments = splitPropertyPath(propertyName);
        if (indexedPropertySegments.length != propertySegments.length) {
            return false;
        }
        boolean indexed = false;
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
            indexed = true;
        }
        return indexed;
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

    private static boolean copyAllowedCollectionProperty(Map secureSource, String targetPropertyName, Collection collection, String sourcePropertyName) {
        return copyAllowedCollectionProperty(secureSource, targetPropertyName, collection, sourcePropertyName, sourcePropertyName, null, null);
    }

    private static boolean copyAllowedCollectionProperty(Map secureSource, String targetPropertyName, Collection collection, String sourcePropertyName, String targetLookupPropertyName, Object targetCollection, Class targetElementType) {
        int separator = propertyPathSeparator(targetPropertyName);
        if (separator == -1) {
            return false;
        }
        String targetRootPropertyName = targetPropertyName.substring(0, separator);
        String nestedTargetPropertyName = targetPropertyName.substring(separator + 1);
        List filteredCollection = getOrCreateNestedCollection(secureSource, targetRootPropertyName, collection.size());
        int index = 0;
        boolean copied = false;
        for (Object item : collection) {
            Map filteredItem = (Map) filteredCollection.get(index);
            Object targetItem = getCollectionValue(targetCollection, index);
            Class itemTargetType = targetItem == null ? targetElementType : targetItem.getClass();
            if (copyAllowedProperty(filteredItem, nestedTargetPropertyName, targetLookupPropertyName, targetItem, itemTargetType, item, sourcePropertyName)) {
                copied = true;
            }
            index++;
        }
        return copied;
    }

    private static Object getCollectionValue(Object targetCollection, int index) {
        if (targetCollection instanceof List && ((List) targetCollection).size() > index) {
            return ((List) targetCollection).get(index);
        }
        if (targetCollection instanceof Collection) {
            int currentIndex = 0;
            for (Object value : (Collection) targetCollection) {
                if (currentIndex == index) {
                    return value;
                }
                currentIndex++;
            }
        }
        return null;
    }

    private static boolean copyAllowedDirectScalarMapProperty(Map secureSource, String targetPropertyName, Map map, String sourcePropertyName) {
        if (propertyPathSeparator(sourcePropertyName) > -1 || !containsSourceProperty(map, sourcePropertyName)) {
            return false;
        }
        Object directValue = getSourcePropertyValue(map, sourcePropertyName);
        if (isNestedSource(directValue)) {
            return false;
        }
        putNestedValue(secureSource, targetPropertyName, directValue);
        return true;
    }

    private static boolean copyAllowedDirectMapProperty(Map secureSource, String targetPropertyName, Map map, String sourcePropertyName) {
        if (propertyPathSeparator(sourcePropertyName) > -1 || !containsSourceProperty(map, sourcePropertyName)) {
            return false;
        }
        putNestedValue(secureSource, targetPropertyName, getSourcePropertyValue(map, sourcePropertyName));
        return true;
    }

    private static boolean copyAllowedMapProperty(Map secureSource, String targetPropertyName, Map map, String sourcePropertyName, Class targetValueType) {
        if (!hasNestedSourceEntries(map)) {
            return false;
        }

        int separator = propertyPathSeparator(targetPropertyName);
        if (separator == -1) {
            return false;
        }
        String targetRootPropertyName = targetPropertyName.substring(0, separator);
        String nestedTargetPropertyName = targetPropertyName.substring(separator + 1);
        boolean copied = false;
        for (Object entryObject : map.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            String targetIndexedPropertyName = targetRootPropertyName + "[" + entry.getKey() + "]";
            if (copyAllowedProperty(secureSource, targetIndexedPropertyName + "." + nestedTargetPropertyName, nestedTargetPropertyName, null, targetValueType, entry.getValue(), sourcePropertyName)) {
                copied = true;
            }
        }
        return copied;
    }

    private static boolean isNestedSource(Object value) {
        return value instanceof Map || value instanceof Collection || value instanceof DataBindingSource;
    }

    private static boolean hasNestedSourceEntries(Map map) {
        for (Object value : map.values()) {
            if (isNestedSource(value)) {
                return true;
            }
        }
        return false;
    }

    private static List getOrCreateNestedCollection(Map secureSource, String propertyName, int size) {
        Object existingValue = secureSource.get(propertyName);
        List collection;
        if (existingValue instanceof List) {
            collection = (List) existingValue;
        }
        else {
            collection = new ArrayList(size);
            secureSource.put(propertyName, collection);
        }
        while (collection.size() < size) {
            collection.add(new LinkedHashMap());
        }
        return collection;
    }

    private static void putNestedValue(Map secureSource, String propertyName, Object value) {
        int separator = propertyPathSeparator(propertyName);
        if (separator == -1) {
            secureSource.put(propertyName, value);
            return;
        }
        String rootPropertyName = propertyName.substring(0, separator);
        Map nestedSource = getOrCreateNestedMap(secureSource, rootPropertyName);
        putNestedValue(nestedSource, propertyName.substring(separator + 1), value);
    }

    private static Map getOrCreateNestedMap(Map secureSource, String propertyName) {
        Object existingValue = secureSource.get(propertyName);
        if (existingValue instanceof Map) {
            return (Map) existingValue;
        }
        Map nestedSource = new LinkedHashMap();
        secureSource.put(propertyName, nestedSource);
        return nestedSource;
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

    public static void assignNullToMissingAllowedProperties(Object object, Object source, List allowedParams) {
        assignNullToMissingAllowedProperties(object, source, allowedParams, null);
    }

    public static void assignNullToMissingAllowedProperties(Object object, Object source, List allowedParams, String filter) {
        GrailsApplication application = Holders.findApplication();
        DataBindingSource bindingSource = createDataBindingSource(application, object.getClass(), source);
        assignNullToMissingAllowedProperties(object, bindingSource, allowedParams, filter);
    }

    private static void assignNullToMissingAllowedProperties(Object object, DataBindingSource bindingSource, List allowedParams, String filter) {
        for (Object allowedParam : allowedParams) {
            if (allowedParam instanceof CharSequence) {
                String propertyName = allowedParam.toString();
                if (propertyName.indexOf('*') == -1) {
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
        if (splitPropertyPath(sourcePropertyName).length > splitPropertyPath(targetPropertyName).length) {
            if (containsSourceProperty(source, sourceRootPropertyName)) {
                return assignNullToMissingIndexedProperties(object, getSourcePropertyValue(source, sourceRootPropertyName), targetPathPrefix, nestedSourcePropertyName, targetPropertyName);
            }
            return false;
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
            if (containsSourceProperty(source, indexedSourcePropertyName)) {
                indexed = true;
                String targetIndexedPropertyName = appendPropertyPath(targetPathPrefix, targetRootPropertyName + indexedSourcePropertyName.substring(sourceRootPropertyName.length()));
                Object nestedSource = getSourcePropertyValue(source, indexedSourcePropertyName);
                if (!assignNullToMissingIndexedProperties(object, nestedSource, targetIndexedPropertyName, nestedSourcePropertyName, nestedTargetPropertyName) && !containsPropertyPath(nestedSource, nestedSourcePropertyName)) {
                    setPropertyToNull(object, targetIndexedPropertyName + "." + nestedTargetPropertyName);
                }
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

    private static void assignNullToMissingNestedProperty(Object object, Object nestedSource, String targetIndexedPropertyName, String nestedSourcePropertyName, String nestedTargetPropertyName) {
        if (!assignNullToMissingIndexedProperties(object, nestedSource, targetIndexedPropertyName, nestedSourcePropertyName, nestedTargetPropertyName) && !containsPropertyPath(nestedSource, nestedSourcePropertyName)) {
            setPropertyToNull(object, targetIndexedPropertyName + "." + nestedTargetPropertyName);
        }
    }

    private static String appendPropertyPath(String parentPath, String propertyName) {
        if (parentPath == null || parentPath.length() == 0) {
            return propertyName;
        }
        return parentPath + "." + propertyName;
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

    private static void setPropertyToNull(Object object, String propertyName) {
        String[] propertyNames = splitPropertyPath(propertyName);
        Object currentObject = object;
        for (int i = 0; i < propertyNames.length - 1 && currentObject != null; i++) {
            currentObject = getPropertyValue(currentObject, propertyNames[i]);
        }
        if (currentObject != null) {
            setPropertyValueToNull(currentObject, propertyNames[propertyNames.length - 1]);
        }
    }

    private static Object getPropertyValue(Object object, String propertyName) {
        int bracket = propertyName.indexOf('[');
        if (bracket == -1) {
            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
            return mc.getProperty(object, propertyName);
        }

        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
        Object indexedProperty = mc.getProperty(object, propertyName.substring(0, bracket));
        return getIndexedValue(indexedProperty, propertyName.substring(bracket + 1, propertyName.indexOf(']', bracket)));
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
        if (bracket == -1) {
            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
            mc.setProperty(object, propertyName, null);
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

    private static Integer parseIndex(String index) {
        try {
            return Integer.valueOf(index);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private static String resolveBindingErrorMessage(Exception e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getName();
    }

    /**
     * Binds the given source object to the given target object performing type conversion if necessary
     *
     * @param entity The PersistentEntity instance
     * @param object The object to bind to
     * @param source The source object
     * @param include The list of properties to include
     * @param exclude The list of properties to exclude
     * @param filter The prefix to filter by
     *
     * @see org.grails.datastore.mapping.model.PersistentEntity
     *
     * @return A BindingResult if there were errors or null if it was successful
     */
    @SuppressWarnings("unchecked")
    public static BindingResult bindObjectToDomainInstance(PersistentEntity entity, Object object,
                                                           Object source, List include, List exclude, String filter) {
        BindingResult bindingResult = null;
        GrailsApplication grailsApplication = Holders.findApplication();

        try {
            final DataBindingSource bindingSource = createDataBindingSource(grailsApplication, object.getClass(), source);
            final DataBinder grailsWebDataBinder = getGrailsWebDataBinder(grailsApplication);
            grailsWebDataBinder.bind(object, bindingSource, filter, include, exclude);
        } catch (InvalidRequestBodyException e) {
            String messageCode = "invalidRequestBody";
            Class objectType = object.getClass();
            String defaultMessage = "An error occurred parsing the body of the request";
            String[] codes = getMessageCodes(messageCode, objectType);
            bindingResult = new BeanPropertyBindingResult(object, objectType.getName());
            bindingResult.addError(new ObjectError(bindingResult.getObjectName(), codes, null, defaultMessage));
        } catch (Exception e) {
            bindingResult = new BeanPropertyBindingResult(object, object.getClass().getName());
            bindingResult.addError(new ObjectError(bindingResult.getObjectName(), resolveBindingErrorMessage(e)));
        }

        return processBindingResult(entity, object, bindingResult);
    }

    private static BindingResult processBindingResult(PersistentEntity entity, Object object, BindingResult bindingResult) {
        if (entity != null && bindingResult != null) {
            BindingResult newResult = new ValidationErrors(object);
            for (Object error : bindingResult.getAllErrors()) {
                if (error instanceof FieldError) {
                    FieldError fieldError = (FieldError) error;
                    final boolean isBlank = BLANK.equals(fieldError.getRejectedValue());
                    if (!isBlank) {
                        newResult.addError(fieldError);
                    }
                    else {
                        PersistentProperty property = entity.getPropertyByName(fieldError.getField());
                        if (property != null) {
                            final boolean isOptional = property.isNullable();
                            if (!isOptional) {
                                newResult.addError(fieldError);
                            }
                        }
                        else {
                            newResult.addError(fieldError);
                        }
                    }
                }
                else {
                    newResult.addError((ObjectError) error);
                }
            }
            bindingResult = newResult;
        }
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass());
        if (mc.hasProperty(object, "errors") != null && bindingResult != null) {
            ValidationErrors errors = new ValidationErrors(object);
            errors.addAllErrors(bindingResult);
            mc.setProperty(object, "errors", errors);
        }
        return bindingResult;
    }

    protected static String[] getMessageCodes(String messageCode,
            Class objectType) {
        String[] codes = {objectType.getName() + "." + messageCode, messageCode};
        return codes;
    }

    public static DataBindingSourceRegistry getDataBindingSourceRegistry(GrailsApplication grailsApplication) {
        DataBindingSourceRegistry registry = null;
        if (grailsApplication != null) {
            ApplicationContext context = grailsApplication.getMainContext();
            if (context != null) {
                if (context.containsBean(DataBindingSourceRegistry.BEAN_NAME)) {
                    registry = context.getBean(DataBindingSourceRegistry.BEAN_NAME, DataBindingSourceRegistry.class);
                }
            }
        }
        if (registry == null) {
            registry = new DefaultDataBindingSourceRegistry();
        }

        return registry;
    }

    public static DataBindingSource createDataBindingSource(GrailsApplication grailsApplication, Class bindingTargetType, Object bindingSource) {
        final DataBindingSourceRegistry registry = getDataBindingSourceRegistry(grailsApplication);
        final MimeType mimeType = getMimeType(grailsApplication, bindingSource);
        return registry.createDataBindingSource(mimeType, bindingTargetType, bindingSource);
    }

    public static CollectionDataBindingSource createCollectionDataBindingSource(GrailsApplication grailsApplication, Class bindingTargetType, Object bindingSource) {
        final DataBindingSourceRegistry registry = getDataBindingSourceRegistry(grailsApplication);
        final MimeType mimeType = getMimeType(grailsApplication, bindingSource);
        return registry.createCollectionDataBindingSource(mimeType, bindingTargetType, bindingSource);
    }

    public static MimeType getMimeType(GrailsApplication grailsApplication,
            Object bindingSource) {
        final MimeTypeResolver mimeTypeResolver = getMimeTypeResolver(grailsApplication);
        return resolveMimeType(bindingSource, mimeTypeResolver);
    }

    public static MimeTypeResolver getMimeTypeResolver(
            GrailsApplication grailsApplication) {
        MimeTypeResolver mimeTypeResolver = null;
        if (grailsApplication != null) {
            ApplicationContext context = grailsApplication.getMainContext();
            if (context != null) {
                if (context.containsBean(MimeTypeResolver.BEAN_NAME)) {
                    mimeTypeResolver = context.getBean(MimeTypeResolver.BEAN_NAME, MimeTypeResolver.class);
                }
            }
        }
        return mimeTypeResolver;
    }

    public static MimeType resolveMimeType(Object bindingSource, MimeTypeResolver mimeTypeResolver) {
        return MimeTypeUtils.resolveMimeType(bindingSource, mimeTypeResolver);
    }

    private static DataBinder getGrailsWebDataBinder(final GrailsApplication grailsApplication) {
        DataBinder dataBinder = null;
        if (grailsApplication != null) {
            final ApplicationContext mainContext = grailsApplication.getMainContext();
            if (mainContext != null && mainContext.containsBean(DATA_BINDER_BEAN_NAME)) {
                dataBinder = mainContext.getBean(DATA_BINDER_BEAN_NAME, DataBinder.class);
            }
        }
        if (dataBinder == null) {
            // this should really never happen in the running app as the binder
            // should always be found in the context
            dataBinder = new GrailsWebDataBinder(grailsApplication);
        }
        return dataBinder;
    }

    @SuppressWarnings("unchecked")
    public static Map convertPotentialGStrings(Map<Object, Object> args) {
        Map newArgs = new HashMap(args.size());
        for (Map.Entry<Object, Object> entry : args.entrySet()) {
            newArgs.put(unwrapGString(entry.getKey()), unwrapGString(entry.getValue()));
        }
        return newArgs;
    }

    private static Object unwrapGString(Object value) {
        if (value instanceof CharSequence) {
            return value.toString();
        }
        return value;
    }
}

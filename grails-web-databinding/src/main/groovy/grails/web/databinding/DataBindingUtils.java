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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;

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
                sourceToBind = createSecureDataBindingSource(dataBindingSource, include, null);
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
        return createSecureDataBindingSource(bindingSource, allowedParams, filter);
    }

    public static BindingResult secureBindObjectToInstance(Object object, Object source, List allowedParams, String filter, boolean nullMissing) {
        BindingResult bindingResult = null;
        GrailsApplication grailsApplication = Holders.findApplication();
        PersistentEntity entity = findPersistentEntity(grailsApplication, object);

        try {
            final DataBindingSource bindingSource = createDataBindingSource(grailsApplication, object.getClass(), source);
            final DataBindingSource secureBindingSource = createSecureDataBindingSource(bindingSource, allowedParams, filter);
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
            bindingResult.addError(new ObjectError(bindingResult.getObjectName(), e.getMessage()));
        }

        return processBindingResult(entity, object, bindingResult);
    }

    public static DataBindingSource createSecureDataBindingSource(DataBindingSource bindingSource, List allowedParams, String filter) {
        Map secureSource = new LinkedHashMap();
        for (Object allowedParam : allowedParams) {
            if (allowedParam instanceof CharSequence) {
                String propertyName = allowedParam.toString();
                String sourcePropertyName = filter == null ? propertyName : filter + "." + propertyName;
                copyAllowedProperty(secureSource, propertyName, bindingSource, sourcePropertyName);
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
        int separator = propertyName.lastIndexOf('.');
        if (separator == -1) {
            return "_" + propertyName;
        }
        return propertyName.substring(0, separator + 1) + "_" + propertyName.substring(separator + 1);
    }

    private static boolean copyAllowedProperty(Map secureSource, String targetPropertyName, Object source, String sourcePropertyName) {
        if (containsSourceProperty(source, sourcePropertyName)) {
            putNestedValue(secureSource, targetPropertyName, getSourcePropertyValue(source, sourcePropertyName));
            return true;
        }
        int separator = sourcePropertyName.indexOf('.');
        if (separator == -1) {
            return false;
        }
        String sourceRootPropertyName = sourcePropertyName.substring(0, separator);
        if (!containsSourceProperty(source, sourceRootPropertyName)) {
            return false;
        }
        Object nestedSource = getSourcePropertyValue(source, sourceRootPropertyName);
        String nestedSourcePropertyName = sourcePropertyName.substring(separator + 1);
        if (nestedSource instanceof Collection) {
            return copyAllowedCollectionProperty(secureSource, targetPropertyName, (Collection) nestedSource, nestedSourcePropertyName);
        }
        return copyAllowedProperty(secureSource, targetPropertyName, nestedSource, nestedSourcePropertyName);
    }

    private static boolean copyAllowedCollectionProperty(Map secureSource, String targetPropertyName, Collection collection, String sourcePropertyName) {
        int separator = targetPropertyName.indexOf('.');
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
            if (copyAllowedProperty(filteredItem, nestedTargetPropertyName, item, sourcePropertyName)) {
                copied = true;
            }
            index++;
        }
        return copied;
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
        int separator = propertyName.indexOf('.');
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
                if (propertyName.indexOf('*') == -1 && !bindingSourceContainsProperty(bindingSource, propertyName, filter)) {
                    setPropertyToNull(object, propertyName);
                }
            }
        }
    }

    private static boolean bindingSourceContainsProperty(DataBindingSource bindingSource, String propertyName, String filter) {
        String sourcePropertyName = filter == null ? propertyName : filter + "." + propertyName;
        return containsPropertyPath(bindingSource, sourcePropertyName) || containsPropertyPath(bindingSource, checkboxMarkerPropertyName(sourcePropertyName));
    }

    private static boolean containsPropertyPath(Object source, String propertyName) {
        if (containsSourceProperty(source, propertyName)) {
            return true;
        }
        int separator = propertyName.indexOf('.');
        if (separator == -1) {
            return false;
        }
        String rootPropertyName = propertyName.substring(0, separator);
        if (!containsSourceProperty(source, rootPropertyName)) {
            return false;
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

    private static void setPropertyToNull(Object object, String propertyName) {
        String[] propertyNames = propertyName.split("\\.");
        Object currentObject = object;
        for (int i = 0; i < propertyNames.length - 1 && currentObject != null; i++) {
            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(currentObject.getClass());
            currentObject = mc.getProperty(currentObject, propertyNames[i]);
        }
        if (currentObject != null) {
            MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(currentObject.getClass());
            mc.setProperty(currentObject, propertyNames[propertyNames.length - 1], null);
        }
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
            bindingResult.addError(new ObjectError(bindingResult.getObjectName(), e.getMessage()));
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

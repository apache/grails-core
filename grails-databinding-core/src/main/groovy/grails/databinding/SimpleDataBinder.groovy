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
package grails.databinding

import java.lang.annotation.Annotation
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.security.ProtectionDomain

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.slurpersupport.GPathResult
import org.codehaus.groovy.reflection.CachedMethod

import grails.databinding.converters.FormattedValueConverter
import grails.databinding.converters.ValueConverter
import grails.databinding.events.DataBindingListener
import grails.databinding.initializers.ValueInitializer
import org.grails.databinding.ClosureValueConverter
import org.grails.databinding.ClosureValueInitializer
import org.grails.databinding.IndexedPropertyReferenceDescriptor
import org.grails.databinding.converters.ConversionService
import org.grails.databinding.converters.FormattedDateValueConverter
import org.grails.databinding.converters.StructuredCalendarBindingEditor
import org.grails.databinding.converters.StructuredDateBindingEditor
import org.grails.databinding.converters.StructuredSqlDateBindingEditor
import org.grails.databinding.errors.SimpleBindingError
import org.grails.databinding.xml.GPathResultMap

/**
 * A data binder that will bind nested Maps to an object.
 *
 <pre>
 class Person {
     String firstName
     Address homeAddress
 }

 class Address {
     String city
     String state
 }

 def person = new Person()
 def binder = new SimpleDataBinder()
 binder.bind person, [firstName: 'Steven', homeAddress: [city: 'St. Louis', state: 'Missouri']]
 assert person.firstName == 'Steven'
 assert person.homeAddress.city == 'St. Louis'
 assert person.homeAddress.state == 'Missouri'

 </pre>
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 3.0
 */
@CompileStatic
class SimpleDataBinder implements DataBinder {

    protected Map<Class, StructuredBindingEditor> structuredEditors = new HashMap<Class, StructuredBindingEditor>()
    ConversionService conversionService
    protected Map<Class, List<ValueConverter>> conversionHelpers = [:].withDefault { c -> [] }
    protected Map<Class, FormattedValueConverter> formattedValueConversionHelpers = new HashMap<Class, FormattedValueConverter>()
    protected static final List<Class> BASIC_TYPES = [
        String,
        Boolean,
        Byte,
        Short,
        Integer,
        Long,
        Float,
        Double,
        Character
    ] as List<Class>

    static final INDEXED_PROPERTY_REGEX = /(.*)\[\s*([^\s]*)\s*\]\s*$/

    int autoGrowCollectionLimit = 256

    SimpleDataBinder() {
        registerStructuredEditor Date, new StructuredDateBindingEditor()
        registerStructuredEditor java.sql.Date, new StructuredSqlDateBindingEditor()
        registerStructuredEditor Calendar, new StructuredCalendarBindingEditor()

        registerFormattedValueConverter new FormattedDateValueConverter()
    }

    void registerStructuredEditor(Class clazz, StructuredBindingEditor editor) {
        structuredEditors[clazz] = editor
    }

    void registerConverter(ValueConverter converter) {
        conversionHelpers[converter.targetType] << converter
    }
    void registerFormattedValueConverter(FormattedValueConverter converter) {
        formattedValueConversionHelpers[converter.targetType] = converter
    }

    /**
     *
     * @param obj The object being bound to
     * @param source The data binding source
     * @see DataBindingSource
     */
    void bind(obj, DataBindingSource source) {
        bind obj, source, null, null, null, null
    }

    /**
     *
     * @param obj The object being bound to
     * @param source The data binding source
     * @param listener A listener which will be notified of data binding events triggered
     * by this binding
     * @see DataBindingSource
     * @see DataBindingListener
     */
    void bind(obj, DataBindingSource source, DataBindingListener listener) {
        bind obj, source, null, null, null, listener
    }

    /**
     *
     * @param obj The object being bound to
     * @param source The data binding source
     * @param whiteList A list of property names to be included during this
     * data binding.  All other properties represented in the binding source
     * will be ignored
     * @see DataBindingSource
     */
    void bind(obj, DataBindingSource source, List whiteList) {
        bind obj, source, null, whiteList, null, null
    }

    /**
     *
     * @param obj The object being bound to
     * @param source The data binding source
     * @param whiteList A list of property names to be included during this
     * data binding.  All other properties represented in the binding source
     * will be ignored
     * @param blackList A list of properties names to be excluded during
     * this data binding.
     * @see DataBindingSource
     */
    void bind(obj, DataBindingSource source, List whiteList, List blackList) {
        bind obj, source, null, whiteList, blackList, null
    }

    /**
     *
     * @param obj The object being bound to
     * @param gpath A GPathResult which represents the data being bound.
     * @see DataBindingSource
     */
    void bind(obj, GPathResult gpath) {
        bind obj, new SimpleMapDataBindingSource(new GPathResultMap(gpath))
    }

    /**
     *
     * @param obj The object being bound to
     * @param source The data binding source
     * @param filter Only properties beginning with filter will be included in the
     * data binding.  For example, if filter is &quot;person&quot; and the binding
     * source contains data for properties &quot;person.name&quot; and &quot;author.name&quot;
     * the value of &quot;person.name&quot; will be bound to obj.name.  The value of
     * &quot;author.name&quot; will be ignored.
     * @param whiteList A list of property names to be included during this
     * data binding.  All other properties represented in the binding source
     * will be ignored
     * @param blackList A list of properties names to be excluded during
     * this data binding.
     * @see DataBindingSource
     */
    void bind(obj, DataBindingSource source, String filter, List whiteList, List blackList) {
        bind obj, source, filter, whiteList, blackList, null
    }

    /**
     *
     * @param obj The object being bound to
     * @param source The data binding source
     * @param filter Only properties beginning with filter will be included in the
     * data binding.  For example, if filter is &quot;person&quot; and the binding
     * source contains data for properties &quot;person.name&quot; and &quot;author.name&quot;
     * the value of &quot;person.name&quot; will be bound to obj.name.  The value of
     * &quot;author.name&quot; will be ignored.
     * @param whiteList A list of property names to be included during this
     * data binding.  All other properties represented in the binding source
     * will be ignored
     * @param blackList A list of properties names to be excluded during
     * this data binding.
     * @param listener A listener which will be notified of data binding events triggered
     * by this binding
     * @see DataBindingSource
     * @see DataBindingListener
     */
    void bind(obj, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener) {
        doBind obj, source, filter, whiteList, blackList, listener, null
    }

    protected void doBind(obj, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener, errors) {

        def keys = source.getPropertyNames()
        for (String key in keys) {
            if (!filter || key.startsWith(filter + '.')) {
                String propName = key
                if (filter) {
                    propName = key[(1+filter.size())..-1]
                }
                def metaProperty = obj.metaClass.getMetaProperty propName

                if (metaProperty) { // normal property
                    if (isOkToBind(metaProperty, whiteList, blackList)) {
                        def val = source[key]
                        try {
                            def converter = getValueConverter(obj, metaProperty.name)
                            if(converter) {
                                bindProperty obj, source, metaProperty, converter.convert(source), listener, errors
                            } else {
                                processProperty obj, metaProperty, preprocessValue(val), source, listener, errors
                            }
                        } catch (Exception e) {
                            addBindingError(obj, propName, val, e, listener, errors)
                        }
                    }
                } else {
                    def descriptor = getIndexedPropertyReferenceDescriptor propName
                    if (descriptor) { // indexed property
                        metaProperty = obj.metaClass.getMetaProperty descriptor.propertyName
                        if (metaProperty && isOkToBind(metaProperty, whiteList, blackList)) {
                            def val = source.getPropertyValue key
                            processIndexedProperty obj, metaProperty, descriptor, val, source, listener, errors
                        }
                    } else if (propName.startsWith('_') && propName.length() > 1) { // boolean special handling
                        def restOfPropertyName = propName[1..-1]
                        if (!source.containsProperty(restOfPropertyName)) {
                            metaProperty = obj.metaClass.getMetaProperty restOfPropertyName
                            if (metaProperty && isOkToBind(metaProperty, whiteList, blackList)) {
                                if ((Boolean == metaProperty.type || Boolean.TYPE == metaProperty.type)) {
                                    bindProperty obj, source, metaProperty, false, listener, errors
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected boolean isOkToBind(String propName, List whiteList, List blackList) {
        'class' != propName && 'classLoader' != propName && 'protectionDomain' != propName && 'metaClass' != propName && 'metaPropertyValues' != propName && 'properties' != propName && !blackList?.contains(propName) && (!whiteList || whiteList.contains(propName) || whiteList.find { it -> it?.toString()?.startsWith(propName + '.')})
    }

    protected boolean isOkToBind(MetaProperty property, List whitelist, List blacklist) {
        isOkToBind(property.name, whitelist, blacklist) &&
                (property.type != null) &&
                !Modifier.isStatic(property.modifiers) &&
                !(ClassLoader.class.isAssignableFrom(property.type) || ProtectionDomain.class.isAssignableFrom(property.type) || MetaProperty.class.isAssignableFrom(property.type) || CachedMethod.class.isAssignableFrom(property.type))
    }

    protected IndexedPropertyReferenceDescriptor getIndexedPropertyReferenceDescriptor(propName) {
        IndexedPropertyReferenceDescriptor descriptor
        def matcher = propName =~ INDEXED_PROPERTY_REGEX
        if (matcher) {
            def indexedPropertyName = matcher.group(1)
            def index = matcher.group(2)
            if (index.size() > 2 && ((index.startsWith("'") && index.endsWith("'")) || (index.startsWith('"') && index.endsWith('"')))) {
                index = index[1..-2]
            }
            descriptor = new IndexedPropertyReferenceDescriptor(propertyName: indexedPropertyName, index: index)
        }
        descriptor
    }

    protected processProperty(obj, MetaProperty metaProperty, val, DataBindingSource source, DataBindingListener listener, errors) {
        def propName = metaProperty.name
        def propertyType = metaProperty.type
        if (structuredEditors.containsKey(propertyType) && ('struct' == val || 'date.struct' == val)) {
            def structuredEditor = structuredEditors[propertyType]
            val = structuredEditor.getPropertyValue obj, propName, source
        }
        bindProperty obj, source, metaProperty, val, listener, errors
    }

    protected SimpleMapDataBindingSource splitIndexedStruct(IndexedPropertyReferenceDescriptor indexedPropertyReferenceDescriptor, DataBindingSource source) {
        def propName = indexedPropertyReferenceDescriptor.propertyName
        Map structValues = new HashMap()
        String prefix = indexedPropertyReferenceDescriptor.toString()
        for (String propertyName: source.propertyNames) {
            if (propertyName.startsWith(prefix)) {
                String deIndexedPropertyName = propName
                String[] parts = propertyName.split('_')
                if (parts.length > 1) {
                    deIndexedPropertyName = deIndexedPropertyName + '_' + parts[1]
                }
                structValues.put(deIndexedPropertyName, source.getPropertyValue(propertyName))
            }
        }
        new SimpleMapDataBindingSource(structValues)
    }

    protected processIndexedProperty(obj, MetaProperty metaProperty, IndexedPropertyReferenceDescriptor indexedPropertyReferenceDescriptor,
        val, DataBindingSource source, DataBindingListener listener, errors) {

        def propName = indexedPropertyReferenceDescriptor.propertyName
        def propertyType = metaProperty.type
        Class genericType = getReferencedTypeForCollection(propName, obj)

        if (structuredEditors.containsKey(genericType) && ('struct' == val || 'date.struct' == val)) {
            def structuredEditor = structuredEditors[genericType]
            val = structuredEditor.getPropertyValue obj, propName, splitIndexedStruct(indexedPropertyReferenceDescriptor, source)
        }

        if (propertyType.isArray()) {
            def index = Integer.parseInt(indexedPropertyReferenceDescriptor.index)
            def array = initializeArray(obj, propName, propertyType.componentType, index)
            if (array != null) {
                addElementToArrayAt array, index, val
            }
        } else if (Collection.isAssignableFrom(propertyType)) {
            def index = Integer.parseInt(indexedPropertyReferenceDescriptor.index)
            Collection collectionInstance = initializeCollection obj, propName, propertyType
            def indexedInstance = null
            if(!(Set.isAssignableFrom(propertyType))) {
                indexedInstance = collectionInstance[index]
            }
            if (indexedInstance == null) {
                if (genericType) {
                    if (genericType.isAssignableFrom(val?.getClass())) {
                        addElementToCollectionAt obj, propName, collectionInstance, index, val
                    } else if (isBasicType(genericType)) {
                        addElementToCollectionAt obj, propName, collectionInstance, index, convert(genericType, val)
                    } else if (val instanceof Map){
                        indexedInstance = genericType.getDeclaredConstructor().newInstance()
                        bind indexedInstance, new SimpleMapDataBindingSource(val), listener
                        addElementToCollectionAt obj, propName, collectionInstance, index, indexedInstance
                    } else if (val instanceof DataBindingSource) {
                        indexedInstance = genericType.getDeclaredConstructor().newInstance()
                        bind indexedInstance, val, listener
                        addElementToCollectionAt obj, propName, collectionInstance, index, indexedInstance
                    } else if(genericType.isEnum() && val instanceof CharSequence) {
                        def enumValue = convertStringToEnum(genericType, val.toString())
                        addElementToCollectionAt obj, propName, collectionInstance, index, enumValue
                    } else {
                        addElementToCollectionAt obj, propName, collectionInstance, index, convert(genericType, val)
                    }
                } else {
                    addElementToCollectionAt obj, propName, collectionInstance, index, val
                }
            } else {
                if (val instanceof Map) {
                    bind indexedInstance, new SimpleMapDataBindingSource(val), listener
                } else if (val instanceof DataBindingSource) {
                    bind indexedInstance, val, listener
                } else if (val == null && indexedInstance != null) {
                    addElementToCollectionAt obj, propName, collectionInstance, index, null
                }
            }
        } else if (Map.isAssignableFrom(propertyType)) {
            Map mapInstance = initializeMap obj, propName
            if (mapInstance.size() < autoGrowCollectionLimit || mapInstance.containsKey(indexedPropertyReferenceDescriptor.index)) {
                def referencedType = getReferencedTypeForCollection propName, obj
                if (referencedType != null) {
                    if(val instanceof Map) {
                        mapInstance[indexedPropertyReferenceDescriptor.index] = referencedType.newInstance(val)
                    } else {
                        mapInstance[indexedPropertyReferenceDescriptor.index] = convert(referencedType, val)
                    }
                } else {
                    mapInstance[indexedPropertyReferenceDescriptor.index] = val
                }
            }
        }
    }


    @CompileStatic(TypeCheckingMode.SKIP)
    protected initializeArray(obj, String propertyName, Class arrayType, int index) {
        Object[] array = obj[propertyName]
        if (array == null && index < autoGrowCollectionLimit) {
            array = Array.newInstance(arrayType, index + 1)
            obj[propertyName] = array
        } else if (array != null && array.length <= index && index < autoGrowCollectionLimit) {
            def newArray = Array.newInstance(arrayType, index + 1)
            System.arraycopy(array, 0, newArray, 0, array.length)
            array = newArray
            obj[propertyName] = newArray
        }
        array
    }

    protected boolean isBasicType(Class c) {
        BASIC_TYPES.contains(c) || c.isPrimitive()
    }


    protected Class<?> getReferencedTypeForCollectionInClass(String propertyName, Class clazz) {
        Class referencedType
        def field = getField(clazz, propertyName)
        if(field) {
            def genericType = field.genericType
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType)genericType
                Class rawType = pt.getRawType()
                if(Map.isAssignableFrom(rawType)) {
                    referencedType = pt.getActualTypeArguments()[1]
                } else {
                    referencedType = pt.getActualTypeArguments()[0]
                }
            }
        }
        referencedType
    }

    protected Class<?> getReferencedTypeForCollection(String propertyName, Object obj) {
        getReferencedTypeForCollectionInClass propertyName, obj.getClass()
    }

    protected boolean isOkToAddElementAt(Collection collection, int index) {
        boolean isOk
        if (collection instanceof Set) {
            isOk = collection.size() < autoGrowCollectionLimit
        } else {
            isOk = (index < autoGrowCollectionLimit || index < collection.size())
        }
        return isOk
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected addElementToCollectionAt(obj, String propertyName, Collection collection, index, val) {
        if (isOkToAddElementAt(collection, index)) {
            if (collection instanceof Set) {
                collection.add val
            } else {
                collection[index] = val
            }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected addElementToArrayAt(array, index, val) {
        if (array.length > index) {
            array[index] = convert(array.class.componentType, val)
        }
    }

    protected Map initializeMap(obj, String propertyName) {
        if (obj[propertyName] == null) {
            obj[propertyName] = [:]
        }
        return (Map)obj[propertyName]
    }

    protected Collection initializeCollection(obj, String propertyName, Class type, boolean reuseExistingCollectionIfExists = true) {
        def val = null
        if(reuseExistingCollectionIfExists) {
            val = obj[propertyName]
        }
        if (val == null) {
            val = getDefaultCollectionInstanceForType(type)
            obj[propertyName] = val
        }
        return (Collection)val
    }

    protected getDefaultCollectionInstanceForType(Class type) {
        def val
        if (List.isAssignableFrom(type)) {
            val = []
        } else if (SortedSet.isAssignableFrom(type)) {
            val = new TreeSet()
        } else if (LinkedHashSet.isAssignableFrom(type)) {
            val = new LinkedHashSet()
        } else if (Collection.isAssignableFrom(type)) {
            val = new HashSet()
        }
        val
    }

    /**
     * Get a ValueConverter for field
     *
     * @param field The field to retrieve a converter for
     * @param formattingValue The format that the converter will use
     * @return a ValueConverter for field which uses formattingValue for its format
     * @see BindingFormat
     */
    protected ValueConverter getFormattedConverter(Field field, String formattingValue) {
        ValueConverter converter
        def formattedConverter = formattedValueConversionHelpers[field.type]
        if (formattedConverter) {
            converter = { SimpleMapDataBindingSource source ->
                def value = preprocessValue(source.getPropertyValue(field.name))
                def convertedValue = null
                if(value != null) {
                    convertedValue = formattedConverter.convert (value, formattingValue)
                }
                convertedValue
            } as ValueConverter
        }
        converter
    }

    protected Field getField(Class clazz, String fieldName) {
        Field field = null
        try {
            field = clazz.getDeclaredField(fieldName)
        } catch (NoSuchFieldException ignored) {
            def superClass = clazz.getSuperclass()
            if(superClass != Object) {
                field = getField(superClass, fieldName)
            }
        }
        return field
    }

    protected ValueConverter getValueConverterForField(obj, String propName) {
        ValueConverter converter
        try {
            def field = getField(obj.getClass(), propName)
            if (field) {
                def annotation = field.getAnnotation(BindUsing)
                if (annotation) {
                    def valueClass = getValueOfBindUsing(annotation)
                    if (Closure.isAssignableFrom(valueClass)) {
                        Closure closure = (Closure)valueClass.newInstance(null, null)
                        converter = new ClosureValueConverter(converterClosure: closure.curry(obj), targetType: field.type)
                    }
                } else {
                    annotation = field.getAnnotation(BindingFormat)
                    if (annotation) {
                        converter = getFormattedConverter field, getFormatString(annotation)
                    }
                }
            }
            return converter
        } catch (Exception ignored) {
        }
    }

    /**
     * @param annotation An instance of grails.databinding.BindingUsing or org.grails.databinding.BindingUsing
     * @return the value Class of the annotation
     */
    protected Class getValueOfBindUsing(Annotation annotation) {
        assert annotation instanceof BindUsing
        if (annotation instanceof BindUsing) {
            return ((BindUsing) annotation).value()
        }
    }

    /**
     * @param annotation An instance of grails.databinding.BindingFormat or org.grails.databinding.BindingFormat
     * @return the value String of the annotation
     */
    protected String getFormatString(Annotation annotation) {
        assert annotation instanceof BindingFormat
        if (annotation instanceof BindingFormat) {
            return ((BindingFormat) annotation).value()
        }
    }

    protected ValueConverter getValueConverterForClass(obj, String propName) {
        ValueConverter converter
        def objClass = obj.getClass()
        def annotation = objClass.getAnnotation(BindUsing)
        if (annotation) {
            def valueClass = getValueOfBindUsing(annotation)
            if (BindingHelper.isAssignableFrom(valueClass)) {
                BindingHelper dataConverter = (BindingHelper) valueClass.getDeclaredConstructor().newInstance()
                converter = new ClosureValueConverter(converterClosure: { DataBindingSource it -> dataConverter.getPropertyValue(obj, propName, it) })
            }
        }
        converter
    }

    protected ValueConverter getValueConverter(obj, String propName) {
        def converter = getValueConverterForField obj, propName
        if (!converter) {
            converter = getValueConverterForClass obj, propName
        }
        converter
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected convertStringToEnum(Class<? extends Enum> enumClass, String value) {
        try {
            enumClass.valueOf(value)
        } catch (IllegalArgumentException iae) {}
    }

    protected preprocessValue(propertyValue) {
        propertyValue
    }

    protected setPropertyValue(obj, DataBindingSource source, MetaProperty metaProperty, propertyValue, DataBindingListener listener) {
        def convertCollectionElements = false
        if(propertyValue instanceof Collection) {
            def referencedType = getReferencedTypeForCollection(metaProperty.name, obj)
            if(referencedType) {
                def nonAssignableValue = propertyValue.find { it != null && !(referencedType.isAssignableFrom(it.getClass()))}
                if(nonAssignableValue != null) {
                    convertCollectionElements = true
                }
            }
        }

        setPropertyValue obj, source, metaProperty, propertyValue, listener, convertCollectionElements
    }

    protected setPropertyValue(obj, DataBindingSource source, MetaProperty metaProperty, propertyValue, DataBindingListener listener, boolean convertCollectionElements) {
        def propName = metaProperty.name
        def propertyType
        def propertyGetter
        if (metaProperty instanceof MetaBeanProperty) {
            def mbp = (MetaBeanProperty)metaProperty
            propertyType = mbp.getter?.returnType ?: mbp.field?.type
            if(propertyType && (propertyType.interface || Modifier.isAbstract(propertyType.modifiers))) {
                propertyType = mbp.field?.type
            }
            propertyGetter = mbp.getter
        }
        if (propertyType == null || propertyType == Object) {
            propertyType = metaProperty.type
            if(propertyType == null || propertyType == Object) {
                propertyType = getField(obj.getClass(), propName)?.type ?: Object
            }
        }
        if (propertyValue == null || propertyType == Object || propertyType.isAssignableFrom(propertyValue.getClass())) {
            if (convertCollectionElements && ((!(propertyValue instanceof Range) && propertyValue instanceof Collection && Collection.isAssignableFrom(propertyType) && propertyGetter))) {
                addElementsToCollection(obj, propName, propertyValue, true)
            } else {
                obj[propName] = propertyValue
            }
        } else if (propertyValue instanceof List &&
                  Set.isAssignableFrom(propertyType) &&
                  !SortedSet.isAssignableFrom(propertyType)) {
            addElementsToCollection(obj, propName, propertyValue, true)
        } else {
            if (propertyValue instanceof Map) {
                if (Collection.isAssignableFrom(propertyType) &&
                   propertyValue.size() == 1 &&
                   ((Map)propertyValue)[propertyValue.keySet()[0]] instanceof List) {
                    def key = propertyValue.keySet()[0]
                    List list = (List)((Map)propertyValue)[key]
                    addElementsToCollection(obj, propName, list)
                } else {
                    if (obj[propName] == null) {
                        initializeProperty(obj, propName, propertyType, source)
                    }
                    bind obj[propName], new SimpleMapDataBindingSource(propertyValue), listener
                }
            } else if (propertyValue instanceof DataBindingSource) {
                if (Collection.isAssignableFrom(propertyType) &&
                   propertyValue.size() == 1 &&
                   ((Map)propertyValue)[propertyValue.getPropertyNames()[0]] instanceof List) {
                    def key = propertyValue.getPropertyNames()[0]
                    List list = (List)((Map)propertyValue)[key]
                    addElementsToCollection(obj, propName, list)
                } else {
                    if (obj[propName] == null) {
                        initializeProperty(obj, propName, propertyType, source)
                    }
                    bind obj[propName], propertyValue, listener
                }
            } else if(Collection.isAssignableFrom(propertyType) && propertyValue instanceof String) {
                addElementToCollection obj, propName, propertyType, propertyValue, true
            } else if(Collection.isAssignableFrom(propertyType) && propertyValue instanceof Number) {
                addElementToCollection obj, propName, propertyType, propertyValue, true
            } else if(Collection.isAssignableFrom(propertyType) && propertyValue.getClass().isArray()) {
                addElementsToCollection obj, propName, propertyValue as Collection, true
            } else {
                obj[propName] = convert(propertyType, propertyValue)
            }
        }
    }
    protected addElementToCollection(obj, String propName, Class propertyType, propertyValue, boolean clearCollection) {
        boolean isSet = false
        def coll = initializeCollection obj, propName, propertyType
        if (coll != null) {
            if (clearCollection) {
                coll.clear()
            }
            def referencedType = getReferencedTypeForCollection propName, obj
            if (referencedType != null) {
                if (propertyValue == null || referencedType.isAssignableFrom(propertyValue.getClass())) {
                    coll << propertyValue
                    isSet = true
                } else {
                    coll << convert(referencedType, propertyValue)
                    isSet = true
                }
            }
        }
        isSet
    }

    protected bindProperty(obj, DataBindingSource source, MetaProperty metaProperty, propertyValue, DataBindingListener listener, errors) {
        def propName = metaProperty.name
        if (listener == null || listener.beforeBinding(obj, propName, propertyValue, errors) != false) {
            try {
                setPropertyValue obj, source, metaProperty, propertyValue, listener
            } catch (Exception e) {
                addBindingError(obj, propName, propertyValue, e, listener, errors)
            }
        } else if (listener != null && propertyValue instanceof Map && obj[propName] != null) {
            bind obj[propName], new SimpleMapDataBindingSource(propertyValue)
        }
        listener?.afterBinding obj, propName, errors
    }

    protected addBindingError(obj, String propName, propertyValue, Exception e, DataBindingListener listener, errors) {
        if (listener) {
            def error = new SimpleBindingError(obj, propName, propertyValue, e.cause ?: e)
            listener.bindingError error, errors
        }
    }

    private void addElementsToCollection(obj, String collectionPropertyName, Collection collection, boolean removeExistingElements = false) {
        Class propertyType = obj.metaClass.getMetaProperty(collectionPropertyName).type
        def referencedType = getReferencedTypeForCollection(collectionPropertyName, obj)
        def coll = initializeCollection(obj, collectionPropertyName, propertyType, !removeExistingElements)
        if (removeExistingElements == true) {
            coll.clear()
        }
        for(element in collection) {
            if (element == null || referencedType == null || referencedType.isAssignableFrom(element.getClass())) {
                coll << element
            } else {
                coll << convert(referencedType, element)
            }
        }
        obj[collectionPropertyName] = coll
    }

    protected initializeProperty(obj, String propName, Class propertyType, DataBindingSource source) {
        def initializer = getPropertyInitializer(obj,propName)
        if(initializer){
            obj[propName] = initializer.initialize()
        }
        else{
            obj[propName] = propertyType.getDeclaredConstructor().newInstance()
        }
    }

    protected ValueInitializer getPropertyInitializer(obj, String propName){
        def initializer = getValueInitializerForField obj, propName
        initializer
    }

    protected ValueInitializer getValueInitializerForField(obj, String propName) {
        ValueInitializer initializer
        try {
            def field = getField(obj.getClass(), propName)
            if (field) {
                def annotation = field.getAnnotation(BindInitializer)
                if (annotation) {
                    def valueClass = getValueOfBindInitializer(annotation)
                    if (Closure.isAssignableFrom(valueClass)) {
                        Closure closure = (Closure)valueClass.newInstance(null, null)
                        initializer = new ClosureValueInitializer(initializerClosure: closure.curry(obj), targetType: field.type)
                    }
                }
            }
        } catch (Exception e) {
        }
        initializer
    }
    /**
     * @param annotation An instance of grails.databinding.BindInitializer
     * @return the value Class of the annotation
     */
    protected Class getValueOfBindInitializer(Annotation annotation) {
        assert annotation instanceof BindInitializer
        def value
        if(annotation instanceof BindInitializer) {
            value = ((BindInitializer)annotation).value()
        }
        value
    }

    protected convert(Class typeToConvertTo, value) {
        if (value == null || typeToConvertTo.isAssignableFrom(value?.getClass())) {
            return value
        }
        if (conversionHelpers.containsKey(typeToConvertTo)) {
            ValueConverter converter = getConverter(typeToConvertTo, value)
            if (converter) {
                return converter.convert(value)
            }
        }
        if (conversionService?.canConvert(value.getClass(), typeToConvertTo)) {
            return conversionService.convert(value, typeToConvertTo)
        }
        if (Collection.isAssignableFrom(typeToConvertTo) && value instanceof String[]) {
            if (Set == typeToConvertTo) {
                return value as Set
            }
            if (List == typeToConvertTo) {
                return value as List
            }
        } else if (typeToConvertTo.isPrimitive() || typeToConvertTo.isArray()) {
            return value
        } else if (value instanceof Map) {
            def obj = typeToConvertTo.getDeclaredConstructor().newInstance()
            bind obj, new SimpleMapDataBindingSource(value)
            return obj
        } else if (Enum.isAssignableFrom(typeToConvertTo) && value instanceof String) {
            return convertStringToEnum((Class<? extends Enum>) typeToConvertTo, value)
        }
        typeToConvertTo.newInstance value
    }

    protected ValueConverter getConverter(Class typeToConvertTo, value) {
        def converters = conversionHelpers.get(typeToConvertTo)
        converters?.find { ValueConverter c -> c.canConvert(value) }
    }
}

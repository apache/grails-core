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
package grails.web.databinding

import java.lang.annotation.Annotation
import java.lang.reflect.Array

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.xml.slurpersupport.GPathResult
import org.codehaus.groovy.runtime.InvokerHelper
import org.codehaus.groovy.runtime.MetaClassHelper
import org.codehaus.groovy.runtime.metaclass.ThreadManagedMetaBeanProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError

import grails.core.GrailsApplication
import grails.databinding.BindingFormat
import grails.databinding.DataBindingSource
import grails.databinding.FrameworkPropertyNames
import grails.databinding.SimpleDataBinder
import grails.databinding.SimpleMapDataBindingSource
import grails.databinding.TypedStructuredBindingEditor
import grails.databinding.converters.FormattedValueConverter
import grails.databinding.converters.ValueConverter
import grails.databinding.events.DataBindingListener
import grails.util.GrailsClassUtils
import grails.util.GrailsMessageSourceUtils
import grails.util.GrailsMetaClassUtils
import grails.util.GrailsNameUtils
import grails.validation.DeferredBindingActions
import grails.validation.ValidationErrors
import org.grails.core.artefact.AnnotationDomainClassArtefactHandler
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.core.exceptions.GrailsConfigurationException
import org.grails.databinding.IndexedPropertyReferenceDescriptor
import org.grails.databinding.xml.GPathResultMap
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.datastore.mapping.model.types.Simple
import org.grails.web.databinding.DataBindingEventMulticastListener
import org.grails.web.databinding.DefaultASTDatabindingHelper
import org.grails.web.databinding.GrailsWebDataBindingListener
import org.grails.web.databinding.SpringConversionServiceAdapter
import org.grails.web.databinding.converters.ByteArrayMultipartFileValueConverter
import org.grails.web.servlet.mvc.GrailsWebRequest

import static grails.web.databinding.DataBindingUtils.addUnbindablePropertyNames
import static grails.web.databinding.DataBindingUtils.getBindingIncludeList

@CompileStatic
class GrailsWebDataBinder extends SimpleDataBinder {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsWebDataBinder)
    private static final int MAX_WARNED_BINDING_SHAPES = 1024
    private static final Set<String> WARNED_BINDING_SHAPES = new LinkedHashSet<>()
    protected GrailsApplication grailsApplication
    protected MessageSource messageSource
    boolean trimStrings = true
    boolean convertEmptyStringsToNull = true
    protected List<DataBindingListener> listeners = []

    private volatile ObservationRegistry observationRegistry
    private final ThreadLocal<List> bindingIncludeList = new ThreadLocal<List>()
    private final ThreadLocal<List> nestedBindingIncludeList = new ThreadLocal<List>()
    private final ThreadLocal<Class<?>> bindingTargetType = new ThreadLocal<Class<?>>()

    GrailsWebDataBinder(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
        this.conversionService = new SpringConversionServiceAdapter()
        registerConverter(new ByteArrayMultipartFileValueConverter())
    }

    @Override
    void bind(obj, DataBindingSource source) {
        List nestedIncludeList = nestedBindingIncludeList.get()
        bind(obj, source, null, nestedIncludeList != null ? nestedIncludeList : getBindingIncludeList(obj), null, null)
    }

    @Override
    void bind(obj, DataBindingSource source, DataBindingListener listener) {
        List nestedIncludeList = nestedBindingIncludeList.get()
        bind(obj, source, null, nestedIncludeList != null ? nestedIncludeList : getBindingIncludeList(obj), null, listener)
    }

    @Override
    void bind(obj, DataBindingSource source, List whiteList) {
        bind(obj, source, null, whiteList, null, null)
    }

    @Override
    void bind(obj, DataBindingSource source, List whiteList, List blackList) {
        bind(obj, source, null, whiteList, blackList, null)
    }

    @Override
    void bind(obj, DataBindingSource source, String filter, List whiteList, List blackList) {
        bind(obj, source, filter, whiteList, blackList, null)
    }

    @Override
    void bind(object, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener) {
        bindInternal(object, source, filter, normalizeBindingIncludeList(object, whiteList), blackList, listener)
    }

    private void bindInternal(object, DataBindingSource source, String filter, List whiteList, List blackList,
            DataBindingListener listener) {
        def bindingResult = new BeanPropertyBindingResult(object, object.getClass().name)
        doBind(object, source, filter, whiteList, blackList, listener, bindingResult)
    }

    private List normalizeBindingIncludeList(object, List includeList) {
        if (includeList == null) {
            return getBindingIncludeList(object)
        }
        if (includeList.isEmpty() && !isBindAllIncludeList(includeList)) {
            return [DefaultASTDatabindingHelper.NO_BINDABLE_PROPERTIES]
        }
        includeList
    }

    static List bindAllBindingIncludeList() {
        SimpleDataBinder.getBindAllBindingIncludeList()
    }

    static boolean isBindAllIncludeList(List includeList) {
        SimpleDataBinder.isBindAllBindingIncludeList(includeList)
    }

    @Override
    protected void doBind(object, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener, errors) {
        def observationRegistry = resolveObservationRegistry()
        if (observationRegistry == null || observationRegistry.noop) {
            doBindInternal(object, source, filter, whiteList, blackList, listener, errors)
            return
        }
        def target = object != null ? object.getClass().simpleName : 'unknown'
        def observation = Observation.createNotStarted('grails.databinding', observationRegistry)
                .contextualName('grails.databinding ' + target)
                .lowCardinalityKeyValue('grails.databinding.target', target)
                .start()
        def observationScope = observation.openScope()
        try {
            doBindInternal(object, source, filter, whiteList, blackList, listener, errors)
        }
        catch (Throwable t) {
            observation.error(t)
            throw t
        }
        finally {
            observationScope.close()
            observation.stop()
        }
    }

    private ObservationRegistry resolveObservationRegistry() {
        def registry = this.observationRegistry
        if (registry == null) {
            def ctx = grailsApplication?.mainContext
            if (ctx == null) {
                // context not ready (e.g. binding during bootstrap) — return NOOP without caching
                // so a later call re-resolves the real registry
                return ObservationRegistry.NOOP
            }
            registry = ctx.getBeanProvider(ObservationRegistry).getIfAvailable({ -> ObservationRegistry.NOOP })
            this.observationRegistry = registry
        }
        return registry
    }

    protected void doBindInternal(object, DataBindingSource source, String filter, List whiteList, List blackList, DataBindingListener listener, errors) {
        BeanPropertyBindingResult bindingResult = (BeanPropertyBindingResult) errors
        def errorHandlingListener = new GrailsWebDataBindingListener(messageSource)

        List<DataBindingListener> allListeners = []
        allListeners << errorHandlingListener
        if (listener instanceof DataBindingEventMulticastListener) {
            allListeners.addAll(listener.listeners.findAll { DataBindingListener nestedListener ->
                !(nestedListener instanceof GrailsWebDataBindingListener) && !listeners.contains(nestedListener)
            })
        } else if (listener != null) {
            allListeners << listener
        }
        allListeners.addAll(listeners.findAll { DataBindingListener l -> l.supports(object.getClass()) })

        def listenerWrapper = new DataBindingEventMulticastListener(allListeners)

        boolean bind = listenerWrapper.beforeBinding(object, bindingResult)

        if (bind) {
            List previousIncludeList = bindingIncludeList.get()
            Class<?> previousTargetType = bindingTargetType.get()
            bindingIncludeList.set(whiteList)
            bindingTargetType.set(object.getClass())
            try {
                super.doBind(object, source, filter, whiteList, addUnbindablePropertyNames(object, blackList), listenerWrapper, bindingResult)
            } finally {
                if (previousIncludeList != null) {
                    bindingIncludeList.set(previousIncludeList)
                } else {
                    bindingIncludeList.remove()
                }
                if (previousTargetType != null) {
                    bindingTargetType.set(previousTargetType)
                } else {
                    bindingTargetType.remove()
                }
            }
        }

        listenerWrapper.afterBinding(object, bindingResult)

        populateErrors(object, bindingResult)
    }

    @Override
    protected boolean isOkToBind(MetaProperty property, List whiteList, List blackList) {
        boolean allowed = super.isOkToBind(property, whiteList, blackList)
        if (!allowed && DataBindingUtils.isGeneratedBindingIncludeList(whiteList) &&
                super.isOkToBind(property, null, blackList) &&
                !FrameworkPropertyNames.FRAMEWORK_MANAGED_PROPERTIES.contains(property.name)) {
            warnAboutIgnoredBindingProperty(bindingTargetType.get(), property.name)
        }
        allowed
    }

    protected void warnAboutIgnoredBindingProperty(Class<?> targetType, String propertyName) {
        if (!isBindingWarningEnabled() || targetType == null) {
            return
        }
        String warningKey = targetType.name + '#' + propertyName
        if (markBindingShapeWarned(warningKey)) {
            logBindingWarning(ignoredBindingPropertyMessage(targetType, propertyName))
        }
    }

    protected boolean isBindingWarningEnabled() {
        LOG.warnEnabled
    }

    protected void logBindingWarning(String message) {
        LOG.warn(message)
    }

    static String ignoredBindingPropertyMessage(Class<?> targetType, String propertyName) {
        "Ignored request parameter [${propertyName}] while binding to [${targetType.name}]: it is not in the binding allowlist. " +
                'Secure data binding is enabled and binds only allowlisted properties to prevent mass assignment (CWE-915). ' +
                "To bind [${propertyName}], declare it bindable - `static constraints = { ${propertyName} bindable: true }` on the class, " +
                "add it to the binding `include:` list, or annotate the controller action parameter with `@BindAllowed(['${propertyName}'])`. " +
                'To restore compatibility binding for the whole application, remove `grails.databinding.denyByDefault` or set it to `false`.'
    }

    private static boolean markBindingShapeWarned(String warningKey) {
        synchronized (WARNED_BINDING_SHAPES) {
            if (WARNED_BINDING_SHAPES.contains(warningKey) || WARNED_BINDING_SHAPES.size() >= MAX_WARNED_BINDING_SHAPES) {
                return false
            }
            WARNED_BINDING_SHAPES.add(warningKey)
        }
    }

    @Override
    void bind(obj, GPathResult gpath) {
        bind(obj, new SimpleMapDataBindingSource(new GPathResultMap(gpath)), getBindingIncludeList(obj))
    }

    protected populateErrors(obj, BindingResult bindingResult) {
        PersistentEntity domain = getPersistentEntity(obj.getClass())

        if (domain != null && bindingResult != null) {
            def newResult = new ValidationErrors(obj)
            for (Object error : bindingResult.getAllErrors()) {
                if (error instanceof FieldError) {
                    def fieldError = (FieldError) error
                    final boolean isBlank = ''.equals(fieldError.getRejectedValue())
                    if (!isBlank) {
                        newResult.addError(fieldError)
                    }
                    else {
                        PersistentProperty prop = domain.getPropertyByName(fieldError.getField())
                        if (prop != null) {
                            final boolean isOptional = prop.isNullable()
                            if (!isOptional) {
                                newResult.addError(fieldError)
                            }
                        }
                        else {
                            newResult.addError(fieldError)
                        }
                    }
                }
                else {
                    newResult.addError((ObjectError) error)
                }
            }
            bindingResult = newResult
        }
        def mc = GroovySystem.getMetaClassRegistry().getMetaClass(obj.getClass())
        if (mc.hasProperty(obj, 'errors') != null && bindingResult != null) {
            def errors = new ValidationErrors(obj)
            errors.addAllErrors(bindingResult)
            mc.setProperty(obj, 'errors', errors)
        }
    }

    @Override
    protected Class<?> getReferencedTypeForCollection(String name, Object target) {
        def referencedType = super.getReferencedTypeForCollection(name, target)
        if (referencedType == null) {
            PersistentEntity dc = getPersistentEntity(target.getClass())

            if (dc != null) {
                def domainProperty = dc.getPropertyByName(name)
                if (domainProperty != null) {
                    if (domainProperty instanceof Association) {
                        Association association = ((Association) domainProperty)
                        PersistentEntity entity = association.getAssociatedEntity()
                        if (entity != null) {
                            referencedType = entity.getJavaClass()
                        } else if (association.isBasic()) {
                            referencedType = ((Basic) association).getComponentType()
                        }
                    } else if (domainProperty instanceof Simple) {
                        referencedType = domainProperty.getType()
                    }
                }
            }
        }
        referencedType
    }

    @Override
    protected initializeProperty(obj, String propName, Class propertyType, DataBindingSource source) {
        def isInitialized = false
        if (source.dataSourceAware) {
            def isDomainClass = isDomainClass(propertyType)
            if (isDomainClass && source.containsProperty(propName)) {
                def val = source.getPropertyValue(propName)
                def idValue = getIdentifierValueFrom(val)
                if (idValue != null) {
                    def persistentInstance = getPersistentInstance(propertyType, idValue)
                    if (persistentInstance != null) {
                        obj[propName] = persistentInstance
                        isInitialized = true
                    }
                }
            }
        }
        if (!isInitialized) {
            super.initializeProperty(obj, propName,  propertyType, source)
        }
    }

    protected getPersistentInstance(Class<?> type, id) {
        try {
            InvokerHelper.invokeStaticMethod(type, 'get', id)
        } catch (Exception ignored) {}
    }

    /**
     * @param obj any object
     * @param propName the name of a property on obj
     * @return the Class of the domain class referenced by propName, null if propName does not reference a domain class
     */
    protected Class getDomainClassType(obj, String propName) {
        def domainClassType
        def objClass = obj.getClass()
        def propertyType = GrailsClassUtils.getPropertyType(objClass, propName)
        if (propertyType && isDomainClass(propertyType)) {
            domainClassType = propertyType
        }
        domainClassType
    }

    protected boolean isDomainClass(final Class<?> clazz) {
        return DomainClassArtefactHandler.isDomainClass(clazz) || AnnotationDomainClassArtefactHandler.isJPADomainClass(clazz)
    }

    protected getIdentifierValueFrom(source) {
        def idValue = null
        if (source instanceof DataBindingSource && ((DataBindingSource) source).hasIdentifier()) {
            idValue = source.getIdentifierValue()
        } else if (source instanceof CharSequence) {
            idValue = source
        } else if (source instanceof Map && ((Map) source).containsKey('id')) {
            idValue = source['id']
        } else if (source instanceof Number) {
            idValue = source.toString()
        }
        if (idValue instanceof GString) {
            idValue = idValue.toString()
        }
        idValue
    }

    @Override
    protected processProperty(obj, MetaProperty metaProperty, val, DataBindingSource source, DataBindingListener listener, errors) {
        boolean needsBinding = true
        List nestedIncludeList = getNestedBindingIncludeList(metaProperty.name)

        if (source.dataSourceAware) {
            def propName = metaProperty.name
            def propertyType = getDomainClassType(obj, metaProperty.name)
            if (propertyType && isDomainClass(propertyType)) {
                def idValue = getIdentifierValueFrom(val)
                if (idValue != 'null' && idValue != null && idValue != '') {
                    def persistedInstance = getPersistentInstance(propertyType, idValue)
                    if (persistedInstance != null) {
                        needsBinding = false
                        bindProperty(obj, source, metaProperty, persistedInstance, listener, errors)
                        if (persistedInstance != null) {
                            if (val instanceof Map) {
                                bindNested(persistedInstance, new SimpleMapDataBindingSource(val), nestedIncludeList, listener)
                            } else if (val instanceof DataBindingSource) {
                                bindNested(persistedInstance, val, nestedIncludeList, listener)
                            }
                        }
                    }
                } else {
                    boolean shouldBindNull = false
                    if (val instanceof DataBindingSource) {
                        // bind null if this binding source does contain an identifier
                        shouldBindNull = ((DataBindingSource) val).hasIdentifier()
                    } else if (val instanceof Map) {
                        // bind null if this Map does contain an id
                        shouldBindNull = ((Map) val).containsKey('id')
                    } else if (idValue instanceof CharSequence) {
                        // bind null if idValue is a CharSequence because it would have
                        // to be 'null' or '' in order for control to be in this else block
                        shouldBindNull = true
                    }
                    if (shouldBindNull) {
                        needsBinding = false
                        bindProperty(obj, source, metaProperty, null, listener, errors)
                    }
                }
            } else if (metaProperty.type.isArray() && val instanceof Collection &&
                    !isBasicType(metaProperty.type.componentType) && ((Collection) val).any {
                        it instanceof Map || it instanceof DataBindingSource
                    }) {
                needsBinding = false
                Class<?> componentType = metaProperty.type.componentType
                List boundItems = []
                ((Collection) val).each { item ->
                    if (item == null || componentType.isAssignableFrom(item.getClass())) {
                        boundItems << item
                    } else if (item instanceof Map || item instanceof DataBindingSource) {
                        DataBindingSource itemBindingSource = item instanceof DataBindingSource ?
                                (DataBindingSource) item : new SimpleMapDataBindingSource((Map) item)
                        def instance
                        if (isDomainClass(componentType)) {
                            def idValue = getIdentifierValueFrom(item)
                            if (idValue != null && idValue != '' && idValue != 'null') {
                                instance = getPersistentInstance(componentType, idValue)
                            }
                        }
                        if (instance == null) {
                            instance = instantiateAndBindNestedOrUseMapConstructor(
                                    componentType, item, itemBindingSource, nestedIncludeList, listener)
                        } else {
                            bindNested(instance, itemBindingSource, nestedIncludeList, listener)
                        }
                        if (instance != null) {
                            boundItems << instance
                        }
                    } else {
                        boundItems << convert(componentType, item)
                    }
                }
                def array = Array.newInstance(componentType, boundItems.size())
                boundItems.eachWithIndex { item, int index ->
                    Array.set(array, index, item)
                }
                bindProperty(obj, source, metaProperty, array, listener, errors)
            } else if (Collection.isAssignableFrom(metaProperty.type)) {
                def referencedType = getReferencedTypeForCollection(propName, obj)
                if (referencedType) {
                    List listValue
                    if (val instanceof List) {
                        listValue = (List) val
                    } else if (val instanceof GPathResultMap && ((GPathResultMap) val).size() == 1) {
                        def mapValue = (GPathResultMap) val
                        def valueInMap = mapValue[mapValue.keySet()[0]]
                        if (valueInMap instanceof List) {
                            listValue = (List) valueInMap
                        } else {
                            listValue = [valueInMap]
                        }
                    }
                    if (listValue != null) {
                        needsBinding = false
                        def coll = initializeCollection(obj, metaProperty.name, metaProperty.type)
                        if (coll instanceof Collection) {
                            coll.clear()
                        }
                        def itemsWhichNeedBinding = []
                        listValue.each { item ->
                            def persistentInstance
                            if (isDomainClass(referencedType)) {
                                if (item instanceof Map || item instanceof DataBindingSource) {
                                    def idValue = getIdentifierValueFrom(item)
                                    if (idValue != null) {
                                        persistentInstance = getPersistentInstance(referencedType, idValue)
                                        if (persistentInstance != null) {
                                            DataBindingSource newBindingSource
                                            if (item instanceof DataBindingSource) {
                                                newBindingSource = (DataBindingSource) item
                                            } else {
                                                newBindingSource = new SimpleMapDataBindingSource((Map) item)
                                            }
                                            bindNested(persistentInstance, newBindingSource, nestedIncludeList, listener)
                                            itemsWhichNeedBinding << persistentInstance
                                        }
                                    }
                                }
                            }
                            if (persistentInstance == null) {
                                if (item == null || referencedType.isAssignableFrom(item.getClass())) {
                                    // Already of the element type, so there is nothing to instantiate
                                    // and nothing to bind into. A raw collection always lands here:
                                    // Basic#componentType falls back to Object.class when a property
                                    // carries no generic signature, and Object is assignable from
                                    // everything. The array branch above and the Map branch below ask
                                    // the same question before instantiating; only this one did not,
                                    // so a map element was replaced by an empty Object and its
                                    // contents were dropped.
                                    itemsWhichNeedBinding << item
                                } else if (item instanceof Map || item instanceof DataBindingSource) {
                                    DataBindingSource itemBindingSource = item instanceof DataBindingSource ?
                                            (DataBindingSource) item : new SimpleMapDataBindingSource((Map) item)
                                    def instance = instantiateAndBindNestedOrUseMapConstructor(
                                            referencedType, item, itemBindingSource, nestedIncludeList, listener)
                                    if (instance != null) {
                                        itemsWhichNeedBinding << instance
                                    }
                                } else {
                                    itemsWhichNeedBinding << item
                                }
                            }
                        }
                        if (itemsWhichNeedBinding) {
                            for (item in itemsWhichNeedBinding) {
                                addElementToCollection(obj, metaProperty.name, metaProperty.type, item, false)
                            }
                        }
                    }
                }
            } else if (Map.isAssignableFrom(metaProperty.type) && val instanceof Map) {
                def referencedType = getReferencedTypeForCollection(propName, obj)
                if (referencedType != null) {
                    needsBinding = false
                    // Match bindProperty listener order: beforeBinding on the source value first
                    // so a veto skips conversion and nested binding entirely; then mutate the
                    // target map in place (supports getter-only Map properties).
                    if (listener == null || listener.beforeBinding(obj, propName, val, errors) != false) {
                        try {
                            Map boundMap = new LinkedHashMap()
                            ((Map) val).each { key, item ->
                                if (item == null || referencedType.isAssignableFrom(item.getClass())) {
                                    boundMap[key] = item
                                } else if (item instanceof Map || item instanceof DataBindingSource) {
                                    def instance
                                    if (isDomainClass(referencedType)) {
                                        def idValue = getIdentifierValueFrom(item)
                                        if (idValue != null && idValue != '' && idValue != 'null') {
                                            instance = getPersistentInstance(referencedType, idValue)
                                        }
                                    }
                                    DataBindingSource itemBindingSource = item instanceof DataBindingSource ?
                                            (DataBindingSource) item : new SimpleMapDataBindingSource((Map) item)
                                    if (instance == null) {
                                        instance = instantiateAndBindNestedOrUseMapConstructor(
                                                referencedType, item, itemBindingSource, nestedIncludeList, listener)
                                    } else {
                                        bindNested(instance, itemBindingSource, nestedIncludeList, listener)
                                    }
                                    if (instance != null) {
                                        boundMap[key] = instance
                                    }
                                } else {
                                    boundMap[key] = convert(referencedType, item)
                                }
                            }
                            Map map = initializeMap(obj, propName)
                            map.clear()
                            map.putAll(boundMap)
                        } catch (Exception e) {
                            addBindingError(obj, propName, val, e, listener, errors)
                        }
                    }
                    listener?.afterBinding(obj, propName, errors)
                }
            } else if (grailsApplication != null) { // Fixes bidirectional oneToOne binding issue #9308
                PersistentEntity domainClass = getPersistentEntity(obj.getClass())

                if (domainClass != null) {
                    def property = domainClass.getPropertyByName(metaProperty.name)
                    if (property != null && property instanceof Association) {
                        Association association = (Association) property
                        if (association.isBidirectional()) {
                            def otherSide = association.inverseSide
                            if (otherSide instanceof OneToOne) {
                                val[otherSide.name] = obj
                            }
                        }
                    }
                }
            }
        }
        if (needsBinding) {
            super.processProperty(obj, metaProperty, val, source, listener, errors)
        }
    }

    private Object instantiateAndBindNestedOrUseMapConstructor(Class referencedType, Object value,
            DataBindingSource source, List includeList, DataBindingListener listener) {
        def instance
        try {
            instance = referencedType.getDeclaredConstructor().newInstance()
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            if (value instanceof Map) {
                if (isBindAllIncludeList(includeList) ||
                        !DataBindingUtils.isDenyByDefaultEnabled()) {
                    return referencedType.newInstance(filterUnbindableMapConstructorArguments(referencedType, (Map) value))
                }
                if (DataBindingUtils.isGeneratedBindingIncludeList(bindingIncludeList.get())) {
                    warnAboutMissingNoArgConstructor(referencedType)
                }
                return null
            }
            throw ignored
        }
        bindNested(instance, source, includeList, listener)
        instance
    }

    private Map filterUnbindableMapConstructorArguments(Class referencedType, Map values) {
        List unbindablePropertyNames = DataBindingUtils.getUnbindablePropertyNames(referencedType)
        if (unbindablePropertyNames.isEmpty()) {
            return values
        }
        Map filteredValues = new LinkedHashMap(values)
        unbindablePropertyNames.each { filteredValues.remove(it) }
        filteredValues
    }

    @Override
    protected Object instantiateAndBindOrUseMapConstructor(Class referencedType, Map values,
            DataBindingListener listener) {
        instantiateAndBindNestedOrUseMapConstructor(referencedType, values,
                new SimpleMapDataBindingSource(values), nestedBindingIncludeList.get(), listener)
    }

    protected void warnAboutMissingNoArgConstructor(Class<?> targetType) {
        if (!isBindingWarningEnabled()) {
            return
        }
        String warningKey = targetType.name + '#no-arg-constructor'
        if (markBindingShapeWarned(warningKey)) {
            logBindingWarning(missingNoArgConstructorMessage(targetType))
        }
    }

    static String missingNoArgConstructorMessage(Class<?> targetType) {
        "Cannot securely data-bind [${targetType.name}] because it has no accessible no-arg constructor. " +
                'Add a no-arg constructor and bindable constraints, pass an explicit bind-all include for this element, ' +
                'or set `grails.databinding.denyByDefault=false`.'
    }

    static void resetWarnedBindingShapes() {
        synchronized (WARNED_BINDING_SHAPES) {
            WARNED_BINDING_SHAPES.clear()
        }
    }

    @Override
    protected processIndexedProperty(obj, MetaProperty metaProperty, IndexedPropertyReferenceDescriptor indexedPropertyReferenceDescriptor, val,
            DataBindingSource source, DataBindingListener listener, errors) {
        List previousNestedIncludeList = nestedBindingIncludeList.get()
        List indexedNestedIncludeList = getNestedBindingIncludeList(indexedPropertyReferenceDescriptor.propertyName)
        if (indexedNestedIncludeList != null) {
            nestedBindingIncludeList.set(indexedNestedIncludeList)
        } else {
            nestedBindingIncludeList.remove()
        }
        try {
            processIndexedPropertyWithNestedIncludeList(obj, metaProperty, indexedPropertyReferenceDescriptor, val, source, listener, errors)
        } finally {
            if (previousNestedIncludeList != null) {
                nestedBindingIncludeList.set(previousNestedIncludeList)
            } else {
                nestedBindingIncludeList.remove()
            }
        }
    }

    private void processIndexedPropertyWithNestedIncludeList(obj, MetaProperty metaProperty,
            IndexedPropertyReferenceDescriptor indexedPropertyReferenceDescriptor, val,
            DataBindingSource source, DataBindingListener listener, errors) {
        boolean needsBinding = true
        if (source.dataSourceAware) {
            def propName = indexedPropertyReferenceDescriptor.propertyName

            def idValue = getIdentifierValueFrom(val)
            if (idValue != null && idValue != '') {
                def propertyType = getDomainClassType(obj, propName)
                def referencedType = getReferencedTypeForCollection(propName, obj)
                if (referencedType != null && isDomainClass(referencedType)) {
                    needsBinding = false
                    if (Set.isAssignableFrom(metaProperty.type)) {
                        def collection = initializeCollection(obj, propName, metaProperty.type)
                        def instance
                        if (collection != null) {
                            instance = findAlementWithId((Set) collection, idValue)
                        }
                        if (instance == null) {
                            if ('null' != idValue) {
                                instance = getPersistentInstance(referencedType, idValue)
                            }
                            if (instance == null) {
                                def message = "Illegal attempt to update element in [${propName}] Set with id [${idValue}]. No such record was found."
                                Exception e = new IllegalArgumentException(message)
                                addBindingError(obj, propName, idValue, e, listener, errors)
                            } else {
                                addElementToCollectionAt(obj, propName, collection, Integer.parseInt(indexedPropertyReferenceDescriptor.index), instance)
                            }
                        }
                        if (instance != null) {
                            if (val instanceof Map) {
                                bind(instance, new SimpleMapDataBindingSource(val), listener)
                            } else if (val instanceof DataBindingSource) {
                                bind(instance, val, listener)
                            }
                        }
                    } else if (Collection.isAssignableFrom(metaProperty.type)) {
                        def collection = initializeCollection(obj, propName, metaProperty.type)
                        def idx = Integer.parseInt(indexedPropertyReferenceDescriptor.index)
                        if ('null' == idValue) {
                            if (idx < collection.size()) {
                                def element = collection[idx]
                                if (element != null) {
                                    collection.remove(element)
                                }
                            }
                        } else {
                            def instance = getPersistentInstance(referencedType, idValue)
                            addElementToCollectionAt(obj, propName, collection, idx, instance)
                            if (instance != null) {
                                if (val instanceof Map) {
                                    bind(instance, new SimpleMapDataBindingSource(val), listener)
                                } else if (val instanceof DataBindingSource) {
                                    bind(instance, val, listener)
                                }
                            }
                        }
                    } else if (Map.isAssignableFrom(metaProperty.type)) {
                        Map map = (Map) obj[propName]
                        if (idValue == 'null' || idValue == null || idValue == '') {
                            if (map != null) {
                                map.remove(indexedPropertyReferenceDescriptor.index)
                            }
                        } else {
                            map = initializeMap(obj, propName)
                            def persistedInstance = getPersistentInstance(referencedType, idValue)
                            if (persistedInstance != null) {
                                if (map.size() < autoGrowCollectionLimit || map.containsKey(indexedPropertyReferenceDescriptor.index)) {
                                    map[indexedPropertyReferenceDescriptor.index] = persistedInstance
                                    if (val instanceof Map) {
                                        bind(persistedInstance, new SimpleMapDataBindingSource(val), listener)
                                    } else if (val instanceof DataBindingSource) {
                                        bind(persistedInstance, val, listener)
                                    }
                                }
                            } else {
                                map.remove(indexedPropertyReferenceDescriptor.index)
                            }
                        }
                    }
                }
            }
        }
        if (needsBinding) {
            super.processIndexedProperty(obj, metaProperty, indexedPropertyReferenceDescriptor, val, source, listener, errors)
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private findAlementWithId(Set set,  idValue) {
        set.find {
            it.id == idValue
        }
    }

    @Override
    protected addElementToCollectionAt(obj, String propertyName, Collection collection, index, val) {
        super.addElementToCollectionAt(obj, propertyName, collection, index, val)

        def domainClass = getPersistentEntity(obj.getClass())
        if (domainClass != null) {
            def property = domainClass.getPropertyByName(propertyName)
            if (property != null && property instanceof Association) {
                Association association = (Association) property
                if (association.isBidirectional()) {
                    def otherSide = association.inverseSide
                    if (otherSide instanceof ManyToOne) {
                        val[otherSide.name] = obj
                    }
                }
            }
        }
    }

    private Map resolveConstrainedProperties(object) {
        Map constrainedProperties = null
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(object.getClass())
        MetaProperty metaProp = mc.getMetaProperty('constraints')
        if (metaProp != null) {
            Object constrainedPropsObj = getMetaPropertyValue(metaProp, object)
            if (constrainedPropsObj instanceof Map) {
                constrainedProperties = (Map) constrainedPropsObj
            }
        }
        constrainedProperties
    }
    private getMetaPropertyValue(MetaProperty metaProperty, delegate) {
        if (metaProperty instanceof ThreadManagedMetaBeanProperty) {
            return ((ThreadManagedMetaBeanProperty) metaProperty).getGetter().invoke(delegate, MetaClassHelper.EMPTY_ARRAY)
        }

        return metaProperty.getProperty(delegate)
    }

    @Override
    protected bindProperty(obj, DataBindingSource source, MetaProperty metaProperty, propertyValue,
            DataBindingListener listener, errors) {
        List previousNestedIncludeList = nestedBindingIncludeList.get()
        List propertyNestedIncludeList = getNestedBindingIncludeList(metaProperty.name)
        if (propertyNestedIncludeList != null) {
            nestedBindingIncludeList.set(propertyNestedIncludeList)
        } else {
            nestedBindingIncludeList.remove()
        }
        try {
            super.bindProperty(obj, source, metaProperty, propertyValue, listener, errors)
        } finally {
            if (previousNestedIncludeList != null) {
                nestedBindingIncludeList.set(previousNestedIncludeList)
            } else {
                nestedBindingIncludeList.remove()
            }
        }
    }

    @Override
    protected setPropertyValue(obj, DataBindingSource source, MetaProperty metaProperty, propertyValue, DataBindingListener listener) {
        def propName = metaProperty.name
        boolean isSet = false
        def domainClass = getPersistentEntity(obj.getClass())
        if (domainClass != null) {
            PersistentProperty property = domainClass.getPropertyByName(propName)
            if (property != null) {
                if (Collection.isAssignableFrom(property.type)) {
                    if (propertyValue instanceof String) {
                        isSet = addElementToCollection(obj, propName, property, propertyValue, true)
                    } else if (propertyValue instanceof String[]) {
                        if (property instanceof Association) {
                            Association association = (Association) property
                            if (association.associatedEntity != null) {
                                propertyValue.each { val ->
                                    boolean clearCollection = !isSet
                                    isSet = addElementToCollection(obj, propName, association, val, clearCollection) || isSet
                                }
                            }

                        }
                    }
                }
                PersistentProperty otherSide
                if (property instanceof Association) {
                    if (((Association) property).bidirectional) {
                        otherSide = ((Association) property).inverseSide
                    }
                }
                if (otherSide != null && List.isAssignableFrom(otherSide.getType())) {
                    DeferredBindingActions.addBindingAction(new Runnable() {
                        void run() {
                            if (obj[propName] != null && otherSide instanceof OneToMany) {
                                Collection collection = GrailsMetaClassUtils.getPropertyIfExists(obj[propName], otherSide.name, Collection)
                                if (collection == null || !collection.contains(obj)) {
                                    def methodName = 'addTo' + GrailsNameUtils.getClassName(otherSide.name)
                                    GrailsMetaClassUtils.invokeMethodIfExists(obj[propName], methodName, [obj] as Object[])
                                }
                            }
                        }
                    })
                }
            }
        }

        if (!isSet) {
            List nestedIncludeList = getNestedBindingIncludeList(propName)
            if (propertyValue instanceof Map || propertyValue instanceof DataBindingSource) {
                List previousNestedIncludeList = nestedBindingIncludeList.get()
                if (nestedIncludeList != null) {
                    nestedBindingIncludeList.set(nestedIncludeList)
                } else {
                    nestedBindingIncludeList.remove()
                }
                try {
                    super.setPropertyValue(obj, source, metaProperty, propertyValue, listener)
                } finally {
                    if (previousNestedIncludeList != null) {
                        nestedBindingIncludeList.set(previousNestedIncludeList)
                    } else {
                        nestedBindingIncludeList.remove()
                    }
                }
            } else {
                super.setPropertyValue(obj, source, metaProperty, propertyValue, listener)
            }
        }
    }

    private List getNestedBindingIncludeList(String propertyName) {
        List includeList = bindingIncludeList.get()
        if (includeList == null) {
            return null
        }
        boolean generatedIncludeList = DataBindingUtils.isGeneratedBindingIncludeList(includeList)
        if (!generatedIncludeList && includeList.any { item ->
            String includeName = item?.toString()
            includeName == propertyName || includeName == propertyName + '.*' || includeName == propertyName + '_*'
        }) {
            return getBindAllBindingIncludeList()
        }
        List nestedIncludeList = []
        String dotPrefix = propertyName + '.'
        String underscorePrefix = propertyName + '_'
        includeList.each { item ->
            String includeName = item?.toString()
            if (includeName?.startsWith(dotPrefix) && includeName != propertyName + '.*') {
                nestedIncludeList << includeName.substring(dotPrefix.length())
            } else if (includeName?.startsWith(underscorePrefix) && includeName != propertyName + '_*') {
                nestedIncludeList << includeName.substring(underscorePrefix.length())
            }
        }
        if (nestedIncludeList) {
            return generatedIncludeList ? DataBindingUtils.asGeneratedBindingIncludeList(nestedIncludeList) : nestedIncludeList
        }
        if (generatedIncludeList && !includeList.contains(propertyName + '.*') &&
                !includeList.contains(propertyName + '_*')) {
            return DataBindingUtils.asGeneratedBindingIncludeList(
                    [DefaultASTDatabindingHelper.NO_BINDABLE_PROPERTIES])
        }
        null
    }

    private void bindNested(Object object, DataBindingSource source, List includeList, DataBindingListener listener) {
        // A null nested include list from a generated parent allowlist (which carries the automatic
        // `prop.*`/`prop_*` wildcards emitted for non-simple bindable properties) must not bind the
        // child unrestricted: secure mode resolves the child's own generated allowlist. The bind-all
        // marker preserves an explicit nested wildcard, and compatibility mode resolves a null
        // (permissive) child allowlist.
        List effectiveIncludeList = includeList != null ? includeList : getBindingIncludeList(object)
        bindInternal(object, source, null, effectiveIncludeList, null, listener)
    }

    @Override
    protected preprocessValue(propertyValue) {
        if (propertyValue instanceof CharSequence) {
            String stringValue = propertyValue.toString()
            if (trimStrings) {
                stringValue = stringValue.trim()
            }
            if (convertEmptyStringsToNull && ''.equals(stringValue)) {
                stringValue = null
            }
            return stringValue
        }
        propertyValue
    }

    @Override
    protected addElementToCollection(obj, String propName, Class propertyType, propertyValue, boolean clearCollection) {

        // Fix for issue #9308 sets propertyValue's otherside value to the owning object for bidirectional manyToOne relationships
        def domainClass = getPersistentEntity(obj.getClass())
        if (domainClass != null) {
            def property = domainClass.getPropertyByName(propName)
            if (property != null && property instanceof Association) {
                Association association = ((Association) property)
                if (association.bidirectional) {
                    def otherSide = association.inverseSide
                    if (otherSide instanceof ManyToOne) {
                        propertyValue[otherSide.name] = obj
                    }
                }
            }
        }

        def elementToAdd = propertyValue
        def referencedType = getReferencedTypeForCollection(propName, obj)
        if (referencedType != null) {
            if (isDomainClass(referencedType)) {
                def persistentInstance = getPersistentInstance(referencedType, propertyValue)
                if (persistentInstance != null) {
                    elementToAdd = persistentInstance
                }
            }
        }
        super.addElementToCollection(obj, propName, propertyType, elementToAdd, clearCollection)
    }

    protected addElementToCollection(obj, String propName, PersistentProperty property, propertyValue, boolean clearCollection) {
        addElementToCollection(obj, propName, property.type, propertyValue, clearCollection)
    }

    @Autowired(required=false)
    void setStructuredBindingEditors(TypedStructuredBindingEditor[] editors) {
        editors.each { TypedStructuredBindingEditor editor ->
            registerStructuredEditor(editor.targetType, editor)
        }
    }

    @Autowired(required=false)
    void setValueConverters(ValueConverter[] converters) {
        converters.each { ValueConverter converter ->
            registerConverter(converter)
        }
    }

    @Autowired(required=false)
    void setFormattedValueConverters(FormattedValueConverter[] converters) {
        converters.each { FormattedValueConverter converter ->
            registerFormattedValueConverter(converter)
        }
    }

    @Autowired(required=false)
    void setDataBindingListeners(DataBindingListener[] listeners) {
        this.listeners.addAll(Arrays.asList(listeners))
    }

    @Override
    protected convert(Class typeToConvertTo, value) {
        if (value == null) {
            return null
        }
        def persistentInstance
        if (isDomainClass(typeToConvertTo)) {
            persistentInstance = getPersistentInstance(typeToConvertTo, value)
        }
        persistentInstance ?: super.convert(typeToConvertTo, value)
    }

    @Autowired
    setMessageSource(List<MessageSource> messageSources) {
        setMessageSource(GrailsMessageSourceUtils.findPreferredMessageSource(messageSources))
    }

    void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @Override
    protected String getFormatString(Annotation annotation) {
        assert annotation instanceof BindingFormat
        def code
        if (annotation instanceof BindingFormat) {
            code = ((BindingFormat) annotation).code()
        }
        def formatString
        if (code) {
            def locale = getLocale()
            formatString = messageSource.getMessage((String) code, [] as Object[], locale)
        }
        if (!formatString) {
            formatString = super.getFormatString(annotation)
        }
        formatString
    }

    protected Locale getLocale() {
        def request = GrailsWebRequest.lookup()
        request ? request.getLocale() : Locale.getDefault()
    }

    private PersistentEntity getPersistentEntity(Class clazz) {
        if (grailsApplication != null) {
            try {
                return grailsApplication.mappingContext.getPersistentEntity(clazz.name)
            } catch (GrailsConfigurationException ignored) {
                //no-op
            }
        }
        null
    }
}

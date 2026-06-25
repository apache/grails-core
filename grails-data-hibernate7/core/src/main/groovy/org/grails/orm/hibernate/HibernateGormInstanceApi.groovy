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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.InvokerHelper

import jakarta.persistence.LockModeType

import org.hibernate.LockMode

import org.hibernate.Hibernate
import org.hibernate.Session
import org.hibernate.collection.spi.PersistentCollection
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.persister.entity.EntityPersister

import org.springframework.validation.Errors
import org.springframework.validation.Validator

import grails.gorm.validation.CascadingValidator
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.event.ValidationEvent
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.proxy.GroovyProxyInterceptorLogic
import org.grails.orm.hibernate.support.ClosureEventListener
import org.grails.orm.hibernate.support.HibernateRuntimeUtils

/**
 * Hibernate GORM instance API.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormInstanceApi<D> extends GormInstanceApi<D> {

    protected Class<? extends Exception> validationException
    protected final ClassLoader classLoader
    protected IHibernateTemplate hibernateTemplate

    HibernateGormInstanceApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore)
        this.classLoader = classLoader ?: persistentClass.classLoader
        this.hibernateTemplate = (IHibernateTemplate) datastore.getHibernateTemplate()
        initializeValidationException(this.classLoader)
    }

    HibernateGormInstanceApi(Class<D> persistentClass, MappingContext mappingContext, DatastoreResolver datastoreResolver, ClassLoader classLoader) {
        super(persistentClass, mappingContext, datastoreResolver)
        this.classLoader = classLoader ?: persistentClass.classLoader
        initializeValidationException(this.classLoader)
    }

    protected void initializeValidationException(ClassLoader classLoader) {
        for (cl in [classLoader, Thread.currentThread().getContextClassLoader(), HibernateGormInstanceApi.classLoader]) {
            if (cl == null) continue
            try {
                this.validationException = (Class<? extends Exception>) cl.loadClass('grails.validation.ValidationException')
                return
            } catch (Throwable e) {
                // ignore
            }
        }
        this.validationException = org.grails.datastore.mapping.validation.ValidationException
    }

    protected HibernateDatastore getHibernateDatastore() {
        return (HibernateDatastore) getDatastore()
    }

    InstanceApiHelper getInstanceApiHelper() {
        return getHibernateDatastore().getInstanceApiHelper()
    }

    /**
     * Handles proxy-related method calls on Hibernate or Groovy proxies (e.g. isInitialized()).
     */
    @CompileDynamic
    Object methodMissing(Object target, String name, Object[] args) {
        if ('isInitialized' == name) {
            Boolean groovyResult = GroovyProxyInterceptorLogic.isInitialized(target)
            return groovyResult != null ? groovyResult : Hibernate.isInitialized(target)
        }
        if ('initialize' == name || 'getTarget' == name) {
            Hibernate.initialize(target)
            return target
        }
        throw new MissingMethodException(name, target?.class ?: persistentClass, args, false)
    }

    @Override
    HibernateGormInstanceApi<D> forQualifier(String qualifier) {
        Datastore ds = getDatastore()
        if (ds == null) return this
        
        org.grails.datastore.gorm.DatastoreResolver resolver = new org.grails.datastore.gorm.DatastoreResolver() {
            @Override Datastore resolve() { org.grails.datastore.gorm.GormRegistry.instance.apiResolver.findDatastore(persistentClass, qualifier) }
        }
        HibernateGormInstanceApi<D> newApi = new HibernateGormInstanceApi<D>(persistentClass, ds.mappingContext, resolver, classLoader)
        newApi.failOnError = failOnError
        newApi.markDirty = markDirty
        return newApi
    }

    protected IHibernateTemplate getHibernateTemplate() {
        if (this.hibernateTemplate == null) {
            HibernateDatastore datastore = getHibernateDatastore()
            IHibernateTemplate template = (IHibernateTemplate) datastore.getHibernateTemplate()
            if (qualifier != null && !org.grails.datastore.mapping.core.connections.ConnectionSource.DEFAULT.equals(qualifier) && datastore.getMultiTenancyMode() == org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
                String connectionName = datastore.connectionSources.defaultConnectionSource.name
                if (!connectionName.equals(qualifier)) {
                    this.hibernateTemplate = new TenantBoundHibernateTemplate(template, (Serializable)qualifier, datastore)
                } else {
                    this.hibernateTemplate = template
                }
            } else {
                // For DEFAULT qualifier or non-discriminator mode, the datastore resolver may return
                // different datastores in different transaction contexts (e.g., preferred datastore switching
                // between a multi-datasource parent and a secondary child). Do not cache here — resolve
                // the template dynamically on every call to avoid using a stale template from a prior context.
                return template
            }
        }
        return hibernateTemplate
    }

    /**
     * Checks whether a field is dirty
     * Gets the original persisted value of a field.
     *
     * @param fieldName The field name
     * @return The original persisted value
     */
    Object getPersistentValue(D instance, String fieldName) {
        SessionImplementor session = (SessionImplementor) getHibernateDatastore().getSessionFactory().getCurrentSession()
        EntityEntry entry = findEntityEntry(instance, session)
        if (entry == null || entry.getLoadedState() == null) {
            if (instance instanceof DirtyCheckable) {
                return ((DirtyCheckable) instance).getOriginalValue(fieldName)
            }
            return null
        }

        EntityPersister persister = entry.getPersister()
        int fieldIndex = Arrays.asList(persister.getPropertyNames()).indexOf(fieldName)
        return fieldIndex == -1 ? null : entry.getLoadedState()[fieldIndex]
    }

    protected EntityEntry findEntityEntry(D instance, SessionImplementor session) {
        return session.getPersistenceContext().getEntry(instance)
    }

    @Override
    List<String> getDirtyPropertyNames(D instance) {
        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable) instance).listDirtyPropertyNames()
        }
        SessionImplementor session = (SessionImplementor) getHibernateDatastore().getSessionFactory().getCurrentSession()
        EntityEntry entry = findEntityEntry(instance, session)
        if (entry == null) {
            return Collections.emptyList()
        }

        Object[] loadedState = entry.getLoadedState()
        if (loadedState == null) {
            return Collections.emptyList()
        }

        EntityPersister persister = entry.getPersister()
        Object[] values = persister.getPropertyValues(instance)
        int[] dirtyPropertyIndexes = persister.findDirty(values, loadedState, instance, session)
        if (dirtyPropertyIndexes == null) {
            return Collections.emptyList()
        }

        List<String> names = new ArrayList<>()
        String[] propertyNames = persister.getPropertyNames()
        for (int index : dirtyPropertyIndexes) {
            names.add(propertyNames[index])
        }
        return names
    }

    @Override
    boolean isDirty(D instance) {
        if (!isAttached(instance)) {
            return false
        }
        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable) instance).hasChanged()
        }
        SessionImplementor session = (SessionImplementor) getHibernateDatastore().getSessionFactory().getCurrentSession()
        EntityEntry entry = findEntityEntry(instance, session)
        if (entry == null) {
            return false
        }

        Object[] loadedState = entry.getLoadedState()
        if (loadedState == null) {
            return true // brand new
        }

        EntityPersister persister = entry.getPersister()
        Object[] values = persister.getPropertyValues(instance)
        int[] dirtyPropertyIndexes = persister.findDirty(values, loadedState, instance, session)
        return dirtyPropertyIndexes != null && dirtyPropertyIndexes.length > 0
    }

    @Override
    boolean isDirty(D instance, String fieldName) {
        if (!isAttached(instance)) {
            return false
        }
        if (instance instanceof DirtyCheckable) {
            return ((DirtyCheckable) instance).hasChanged(fieldName)
        }
        return false
    }

    @Override
    D save(D target, Map arguments) {
        PersistentEntity domainClass = getGormPersistentEntity()
        runDeferredBinding()
        boolean shouldFlush = shouldFlush(arguments)
        boolean shouldValidate = shouldValidate(arguments, domainClass)

        HibernateRuntimeUtils.autoAssociateBidirectionalOneToOnes(domainClass, target)

        boolean deepValidate = true
        if (arguments?.containsKey(HibernateGormValidationApi.ARGUMENT_DEEP_VALIDATE)) {
            deepValidate = ClassUtils.getBooleanFromMap(HibernateGormValidationApi.ARGUMENT_DEEP_VALIDATE, arguments)
        }

        if (shouldValidate) {
            Validator validator = datastore.mappingContext.getEntityValidator(domainClass)
            Errors errors = HibernateRuntimeUtils.setupErrorsProperty(target)

            if (validator) {
                getHibernateDatastore().applicationEventPublisher?.publishEvent new ValidationEvent(getHibernateDatastore(), target)

                if (validator instanceof CascadingValidator) {
                    ((CascadingValidator) validator).validate target, errors, deepValidate
                } else if (validator instanceof org.grails.datastore.gorm.validation.CascadingValidator) {
                    ((org.grails.datastore.gorm.validation.CascadingValidator) validator).validate target, errors, deepValidate
                } else {
                    validator.validate target, errors
                }

                if (errors.hasErrors()) {
                    handleValidationError(domainClass, target, errors)
                    if (shouldFail(arguments)) {
                        throw org.grails.datastore.mapping.validation.ValidationException.newInstance('Validation Error(s) occurred during save()', errors)
                    }
                    return null
                }
                setObjectToReadWrite(target)
            }
        }

        autoRetrieveAssociations getHibernateDatastore(), domainClass, target

        GormValidateable validateable = (GormValidateable) target
        validateable.skipValidation(true)
        if (!deepValidate) {
            ClosureEventListener.SKIP_DEEP_VALIDATION.set(Boolean.TRUE)
        }

        try {
            return performUpsert(target, shouldFlush)
        } finally {
            validateable.skipValidation(false)
            if (!deepValidate) {
                ClosureEventListener.SKIP_DEEP_VALIDATION.remove()
            }
        }
    }

    private static final Class DEFERRED_BINDING

    static {
        try {
            DEFERRED_BINDING = HibernateGormInstanceApi.classLoader.loadClass('org.grails.datastore.mapping.core.DeferredBindingActions')
        } catch (Throwable e) {
            DEFERRED_BINDING = null
        }
    }

    private static void runDeferredBinding() {
        if (DEFERRED_BINDING != null) {
            DEFERRED_BINDING.getMethod('runActions').invoke(null)
        }
    }

    protected void autoRetrieveAssociations(Datastore datastore, PersistentEntity domainClass, D target) {
        // no-op, handled by Hibernate
    }

    protected boolean isAutoFlush() {
        return getHibernateDatastore().isAutoFlush()
    }

    @Override
    boolean isFailOnError() {
        return getHibernateDatastore().isFailOnError()
    }

    @Override
    boolean isMarkDirty() {
        return getHibernateDatastore().markDirty
    }

    protected boolean shouldFlush(Map arguments) {
        if (arguments?.containsKey('flush')) {
            return ClassUtils.getBooleanFromMap('flush', arguments)
        }
        if (arguments?.containsKey(DynamicFinder.ARGUMENT_FLUSH_MODE)) {
            return ClassUtils.getBooleanFromMap(DynamicFinder.ARGUMENT_FLUSH_MODE, arguments)
        }
        return isAutoFlush()
    }

    protected boolean shouldValidate(Map arguments, PersistentEntity domainClass) {
        if (arguments?.containsKey('validate')) {
            return ClassUtils.getBooleanFromMap('validate', arguments)
        }
        if (arguments?.containsKey(org.grails.datastore.gorm.GormValidationApi.ARGUMENT_DEEP_VALIDATE)) {
            return ClassUtils.getBooleanFromMap(org.grails.datastore.gorm.GormValidationApi.ARGUMENT_DEEP_VALIDATE, arguments)
        }
        return true
    }

    protected boolean shouldFail(Map arguments) {
        if (arguments?.containsKey('failOnError')) {
            return ClassUtils.getBooleanFromMap('failOnError', arguments)
        }
        return isFailOnError()
    }

    @Override
    D merge(D target, Map arguments) {
        return performMerge(target, shouldFlush(arguments))
    }

    @Override
    D insert(D target, Map arguments) {
        PersistentEntity domainClass = getGormPersistentEntity()
        runDeferredBinding()
        boolean shouldFlush = shouldFlush(arguments)
        boolean shouldValidate = shouldValidate(arguments, domainClass)

        if (shouldValidate) {
            Validator validator = datastore.mappingContext.getEntityValidator(domainClass)
            Errors errors = HibernateRuntimeUtils.setupErrorsProperty(target)

            if (validator) {
                getHibernateDatastore().applicationEventPublisher?.publishEvent new ValidationEvent(getHibernateDatastore(), target)
                validator.validate target, errors

                if (errors.hasErrors()) {
                    handleValidationError(domainClass, target, errors)
                    if (shouldFail(arguments)) {
                        throw org.grails.datastore.mapping.validation.ValidationException.newInstance('Validation Error(s) occurred during insert()', errors)
                    }
                    return null
                }
            }
        }

        GormValidateable validateable = (GormValidateable) target
        validateable.skipValidation(true)

        try {
            return (D) execute({ org.grails.datastore.mapping.core.Session session ->
                session.insert(target)
                if (shouldFlush) {
                    session.flush()
                }
                return target
            } as org.grails.datastore.mapping.core.SessionCallback)
        } finally {
            validateable.skipValidation(false)
        }
    }

    @Override
    void delete(D target, Map arguments) {
        getHibernateTemplate().execute { Session session ->
            session.remove target
            if (shouldFlush(arguments)) {
                session.flush()
            }
        }
    }

    @Override
    boolean isAttached(D instance) {
        hibernateTemplate.contains instance
    }

    @Override
    D lock(D instance) {
        hibernateTemplate.lock(instance, LockMode.PESSIMISTIC_WRITE)
        instance
    }

    @Override
    D attach(D instance) {
        hibernateTemplate.execute { Session session ->
            HibernateAttachSupport.attach(instance, session)
        }
        return instance
    }

    @Override
    void discard(D target) {
        getHibernateTemplate().execute { Session session ->
            if (sessionContains(session, target)) {
                session.detach target
            }
        }
    }

    @Override
    D refresh(D target) {
        getHibernateTemplate().execute { Session session ->
            session.refresh target
        }
        return target
    }

    D read(Serializable id) {
        (D) getHibernateTemplate().execute { Session session ->
            D instance = (D) session.get(persistentClass, id)
            if (instance != null) {
                session.setReadOnly(instance, true)
            }
            return instance
        }
    }

    protected D performUpsert(D target, boolean shouldFlush) {
        getHibernateTemplate().execute { Session session ->
            if (sessionContains(session, target)) {
                if (shouldFlush) {
                    flushSession session
                }
                return target
            } else {
                PersistentProperty identityProperty = getGormPersistentEntity().identity
                if (identityProperty == null) {
                    // Composite ID entity — the user always supplies all key properties.
                    // Hibernate merge() handles both the first-save (INSERT) and update (UPDATE) paths.
                    return performMerge(target, shouldFlush)
                }
                Serializable id = (Serializable) InvokerHelper.getProperty(target, identityProperty.name)
                if (id == null) {
                    return performPersist(target, shouldFlush)
                } else {
                    return performMerge(target, shouldFlush)
                }
            }
        }
    }

    protected void flushSession(Session session) {
        HibernateDatastore datastore = getHibernateDatastore()
        if (datastore.isOsivReadOnly(datastore.sessionFactory)) {
            return
        }
        session.flush()
    }

    /**
     * Hibernate 7 changed {@code Session.contains()} to throw {@link IllegalArgumentException}
     * when the supplied object's class is not a mapped entity in this session factory, instead of
     * returning {@code false} as Hibernate 5 did.  All call sites in this class must go through
     * this helper so they safely degrade to {@code false} for cross-datasource entities.
     */
    private static boolean sessionContains(Session session, Object target) {
        try {
            return session.contains(target)
        } catch (IllegalArgumentException ignored) {
            return false
        }
    }

    protected D performMerge(final D target, final boolean flush) {
        getHibernateTemplate().execute { Session session ->
            D merged
            if (sessionContains(session, target)) {
                // a second PersistentCollection for the same role+key ("two representations").
                // Just use the entity as-is; dirty-checking + cascade will handle children.
                merged = target
            } else {
                reconcileCollections(session, target)
                merged = (D) session.merge(target)
                session.lock(merged, LockModeType.NONE)
                // Sync id back immediately so target has an identity
                PersistentEntity entity = getGormPersistentEntity()
                String idProp = entity.identity?.name ?: 'id'
                InvokerHelper.setProperty(target, idProp, InvokerHelper.getProperty(merged, idProp))
            }
            if (flush) {
                flushSession session
            }
            // Sync version after flush so the incremented value is captured
            PersistentEntity entity = getGormPersistentEntity()
            PersistentProperty versionProperty = entity.version
            if (versionProperty != null) {
                InvokerHelper.setProperty(target, versionProperty.name, InvokerHelper.getProperty(merged, versionProperty.name))
            }
            // Return the session-managed instance so callers can use it in subsequent session
            // operations without triggering NonUniqueObjectException when the same entity
            // is referenced again (e.g. as a cascade target or query parameter).
            return merged
        }
    }

    protected D performPersist(final D target, final boolean shouldFlush) {
        getHibernateTemplate().execute { Session session ->
            try {
                markInsertActive()
                session.persist target
                if (shouldFlush) {
                    flushSession session
                }
                return target
            } finally {
                resetInsertActive()
            }
        }
    }

    /**
     * Reconciles collection fields on an entity before session.merge() to prevent H7's
     * "Found two representations of same collection" error.
     *
     * Two scenarios cause this error:
     *
     * 1. Stale PersistentCollection: the field holds a PersistentCollection from a previous
     *    (now closed) session. H7 merge in the new session sees two collection objects for the
     *    same role + key. Fix: copy the items to a plain collection so merge can create a fresh one.
     *
     * 2. Plain collection on a managed entity: addTo* created a new ArrayList on a managed entity
     *    that already has a session-tracked PersistentCollection for that field. Fix: handled
     *    upstream by HibernateEntity.addTo override; reconcileCollections handles any residual cases.
     */
    @SuppressWarnings('unchecked')
    private void reconcileCollections(Session session, D target) {
        PersistentEntity entity = getGormPersistentEntity()
        EntityReflector reflector = datastore.mappingContext.getEntityReflector(entity)
        if (reflector == null) return

        SessionImplementor si = (SessionImplementor) session

        for (Association assoc in entity.associations) {
            if (!(assoc instanceof OneToMany) && !(assoc instanceof ManyToMany)) continue

            String propName = assoc.name
            Object fieldValue = reflector.getProperty(target, propName)
            if (fieldValue == null) continue

            if (fieldValue instanceof PersistentCollection) {
                PersistentCollection<?> pc = (PersistentCollection<?>) fieldValue
                // If this PersistentCollection belongs to a different (closed) session,
                // replace it with a plain collection so merge can create a fresh one.
                if (pc.getSession() != si) {
                    Collection<Object> plain = (Collection<Object>) [].asType(assoc.type)
                    if (pc.wasInitialized()) {
                        plain.addAll((Collection<Object>) pc)
                    }
                    reflector.setProperty(target, propName, plain)
                }
                // If it belongs to the current session, leave it alone — no issue.
            }
            // Plain (non-PersistentCollection) fields on managed entities should have been
            // handled by HibernateEntity.addTo; nothing more to do here.
        }
    }

    @CompileDynamic
    protected void handleValidationError(PersistentEntity domainClass, D target, Errors errors) {
        InvokerHelper.setProperty(target, GormProperties.ERRORS, errors)
    }

    @CompileDynamic
    protected void markInsertActive() {
        HibernateRuntimeUtils.markInsertActive()
    }

    @CompileDynamic
    protected static void resetInsertActive() {
        HibernateRuntimeUtils.resetInsertActive()
    }

    @CompileDynamic
    void setObjectToReadWrite(Object target) {
        HibernateRuntimeUtils.setObjectToReadWrite(target, getHibernateDatastore().sessionFactory)
    }

    @CompileDynamic
    void setObjectToReadOnly(Object target) {
        HibernateRuntimeUtils.setObjectToReadOnly(target, getHibernateDatastore().sessionFactory)
    }

    @CompileDynamic
    protected void incrementVersion(Object target) {
        PersistentEntity persistentEntity = getGormPersistentEntity()
        if (persistentEntity.isVersioned() && target.hasProperty(GormProperties.VERSION)) {
            Object version = target."${GormProperties.VERSION}"
            if (version instanceof Long) {
                target."${GormProperties.VERSION}" = ++((Long) version)
            }
        }
    }
}

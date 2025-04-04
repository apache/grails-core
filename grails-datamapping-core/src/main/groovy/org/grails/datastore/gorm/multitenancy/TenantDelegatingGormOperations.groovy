package org.grails.datastore.gorm.multitenancy

import grails.gorm.DetachedCriteria
import grails.gorm.api.GormAllOperations
import grails.gorm.multitenancy.Tenants
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.api.BuildableCriteria
import org.grails.datastore.mapping.query.api.Criteria
import org.springframework.transaction.TransactionDefinition

/**
 * Wraps each method call in the the given tenant id
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantDelegatingGormOperations<D> implements GormAllOperations<D> {
    final Datastore datastore
    final Serializable tenantId
    final GormAllOperations<D> allOperations

    TenantDelegatingGormOperations(Datastore datastore, Serializable tenantId, GormAllOperations<D> allOperations) {
        this.datastore = datastore
        this.tenantId = tenantId
        this.allOperations = allOperations
    }

    @Override
    def propertyMissing(D instance, String name) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.propertyMissing(instance, name)
        }
    }

    @Override
    boolean instanceOf(D instance, Class cls) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.instanceOf(instance, cls)
        }
    }

    @Override
    D lock(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.lock(instance)
        }
    }

    @Override
    def <T> T mutex(D instance, Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.mutex(instance, callable)
        }
    }

    @Override
    D refresh(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.refresh(instance)
        }
    }

    @Override
    D save(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.save(instance)
        }
    }

    @Override
    D insert(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.insert(instance)
        }
    }

    @Override
    D insert(D instance, Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.insert(instance, params)
        }
    }

    @Override
    D merge(D instance, Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.merge(instance, params)
        }
    }

    @Override
    D save(D instance, boolean validate) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.save(instance, validate)
        }
    }

    @Override
    D save(D instance, Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.save(instance, params)
        }
    }

    @Override
    Serializable ident(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.ident(instance)
        }
    }

    @Override
    D attach(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.attach(instance)
        }
    }

    @Override
    boolean isAttached(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.isAttached(instance)
        }
    }

    @Override
    void discard(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.discard(instance)
        }
    }

    @Override
    void delete(D instance) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.delete(instance)
        }
    }

    @Override
    void delete(D instance, Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.save(instance, params)
        }
    }

    @Override
    PersistentEntity getGormPersistentEntity() {
        allOperations.gormPersistentEntity
    }

    @Override
    List<FinderMethod> getGormDynamicFinders() {
        return allOperations.gormDynamicFinders
    }

    @Override
    DetachedCriteria<D> where(Closure callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.where(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereLazy(Closure callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.whereLazy(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereAny(Closure callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.whereAny(callable)
        }
    }

    @Override
    List<D> findAll(Closure callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(callable)
        }
    }

    @Override
    List<D> findAll(Map args, Closure callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(args, callable)
        }
    }

    @Override
    D find(Closure callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(callable)
        }
    }

    @Override
    List<Serializable> saveAll(Object... objectsToSave) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.saveAll(objectsToSave)
        }
    }

    @Override
    List<Serializable> saveAll(Iterable<?> objectsToSave) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.saveAll(objectsToSave)
        }
    }

    @Override
    void deleteAll(Object... objectsToDelete) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.deleteAll(objectsToDelete)
        }
    }

    @Override
    void deleteAll(Iterable objectsToDelete) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.deleteAll(objectsToDelete)
        }
    }

    @Override
    D create() {
        allOperations.create()
    }

    @Override
    D get(Serializable id) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.get(id)
        }
    }

    @Override
    D read(Serializable id) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.read(id)
        }
    }

    @Override
    D load(Serializable id) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.load(id)
        }
    }

    @Override
    D proxy(Serializable id) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.proxy(id)
        }
    }

    @Override
    List<D> getAll(Iterable<Serializable> ids) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.getAll(ids)
        }
    }

    @Override
    List<D> getAll(Serializable... ids) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.getAll(ids)
        }
    }

    @Override
    List<D> getAll() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.getAll()
        }
    }

    @Override
    BuildableCriteria createCriteria() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.createCriteria()
        }
    }

    @Override
    def <T> T withCriteria(@DelegatesTo(Criteria) Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withCriteria callable
        }
    }

    @Override
    def <T> T withCriteria(Map builderArgs, @DelegatesTo(Criteria) Closure callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withCriteria builderArgs, callable
        }
    }

    @Override
    D lock(Serializable id) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.lock(id)
        }
    }

    @Override
    D merge(D d) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.merge(d)
        }
    }

    @Override
    Integer count() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.count()
        }
    }

    @Override
    Integer getCount() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.getCount()
        }
    }

    @Override
    boolean exists(Serializable id) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.exists(id)
        }
    }

    @Override
    List<D> list(Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.list(params)
        }
    }

    @Override
    List<D> list() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.list()
        }
    }

    @Override
    List<D> findAll(Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(params)
        }
    }

    @Override
    List<D> findAll() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll()
        }
    }

    @Override
    List<D> findAll(D example) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(example)
        }
    }

    @Override
    List<D> findAll(D example, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(example, args)
        }
    }

    @Override
    D first() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.first()
        }
    }

    @Override
    D first(String propertyName) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.first(propertyName)
        }
    }

    @Override
    D first(Map queryParams) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.first(queryParams)
        }
    }

    @Override
    D last() {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.last()
        }
    }

    @Override
    D last(String propertyName) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.last(propertyName)
        }
    }

    @Override
    Object methodMissing(String methodName, Object arg) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.methodMissing(methodName, arg)
        }
    }

    @Override
    Object propertyMissing(String property) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.propertyMissing(property)
        }
    }

    @Override
    void propertyMissing(String property, Object value) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.propertyMissing(property, value)
        }
    }

    @Override
    D last(Map queryParams) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.last(queryParams)
        }
    }

    @Override
    List<D> findAllWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAllWhere(queryMap)
        }
    }

    @Override
    List<D> findAllWhere(Map queryMap, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAllWhere(queryMap, args)
        }
    }

    @Override
    D find(D example) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(example)
        }
    }

    @Override
    D find(D example, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(example, args)
        }
    }

    @Override
    D findWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findWhere(queryMap)
        }
    }

    @Override
    D findWhere(Map queryMap, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findWhere(queryMap, args)
        }
    }

    @Override
    D findOrCreateWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findOrCreateWhere(queryMap)
        }
    }

    @Override
    D findOrSaveWhere(Map queryMap) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findOrSaveWhere(queryMap)
        }
    }

    @Override
    def <T> T withSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withSession callable
        }
    }

    @Override
    def <T> T withDatastoreSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withDatastoreSession callable
        }
    }

    @Override
    def <T> T withTransaction(Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withTransaction callable
        }
    }

    @Override
    def <T> T withNewTransaction(Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withNewTransaction callable
        }
    }

    @Override
    def <T> T withTransaction(Map transactionProperties, Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withTransaction transactionProperties, callable
        }
    }

    @Override
    def <T> T withNewTransaction(Map transactionProperties, Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withNewTransaction transactionProperties, callable
        }
    }

    @Override
    def <T> T withTransaction(TransactionDefinition definition, Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withTransaction definition, callable
        }
    }

    @Override
    def <T> T withNewSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withNewSession callable
        }
    }

    @Override
    def <T> T withStatelessSession(Closure<T> callable) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.withStatelessSession callable
        }
    }

    @Override
    List executeQuery(CharSequence query) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeQuery(query)
        }
    }

    @Override
    List executeQuery(CharSequence query, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeQuery(query, args)
        }
    }

    @Override
    List executeQuery(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeQuery(query, params, args)
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeQuery(query, params)
        }
    }

    @Override
    List executeQuery(CharSequence query, Object... params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeQuery(query, params)
        }
    }

    @Override
    List executeQuery(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeQuery(query, params, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeUpdate(query)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeUpdate(query, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeUpdate(query, params, args)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeUpdate(query, params)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Object... params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeUpdate(query, params)
        }
    }

    @Override
    Integer executeUpdate(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.executeUpdate(query, params, args)
        }
    }

    @Override
    D find(CharSequence query) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(query)
        }
    }

    @Override
    D find(CharSequence query, Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(query, params, args)
        }
    }

    @Override
    D find(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Object[] params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(query, params)
        }
    }

    @Override
    D find(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.find(query, params, args)
        }
    }

    @Override
    List<D> findAll(CharSequence query) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(query)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Map params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(query, params, args)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Collection params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Object[] params) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(query, params)
        }
    }

    @Override
    List<D> findAll(CharSequence query, Collection params, Map args) {
        Tenants.withId((Class<Datastore>)datastore.getClass(), tenantId) {
            allOperations.findAll(query, params, args)
        }
    }

    @Override
    def <T> T withTenant(Serializable tenantId, Closure<T> callable) {
        allOperations.withTenant(tenantId, callable)
    }

    @Override
    GormAllOperations<D> eachTenant(Closure callable) {
        allOperations.eachTenant(callable)
    }

    @Override
    GormAllOperations<D> withTenant(Serializable tenantId) {
        allOperations.withTenant(tenantId)
    }
}

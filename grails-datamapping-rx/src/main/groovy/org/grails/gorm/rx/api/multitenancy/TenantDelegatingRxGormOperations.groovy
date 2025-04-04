package org.grails.gorm.rx.api.multitenancy

import grails.gorm.rx.CriteriaBuilder
import grails.gorm.rx.DetachedCriteria
import grails.gorm.rx.api.RxGormAllOperations
import grails.gorm.rx.multitenancy.Tenants
import grails.gorm.rx.proxy.ObservableProxy
import groovy.transform.CompileStatic
import org.grails.datastore.rx.RxDatastoreClient
import rx.Observable

/**
 * Delegates to a RxGORM API ensuring the tenant id is correct
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class TenantDelegatingRxGormOperations<D> implements RxGormAllOperations<D> {
    final RxDatastoreClient datastoreClient
    final Serializable tenantId
    final RxGormAllOperations<D> delegateOperations
    final Class<RxDatastoreClient> datastoreClientClass

    TenantDelegatingRxGormOperations(RxDatastoreClient datastoreClient, Serializable tenantId, RxGormAllOperations<D> delegateOperations) {
        this.datastoreClient = datastoreClient
        this.datastoreClientClass = (Class<RxDatastoreClient>)datastoreClient.getClass()
        this.tenantId = tenantId
        this.delegateOperations = delegateOperations
    }

    @Override
    Serializable ident(D instance) {
        return delegateOperations.ident(instance)
    }

    @Override
    Observable<D> save(D instance) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.save(instance)
        }
    }

    @Override
    Observable<D> save(D instance, Map arguments) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.save(instance, arguments)
        }
    }

    @Override
    Observable<D> insert(D instance) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.insert(instance)
        }
    }

    @Override
    Observable<D> insert(D instance, Map arguments) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.insert(instance, arguments)
        }
    }

    @Override
    Observable<Boolean> delete(D instance) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.delete(instance)
        }
    }

    @Override
    Observable<Boolean> delete(D instance, Map arguments) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.delete(instance, arguments)
        }
    }

    @Override
    D create() {
        delegateOperations.create()
    }

    @Override
    Observable<D> get(Serializable id) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.get(id)
        }
    }

    @Override
    Observable<D> get(Serializable id, Map queryArgs) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.get(id, queryArgs)
        }
    }

    @Override
    ObservableProxy<D> proxy(Serializable id) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.proxy(id)
        }
    }

    @Override
    ObservableProxy<D> proxy(Serializable id, Map queryArgs) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.proxy(id, queryArgs)
        }

    }

    @Override
    ObservableProxy<D> proxy(DetachedCriteria<D> query) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.proxy(query)
        }
    }

    @Override
    ObservableProxy<D> proxy(DetachedCriteria<D> query, Map queryArgs) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.proxy(query, queryArgs)
        }
    }

    @Override
    Observable<Number> count() {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.count()
        }
    }

    @Override
    Observable<Number> deleteAll(D... objects) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.deleteAll(objects)
        }

    }

    @Override
    Observable<Number> deleteAll(Iterable<D> objects) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.deleteAll(objects)
        }

    }

    @Override
    Observable<List<Serializable>> saveAll(Iterable<D> objects) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.saveAll(objects)
        }
    }

    @Override
    Observable<List<Serializable>> saveAll(Iterable<D> objects, Map arguments) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.saveAll(objects, arguments)
        }
    }

    @Override
    Observable<List<Serializable>> saveAll(D... objects) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.saveAll(objects)
        }
    }

    @Override
    Observable<List<Serializable>> insertAll(Iterable<D> objects) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.insertAll(objects)
        }
    }

    @Override
    Observable<List<Serializable>> insertAll(Iterable<D> objects, Map arguments) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.insertAll(objects, arguments)
        }
    }

    @Override
    Observable<List<Serializable>> insertAll(D... objects) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.insertAll(objects)
        }
    }

    @Override
    Observable<Boolean> exists(Serializable id) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.exists(id)
        }
    }

    @Override
    Observable<D> first() {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.first()
        }
    }

    @Override
    Observable<D> first(String propertyName) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.first(propertyName)
        }
    }

    @Override
    Observable<D> first(Map queryParams) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.first(queryParams)
        }
    }

    @Override
    Observable<D> last() {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.last()
        }
    }

    @Override
    Observable<D> last(String propertyName) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.last(propertyName)
        }
    }

    @Override
    Observable<D> last(Map params) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.last(params)
        }
    }

    @Override
    Observable<List<D>> list() {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.list()
        }
    }

    @Override
    Observable<List<D>> list(Map args) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.list(args)
        }
    }

    @Override
    Observable<D> findAll() {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findAll()
        }
    }

    @Override
    Observable<D> findAll(Map args) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findAll(args)
        }
    }

    @Override
    Observable<D> findWhere(Map queryMap) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findWhere(queryMap)
        }
    }

    @Override
    Observable<D> findWhere(Map queryMap, Map args) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findWhere(queryMap, args)
        }
    }

    @Override
    Observable<D> findOrCreateWhere(Map queryMap) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findOrCreateWhere(queryMap)
        }
    }

    @Override
    Observable<D> findOrSaveWhere(Map queryMap) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findOrSaveWhere(queryMap)
        }
    }

    @Override
    Observable<D> findAllWhere(Map queryMap) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findAllWhere(queryMap)
        }
    }

    @Override
    Observable<D> findAllWhere(Map queryMap, Map args) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findAllWhere(queryMap)
        }
    }

    @Override
    Observable<D> findAll(Closure callable) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.findAll(callable)
        }
    }

    @Override
    Observable<D> find(Closure callable) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.find(callable)
        }
    }

    @Override
    DetachedCriteria<D> where(Closure callable) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.where(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereLazy(Closure callable) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.whereLazy(callable)
        }
    }

    @Override
    DetachedCriteria<D> whereAny(Closure callable) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.whereAny(callable)
        }
    }

    @Override
    CriteriaBuilder<D> createCriteria() {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.createCriteria()
        }
    }

    @Override
    Observable withCriteria(@DelegatesTo(CriteriaBuilder) Closure callable) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.withCriteria(callable)
        }
    }

    @Override
    Observable withCriteria(Map builderArgs, @DelegatesTo(CriteriaBuilder) Closure callable) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.withCriteria(builderArgs, callable)
        }
    }

    @Override
    Observable<D> staticMethodMissing(String methodName, Object arg) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.staticMethodMissing(methodName, arg)
        }
    }

    @Override
    Object staticPropertyMissing(String property) {
        Tenants.withId(datastoreClientClass, tenantId) {
            delegateOperations.staticPropertyMissing(property)
        }
    }

    @Override
    def <T> T withTenant(Serializable tenantId, @DelegatesTo(RxGormAllOperations) Closure<T> callable) {
        return delegateOperations.withTenant(tenantId, callable)
    }

    @Override
    RxGormAllOperations<D> eachTenant(@DelegatesTo(RxGormAllOperations) Closure callable) {
        return delegateOperations.eachTenant(callable)
    }

    @Override
    RxGormAllOperations<D> withTenant(Serializable tenantId) {
        return delegateOperations.withTenant(tenantId)
    }
}

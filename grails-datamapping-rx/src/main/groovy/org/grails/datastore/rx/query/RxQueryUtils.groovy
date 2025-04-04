package org.grails.datastore.rx.query

import grails.gorm.rx.collection.RxUnidirectionalCollection
import grails.gorm.rx.proxy.ObservableProxy
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSupport
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.rx.internal.RxDatastoreClientImplementor
import rx.Observable
import rx.functions.FuncN

import jakarta.persistence.FetchType

/**
 * Utility methods for building Query implementations
 *
 * @author Graeme Rocher
 */
@CompileStatic
class RxQueryUtils {

    /**
     * Process fetch strategies for a query
     *
     * @param datastoreClient The client implementor
     * @param observable The original observable
     * @param entity The entity
     * @param fetchStrategies The fetch strategies
     * @param queryState The query state
     *
     * @return The new observable
     */
    static Observable processFetchStrategies(RxDatastoreClientImplementor datastoreClient, Observable observable, PersistentEntity entity, Map<String, FetchType> fetchStrategies, QueryState queryState) {
        if(!fetchStrategies.isEmpty()) {

            EntityReflector entityReflector = entity.mappingContext.getEntityReflector(entity)
            List<String> joinedProperties = []
            observable = observable.switchMap { Object o ->

                List<Observable<?>> observables = [Observable.just(o)] as List<Observable<?>>
                if(entity.isInstance(o)) {

                    for(fetch in fetchStrategies) {
                        PersistentProperty property = entity.getPropertyByName(fetch.key)
                        FetchType fetchType = fetch.value
                        if(fetchType == FetchType.EAGER) {
                            def propertyName = property.name
                            def currentValue = entityReflector.getProperty(o, propertyName)

                            if(property instanceof ToOne && (currentValue instanceof ObservableProxy)) {
                                ToOne toOne = (ToOne)property
                                if(!toOne.isEmbedded()) {
                                    if(!toOne.isForeignKeyInChild()) {
                                        joinedProperties.add(propertyName)
                                        observables.add datastoreClient.get(toOne.associatedEntity.javaClass, ((ObservableProxy)currentValue).getProxyKey(), queryState)
                                    }
                                    else {
                                        joinedProperties.add(propertyName)
                                        def associationQuery = datastoreClient.createQuery(toOne.associatedEntity.javaClass, queryState)
                                        RxQuery rxQuery = (RxQuery)associationQuery.eq(toOne.inverseSide.name, o)
                                                .max(1)

                                        observables.add rxQuery.singleResult()
                                    }
                                }
                            }
                            else if(property instanceof ToMany) {
                                ToMany toMany = (ToMany)property
                                if(toMany.isBidirectional() && !(toMany instanceof ManyToMany)) {
                                    def inverseSide = toMany.inverseSide
                                    if(inverseSide instanceof ManyToOne) {
                                        joinedProperties.add(propertyName)


                                        RxQuery rxQuery = (RxQuery)datastoreClient.createQuery(inverseSide.owner.javaClass, queryState)
                                                .eq(inverseSide.name, o)

                                        observables.add((Observable) rxQuery.findAll().toList())
                                    }
                                }
                                else if(currentValue instanceof RxUnidirectionalCollection) {
                                    RxUnidirectionalCollection ruc = (RxUnidirectionalCollection)currentValue
                                    def associationKeys = ruc.associationKeys
                                    joinedProperties.add(propertyName)
                                    if(associationKeys) {
                                        def inverseEntity = toMany.associatedEntity
                                        RxQuery rxQuery = (RxQuery)datastoreClient.createQuery(inverseEntity.javaClass, queryState)
                                                .in(inverseEntity.identity.name, associationKeys)

                                        observables.add(rxQuery.findAll().toList())
                                    }
                                    else {
                                        observables.add(Observable.just([]))
                                    }
                                }
                            }
                        }
                    }
                }

                return Observable.zip(observables, new FuncN() {
                    @Override
                    Object call(Object... args) {
                        return Arrays.asList(args)
                    }
                })
            }.map { r ->
                List<Object> result = (List<Object>) r
                // first result is the entity
                def entityInstance = result.get(0)
                if(result.size() > 1) {
                    int i = 0
                    for(o in result[1..-1]) {
                        String name = joinedProperties.get(i++)
                        if(o instanceof Collection) {
                            def propertyWriter = entityReflector.getPropertyWriter(name)
                            o = o.asType(propertyWriter.propertyType())
                            propertyWriter.write(entityInstance, DirtyCheckingSupport.wrap((Collection)o, (DirtyCheckable)entityInstance, name))
                        }
                        else {
                            entityReflector.setProperty(entityInstance, name, o)
                        }
                    }
                }
                return entityInstance
            }

        }
        return observable
    }
}

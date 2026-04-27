/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The AS licenses this file
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
package org.grails.datastore.mapping.simple.query

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.api.QueryableCriteria
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.datastore.mapping.simple.SimpleMapSession
import org.grails.datastore.mapping.simple.engine.SimpleMapEntityPersister
import java.util.concurrent.ConcurrentHashMap

/**
 * A query implementation for the simple map-based datastore
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class SimpleMapQuery extends Query {

    private SimpleMapEntityPersister entityPersister

    SimpleMapQuery(SimpleMapSession session, PersistentEntity entity, SimpleMapEntityPersister entityPersister) {
        super(session, entity)
        this.entityPersister = entityPersister
    }

    protected Map<Serializable, Map> getDatastoreMap() {
        ((SimpleMapSession)session).getBackingMap()
    }

    protected Map getIndices() {
        ((SimpleMapSession)session).getIndices()
    }

    Number deleteAll() {
        List<Object> results = list()
        for (Object result in results) {
            session.delete(result)
        }
        return results.size()
    }

    org.grails.datastore.mapping.model.MappingContext getMappingContext() {
        session.datastore.mappingContext
    }

    String getFamily() {
        calculateFamily(entity)
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Query.Junction criteria) {
        def entityMap = [:]
        def datastore = getDatastoreMap()
        def family = getFamily()
        def familyMap = (Map) datastore[family] ?: [:]
        
        entityMap.putAll(familyMap)
        
        if (!criteria.isEmpty()) {
            def criteriaList = criteria.getCriteria()
            def subQueryResult = executeSubQuery(criteria, criteriaList)
            def filteredKeys = subQueryResult.keySet().collect { it instanceof Number ? it.longValue() : it } as Set
            
            entityMap = familyMap.findAll { entry ->
                def key = entry.key instanceof Number ? entry.key.longValue() : entry.key
                return filteredKeys.contains(key)
            }
        }

        if (!entity.isRoot()) {
            def discriminator = entity.discriminator
            entityMap = entityMap.findAll { it.value.discriminator == discriminator }
        }

        def nullEntries = entityMap.entrySet().findAll { it.value == null }
        entityMap.keySet().removeAll(nullEntries.collect { it.key })

        if (orderBy) {
            entityMap = entityMap.sort { a, b ->
                int result = 0
                for (Query.Order order in orderBy) {
                    boolean desc = order.direction == Query.Order.Direction.DESC
                    def val1 = a.value[order.property]
                    def val2 = b.value[order.property]
                    if (desc) {
                        result = val2 <=> val1
                    }
                    else {
                        result = val1 <=> val2
                    }
                    if (result != 0) break
                }
                return result
            }
        } else {
            // Default stable order by ID
            entityMap = entityMap.sort { a, b ->
                def k1 = a.key instanceof Number ? a.key.longValue() : a.key
                def k2 = b.key instanceof Number ? b.key.longValue() : b.key
                return k1 <=> k2
            }
        }

        def resultList = []
        if (max > -1 && offset > -1) {
            def last = offset + max - 1
            def keys = entityMap.keySet().toList()
            if (offset < keys.size()) {
                def to = Math.min(last, keys.size() - 1)
                keys = keys[offset..to]
            }
            else {
                keys = []
            }
            populateQueryResult(keys, resultList, entityMap)
        }
        else if (max > -1) {
            def keys = entityMap.keySet().toList()
            def to = Math.min(max - 1, keys.size() - 1)
            if (to >= 0) {
                keys = keys[0..to]
            }
            else {
                keys = []
            }
            populateQueryResult(keys, resultList, entityMap)
        }
        else if (offset > 0) {
            def keys = entityMap.keySet().toList()
            if (offset < keys.size()) {
                keys = keys[offset..(keys.size() - 1)]
            }
            else {
                keys = []
            }
            populateQueryResult(keys, resultList, entityMap)
        }
        else {
            populateQueryResult(entityMap.keySet().toList(), resultList, entityMap)
        }

        List finalResults
        if (projections.isEmpty()) {
            finalResults = resultList.collect {
                entityPersister.createObjectFromNativeEntry(entity, it.key, it.value)
            }
        }
        else {
            List<Query.Projection> projectionList = projections.projectionList
            boolean hasAggregate = projectionList.any { it instanceof Query.CountProjection || it instanceof Query.CountDistinctProjection || it instanceof Query.AvgProjection || it instanceof Query.MinProjection || it instanceof Query.MaxProjection || it instanceof Query.SumProjection }
            
            if (hasAggregate) {
                def results = []
                for (p in projectionList) {
                    if (p instanceof Query.CountProjection) {
                        results << resultList.size()
                    }
                    else if (p instanceof Query.CountDistinctProjection) {
                        def propertyValues = resultList.collect { it.value[p.propertyName] }.findAll { it != null }
                        results << propertyValues.unique().size()
                    }
                    else if (p instanceof Query.AvgProjection || p instanceof Query.MinProjection || p instanceof Query.MaxProjection || p instanceof Query.SumProjection) {
                         def propertyValues = resultList.collect { it.value[p.propertyName] }.findAll { it != null }
                         if (p instanceof Query.MinProjection) {
                             results << propertyValues.min()
                         }
                         else if (p instanceof Query.MaxProjection) {
                             results << propertyValues.max()
                         }
                         else if (p instanceof Query.SumProjection) {
                             results << propertyValues.sum()
                         }
                         else if (p instanceof Query.AvgProjection) {
                             def res = propertyValues.isEmpty() ? 0 : propertyValues.sum() / propertyValues.size()
                             if (res instanceof BigDecimal) res = res.doubleValue()
                             results << res
                         }
                    }
                    else if (p instanceof Query.IdProjection) {
                        results << (resultList.isEmpty() ? null : resultList[0].key)
                    }
                    else if (p instanceof Query.PropertyProjection) {
                        def val = resultList.isEmpty() ? null : resultList[0].value[p.propertyName]
                        if (val != null) {
                             PersistentProperty prop = entity.getPropertyByName(p.propertyName)
                             if (prop instanceof ToOne && !(prop.type.isInstance(val))) {
                                 val = session.retrieve(prop.type, (Serializable)val)
                             }
                        }
                        results << val
                    }
                }
                finalResults = [results.size() == 1 ? results[0] : results]
            }
            else {
                finalResults = resultList.collect { res ->
                    def results = []
                    for (p in projectionList) {
                        if (p instanceof Query.IdProjection) {
                            results << res.key
                        }
                        else if (p instanceof Query.PropertyProjection) {
                            def val = res.value[p.propertyName]
                            if (val != null) {
                                PersistentProperty prop = entity.getPropertyByName(p.propertyName)
                                if (prop instanceof ToOne && !(prop.type.isInstance(val))) {
                                    val = session.retrieve(prop.type, (Serializable)val)
                                }
                            }
                            results << val
                        }
                    }
                    return results.size() == 1 ? results[0] : results
                }
            }
        }
        return finalResults
    }

    List list(Map arguments) {
        if (arguments?.max || arguments?.offset) {
            if (arguments.max) {
                this.max = (arguments.max as Integer)
            }
            if (arguments.offset) {
                this.offset = (arguments.offset as Integer)
            }
            if (arguments.sort) {
                String sort = arguments.sort
                String order = arguments.order ?: "asc"
                if (order.equalsIgnoreCase("desc")) {
                    this.order(Query.Order.desc(sort))
                } else {
                    this.order(Query.Order.asc(sort))
                }
            }
            return new grails.gorm.PagedResultList(this)
        }
        
        if (arguments?.sort) {
             String sort = arguments.sort
             String order = arguments.order ?: "asc"
             if (order.equalsIgnoreCase("desc")) {
                 this.order(Query.Order.desc(sort))
             } else {
                 this.order(Query.Order.asc(sort))
             }
        }
        return list()
    }

    private String calculateFamily(PersistentEntity entity) {
        return entity.rootEntity.name
    }

    private String getConnectionName() {
        ((SimpleMapDatastore)session.datastore).getConnectionName()
    }

    protected Map executeSubQuery(Query.Junction criteria, List<Query.Criterion> criterionList) {
        def entityMap = [:]
        def datastore = getDatastoreMap()
        def familyMap = (Map) datastore[getFamily()] ?: [:]
        
        if (criteria instanceof Query.Conjunction) {
            def resultList = []
            for (c in criterionList) {
                if (c instanceof Query.Junction) {
                    resultList << executeSubQuery(c, c.getCriteria()).keySet().collect { it instanceof Number ? it.longValue() : it } as Set
                }
                else {
                    resultList << handleCriterion(c).collect { it instanceof Number ? it.longValue() : it } as Set
                }
            }

            if (resultList.isEmpty()) {
                return [:]
            }
            def intersectKeys = resultList[0] as Set
            for (int i = 1; i < resultList.size(); i++) {
                intersectKeys = intersectKeys.intersect(resultList[i] as Set)
            }
            
            for (key in intersectKeys) {
                Object k = key instanceof Number ? key.longValue() : key
                if (familyMap.containsKey(k)) entityMap[k] = familyMap[key]
            }
        }
        else if (criteria instanceof Query.Disjunction) {
            def unionKeys = [] as Set
            for (c in criterionList) {
                if (c instanceof Query.Junction) {
                    unionKeys.addAll(executeSubQuery(c, c.getCriteria()).keySet().collect { it instanceof Number ? it.longValue() : it })
                }
                else {
                    unionKeys.addAll(handleCriterion(c).collect { it instanceof Number ? it.longValue() : it })
                }
            }
            
            for (key in unionKeys) {
                Object k = key instanceof Number ? key.longValue() : key
                if (familyMap.containsKey(k)) entityMap[k] = familyMap[key]
            }
        }
        else if (criteria instanceof Query.Negation) {
             def currentViewKeys = familyMap.keySet().collect { it instanceof Number ? it.longValue() : it } as Set
             
             def excludedKeys = [] as Set
             for (c in criterionList) {
                if (c instanceof Query.Junction) {
                    excludedKeys.addAll(executeSubQuery(c, c.getCriteria()).keySet().collect { it instanceof Number ? it.longValue() : it })
                }
                else {
                    excludedKeys.addAll(handleCriterion(c).collect { it instanceof Number ? it.longValue() : it })
                }
             }
             
             def negatedKeys = currentViewKeys - excludedKeys
             for (key in negatedKeys) {
                 Object k = key instanceof Number ? key.longValue() : key
                 if (familyMap.containsKey(k)) entityMap[k] = familyMap[key]
             }
        }
        else {
            throw new UnsupportedOperationException("Junction type ${criteria.getClass().name} not supported in executeSubQuery")
        }
        return entityMap
    }

    private Object applyFunction(String functionName, Object val) {
        Object result = val
        switch(functionName.toLowerCase()) {
            case 'length': result = val.toString().length(); break
            case 'lower': result = val.toString().toLowerCase(); break
            case 'upper': result = val.toString().toUpperCase(); break
            case 'trim': result = val.toString().trim(); break
            case 'year': 
                def c = Calendar.getInstance()
                c.time = (Date)val
                result = c.get(Calendar.YEAR)
                break
            case 'month':
                def c = Calendar.getInstance()
                c.time = (Date)val
                result = c.get(Calendar.MONTH) + 1
                break
            case 'day':
                def c = Calendar.getInstance()
                c.time = (Date)val
                result = c.get(Calendar.DAY_OF_MONTH)
                break
            case 'hour':
                def c = Calendar.getInstance()
                c.time = (Date)val
                result = c.get(Calendar.HOUR_OF_DAY)
                break
            case 'minute':
                def c = Calendar.getInstance()
                c.time = (Date)val
                result = c.get(Calendar.MINUTE)
                break
            case 'second':
                def c = Calendar.getInstance()
                c.time = (Date)val
                result = c.get(Calendar.SECOND)
                break
        }
        return result
    }

    private boolean matchesCriterion(SimpleMapQuery query, Query.PropertyCriterion pc, Object val) {
        def value = pc.value
        def prop = entity.getPropertyByName(pc.property)
        
        // Marshal both sides to their persistent form
        val = query.marshalValue(prop, val)
        value = query.marshalValue(prop, value)

        if (val instanceof Number && value instanceof Number) {
            val = val.doubleValue()
            value = value.doubleValue()
        }
        
        boolean match = false
        if (pc instanceof Query.Equals) match = (val == value)
        else if (pc instanceof Query.NotEquals) match = (val != value)
        else if (val instanceof Comparable && value instanceof Comparable) {
            try {
                if (pc instanceof Query.GreaterThan) match = (val > value)
                else if (pc instanceof Query.LessThan) match = (val < value)
                else if (pc instanceof Query.GreaterThanEquals) match = (val >= value)
                else if (pc instanceof Query.LessThanEquals) match = (val <= value)
            } catch (Throwable e) {
                // ignore
            }
        }
        
        return match
    }

    private Collection handleCriterion(Query.Criterion c) {
        def handler = handlers[c.getClass()]
        if (!handler) {
            handler = handlers.find { k, v -> k.isAssignableFrom(c.getClass()) }?.value
        }
        if (handler) {
            PersistentProperty property = null
            try {
                if (c instanceof Query.PropertyNameCriterion) {
                    property = entity.getPropertyByName(((Query.PropertyNameCriterion)c).getProperty())
                    
                    // Handle value-side functions
                    if (c instanceof Query.PropertyCriterion) {
                        def val = ((Query.PropertyCriterion)c).getValue()
                        if (val instanceof org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion) {
                            def fcc = (org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion)val
                            def baseVal = fcc.getPropertyCriterion().getValue()
                            def funcResult = applyFunction(fcc.getFunctionName(), baseVal)
                            // Create a new criterion with the resolved value
                            c = c.getClass().newInstance(((Query.PropertyCriterion)c).getProperty(), funcResult)
                        }
                    }
                }
                else if (c instanceof org.grails.datastore.mapping.query.api.AssociationCriteria) {
                    property = ((org.grails.datastore.mapping.query.api.AssociationCriteria)c).getAssociation()
                }
                else if (c instanceof org.grails.datastore.mapping.query.AssociationQuery) {
                    property = ((org.grails.datastore.mapping.query.AssociationQuery)c).getAssociation()
                }
            } catch (Throwable e) {
                // ignore
            }
            def results = handler.call(this, c, property)
            if (results instanceof Collection) {
                def finalRes = results.collect { it instanceof Number ? it.longValue() : it }
                return finalRes
            }
            return []
        }
        return []
    }

    private void populateQueryResult(List keys, List resultList, Map entityMap) {
        for (key in keys) {
            resultList << [key: key, value: entityMap[key]]
        }
    }

    protected marshalValue(PersistentProperty property, value) {
        if (value instanceof org.grails.datastore.mapping.query.api.QueryableCriteria) {
            value = ((org.grails.datastore.mapping.query.api.QueryableCriteria)value).list()
            if (value instanceof List && value.size() == 1) {
                value = value[0]
            }
        }
        
        if (property != null && value != null) {
            if (!property.type.isInstance(value)) {
                try {
                    value = session.getMappingContext().getConversionService().convert(value, property.getType())
                } catch (Throwable e) {
                    // ignore and try other methods
                }
            }
            String propClassName = property.getClass().name
            if (propClassName.contains(".Basic") || propClassName.contains(".Custom")) {
                 if (!(value instanceof Number || value instanceof String || value instanceof Boolean)) {
                     def marshaller = property.getCustomTypeMarshaller()
                     if (marshaller != null && marshaller.supports(getMappingContext())) {
                         try {
                             value = marshaller.write(property, value, [:])
                         } catch (Throwable e) {
                             // ignore
                         }
                     }
                 }
            }
        }

        if (value instanceof Number && property && Number.isAssignableFrom(property.type)) {
            return value.asType(property.type)
        }
        return value
    }

    private static Map handlers = [
        (Query.IdEquals): { SimpleMapQuery query, Query.IdEquals ie, PersistentProperty property ->
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            def prop = property ?: query.entity.identity
            def value = query.marshalValue(prop, ie.value)
            Object v = value instanceof Number ? value.longValue() : value
            if (familyMap.containsKey(v)) {
                return [v]
            }
            return []
        },
        (Query.IsNull): { SimpleMapQuery query, Query.IsNull isNull, PersistentProperty property ->
            def name = isNull.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (val == null) results << ok
            }
            return results
        },
        (Query.IsNotNull): { SimpleMapQuery query, Query.IsNotNull isNotNull, PersistentProperty property ->
            def name = isNotNull.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (val != null) results << ok
            }
            return results
        },
        (Query.Like): { SimpleMapQuery query, Query.Like like, PersistentProperty property ->
            def name = like.property
            def pattern = like.value.replaceAll('%', '.*')
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (val != null && (val.toString() =~ /^${pattern}$/)) results << ok
            }
            return results
        },
        (Query.ILike): { SimpleMapQuery query, Query.ILike like, PersistentProperty property ->
            def name = like.property
            def pattern = like.value.replaceAll('%', '.*')
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (val != null && (val.toString() =~ /(?i)^${pattern}$/)) results << ok
            }
            return results
        },
        (Query.RLike): { SimpleMapQuery query, Query.RLike like, PersistentProperty property ->
            def name = like.property
            def pattern = like.value
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (val != null && (val.toString() =~ pattern)) results << ok
            }
            return results
        },
        (Query.EqualsProperty): { SimpleMapQuery query, Query.EqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 == val2) results << ok
            }
            return results
        },
        (Query.NotEqualsProperty): { SimpleMapQuery query, Query.NotEqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 != val2) results << ok
            }
            return results
        },
        (Query.GreaterThanProperty): { SimpleMapQuery query, Query.GreaterThanProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 > val2) results << ok
            }
            return results
        },
        (Query.GreaterThanEqualsProperty): { SimpleMapQuery query, Query.GreaterThanEqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 >= val2) results << ok
            }
            return results
        },
        (Query.LessThanProperty): { SimpleMapQuery query, Query.LessThanProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 < val2) results << ok
            }
            return results
        },
        (Query.LessThanEqualsProperty): { SimpleMapQuery query, Query.LessThanEqualsProperty ep, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val1 = query.resolveIfEmbedded(ep.property, ov)
                def val2 = query.resolveIfEmbedded(ep.otherProperty, ov)
                if (val1 <= val2) results << ok
            }
            return results
        },
        (org.grails.datastore.mapping.query.api.AssociationCriteria): { SimpleMapQuery query, org.grails.datastore.mapping.query.api.AssociationCriteria ac, PersistentProperty property ->
            def matchingAssociatedKeys = [] as Set
            if (!ac.getCriteria().isEmpty()) {
                def subQuery = query.session.createQuery(ac.getAssociation().getAssociatedEntity().javaClass)
                subQuery.criteria = new Query.Conjunction(ac.getCriteria())
                def subResults = subQuery.list()
                matchingAssociatedKeys = subResults.collect { 
                    def id = query.session.getPersister(it).getObjectIdentifier(it)
                    return id instanceof Number ? id.longValue() : id
                } as Set
            }
            
            def results = []
            def allIndices = query.getIndices()
            def root = "~${ac.getAssociation().owner.rootEntity.name}:${ac.getAssociation().name}:"
            
            allIndices.each { k, v ->
                if (k.startsWith(root) && v instanceof Collection) {
                    if (v.any { matchingAssociatedKeys.contains(it instanceof Number ? it.longValue() : it) }) {
                        def parentIdStr = k.substring(root.length())
                        def parentId = parentIdStr.isLong() ? parentIdStr.toLong() : parentIdStr
                        results << parentId
                    }
                }
            }
            return results
        },
        (org.grails.datastore.mapping.query.AssociationQuery): { SimpleMapQuery query, org.grails.datastore.mapping.query.AssociationQuery aq, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            def subQuery = query.session.createQuery(aq.entity.javaClass)
            subQuery.criteria = aq.getCriteria()
            
            def subResults = subQuery.list()
            def matchingAssociatedKeys = subResults.collect { 
                def id = query.session.getPersister(it).getObjectIdentifier(it)
                return id instanceof Number ? id.longValue() : id
            } as Set
            
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(aq.getAssociation().name, ov)
                if (val instanceof Collection) {
                    def valList = val.collect { it instanceof Number ? it.longValue() : it }
                    if (valList.any { matchingAssociatedKeys.contains(it) }) results << ok
                }
                else if (val != null) {
                    def v = val instanceof Number ? val.longValue() : val
                    if (matchingAssociatedKeys.contains(v)) {
                        results << ok
                    }
                }
            }
            return results
        },
        (org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion): { SimpleMapQuery query, org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion fcc, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            def functionName = fcc.getFunctionName()
            def propertyCriterion = fcc.getPropertyCriterion()
            
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(fcc.property, ov)
                if (val != null) {
                    def functionResult = query.applyFunction(functionName, val)
                    if (query.matchesCriterion(query, propertyCriterion, functionResult)) {
                        results << ok
                    }
                }
            }
            return results
        },
        (Query.Exists): { SimpleMapQuery query, Query.Exists exists, PersistentProperty property ->
            def subQuery = query.session.createQuery(exists.getSubquery().getPersistentEntity().javaClass)
            subQuery.criteria = exists.getSubquery().getCriteria()
            def subResults = subQuery.list()
            if (!subResults.isEmpty()) {
                return query.getDatastoreMap()[query.getFamily()]?.keySet() ?: []
            }
            return []
        },
        (Query.Equals): { SimpleMapQuery query, Query.Equals equals, PersistentProperty property ->
            def value = query.marshalValue(property, equals.value)
            final indexer = query.entityPersister.getPropertyIndexer(property)

            if (value && property instanceof ToOne && property.type.isInstance(value)) {
               value = query.session.getPersister(value).getObjectIdentifier(value)
            }

            if (equals.property.contains('.') || value == null) {
                def results = []
                def datastore = query.getDatastoreMap()
                def familyMap = (Map) datastore[query.getFamily()] ?: [:]
                familyMap.each { ok, ov ->
                    if (query.matchesCriterion(query, equals, query.resolveIfEmbedded(equals.property, ov))) results << ok
                }
                return results
            }
            else {
                def indexName = indexer.getIndexName(value)
                def allIndices = query.getIndices()
                def results = (List)allIndices[indexName] ?: []
                
                if (results.isEmpty()) {
                     // Fallback to scan
                     def scanResults = []
                     def datastore = query.getDatastoreMap()
                     def familyMap = (Map) datastore[query.getFamily()] ?: [:]
                     familyMap.each { ok, ov ->
                         if (query.matchesCriterion(query, equals, query.resolveIfEmbedded(equals.property, ov))) {
                             scanResults << ok
                         }
                     }
                     return scanResults
                }
                return results
            }
        },
        (Query.NotEquals): { SimpleMapQuery query, Query.NotEquals equals, PersistentProperty property ->
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                if (query.matchesCriterion(query, equals, query.resolveIfEmbedded(equals.property, ov))) results << ok
            }
            return results
        },
        (Query.In): { SimpleMapQuery query, Query.In inList, PersistentProperty property ->
            def values = inList.values
            if (values instanceof QueryableCriteria) {
                 values = ((QueryableCriteria)values).list().collect { 
                     def id = query.session.getPersister(it).getObjectIdentifier(it)
                     return id instanceof Number ? id.longValue() : id
                 }
            }
            def results = [] as Set
            for (value in values) {
                results.addAll(handlers[Query.Equals].call(query, new Query.Equals(inList.property, value), property))
            }
            return results
        },
        (Query.Between): { SimpleMapQuery query, Query.Between between, PersistentProperty property ->
            def from = between.from
            def to = between.to
            def name = between.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, new Query.GreaterThanEquals(name, from), val) &&
                    query.matchesCriterion(query, new Query.LessThanEquals(name, to), val)) {
                    results << ok
                }
            }
            return results
        },
        (Query.SizeEquals): { SimpleMapQuery query, Query.SizeEquals se, PersistentProperty property ->
            def name = se.property
            def value = se.value
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            if (property instanceof Association) {
                 def indexer = query.entityPersister.getAssociationIndexer(null, (Association)property)
                 familyMap.each { ok, ov ->
                     if (indexer.query(ok).size() == value) results << ok
                 }
            } else {
                familyMap.each { ok, ov ->
                    def val = query.resolveIfEmbedded(name, ov)
                    if (val instanceof Collection && val.size() == value) results << ok
                    else if (val instanceof Map && val.size() == value) results << ok
                }
            }
            return results
        },
        (Query.SizeGreaterThan): { SimpleMapQuery query, Query.SizeGreaterThan se, PersistentProperty property ->
            def name = se.property
            def value = se.value
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            if (property instanceof Association) {
                 def indexer = query.entityPersister.getAssociationIndexer(null, (Association)property)
                 familyMap.each { ok, ov ->
                     if (indexer.query(ok).size() > value) results << ok
                 }
            } else {
                familyMap.each { ok, ov ->
                    def val = query.resolveIfEmbedded(name, ov)
                    if (val instanceof Collection && val.size() > value) results << ok
                    else if (val instanceof Map && val.size() > value) results << ok
                }
            }
            return results
        },
        (Query.SizeLessThan): { SimpleMapQuery query, Query.SizeLessThan se, PersistentProperty property ->
            def name = se.property
            def value = se.value
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            if (property instanceof Association) {
                 def indexer = query.entityPersister.getAssociationIndexer(null, (Association)property)
                 familyMap.each { ok, ov ->
                     if (indexer.query(ok).size() < value) results << ok
                 }
            } else {
                familyMap.each { ok, ov ->
                    def val = query.resolveIfEmbedded(name, ov)
                    if (val instanceof Collection && val.size() < value) results << ok
                    else if (val instanceof Map && val.size() < value) results << ok
                }
            }
            return results
        },
        (Query.SizeGreaterThanEquals): { SimpleMapQuery query, Query.SizeGreaterThanEquals se, PersistentProperty property ->
            def name = se.property
            def value = se.value
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            if (property instanceof Association) {
                 def indexer = query.entityPersister.getAssociationIndexer(null, (Association)property)
                 familyMap.each { ok, ov ->
                     if (indexer.query(ok).size() >= value) results << ok
                 }
            } else {
                familyMap.each { ok, ov ->
                    def val = query.resolveIfEmbedded(name, ov)
                    if (val instanceof Collection && val.size() >= value) results << ok
                    else if (val instanceof Map && val.size() >= value) results << ok
                }
            }
            return results
        },
        (Query.SizeLessThanEquals): { SimpleMapQuery query, Query.SizeLessThanEquals se, PersistentProperty property ->
            def name = se.property
            def value = se.value
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            if (property instanceof Association) {
                 def indexer = query.entityPersister.getAssociationIndexer(null, (Association)property)
                 familyMap.each { ok, ov ->
                     if (indexer.query(ok).size() <= value) results << ok
                 }
            } else {
                familyMap.each { ok, ov ->
                    def val = query.resolveIfEmbedded(name, ov)
                    if (val instanceof Collection && val.size() <= value) results << ok
                    else if (val instanceof Map && val.size() <= value) results << ok
                }
            }
            return results
        },
        (Query.SizeNotEquals): { SimpleMapQuery query, Query.SizeNotEquals se, PersistentProperty property ->
            def name = se.property
            def value = se.value
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            
            if (property instanceof Association) {
                 def indexer = query.entityPersister.getAssociationIndexer(null, (Association)property)
                 familyMap.each { ok, ov ->
                     if (indexer.query(ok).size() != value) results << ok
                 }
            } else {
                familyMap.each { ok, ov ->
                    def val = query.resolveIfEmbedded(name, ov)
                    if (val instanceof Collection && val.size() != value) results << ok
                    else if (val instanceof Map && val.size() != value) results << ok
                }
            }
            return results
        },
        (Query.GreaterThan): { SimpleMapQuery query, Query.GreaterThan gt, PersistentProperty property ->
            def name = gt.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, gt, val)) results << ok
            }
            return results
        },
        (Query.LessThan): { SimpleMapQuery query, Query.LessThan lt, PersistentProperty property ->
            def name = lt.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, lt, val)) results << ok
            }
            return results
        },
        (Query.GreaterThanEquals): { SimpleMapQuery query, Query.GreaterThanEquals gt, PersistentProperty property ->
            def name = gt.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, gt, val)) results << ok
            }
            return results
        },
        (Query.LessThanEquals): { SimpleMapQuery query, Query.LessThanEquals lte, PersistentProperty property ->
            def name = lte.property
            def results = []
            def datastore = query.getDatastoreMap()
            def familyMap = (Map) datastore[query.getFamily()] ?: [:]
            familyMap.each { ok, ov ->
                def val = query.resolveIfEmbedded(name, ov)
                if (query.matchesCriterion(query, lte, val)) results << ok
            }
            return results
        }
    ]

    protected resolveIfEmbedded(String name, Map entry) {
        if (name.contains('.')) {
            def parts = name.split('\\.')
            def current = entry
            for (part in parts) {
                if (current instanceof Map) {
                    current = current[part]
                }
                else {
                    return null
                }
            }
            return current
        }
        return entry[name]
    }
}

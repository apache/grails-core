/* Copyright (C) 2010-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.mongo.query

import com.mongodb.ReadConcern
import com.mongodb.client.AggregateIterable
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCursor
import com.mongodb.client.MongoIterable
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistry
import org.springframework.dao.InvalidDataAccessResourceUsageException
import org.springframework.transaction.support.TransactionSynchronizationManager

import grails.mongodb.geo.Box
import grails.mongodb.geo.Circle
import grails.mongodb.geo.Distance
import grails.mongodb.geo.GeoJSON
import grails.mongodb.geo.Point
import grails.mongodb.geo.Polygon
import grails.mongodb.geo.Shape
import grails.mongodb.geo.Sphere
import org.grails.datastore.bson.codecs.CodecCustomTypeMarshaller
import org.grails.datastore.bson.query.BsonQuery
import org.grails.datastore.bson.query.BsonQuery.ProjectionHandler
import org.grails.datastore.bson.query.BsonQuery.QueryHandler
import org.grails.datastore.bson.query.EmbeddedQueryEncoder
import org.grails.datastore.gorm.mongo.geo.GeoJSONType
import org.grails.datastore.gorm.query.AbstractResultList
import org.grails.datastore.mapping.core.SessionImplementor
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import org.grails.datastore.mapping.model.EmbeddedPersistentEntity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Custom
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.mongo.AbstractMongoSession
import org.grails.datastore.mapping.mongo.MongoCodecSession
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoCollection
import org.grails.datastore.mapping.mongo.engine.MongoCodecEntityPersister
import org.grails.datastore.mapping.mongo.engine.MongoEntityPersister
import org.grails.datastore.mapping.mongo.engine.MongoIdCoercion
import org.grails.datastore.mapping.mongo.engine.codecs.PersistentEntityCodec
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.Query.AvgProjection
import org.grails.datastore.mapping.query.Query.Conjunction
import org.grails.datastore.mapping.query.Query.CountDistinctProjection
import org.grails.datastore.mapping.query.Query.CountProjection
import org.grails.datastore.mapping.query.Query.Criterion
import org.grails.datastore.mapping.query.Query.Disjunction
import org.grails.datastore.mapping.query.Query.DistinctProjection
import org.grails.datastore.mapping.query.Query.DistinctPropertyProjection
import org.grails.datastore.mapping.query.Query.IdEquals
import org.grails.datastore.mapping.query.Query.IdProjection
import org.grails.datastore.mapping.query.Query.In
import org.grails.datastore.mapping.query.Query.Junction
import org.grails.datastore.mapping.query.Query.MaxProjection
import org.grails.datastore.mapping.query.Query.MinProjection
import org.grails.datastore.mapping.query.Query.Order
import org.grails.datastore.mapping.query.Query.Projection
import org.grails.datastore.mapping.query.Query.PropertyCriterion
import org.grails.datastore.mapping.query.Query.PropertyProjection
import org.grails.datastore.mapping.query.Query.SumProjection
import org.grails.datastore.mapping.query.api.QueryArgumentsAware
import org.grails.datastore.mapping.query.projections.ManualProjections

/**
 * A {@link org.grails.datastore.mapping.query.Query} implementation for the Mongo document store.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@SuppressWarnings('rawtypes')
class MongoQuery extends BsonQuery implements QueryArgumentsAware {

    public static final String MONGO_IN_OPERATOR = IN_OPERATOR
    public static final String MONGO_OR_OPERATOR = OR_OPERATOR
    public static final String MONGO_AND_OPERATOR = AND_OPERATOR
    public static final String MONGO_GTE_OPERATOR = GTE_OPERATOR
    public static final String MONGO_LTE_OPERATOR = LTE_OPERATOR
    public static final String MONGO_GT_OPERATOR = GT_OPERATOR
    public static final String MONGO_LT_OPERATOR = LT_OPERATOR
    public static final String MONGO_NE_OPERATOR = NE_OPERATOR
    public static final String MONGO_NIN_OPERATOR = NIN_OPERATOR
    public static final String MONGO_REGEX_OPERATOR = REGEX_OPERATOR

    public static final String MONGO_WHERE_OPERATOR = WHERE_OPERATOR

    public static final String HINT_ARGUMENT = 'hint'
    public static final String READ_CONCERN_ARGUMENT = 'readConcern'

    private Map queryArguments = Collections.emptyMap()

    public static final String NEAR_OPERATOR = '$near'

    public static final String BOX_OPERATOR = '$box'

    public static final String POLYGON_OPERATOR = '$polygon'

    public static final String WITHIN_OPERATOR = '$within'

    public static final String CENTER_OPERATOR = '$center'

    public static final String GEO_WITHIN_OPERATOR = '$geoWithin'

    public static final String GEOMETRY_OPERATOR = '$geometry'

    public static final String CENTER_SPHERE_OPERATOR = '$centerSphere'

    public static final String GEO_INTERSECTS_OPERATOR = '$geoIntersects'

    public static final String MAX_DISTANCE_OPERATOR = '$maxDistance'

    public static final String NEAR_SPHERE_OPERATOR = '$nearSphere'

    static {
        queryHandlers.put(IdEquals, new QueryHandler<IdEquals>() {
            // Exercised end-to-end by StringIdWithObjectIdStorageSpec:
            //   - "with storedAs ObjectId, point lookup by hex string works" (happy path)
            //   - "with storedAs ObjectId, point lookup of a non-hex id matches the
            //     BSON String the encoder wrote" (null-return fallback for natural keys)
            //   - "with storedAs ObjectId, updates persist (no phantom OptimisticLockingException)"
            //     and related update/delete specs (which also hit IdEquals via the session filter)
            void handle(EmbeddedQueryEncoder queryEncoder, IdEquals criterion, Document query, PersistentEntity entity) {
                Object value = criterion.getValue()
                // Prefer the configured storage type ('storedAs' on the id mapping) so query BSON
                // matches what's actually on disk. Falls back to the declared Java type otherwise.
                Class<?> storedAs = MongoIdCoercion.resolveStoredAs(entity)
                Class<?> targetType = storedAs != null ? storedAs : entity.getIdentity().getType()
                Object converted = entity.getMappingContext().getConversionService().convert(value, targetType)
                // Symmetry with IdentityEncoder's non-hex fallback: if the converter returns
                // null for a non-null input (e.g. a natural-key String being converted to
                // ObjectId), keep the original value so the query targets what the encoder
                // actually wrote rather than {_id: null}.
                if (converted == null && value != null) {
                    converted = value
                }
                query.put(MongoEntityPersister.MONGO_ID_FIELD, converted)
            }
        })

        // Override the In handler so that criteria targeting the identity (findAllByIdInList,
        // Domain.createCriteria().list { 'in'('id', [...]) }, etc.) honor 'storedAs' the same way
        // IdEquals does. Without this, a domain declaring storedAs: ObjectId would send BSON Strings
        // in {_id: {$in: [...]}} and miss all stored ObjectId documents.
        //
        // Exercised end-to-end by StringIdWithObjectIdStorageSpec:
        //   - "with storedAs ObjectId, findAllByIdInList resolves all ids" (happy path via dynamic finder)
        //   - "with storedAs ObjectId, criteria in('id', [...]) resolves all ids" (happy path via createCriteria)
        //   - "with storedAs ObjectId, batch getAll with non-hex ids falls back to BSON String in the in-list"
        //     (null-return fallback for natural keys)
        queryHandlers.put(In, new QueryHandler<In>() {
            void handle(EmbeddedQueryEncoder queryEncoder, In inCriterion, Document query, PersistentEntity entity) {
                Document inQuery = new Document()
                List<Object> values = getInListQueryValues(entity, inCriterion)

                PersistentProperty identityProp = entity.getIdentity()
                boolean isIdInList = identityProp != null && identityProp.getName() == inCriterion.getProperty()
                if (isIdInList && MongoIdCoercion.resolveStoredAs(entity) != null) {
                    List<Object> coerced = new ArrayList<Object>(values.size())
                    for (Object v : values) {
                        coerced.add(MongoIdCoercion.coerceIdToStoredType(v, entity))
                    }
                    values = coerced
                }

                inQuery.put(IN_OPERATOR, values)
                String propertyName = getPropertyName(entity, inCriterion)
                query.put(propertyName, inQuery)
            }
        })

        queryHandlers.put(AssociationQuery, new QueryHandler<AssociationQuery>() {
            void handle(EmbeddedQueryEncoder queryEncoder, AssociationQuery criterion, Document query, PersistentEntity entity) {
                Association<?> association = criterion.getAssociation()
                PersistentEntity associatedEntity = association.getAssociatedEntity()
                if (association instanceof EmbeddedCollection) {
                    Document associationCollectionQuery = new Document()
                    populateMongoQuery(queryEncoder, associationCollectionQuery, criterion.getCriteria(), associatedEntity)
                    Document collectionQuery = new Document('$elemMatch', associationCollectionQuery)
                    String propertyKey = getPropertyName(entity, association.getName())
                    query.put(propertyKey, collectionQuery)
                } else if (associatedEntity instanceof EmbeddedPersistentEntity || association instanceof Embedded) {
                    Document associatedEntityQuery = new Document()
                    populateMongoQuery(queryEncoder, associatedEntityQuery, criterion.getCriteria(), associatedEntity)
                    String propertyKey = getPropertyName(entity, association.getName())
                    prefixEmbeddedQuery(propertyKey, associatedEntityQuery, query)
                } else {
                    throw new UnsupportedOperationException('Join queries are not supported by MongoDB')
                }
            }
        })

        queryHandlers.put(WithinBox, new QueryHandler<WithinBox>() {
            void handle(EmbeddedQueryEncoder queryEncoder, WithinBox withinBox, Document query, PersistentEntity entity) {
                Document nearQuery = new Document()
                Document box = new Document()
                MongoEntityPersister.setDBObjectValue(box, BOX_OPERATOR, withinBox.getValues(), entity.getMappingContext())
                nearQuery.put(WITHIN_OPERATOR, box)
                String propertyName = getPropertyName(entity, withinBox)
                query.put(propertyName, nearQuery)
            }
        })

        queryHandlers.put(WithinPolygon, new QueryHandler<WithinPolygon>() {
            void handle(EmbeddedQueryEncoder queryEncoder, WithinPolygon withinPolygon, Document query, PersistentEntity entity) {
                Document nearQuery = new Document()
                Document box = new Document()
                MongoEntityPersister.setDBObjectValue(box, POLYGON_OPERATOR, withinPolygon.getValues(), entity.getMappingContext())
                nearQuery.put(WITHIN_OPERATOR, box)
                String propertyName = getPropertyName(entity, withinPolygon)
                query.put(propertyName, nearQuery)
            }
        })

        queryHandlers.put(WithinCircle, new QueryHandler<WithinCircle>() {
            void handle(EmbeddedQueryEncoder queryEncoder, WithinCircle withinCentre, Document query, PersistentEntity entity) {
                Document nearQuery = new Document()
                Document center = new Document()
                MongoEntityPersister.setDBObjectValue(center, CENTER_OPERATOR, withinCentre.getValues(), entity.getMappingContext())
                nearQuery.put(WITHIN_OPERATOR, center)
                String propertyName = getPropertyName(entity, withinCentre)
                query.put(propertyName, nearQuery)
            }
        })

        QueryHandler<Near> nearHandler = new QueryHandler<Near>() {
            void handle(EmbeddedQueryEncoder queryEncoder, Near near, Document query, PersistentEntity entity) {
                Document nearQuery = new Document()
                Object value = near.getValue()
                String nearOperator = near instanceof NearSphere ? NEAR_SPHERE_OPERATOR : NEAR_OPERATOR
                if ((value instanceof List) || (value instanceof Map)) {
                    MongoEntityPersister.setDBObjectValue(nearQuery, nearOperator, value, entity.getMappingContext())
                } else if (value instanceof Point) {
                    Document geoJson = GeoJSONType.convertToGeoDocument((Point) value)
                    Document geometry = new Document()
                    geometry.put(GEOMETRY_OPERATOR, geoJson)
                    if (near.maxDistance != null) {
                        geometry.put(MAX_DISTANCE_OPERATOR, near.maxDistance.getValue())
                    }
                    nearQuery.put(nearOperator, geometry)
                }

                String propertyName = getPropertyName(entity, near)
                query.put(propertyName, nearQuery)
            }
        }
        queryHandlers.put(Near, nearHandler)
        queryHandlers.put(NearSphere, nearHandler)

        queryHandlers.put(GeoWithin, new QueryHandler<GeoWithin>() {
            void handle(EmbeddedQueryEncoder queryEncoder, GeoWithin geoWithin, Document query, PersistentEntity entity) {
                Document queryRoot = new Document()
                Document queryGeoWithin = new Document()
                queryRoot.put(GEO_WITHIN_OPERATOR, queryGeoWithin)
                String targetProperty = getPropertyName(entity, geoWithin)
                Object value = geoWithin.getValue()
                if (value instanceof Shape) {
                    Shape shape = (Shape) value
                    if (shape instanceof Polygon) {
                        Polygon p = (Polygon) shape
                        Document geoJson = GeoJSONType.convertToGeoDocument(p)
                        queryGeoWithin.put(GEOMETRY_OPERATOR, geoJson)
                    } else if (shape instanceof Box) {
                        queryGeoWithin.put(BOX_OPERATOR, shape.asList())
                    } else if (shape instanceof Circle) {
                        queryGeoWithin.put(CENTER_OPERATOR, shape.asList())
                    } else if (shape instanceof Sphere) {
                        queryGeoWithin.put(CENTER_SPHERE_OPERATOR, shape.asList())
                    }
                } else if (value instanceof Map) {
                    queryGeoWithin.putAll((Map) value)
                }

                query.put(targetProperty, queryRoot)
            }
        })

        queryHandlers.put(GeoIntersects, new QueryHandler<GeoIntersects>() {
            void handle(EmbeddedQueryEncoder queryEncoder, GeoIntersects geoIntersects, Document query, PersistentEntity entity) {
                Document queryRoot = new Document()
                Document queryGeoWithin = new Document()
                queryRoot.put(GEO_INTERSECTS_OPERATOR, queryGeoWithin)
                String targetProperty = getPropertyName(entity, geoIntersects)
                Object value = geoIntersects.getValue()
                if (value instanceof GeoJSON) {
                    Shape shape = (Shape) value
                    Document geoJson = GeoJSONType.convertToGeoDocument(shape)
                    queryGeoWithin.put(GEOMETRY_OPERATOR, geoJson)
                } else if (value instanceof Map) {
                    queryGeoWithin.putAll((Map) value)
                }

                query.put(targetProperty, queryRoot)
            }
        })
        queryHandlers.put(Conjunction, new QueryHandler<Conjunction>() {
            void handle(EmbeddedQueryEncoder queryEncoder, Conjunction criterion, Document query, PersistentEntity entity) {
                populateMongoQuery(queryEncoder, query, criterion, entity)
            }
        })

        queryHandlers.put(Disjunction, new QueryHandler<Disjunction>() {
            void handle(EmbeddedQueryEncoder queryEncoder, Disjunction criterion, Document query, PersistentEntity entity) {
                populateMongoQuery(queryEncoder, query, criterion, entity)
            }
        })

        groupByProjectionHandlers.put(AvgProjection, new ProjectionHandler<AvgProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, AvgProjection projection) {
                return addProjectionToGroupBy(projectObject, groupBy, projection, AVERAGE_OPERATOR, 'avg_')
            }
        })
        groupByProjectionHandlers.put(CountProjection, new ProjectionHandler<CountProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, CountProjection projection) {
                projectObject.put(MongoEntityPersister.MONGO_ID_FIELD, 1)
                String projectionKey = 'count'
                groupBy.put(projectionKey, new Document(SUM_OPERATOR, 1))
                return projectionKey
            }
        })
        groupByProjectionHandlers.put(CountDistinctProjection, new ProjectionHandler<CountDistinctProjection>() {
            // equivalent of "select count (distinct fieldName) from someTable". Example:
            // db.someCollection.aggregate([{ $group: { _id: "$fieldName"}  },{ $group: { _id: 1, count: { $sum: 1 } } } ])
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, CountDistinctProjection projection) {
                projectObject.put(projection.getPropertyName(), 1)
                String property = projection.getPropertyName()
                String projectionValueKey = 'countDistinct_' + property
                Document id = getIdObjectForGroupBy(groupBy)
                id.put(projectionValueKey, '$' + property)
                return projectionValueKey
            }
        })

        groupByProjectionHandlers.put(MinProjection, new ProjectionHandler<MinProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, MinProjection projection) {
                return addProjectionToGroupBy(projectObject, groupBy, projection, MIN_OPERATOR, 'min_')
            }
        })
        groupByProjectionHandlers.put(MaxProjection, new ProjectionHandler<MaxProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, MaxProjection projection) {
                return addProjectionToGroupBy(projectObject, groupBy, projection, MAX_OPERATOR, 'max_')
            }
        })
        groupByProjectionHandlers.put(SumProjection, new ProjectionHandler<SumProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, SumProjection projection) {
                return addProjectionToGroupBy(projectObject, groupBy, projection, SUM_OPERATOR, 'sum_')
            }
        })

        projectProjectionHandlers.put(DistinctPropertyProjection, new ProjectionHandler<DistinctPropertyProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, DistinctPropertyProjection projection) {
                String property = projection.getPropertyName()
                projectObject.put(property, 1)
                Document id = getIdObjectForGroupBy(groupBy)
                String projectedValueKey = property.replace('.', '_')
                id.put(projectedValueKey, '$' + property)
                return projectedValueKey
            }
        })

        projectProjectionHandlers.put(PropertyProjection, new ProjectionHandler<PropertyProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, PropertyProjection projection) {
                String property = projection.getPropertyName()
                projectObject.put(property, 1)
                Document id = getIdObjectForGroupBy(groupBy)
                String projectedValueKey = property.replace('.', '_')
                id.put(projectedValueKey, '$' + property)
                // we add the id to the grouping to make it not distinct
                id.put(MongoEntityPersister.MONGO_ID_FIELD, '$' + MongoEntityPersister.MONGO_ID_FIELD)
                return projectedValueKey
            }
        })

        projectProjectionHandlers.put(IdProjection, new ProjectionHandler<IdProjection>() {
            @Override
            String handle(PersistentEntity entity, Document projectObject, Document groupBy, IdProjection projection) {
                projectObject.put(MongoEntityPersister.MONGO_ID_FIELD, 1)
                Document id = getIdObjectForGroupBy(groupBy)
                id.put(MongoEntityPersister.MONGO_ID_FIELD, '$_id')

                return MongoEntityPersister.MONGO_ID_FIELD
            }
        })
    }

    @PackageScope
    static Document getIdObjectForGroupBy(Document groupBy) {
        Object value = groupBy.get(MongoEntityPersister.MONGO_ID_FIELD)
        Document id
        if (value instanceof Document) {
            id = (Document) value
        } else {
            id = new Document()
            groupBy.put(MongoEntityPersister.MONGO_ID_FIELD, id)
        }
        return id
    }

    @PackageScope
    static String addProjectionToGroupBy(Document projectObject, Document groupBy, PropertyProjection projection, String operator, String prefix) {
        projectObject.put(projection.getPropertyName(), 1)
        String property = projection.getPropertyName()
        String projectionValueKey = prefix + property.replace('.', '_')
        Document averageProjection = new Document(operator, '$' + property)
        groupBy.put(projectionValueKey, averageProjection)
        return projectionValueKey
    }

    private final AbstractMongoSession mongoSession
    private final EntityPersister mongoEntityPersister
    private final ManualProjections manualProjections
    private boolean isCodecPersister = false

    MongoQuery(AbstractMongoSession session, PersistentEntity entity) {
        super(session, entity)
        this.mongoSession = session
        this.manualProjections = new ManualProjections(entity)
        if (session != null) {
            this.mongoEntityPersister = (EntityPersister) session.getPersister(entity)
            if (this.mongoEntityPersister instanceof MongoCodecEntityPersister) {
                this.isCodecPersister = true
            }
        } else {
            mongoEntityPersister = null
        }
    }

    @Override
    protected void flushBeforeQuery() {
        // with Mongo we only flush the session if a transaction is not active to allow for session-managed transactions
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            super.flushBeforeQuery()
        }
    }

    /**
     * Gets the Mongo query for this query instance
     *
     * @return The Mongo query
     */
    Document getMongoQuery() {
        Document query = createQueryObject(entity)
        populateMongoQuery((AbstractMongoSession) getSession(), query, criteria, entity)
        return query
    }

    @Override
    protected List executeQuery(final PersistentEntity entity, final Junction criteria) {
        final AbstractMongoSession mongoSession = this.mongoSession
        com.mongodb.client.MongoCollection<Document> collection = mongoSession.getCollection(entity)

        final List<Projection> projectionList = projections().getProjectionList()
        boolean hasOnlyDistinct = projectionList.size() == 1 && (projectionList.get(0) instanceof DistinctProjection)
        if (uniqueResult && (projectionList.isEmpty() || hasOnlyDistinct)) {
            if (isCodecPersister) {
                collection = (com.mongodb.client.MongoCollection<Document>) (com.mongodb.client.MongoCollection) collection
                        .withDocumentClass(entity.getJavaClass())
            }
            Object dbObject
            if (criteria.isEmpty()) {
                FindIterable<Document> cursor = mongoSession.find(collection, createQueryObject(entity))

                // Raw on purpose: with the codec persister the collection was re-typed to the entity
                // class above, so first() yields an entity, and a FindIterable<Document> here would
                // make the static compiler insert a cast to Document that fails at runtime.
                dbObject = ((FindIterable) setHint(cursor)).limit(1)
                        .first()
            } else {
                FindIterable<Document> cursor = mongoSession.find(collection, getMongoQuery())

                // Raw on purpose: with the codec persister the collection was re-typed to the entity
                // class above, so first() yields an entity, and a FindIterable<Document> here would
                // make the static compiler insert a cast to Document that fails at runtime.
                dbObject = ((FindIterable) setHint(cursor)).limit(1)
                        .first()
            }
            if (dbObject == null) {
                return wrapObjectResultInList(dbObject)
            }
            if (isCodecPersister) {
                if (!mongoSession.contains(dbObject)) {
                    final EntityAccess entityAccess = mongoSession.createEntityAccess(entity, dbObject)
                    mongoEntityPersister.firePostLoadEvent(entity, entityAccess)
                    mongoSession.cacheInstance(dbObject.getClass(), (Serializable) entityAccess.getIdentifier(), dbObject)
                }
                return wrapObjectResultInList(dbObject)
            }
            return wrapObjectResultInList(createObjectFromDBObject((Document) dbObject))
        }

        MongoCursor<Document> cursor
        Document query = createQueryObject(entity)

        if (projectionList.isEmpty() || hasOnlyDistinct) {
            if (isCodecPersister) {
                collection = (com.mongodb.client.MongoCollection<Document>) (com.mongodb.client.MongoCollection) collection
                        .withDocumentClass(entity.getJavaClass())
                        .withCodecRegistry(mongoSession.getDatastore().getCodecRegistry())
            }
            cursor = executeQuery(entity, criteria, collection, query)
            return new MongoResultList(cursor, offset, mongoEntityPersister)
        }

        populateMongoQuery((AbstractMongoSession) session, query, criteria, entity)
        AggregatePipeline aggregatePipeline = buildAggregatePipeline(entity, query, projectionList)
        List<Document> aggregationPipeline = aggregatePipeline.getAggregationPipeline()
        boolean singleResult = aggregatePipeline.isSingleResult()
        List<ProjectedProperty> projectedKeys = aggregatePipeline.getProjectedKeys()
        List projectedResults = new ArrayList()

        AggregateIterable<Document> aggregatedResults = mongoSession.aggregate(collection, aggregationPipeline)
        aggregatedResults = (AggregateIterable<Document>) setHint(aggregatedResults)
        final MongoCursor<Document> aggregateCursor = aggregatedResults.iterator()

        if (singleResult && aggregateCursor.hasNext()) {
            Document dbo = aggregateCursor.next()
            for (ProjectedProperty projectedProperty : projectedKeys) {
                Object value = dbo.get(projectedProperty.projectionKey)
                PersistentProperty property = projectedProperty.property
                if (value != null) {
                    if (property instanceof ToOne) {
                        projectedResults.add(session.retrieve(property.getType(), (Serializable) value))
                    } else {
                        projectedResults.add(value)
                    }
                } else {
                    if (projectedProperty.projection instanceof CountProjection) {
                        projectedResults.add(0)
                    }
                }
            }
        } else {
            return new AggregatedResultList((AbstractMongoSession) getSession(), aggregateCursor, projectedKeys)
        }

        return projectedResults
    }

    protected AggregatePipeline buildAggregatePipeline(PersistentEntity entity, Document query, List<Projection> projectionList) {
        return new AggregatePipeline(this, entity, query, projectionList).build()
    }

    protected MongoCursor<Document> executeQuery(final PersistentEntity entity,
                                                 final Junction criteria,
                                                 final com.mongodb.client.MongoCollection<Document> collection,
                                                 final Document query) {
        FindIterable<Document> cursor
        if (criteria.isEmpty()) {
            cursor = executeQueryAndApplyPagination(collection, query)
        } else {
            populateMongoQuery((AbstractMongoSession) session, query, criteria, entity)
            cursor = executeQueryAndApplyPagination(collection, query)
        }

        cursor = (FindIterable<Document>) setHint(cursor)

        return cursor.iterator()
    }

    private MongoIterable<Document> setHint(MongoIterable<Document> cursor) {
        MongoIterable<Document> result = cursor

        if (queryArguments != null) {
            if (queryArguments.containsKey(HINT_ARGUMENT)) {
                Object hint = queryArguments.get(HINT_ARGUMENT)
                if (hint instanceof Map) {
                    if (cursor instanceof FindIterable) {
                        result = ((FindIterable<Document>) cursor).hint(new Document((Map<String, Object>) hint))
                    } else if (cursor instanceof AggregateIterable) {
                        result = ((AggregateIterable<Document>) cursor).hint(new Document((Map<String, Object>) hint))
                    }
                } else {
                    if (cursor instanceof FindIterable) {
                        result = ((FindIterable<Document>) cursor).hintString(hint.toString())
                    } else if (cursor instanceof AggregateIterable) {
                        result = ((AggregateIterable<Document>) cursor).hintString(hint.toString())
                    }
                }
            }
        }

        return result
    }

    protected FindIterable<Document> executeQueryAndApplyPagination(com.mongodb.client.MongoCollection<Document> collection, Document query) {
        com.mongodb.client.MongoCollection<Document> target = collection
        Object readConcernObject = queryArguments.get(READ_CONCERN_ARGUMENT)
        if (readConcernObject instanceof ReadConcern) {
            target = target.withReadConcern(
                    (ReadConcern) readConcernObject
            )
        }

        final FindIterable<Document> iterable = mongoSession.find(target, query)
        if (offset != null && offset > 0) {
            iterable.skip(offset)
        }
        if (max != null && max > -1) {
            iterable.limit(max)
        }
        if (uniqueResult) {
            iterable.limit(1)
        }

        if (!orderBy.isEmpty()) {
            Document orderObject = new Document()
            for (Order order : orderBy) {
                String property = order.getProperty()
                property = getPropertyName(entity, property)
                orderObject.put(property, order.getDirection() == Order.Direction.DESC ? -1 : 1)
            }
            iterable.sort(orderObject)
        } else {
            MongoCollection coll = (MongoCollection) entity.getMapping().getMappedForm()
            if (coll != null && coll.getSort() != null) {
                Document orderObject = new Document()
                Order order = (Order) coll.getSort()
                String property = order.getProperty()
                property = getPropertyName(entity, property)
                orderObject.put(property, order.getDirection() == Order.Direction.DESC ? -1 : 1)
                iterable.sort(orderObject)
            }
        }

        return iterable
    }

    private Document getClassFieldDocument(final PersistentEntity entity) {
        Object classFieldValue
        Collection<PersistentEntity> childEntities = entity.getMappingContext().getChildEntities(entity)
        if (childEntities.size() > 0) {
            HashMap classValue = new HashMap()
            ArrayList classes = new ArrayList()
            classes.add(entity.getDiscriminator())
            for (PersistentEntity childEntity : childEntities) {
                classes.add(childEntity.getDiscriminator())
            }
            classValue.put(MONGO_IN_OPERATOR, classes)
            classFieldValue = classValue
        } else {
            classFieldValue = entity.getDiscriminator()
        }
        return new Document(MongoEntityPersister.MONGO_CLASS_FIELD, classFieldValue)
    }

    protected Document createQueryObject(PersistentEntity persistentEntity) {
        Document query
        if (persistentEntity.isRoot()) {
            query = new Document()
        } else {
            query = getClassFieldDocument(persistentEntity)
        }
        return query
    }

    static void populateMongoQuery(final AbstractMongoSession session, Document query, Junction criteria, final PersistentEntity entity) {
        EmbeddedQueryEncoder queryEncoder
        if (session instanceof MongoCodecSession) {
            final MongoDatastore datastore = session.getDatastore()
            final CodecRegistry codecRegistry = datastore.getCodecRegistry()
            queryEncoder = new EmbeddedQueryEncoder() {
                @Override
                Object encode(Embedded embedded, Object instance) {
                    final PersistentEntityCodec codec = (PersistentEntityCodec) codecRegistry.get(embedded.getType())
                    final BsonDocument doc = new BsonDocument()
                    codec.encode(new BsonDocumentWriter(doc), instance, ENCODER_CONTEXT, false)
                    return doc
                }
            }
        } else {
            queryEncoder = new EmbeddedQueryEncoder() {
                @Override
                Object encode(Embedded embedded, Object instance) {
                    MongoEntityPersister persister = (MongoEntityPersister) session.getPersister(entity.getJavaClass())
                    return persister.createNativeObjectForEmbedded(embedded, instance)
                }
            }
        }

        populateMongoQuery(queryEncoder, query, criteria, entity)
    }

    @SuppressWarnings('unchecked')
    static void populateMongoQuery(final EmbeddedQueryEncoder queryEncoder, Document query, Junction criteria, final PersistentEntity entity) {
        List subList = null
        // if a query combines more than 1 item, wrap the items in individual $and or $or arguments
        // so that property names can't clash (e.g. for an $and containing two $ors)
        if (criteria.getCriteria().size() > 1) {
            if (criteria instanceof Disjunction) {
                subList = new ArrayList()
                query.put(OR_OPERATOR, subList)
            } else if (criteria instanceof Conjunction) {
                subList = new ArrayList()
                query.put(AND_OPERATOR, subList)
            }
        }
        for (Criterion criterion : criteria.getCriteria()) {
            final QueryHandler queryHandler = queryHandlers.get(criterion.getClass())
            if (queryHandler != null) {
                Document dbo = query
                if (subList != null) {
                    dbo = new Document()
                    subList.add(dbo)
                }

                if (criterion instanceof PropertyCriterion && !(criterion instanceof GeoCriterion)) {
                    PropertyCriterion pc = (PropertyCriterion) criterion
                    PersistentProperty property = entity.getPropertyByName(pc.getProperty())
                    if (property instanceof Custom) {
                        CustomTypeMarshaller<Object, Object, Document> customTypeMarshaller = (CustomTypeMarshaller<Object, Object, Document>) ((Custom) property).getCustomTypeMarshaller()
                        if (!(customTypeMarshaller instanceof CodecCustomTypeMarshaller)) {
                            customTypeMarshaller.query(property, pc, query)
                            continue
                        }
                    }
                }
                queryHandler.handle(queryEncoder, criterion, dbo, entity)
            } else {
                throw new InvalidDataAccessResourceUsageException('Queries of type ' + criterion.getClass().getSimpleName() + ' are not supported by this implementation')
            }
        }
    }

    /**
     * Rewrites a query built against an embedded entity so it applies to the owning document,
     * qualifying each property name with the embedded property's path. Logical operators such as
     * {@code $and} and {@code $or} must stay at the current level (a key like {@code extRef1.$and}
     * matches nothing), so their nested documents are rewritten recursively instead.
     */
    @PackageScope
    static void prefixEmbeddedQuery(String prefix, Document source, Document target) {
        for (String key : source.keySet()) {
            Object value = source.get(key)
            if (key.charAt(0) == ('$' as char)) {
                if (!(value instanceof List)) {
                    // A top-level operator whose value is not a rewritable list of clauses
                    // (e.g. $where from a property-to-property comparison, or $text) cannot be
                    // qualified with the embedded path - prefixing it would silently match nothing.
                    throw new UnsupportedOperationException('Criterion [' + key +
                            '] is not supported inside an embedded association query')
                }
                List<Object> rewritten = new ArrayList<>()
                for (Object element : (List) value) {
                    if (element instanceof Document) {
                        Document rewrittenElement = new Document()
                        prefixEmbeddedQuery(prefix, (Document) element, rewrittenElement)
                        rewritten.add(rewrittenElement)
                    } else {
                        rewritten.add(element)
                    }
                }
                target.put(key, rewritten)
            } else {
                target.put(prefix + '.' + key, value)
            }
        }
    }

    private Object createObjectFromDBObject(Document dbObject) {
        // we always use the session cached version where available.
        final Object id = dbObject.get(MongoEntityPersister.MONGO_ID_FIELD)
        Class type = mongoEntityPersister.getPersistentEntity().getJavaClass()
        Object instance = mongoSession.getCachedInstance(type, (Serializable) id)
        if (instance == null) {
            instance = ((MongoEntityPersister) mongoEntityPersister).createObjectFromNativeEntry(
                    mongoEntityPersister.getPersistentEntity(), (Serializable) id, dbObject)
            mongoSession.cacheInstance(type, (Serializable) id, instance)
        }
        // note cached instances may be stale, but user can call 'refresh' to fix that.
        return instance
    }

    private List wrapObjectResultInList(Object object) {
        List result = new ArrayList()
        result.add(object)
        return result
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query near(String property, List value) {
        add(new Near(property, value))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query near(String property, Point value) {
        add(new Near(property, value))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query near(String property, List value, Distance maxDistance) {
        add(new Near(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query near(String property, Point value, Distance maxDistance) {
        add(new Near(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query near(String property, List value, Number maxDistance) {
        add(new Near(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query near(String property, Point value, Number maxDistance) {
        add(new Near(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query nearSphere(String property, List value) {
        add(new NearSphere(property, value))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query nearSphere(String property, Point value) {
        add(new NearSphere(property, value))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query nearSphere(String property, List value, Distance maxDistance) {
        add(new NearSphere(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query nearSphere(String property, Point value, Distance maxDistance) {
        add(new NearSphere(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query nearSphere(String property, List value, Number maxDistance) {
        add(new NearSphere(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values near the given two dimensional list
     *
     * @param property The property
     * @param value    A two dimensional list of values
     * @return this
     */
    Query nearSphere(String property, Point value, Number maxDistance) {
        add(new NearSphere(property, value, maxDistance))
        return this
    }

    /**
     * Geospacial query for values within a given box. A box is defined as a multi-dimensional list in the form
     * [[40.73083, -73.99756], [40.741404,  -73.988135]]
     *
     * @param property The property
     * @param value    A multi-dimensional list of values
     * @return This query
     */
    Query withinBox(String property, List value) {
        add(new WithinBox(property, value))
        return this
    }

    /**
     * Geospacial query for values within the given shape
     *
     * @param property The property
     * @param shape    The shape
     * @return The query instance
     */
    Query geoWithin(String property, Shape shape) {
        add(new GeoWithin(property, shape))
        return this
    }

    /**
     * Geospacial query for values within the given shape
     *
     * @param property The property
     * @param shape    The shape
     * @return The query instance
     */
    Query geoIntersects(String property, GeoJSON shape) {
        add(new GeoIntersects(property, shape))
        return this
    }

    /**
     * Geospacial query for values within a given polygon. A polygon is defined as a multi-dimensional list in the form
     * [[0, 0], [3, 6], [6, 0]]
     *
     * @param property The property
     * @param value    A multi-dimensional list of values
     * @return This query
     */
    Query withinPolygon(String property, List value) {
        add(new WithinPolygon(property, value))
        return this
    }

    /**
     * Geospacial query for values within a given circle. A circle is defined as a multi-dimensial list containing the position of the center and the radius:
     * [[50, 50], 10]
     *
     * @param property The property
     * @param value    A multi-dimensional list of values
     * @return This query
     */
    Query withinCircle(String property, List value) {
        add(new WithinBox(property, value))
        return this
    }

    /**
     * @param arguments The query arguments
     */
    void setArguments(Map arguments) {
        this.queryArguments = arguments
    }

    /**
     * Used for Geospacial querying
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    static class Near extends GeoCriterion {

        Distance maxDistance = null

        Near(String name, Object value) {
            super(name, value)
        }

        Near(String name, Object value, Distance maxDistance) {
            super(name, value)
            this.maxDistance = maxDistance
        }

        Near(String name, Object value, Number maxDistance) {
            super(name, value)
            this.maxDistance = Distance.valueOf(maxDistance.doubleValue())
        }

        void setMaxDistance(Distance maxDistance) {
            this.maxDistance = maxDistance
        }

    }

    /**
     * Used for Geospacial querying with the $nearSphere operator
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    static class NearSphere extends Near {

        NearSphere(String name, Object value) {
            super(name, value)
        }

        NearSphere(String name, Object value, Distance maxDistance) {
            super(name, value, maxDistance)
        }

        NearSphere(String name, Object value, Number maxDistance) {
            super(name, value, maxDistance)
        }

    }

    /**
     * Used for Geospacial querying of boxes
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    static class WithinBox extends PropertyCriterion {

        WithinBox(String name, List value) {
            super(name, value)
        }

        List getValues() {
            return (List) getValue()
        }

        void setValue(List matrix) {
            // Direct field access: through property syntax Groovy would route the assignment back
            // into this setter.
            this.@value = matrix
        }

    }

    /**
     * Used for Geospacial querying of polygons
     */
    static class WithinPolygon extends PropertyCriterion {

        WithinPolygon(String name, List value) {
            super(name, value)
        }

        List getValues() {
            return (List) getValue()
        }

        void setValue(List value) {
            // Direct field access: through property syntax Groovy would route the assignment back
            // into this setter.
            this.@value = value
        }

    }

    /**
     * Used for Geospacial querying of circles
     *
     * @author Graeme Rocher
     * @since 1.0
     */
    static class WithinCircle extends PropertyCriterion {

        WithinCircle(String name, List value) {
            super(name, value)
        }

        List getValues() {
            return (List) getValue()
        }

        void setValue(List matrix) {
            // Direct field access: through property syntax Groovy would route the assignment back
            // into this setter.
            this.@value = matrix
        }

    }

    /**
     * Used for all GeoSpacial queries using 2dsphere indexes
     */
    static class GeoCriterion extends PropertyCriterion {

        GeoCriterion(String name, Object value) {
            super(name, value)
        }

    }

    static class GeoWithin extends GeoCriterion {

        GeoWithin(String name, Object value) {
            super(name, value)
        }

    }

    static class GeoIntersects extends GeoCriterion {

        GeoIntersects(String name, Object value) {
            super(name, value)
        }

    }

    static class AggregatedResultList extends AbstractList implements Closeable {

        @PackageScope MongoCursor cursor
        private List<ProjectedProperty> projectedProperties
        private List initializedObjects = new ArrayList()
        private int internalIndex = 0
        @PackageScope boolean initialized = false
        private boolean containsAssociations = false
        private AbstractMongoSession session

        AggregatedResultList(AbstractMongoSession session, MongoCursor<Document> cursor, List<ProjectedProperty> projectedProperties) {
            this.cursor = cursor
            this.projectedProperties = projectedProperties
            this.session = session
            for (ProjectedProperty projectedProperty : projectedProperties) {
                if (projectedProperty.property instanceof Association) {
                    this.containsAssociations = true
                    break
                }
            }
        }

        @Override
        String toString() {
            return initializedObjects.toString()
        }

        @Override
        Object get(int index) {
            if (containsAssociations) {
                initializeFully()
            }
            if (initializedObjects.size() > index) {
                return initializedObjects.get(index)
            } else if (!initialized) {
                boolean hasResults = false
                while (cursor.hasNext()) {
                    hasResults = true
                    Document dbo = (Document) cursor.next()
                    Object projected = addInitializedObject(dbo)
                    if (index == internalIndex) {
                        return projected
                    }
                }
                if (!hasResults) {
                    handleNoResults()
                }
                initialized = true
            }
            throw new ArrayIndexOutOfBoundsException('Index value ' + index + ' exceeds size of aggregate list')
        }

        @Override
        Object set(int index, Object element) {
            initializeFully()
            return initializedObjects.set(index, element)
        }

        @Override
        ListIterator listIterator() {
            return listIterator(0)
        }

        @Override
        ListIterator listIterator(int index) {
            initializeFully()
            return initializedObjects.listIterator(index)
        }

        protected void initializeFully() {
            if (initialized) {
                return
            }
            if (containsAssociations) {
                if (projectedProperties.size() == 1) {
                    ProjectedProperty projectedProperty = projectedProperties.get(0)
                    PersistentProperty property = projectedProperty.property
                    List<Serializable> identifiers = new ArrayList<>()
                    boolean hasResults = false
                    while (cursor.hasNext()) {
                        hasResults = true
                        Document dbo = (Document) cursor.next()
                        Object id = getProjectedValue(dbo, projectedProperty.projectionKey)
                        identifiers.add((Serializable) id)
                    }
                    if (!hasResults) {
                        handleNoResults()
                    } else if (property instanceof Embedded) {
                        Embedded embedded = (Embedded) property
                        List embeddedList = new ArrayList()
                        CodecRegistry codecRegistry = session.getDatastore().getCodecRegistry()
                        PersistentEntityCodec codec = new PersistentEntityCodec(codecRegistry, embedded.getAssociatedEntity())

                        for (Serializable embeddedDoc : identifiers) {
                            if (embeddedDoc instanceof Document) {
                                Document documentObject = (Document) embeddedDoc

                                Object decoded = codec.decode(new BsonDocumentReader(documentObject.toBsonDocument(Document, codecRegistry)))
                                embeddedList.add(
                                        decoded
                                )
                            }
                        }

                        this.initializedObjects = embeddedList
                    } else {
                        this.initializedObjects = session.retrieveAll(property.getType(), identifiers)
                    }
                } else {
                    Map<Integer, Map<Class, List<Serializable>>> associationMap = createAssociationMap()

                    boolean hasResults = false
                    while (cursor.hasNext()) {
                        hasResults = true
                        Document dbo = (Document) cursor.next()
                        List<Object> projectedResult = new ArrayList<>()
                        int index = 0
                        for (ProjectedProperty projectedProperty : projectedProperties) {
                            PersistentProperty property = projectedProperty.property
                            Object value = getProjectedValue(dbo, projectedProperty.projectionKey)
                            if (property instanceof Association) {
                                if ((!(property instanceof Embedded) && !(property instanceof EmbeddedCollection) && !(property instanceof Basic))) {
                                    Map<Class, List<Serializable>> identifierMap = associationMap.get(index)
                                    Class type = ((Association) property).getAssociatedEntity().getJavaClass()
                                    identifierMap.get(type).add((Serializable) value)
                                }
                            }
                            projectedResult.add(value)
                            index++
                        }

                        initializedObjects.add(projectedResult)
                    }

                    if (!hasResults) {
                        handleNoResults()
                        return
                    }

                    Map<Integer, List> finalResults = new HashMap<>()
                    for (Integer index : associationMap.keySet()) {
                        Map<Class, List<Serializable>> associatedEntityIdentifiers = associationMap.get(index)
                        for (Class associationClass : associatedEntityIdentifiers.keySet()) {
                            List<Serializable> identifiers = associatedEntityIdentifiers.get(associationClass)
                            finalResults.put(index, session.retrieveAll(associationClass, identifiers))
                        }
                    }

                    for (Object initializedObject : initializedObjects) {
                        List projected = (List) initializedObject
                        for (Integer index : finalResults.keySet()) {
                            List resultsByIndex = finalResults.get(index)
                            if (index < resultsByIndex.size()) {
                                projected.set(index, resultsByIndex.get(index))
                            } else {
                                projected.set(index, null)
                            }
                        }
                    }
                }
            } else {
                boolean hasResults = false
                while (cursor.hasNext()) {
                    hasResults = true
                    Document dbo = (Document) cursor.next()
                    addInitializedObject(dbo)
                }
                if (!hasResults) {
                    handleNoResults()
                }
            }
            initialized = true
        }

        protected void handleNoResults() {
            ProjectedProperty projectedProperty = projectedProperties.get(0)
            if (projectedProperty.projection instanceof CountProjection) {
                initializedObjects.add(0)
            }
        }

        private Map<Integer, Map<Class, List<Serializable>>> createAssociationMap() {
            Map<Integer, Map<Class, List<Serializable>>> associationMap = new HashMap<>()
            associationMap = associationMap.withDefault { Object o ->
                Map<Class, List<Serializable>> subMap = new HashMap<>()
                subMap = subMap.withDefault { Object k ->
                    return new ArrayList<Serializable>()
                }
                return subMap
            }
            return associationMap
        }

        @Override
        Iterator iterator() {
            if (initialized || containsAssociations || internalIndex > 0) {
                initializeFully()
                return initializedObjects.iterator()
            }

            if (!cursor.hasNext()) {
                handleNoResults()
                return initializedObjects.iterator()
            }

            return new Iterator() {
                @Override
                boolean hasNext() {
                    boolean hasMore = cursor.hasNext()
                    if (!hasMore) {
                        initialized = true
                    }
                    return hasMore
                }

                @Override
                Object next() {
                    Document dbo = (Document) cursor.next()
                    return addInitializedObject(dbo)
                }

                @Override
                void remove() {
                    throw new UnsupportedOperationException('Aggregate result list cannot be mutated.')
                }
            }
        }

        @PackageScope
        Object addInitializedObject(Document dbo) {
            if (projectedProperties.size() > 1) {
                List<Object> projected = new ArrayList<>()
                for (ProjectedProperty projectedProperty : projectedProperties) {
                    Object value
                    value = getProjectedValue(dbo, projectedProperty.projectionKey)
                    projected.add(value)
                }
                initializedObjects.add(internalIndex, projected)
                internalIndex++
                return projected
            }
            ProjectedProperty projectedProperty = projectedProperties.get(0)
            Object projected = getProjectedValue(dbo, projectedProperty.projectionKey)
            initializedObjects.add(internalIndex, projected)
            internalIndex++
            return projected
        }

        private Object getProjectedValue(Document dbo, String projectionKey) {
            Object value
            if (projectionKey.startsWith('id.')) {
                String key = projectionKey.substring(3)
                Document id = (Document) dbo.get(MongoEntityPersister.MONGO_ID_FIELD)
                value = id.get(key)
            } else {
                value = dbo.get(projectionKey)
            }
            return value
        }

        @Override
        int size() {
            initializeFully()
            return initializedObjects.size()
        }

        @Override
        void close() throws IOException {
            cursor.close()
        }

    }

    @SuppressWarnings('serial')
    static class MongoResultList extends AbstractResultList {

        private EntityPersister mongoEntityPersister
        private MongoCursor cursor
        private boolean isCodecPersister

        MongoResultList(MongoCursor cursor, Integer offset, EntityPersister mongoEntityPersister) {
            super(offset == null ? 0 : offset, cursor)
            this.cursor = cursor
            this.mongoEntityPersister = mongoEntityPersister
            this.isCodecPersister = mongoEntityPersister instanceof MongoCodecEntityPersister
        }

        @Override
        void close() throws IOException {
            cursor.close()
        }

        @Override
        String toString() {
            initializeFully()
            return initializedObjects.toString()
        }

        /**
         * @return The underlying MongoDB cursor instance
         */
        MongoCursor getCursor() {
            return cursor
        }

        @Override
        protected Object nextDecoded() {
            final Object o = cursor.next()
            if (isCodecPersister) {
                final AbstractMongoSession session = (AbstractMongoSession) mongoEntityPersister.getSession()
                if (!session.contains(o)) {
                    final PersistentEntity entity = mongoEntityPersister.getPersistentEntity()
                    final EntityAccess entityAccess = session.createEntityAccess(entity, o)
                    final Object id = entityAccess.getIdentifier()
                    if (id != null) {
                        session.cacheInstance(entity.getJavaClass(), (Serializable) id, o)
                    }
                    mongoEntityPersister.firePostLoadEvent(entity, entityAccess)
                }
            }
            return o
        }

        @Override
        protected Object convertObject(Object object) {
            return isCodecPersister ? object : convertDBObject(object)
        }

        protected Object convertDBObject(Object object) {
            if (mongoEntityPersister instanceof MongoCodecEntityPersister) {
                return object
            }
            final Document dbObject = (Document) object
            Object id = dbObject.get(MongoEntityPersister.MONGO_ID_FIELD)
            SessionImplementor session = (SessionImplementor) mongoEntityPersister.getSession()
            Class type = mongoEntityPersister.getPersistentEntity().getJavaClass()
            Object instance = session.getCachedInstance(type, (Serializable) id)
            if (instance == null) {
                final MongoEntityPersister mep = (MongoEntityPersister) this.mongoEntityPersister
                instance = mep.createObjectFromNativeEntry(
                        this.mongoEntityPersister.getPersistentEntity(), (Serializable) id, dbObject)
                session.cacheInstance(type, (Serializable) id, instance)
            }
            return instance
        }

    }

    static class ProjectedProperty {

        public Projection projection
        public String projectionKey
        public PersistentProperty property

    }

    /**
     * Exposes the {@code protected static} handler maps inherited from {@link BsonQuery} to
     * {@link AggregatePipeline}, which cannot reach them directly: it does not itself extend
     * {@link BsonQuery}, so the cross-package protected access that {@link MongoQuery} has as a
     * subclass does not carry over to a nested class merely enclosed by it.
     */
    private static Map<Class, ProjectionHandler> resolveProjectProjectionHandlers() {
        projectProjectionHandlers
    }

    private static Map<Class, ProjectionHandler> resolveGroupByProjectionHandlers() {
        groupByProjectionHandlers
    }

    protected static class AggregatePipeline {

        private PersistentEntity entity
        private Document query
        private List<Projection> projectionList
        private List<Document> aggregationPipeline
        private List<ProjectedProperty> projectedKeys
        private boolean singleResult
        private final MongoQuery mongoQuery

        AggregatePipeline(MongoQuery mongoQuery, PersistentEntity entity, Document queryObject, List<Projection> projectionList) {
            this.mongoQuery = mongoQuery
            this.entity = entity
            this.query = queryObject
            this.projectionList = projectionList
        }

        List<Document> getAggregationPipeline() {
            return aggregationPipeline
        }

        List<ProjectedProperty> getProjectedKeys() {
            return projectedKeys
        }

        boolean isSingleResult() {
            return singleResult
        }

        AggregatePipeline build() {
            aggregationPipeline = new ArrayList<Document>()

            if (!query.keySet().isEmpty()) {
                aggregationPipeline.add(new Document(MATCH_OPERATOR, query))
            }

            projectedKeys = new ArrayList<ProjectedProperty>()
            singleResult = true

            Document projectObject = new Document()

            Document groupByObject = new Document()
            groupByObject.put(MongoEntityPersister.MONGO_ID_FIELD, 0)
            Document additionalGroupBy = null

            for (Projection projection : projectionList) {
                ProjectionHandler projectionHandler = resolveProjectProjectionHandlers().get(projection.getClass())
                ProjectedProperty projectedProperty = new ProjectedProperty()
                projectedProperty.projection = projection
                if (projection instanceof PropertyProjection) {
                    PropertyProjection propertyProjection = (PropertyProjection) projection
                    String propertyName = propertyProjection.getPropertyName()

                    PersistentProperty property = entity.getPropertyByName(propertyName)
                    if (property != null) {
                        projectedProperty.property = property
                    } else if (!propertyName.contains('.')) {
                        throw new InvalidDataAccessResourceUsageException('Attempt to project on a non-existent project [' + propertyName + ']')
                    }
                }
                if (projectionHandler != null) {
                    singleResult = false

                    String aggregationKey = projectionHandler.handle(entity, projectObject, groupByObject, projection)
                    aggregationKey = 'id.' + aggregationKey
                    projectedProperty.projectionKey = aggregationKey
                    projectedKeys.add(projectedProperty)
                } else {
                    projectionHandler = resolveGroupByProjectionHandlers().get(projection.getClass())
                    if (projectionHandler != null) {
                        projectedProperty.projectionKey = projectionHandler.handle(entity, projectObject, groupByObject, projection)
                        projectedKeys.add(projectedProperty)

                        if (projection instanceof CountDistinctProjection) {
                            Document finalCount = new Document(MongoEntityPersister.MONGO_ID_FIELD, 1)
                            finalCount.put(projectedProperty.projectionKey, new Document(SUM_OPERATOR, 1))
                            additionalGroupBy = new Document(GROUP_OPERATOR, finalCount)
                        }
                    }
                }
            }

            if (!projectObject.isEmpty()) {
                aggregationPipeline.add(new Document(PROJECT_OPERATOR, projectObject))
            }

            aggregationPipeline.add(new Document(GROUP_OPERATOR, groupByObject))

            if (additionalGroupBy != null) {
                aggregationPipeline.add(additionalGroupBy)
            }

            List<Order> orderBy = mongoQuery.getOrderBy()
            if (!orderBy.isEmpty()) {
                Document sortBy = new Document()
                for (Order order : orderBy) {
                    String prop = order.getProperty()
                    String sortKey = prop
                    for (ProjectedProperty pp : projectedKeys) {
                        if (pp.property != null && pp.property.getName() == prop) {
                            sortKey = pp.projectionKey
                            if (sortKey.startsWith('id.')) {
                                sortKey = MongoEntityPersister.MONGO_ID_FIELD + '.' + sortKey.substring(3)
                            }
                            break
                        }
                    }
                    sortBy.put(sortKey, order.getDirection() == Order.Direction.ASC ? 1 : -1)
                }
                aggregationPipeline.add(new Document(SORT_OPERATOR, sortBy))
            }

            int max = mongoQuery.getMax() != null ? mongoQuery.getMax() : -1
            if (max > 0) {
                aggregationPipeline.add(new Document('$limit', max))
            }
            int offset = mongoQuery.getOffset() != null ? mongoQuery.getOffset() : 0
            if (offset > 0) {
                aggregationPipeline.add(new Document('$skip', offset))
            }

            return this
        }

    }

}

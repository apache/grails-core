/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.neo4j.engine

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j
import org.grails.datastore.gorm.neo4j.*
import org.grails.datastore.gorm.neo4j.collection.Neo4jResultList
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.QueryException
import org.grails.datastore.mapping.reflect.EntityReflector
import org.neo4j.driver.Record
import org.neo4j.driver.Result
import org.neo4j.driver.QueryRunner
import org.neo4j.driver.Value
import org.neo4j.driver.types.Node

import javax.persistence.FetchType

/**
 * perform criteria queries on a Neo4j backend
 *
 * @author Stefan Armbruster <stefan@armbruster-it.de>
 * @author Graeme Rocher
 */
@CompileStatic
@Slf4j
class Neo4jQuery extends Query {

    private static final String ORDER_BY_CLAUSE = " ORDER BY "
    private static final String BLANK = ""

    final Neo4jEntityPersister neo4jEntityPersister
    final boolean isRelationshipEntity

    Neo4jQuery(Neo4jSession session, PersistentEntity entity, Neo4jEntityPersister neo4jEntityPersister) {
        super(session, entity)
        session.assertTransaction()
        this.neo4jEntityPersister = neo4jEntityPersister
        this.isRelationshipEntity = entity instanceof RelationshipPersistentEntity
    }

    private static Map<Class<? extends Query.PropertyComparisonCriterion>, String> COMPARISON_OPERATORS = [
            (Query.GreaterThanEqualsProperty): CriterionHandler.OPERATOR_GREATER_THAN_EQUALS,
            (Query.EqualsProperty): CriterionHandler.OPERATOR_EQUALS,
            (Query.NotEqualsProperty): CriterionHandler.OPERATOR_NOT_EQUALS,
            (Query.LessThanEqualsProperty): CriterionHandler.OPERATOR_LESS_THAN_EQUALS,
            (Query.LessThanProperty): CriterionHandler.OPERATOR_LESS_THAN,
            (Query.GreaterThanProperty): CriterionHandler.OPERATOR_GREATER_THAN
    ]

    protected static Map<Class<? extends Query.Projection>, ProjectionHandler<? extends Projection>> PROJECT_HANDLERS = [
            (Query.CountProjection): new ProjectionHandler<Query.CountProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.CountProjection projection, CypherBuilder builder) {
                    return ProjectionHandler.COUNT
                }
            },
            (Query.IdProjection): new ProjectionHandler<Query.IdProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.IdProjection projection, CypherBuilder builder) {
                    GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity

                    if(graphEntity.isRelationshipEntity()) {
                        return graphEntity.formatId(CypherBuilder.REL_VAR)
                    }
                    else {
                        return graphEntity.formatId(CypherBuilder.NODE_VAR)
                    }
                }
            },
            (Query.CountDistinctProjection): new ProjectionHandler<Query.CountDistinctProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.CountDistinctProjection projection, CypherBuilder builder) {
                    GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity
                    String var = graphEntity.formatProperty(graphEntity.variableName, projection.propertyName)
                    return "count( distinct ${var} )"
                }
            },
            (Query.MinProjection): new ProjectionHandler<Query.MinProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.MinProjection projection, CypherBuilder builder) {
                    GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity
                    String var = graphEntity.formatProperty(graphEntity.variableName, projection.propertyName)

                    return "min(${var})"
                }
            },
            (Query.MaxProjection): new ProjectionHandler<Query.MaxProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.MaxProjection projection, CypherBuilder builder) {
                    GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity
                    String var = graphEntity.formatProperty(graphEntity.variableName, projection.propertyName)

                    return "max(${var})"
                }
            },
            (Query.SumProjection): new ProjectionHandler<Query.SumProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.SumProjection projection, CypherBuilder builder) {
                    GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity
                    String var = graphEntity.formatProperty(graphEntity.variableName, projection.propertyName)
                    return "sum(${var})"
                }
            },
            (Query.AvgProjection): new ProjectionHandler<Query.AvgProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.AvgProjection projection, CypherBuilder builder) {
                    GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity
                    String var = graphEntity.formatProperty(graphEntity.variableName, projection.propertyName)
                    return "avg($var)"
                }
            },
            (Query.PropertyProjection): new ProjectionHandler<Query.PropertyProjection>() {
                @Override
                @CompileStatic
                String handle(PersistentEntity entity, Query.PropertyProjection projection, CypherBuilder builder) {
                    String propertyName = ((Query.PropertyProjection) projection).propertyName
                    PersistentProperty association = entity.getPropertyByName(propertyName)
                    GraphPersistentEntity graphEntity = (GraphPersistentEntity) entity
                    String var = graphEntity.variableName
                    if (association instanceof Association && !(association instanceof Basic)) {
                        if(entity instanceof RelationshipPersistentEntity) {
                            throw new QueryException("Cannot apply projection on property [$propertyName] of class [$entity.name]. Associations on relationships are not allowed")
                        }
                        def targetNodeName = "${association.name}_${builder.getNextMatchNumber()}"
                        builder.addMatch("(n)${RelationshipUtils.matchForAssociation((Association)association)}(${targetNodeName})")
                        return targetNodeName
                    } else {
                        return graphEntity.formatProperty(var, propertyName)
                    }
                }


            }
    ]

    public static Map<Class<? extends Query.Criterion>, CriterionHandler<? extends Criterion>> CRITERION_HANDLERS = [
            (Query.Conjunction): new CriterionHandler<Query.Conjunction>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.Conjunction criterion, CypherBuilder builder, String prefix) {
                    def inner = ((Query.Junction)criterion).criteria
                            .collect { Query.Criterion it ->
                                def handler = CRITERION_HANDLERS.get(it.getClass())
                                if(handler == null) {
                                    throw new UnsupportedOperationException("Criterion of type ${it.class.name} are not supported by GORM for Neo4j")
                                }
                                handler.handle(entity, it, builder, prefix).toString()
                            }
                            .join( CriterionHandler.OPERATOR_AND )
                    return new CypherExpression(inner ? "( $inner )" : inner)
                }
            },
            (Query.Disjunction): new CriterionHandler<Query.Disjunction>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.Disjunction criterion, CypherBuilder builder, String prefix) {
                    def inner = ((Query.Junction)criterion).criteria
                            .collect { Query.Criterion it ->
                        def handler = CRITERION_HANDLERS.get(it.getClass())
                        if(handler == null) {
                            throw new UnsupportedOperationException("Criterion of type ${it.class.name} are not supported by GORM for Neo4j")
                        }
                        handler.handle(entity, it, builder, prefix).toString()
                    }
                    .join( CriterionHandler.OPERATOR_OR )
                    return new CypherExpression(inner ? "( $inner )" : inner)
                }
            },
            (Query.Negation): new CriterionHandler<Query.Negation>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.Negation criterion, CypherBuilder builder, String prefix) {
                    List<Query.Criterion> criteria = criterion.criteria
                    def disjunction = new Query.Disjunction(criteria)
                    CriterionHandler<Query.Criterion> handler = { ->
                        CRITERION_HANDLERS.get(Query.Disjunction)
                    }.call()
                    new CypherExpression("NOT (${handler.handle(entity, disjunction, builder, prefix)})")
                }
            },
            (Query.Equals): new CriterionHandler<Query.Equals>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity graphEntity, Query.Equals criterion, CypherBuilder builder, String prefix) {
                    Neo4jMappingContext mappingContext = (Neo4jMappingContext)graphEntity.mappingContext
                    int paramNumber = builder.addParam( mappingContext.convertToNative(criterion.value) )
                    PersistentProperty association = graphEntity.getPropertyByName(criterion.property)
                    boolean isRelationshipEntity = graphEntity.isRelationshipEntity()

                    String lhs
                    if (association instanceof Association && !(association instanceof Basic)) {

                        if(isRelationshipEntity && RelationshipPersistentEntity.isRelationshipAssociation((Association)association)) {
                            lhs = graphEntity.formatId(association.name)
                        }
                        else {
                            String targetNodeName = "m_${builder.getNextMatchNumber()}"
                            builder.addMatch(
                                    graphEntity.formatAssociationPatternFromExisting((Association)association, "", prefix, targetNodeName)
                            )
                            lhs = graphEntity.formatId(targetNodeName)
                        }

                    } else {
                        if(isRelationshipEntity && criterion.property == RelationshipPersistentEntity.TYPE) {
                            builder.replaceFirstRelationshipMatch( ((RelationshipPersistentEntity)graphEntity).buildRelationshipMatchTo(null, CypherBuilder.REL_VAR))
                            lhs = "TYPE($CypherBuilder.REL_VAR)"
                        }
                        else {
                            lhs = graphEntity.formatProperty(prefix, criterion.property)
                        }
                    }

                    return new CypherExpression(lhs, "\$$paramNumber", CriterionHandler.OPERATOR_EQUALS)
                }

            },
            (Query.IdEquals): new CriterionHandler<Query.IdEquals>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.IdEquals criterion, CypherBuilder builder, String prefix) {
                    int paramNumber = addBuildParameterForCriterion(builder, entity, criterion)
                    return new CypherExpression(entity.formatId(prefix), "\$$paramNumber", CriterionHandler.OPERATOR_EQUALS)
                }
            },
            (Query.Like): new CriterionHandler<Query.Like>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.Like criterion, CypherBuilder builder, String prefix) {
                    int paramNumber = addBuildParameterForCriterion(builder, entity, criterion)
                    String operator = handleLike(criterion, builder, paramNumber, false)
                    return new CypherExpression(entity.formatProperty(prefix, criterion.property), "\$$paramNumber", operator)
                }
            },
            (Query.ILike): new CriterionHandler<Query.ILike>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.ILike criterion, CypherBuilder builder, String prefix) {
                    int paramNumber = addBuildParameterForCriterion(builder, entity, criterion)
                    String operator = handleLike(criterion, builder, paramNumber, true)
                    String propertyRef = entity.formatProperty(prefix, criterion.property)
                    String parameterRef = "\$$paramNumber"
                    if(operator != CriterionHandler.OPERATOR_LIKE) {
                        propertyRef = "lower($propertyRef)"
                        parameterRef = "lower($parameterRef)"
                    }
                    return new CypherExpression(propertyRef, parameterRef, operator)
                }
            },
            (Query.RLike): new CriterionHandler<Query.RLike>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.RLike criterion, CypherBuilder builder, String prefix) {
                    int paramNumber = addBuildParameterForCriterion(builder, entity, criterion)
                    return new CypherExpression(entity.formatProperty(prefix, criterion.property), "\$$paramNumber", CriterionHandler.OPERATOR_LIKE)
                }
            },
            (Query.In): new CriterionHandler<Query.In>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.In criterion, CypherBuilder builder, String prefix) {
                    int paramNumber = addBuildParameterForCriterion(builder, entity, criterion)
                    GraphPersistentEntity graphPersistentEntity = (GraphPersistentEntity)entity
                    String lhs
                    Collection values = ((Query.In) criterion).values
                    if(graphPersistentEntity.isRelationshipEntity()) {
                        PersistentProperty persistentProperty = entity.getPropertyByName(criterion.property)
                        if(RelationshipPersistentEntity.isRelationshipAssociation(persistentProperty)) {
                            GraphPersistentEntity associatedEntity = (GraphPersistentEntity)((Association) persistentProperty).associatedEntity
                            lhs = associatedEntity.formatId(criterion.property)
                            def associatedReflector = associatedEntity.reflector
                            values = values?.collect {
                                associatedReflector.getIdentifier(it)
                            }
                        }
                        else {
                            lhs = graphPersistentEntity.formatProperty(prefix, criterion.property)
                        }
                    }
                    else {
                        lhs = graphPersistentEntity.formatProperty(prefix, criterion.property)
                    }
                    builder.replaceParamAt(paramNumber, convertEnumsInList(values))
                    return new CypherExpression(lhs, "\$$paramNumber", CriterionHandler.OPERATOR_IN)
                }
            },
            (Query.IsNull): new CriterionHandler<Query.IsNull>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.IsNull criterion, CypherBuilder builder, String prefix) {
                    return new CypherExpression("${entity.formatProperty(prefix, criterion.property)} IS NULL")
                }
            },
            (Query.IsEmpty): new CriterionHandler<Query.IsEmpty>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.IsEmpty criterion, CypherBuilder builder, String prefix) {
                    return new CypherExpression("length(${entity.formatProperty(prefix, criterion.property)}) = 0")
                }
            },
            (Query.IsNotEmpty): new CriterionHandler<Query.IsNotEmpty>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.IsNotEmpty criterion, CypherBuilder builder, String prefix) {
                    return new CypherExpression("length(${entity.formatProperty(prefix, criterion.property)}) > 0")
                }
            },
            (Query.IsNotNull): new CriterionHandler<Query.IsNotNull>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.IsNotNull criterion, CypherBuilder builder, String prefix) {
                    return new CypherExpression("${entity.formatProperty(prefix, criterion.property)} IS NOT NULL")
                }
            },
            (AssociationQuery): new AssociationQueryHandler(),
            (Query.GreaterThan): ComparisonCriterionHandler.GREATER_THAN,
            (Query.GreaterThanEquals): ComparisonCriterionHandler.GREATER_THAN_EQUALS,
            (Query.LessThan): ComparisonCriterionHandler.LESS_THAN,
            (Query.LessThanEquals): ComparisonCriterionHandler.LESS_THAN_EQUALS,
            (Query.NotEquals): ComparisonCriterionHandler.NOT_EQUALS,

            (Query.GreaterThanProperty): PropertyComparisonCriterionHandler.GREATER_THAN,
            (Query.GreaterThanEqualsProperty): PropertyComparisonCriterionHandler.GREATER_THAN_EQUALS,
            (Query.LessThanProperty): PropertyComparisonCriterionHandler.LESS_THAN,
            (Query.LessThanEqualsProperty): PropertyComparisonCriterionHandler.LESS_THAN_EQUALS,
            (Query.NotEqualsProperty): PropertyComparisonCriterionHandler.NOT_EQUALS,
            (Query.EqualsProperty): PropertyComparisonCriterionHandler.EQUALS,

            (Query.Between): new CriterionHandler<Query.Between>() {
                @Override
                @CompileStatic
                CypherExpression handle(GraphPersistentEntity entity, Query.Between criterion, CypherBuilder builder, String prefix) {
                    int paramNumber = addBuildParameterForCriterion(builder, entity, criterion)
                    Neo4jMappingContext mappingContext = (Neo4jMappingContext)entity.mappingContext
                    int paramNumberFrom = builder.addParam( mappingContext.convertToNative(criterion.from) )
                    int parmaNumberTo = builder.addParam( mappingContext.convertToNative(criterion.to) )
                    new CypherExpression( "\$$paramNumberFrom<=${prefix}.$criterion.property and ${prefix}.$criterion.property<=\$$parmaNumberTo")
                }
            },
            (Query.SizeLessThanEquals): SizeCriterionHandler.LESS_THAN_EQUALS,
            (Query.SizeLessThan): SizeCriterionHandler.LESS_THAN,
            (Query.SizeEquals): SizeCriterionHandler.EQUALS,
            (Query.SizeNotEquals): SizeCriterionHandler.NOT_EQUALS,
            (Query.SizeGreaterThan): SizeCriterionHandler.GREATER_THAN,
            (Query.SizeGreaterThanEquals): SizeCriterionHandler.GREATER_THAN_EQUALS

    ]


    private String applyOrderAndLimits(CypherBuilder cypherBuilder) {
        StringBuilder cypher = new StringBuilder(BLANK)
        if (!orderBy.empty) {
            cypher << ORDER_BY_CLAUSE
            cypher << orderBy.collect { Query.Order order ->
                "${isRelationshipEntity ? RelationshipPersistentEntity.FROM : CypherBuilder.NODE_VAR}.${order.property} $order.direction"
            }.join(", ")
        }

        if (offset != 0) {
            int skipParam = cypherBuilder.addParam(offset)
            cypher << " SKIP \$$skipParam"
        }

        if (max != -1) {
            int limitParam = cypherBuilder.addParam(max)
            cypher << " LIMIT \$$limitParam"
        }
        cypher.toString()
    }

    @Override
    protected List executeQuery(PersistentEntity persistentEntity, Query.Junction criteria) {

        CypherBuilder cypherBuilder = buildBaseQuery(persistentEntity, criteria)
        cypherBuilder.setOrderAndLimits(applyOrderAndLimits(cypherBuilder))
        GraphPersistentEntity graphEntity = (GraphPersistentEntity)persistentEntity
        def projectionList = projections.projectionList

        if(projectionList.isEmpty()) {
             if(isRelationshipEntity) {
                 cypherBuilder.addReturnColumn(CypherBuilder.DEFAULT_REL_RETURN_STATEMENT)
             }
             else {
                 Set<Association> associations = new TreeSet<Association>((Comparator<Association>){ Association a1, Association a2 -> a1.name <=> a2.name })
                 Collection<PersistentEntity> childEntities = persistentEntity.mappingContext.getChildEntities(persistentEntity)
                 if (!childEntities.empty) {
                     for (PersistentEntity childEntity : childEntities) {
                         associations.addAll(childEntity.associations)
                     }
                 }
                 associations.addAll(persistentEntity.associations)

                 if(associations.size() > 0) {
                     int i = 0
                     List previousAssociations = []
                     cypherBuilder.addReturnColumn(CypherBuilder.DEFAULT_RETURN_TYPES)

                     for(Association association in associations) {
                         if(association.isBasic()) continue

                         FetchType fetchType = fetchStrategy(association.name)
                         boolean isEager = fetchType.is(fetchType.EAGER)

                         String r = "r${i++}"

                         String associationName = association.name
                         GraphPersistentEntity associatedGraphEntity = (GraphPersistentEntity)association.associatedEntity
                         boolean isAssociationRelationshipEntity = associatedGraphEntity.isRelationshipEntity()
                         boolean isToMany = association instanceof ToMany
                         boolean isToOne = association instanceof ToOne

                         boolean lazy  = false
                         boolean isNullable = false
                         if(isToOne && !isEager) {
                             Property propertyMapping = association.mapping.mappedForm
                             Boolean isLazy = propertyMapping.getLazy()
                             isNullable = propertyMapping.isNullable()
                             lazy = (isLazy != null ? isLazy : (association instanceof ManyToOne ? !association.isCircular() : true))

                         }
                         else if(isToMany) {
                             lazy = ((ToMany)association).lazy
                         }

                         // if there are associations, add a join to get them
                         String withMatch = "WITH n, ${previousAssociations.size() > 0 ? previousAssociations.join(", ") + ", " : ""}"
                         String associationIdsRef = "${associationName}Ids"
                         String associationNodeRef = "${associationName}Node"
                         String associationNodesRef = "${associationName}Nodes"

                         boolean addOptionalMatch = false
                         // If it is a one-to-many and lazy=true
                         // Or it is a one-to-one where the association is nullable or not lazy
                         // then just collect the identifiers and not the nodes
                         if((isToMany && lazy) || (isToOne && !isEager && (isNullable || !lazy ) )) {
                             withMatch += "collect(DISTINCT ${associatedGraphEntity.formatId(associationNodeRef)}) as ${associationIdsRef}"
                             cypherBuilder.addReturnColumn(associationIdsRef)
                             previousAssociations << associationIdsRef
                             addOptionalMatch = true
                         }
                         else if(isEager) {
                             withMatch += "collect(DISTINCT $associationNodeRef) as $associationNodesRef"
                             cypherBuilder.addReturnColumn(associationNodesRef)
                             if(isAssociationRelationshipEntity) {
                                 withMatch += ", collect($r) as ${associationName}Rels"
                                 cypherBuilder.addReturnColumn("${associationName}Rels")
                             }
                             previousAssociations << associationNodesRef
                             addOptionalMatch = true
                         }

                         if(addOptionalMatch) {

                             String relationshipPattern = graphEntity
                                     .formatAssociationPatternFromExisting(
                                     association,
                                     r,
                                     CypherBuilder.NODE_VAR,
                                     associationNodeRef
                             )
                             cypherBuilder.addOptionalMatch(
                                     "$relationshipPattern $withMatch"
                             )
                         }

                     }
                 }
             }
        }
        else {
            for (projection in projectionList) {
                cypherBuilder.addReturnColumn(buildProjection(projection, cypherBuilder))
            }
        }


        String cypher = cypherBuilder.build()
        Map<String, Object> params = cypherBuilder.getParams()

        log.debug("QUERY Cypher [$cypher] for params [$params]")

        QueryRunner statementRunner = session.hasTransaction() ? session.getTransaction().getTransaction() : boltSession
        Result executionResult = params.isEmpty() ? statementRunner.run(cypher) : statementRunner.run(cypher, params)
        if (projectionList.empty) {
            return new Neo4jResultList(offset, executionResult, neo4jEntityPersister, lockResult)
        } else {

            List projectedResults = []
            while( executionResult.hasNext() ) {

                Record record = executionResult.next()
                def columnNames = executionResult.keys()
                projectedResults.add columnNames.collect { String columnName ->
                    Value value = record.get(columnName)
                    if(value.type() == session.boltDriver.defaultTypeSystem().NODE()) {
                        // if a Node has been project then this is an association
                        def propName = columnName.substring(0, columnName.lastIndexOf('_'))
                        def prop = persistentEntity.getPropertyByName(propName)
                        if(prop instanceof ToOne) {
                            Association association = (Association)prop
                            Node childNode = value.asNode()

                            def persister = getSession().getEntityPersister(association.type)

                            def data = Collections.<String,Object>singletonMap( CypherBuilder.NODE_DATA, childNode)
                            return persister.unmarshallOrFromCache(
                                    association.associatedEntity, data)
                        }
                    }
                    return value.asObject()
                }
            }

            if(projectionList.size() == 1 || projectedResults.size() == 1) {
                return projectedResults.flatten()
            }
            else {
                return projectedResults
            }
        }
    }

    /**
     * Obtains the root query for this Neo4jQuery instance without any RETURN statements, projections or limits applied
     *
     * @return The base query containing only the conditions
     */
    CypherBuilder getBaseQuery() {
        buildBaseQuery(entity, criteria)
    }

    protected CypherBuilder buildBaseQuery(PersistentEntity persistentEntity, Query.Junction criteria) {
        GraphPersistentEntity graphEntity = (GraphPersistentEntity) persistentEntity

        CypherBuilder cypherBuilder
        if(isRelationshipEntity) {
            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity)graphEntity
            GraphPersistentEntity fromEntity = relEntity.getFromEntity()
            cypherBuilder = new CypherBuilder(fromEntity.labelsAsString)
            cypherBuilder.setStartNode(RelationshipPersistentEntity.FROM)
            cypherBuilder.addRelationshipMatch(relEntity.buildToMatch())
        }
        else {
            cypherBuilder = new CypherBuilder(graphEntity.labelsAsString)
        }

        def conditions = buildConditions(criteria, cypherBuilder, isRelationshipEntity ? CypherBuilder.REL_VAR : CypherBuilder.NODE_VAR)
        cypherBuilder.setConditions(conditions)
        cypherBuilder
    }


    String buildProjection(Query.Projection projection, CypherBuilder cypherBuilder) {
        def handler = PROJECT_HANDLERS.get(projection.getClass())
        if(handler != null) {
            return handler.handle(entity, projection, cypherBuilder)
        }
        else {
            throw new UnsupportedOperationException("projection ${projection.class} not supported by GORM for Neo4j")
        }
    }

    String buildConditions(Query.Criterion criterion, CypherBuilder builder, String prefix) {
        def handler = CRITERION_HANDLERS.get(criterion.getClass())
        if(handler != null) {
            return handler.handle((GraphPersistentEntity)entity, criterion, builder, prefix).toString()
        }
        else {
            throw new UnsupportedOperationException("Criterion of type ${criterion.class.name} are not supported by GORM for Neo4j")
        }
    }

    private static Collection convertEnumsInList(Collection collection) {
        collection.collect {
            it.getClass().isEnum() ? it.toString() : it
        }
    }

    @Deprecated
    static String matchForAssociation(Association association, String var = "", Map<String, String> attributes = Collections.emptyMap()) {
        RelationshipUtils.matchForAssociation(association, var, attributes)
    }

    private static int addBuildParameterForCriterion(CypherBuilder builder, PersistentEntity entity, Query.PropertyCriterion criterion) {
        Neo4jMappingContext mappingContext = (Neo4jMappingContext)entity.mappingContext
        return builder.addParam( mappingContext.convertToNative(criterion.value) )
    }

    @Override
    Neo4jSession getSession() {
        return (Neo4jSession)super.getSession()
    }

    org.neo4j.driver.Session getBoltSession() {
        return (org.neo4j.driver.Session)getSession().getNativeInterface()
    }


    protected static String handleLike(Query.PropertyCriterion criterion, CypherBuilder builder, int paramNumber, boolean caseSensitive) {
        String value = criterion.value?.toString()
        String operator
        if (value) {
            int length = value.length()
            boolean left = value.charAt(0) == CriterionHandler.PATTERN_CHAR
            boolean right = value.charAt(length - 1) == CriterionHandler.PATTERN_CHAR
            if (left && right) {
                operator = CriterionHandler.OPERATOR_CONTAINS
                builder.replaceParamAt(paramNumber, value.substring(1, length - 1))
            } else if (right) {
                operator = CriterionHandler.OPERATOR_STARTS_WITH
                builder.replaceParamAt(paramNumber, value.substring(0, length - 1))
            } else if (left) {
                operator = CriterionHandler.OPERATOR_ENDS_WITH
                builder.replaceParamAt(paramNumber, value.substring(1, length))
            } else if(value.indexOf((int)CriterionHandler.PATTERN_CHAR) > -1) {
                operator = CriterionHandler.OPERATOR_LIKE
                String pattern = Query.patternToRegex(value)
                if(caseSensitive) {
                    builder.replaceParamAt(paramNumber, "(?i)${pattern}".toString())
                }
                else {
                    builder.replaceParamAt(paramNumber, pattern)
                }

            }
            else {
                operator = CriterionHandler.OPERATOR_EQUALS
            }
        } else {
            operator = CriterionHandler.OPERATOR_EQUALS
        }
        return operator
    }
    /**
     * Interface for handling projections when building Cypher queries
     *
     * @param < T > The projection type
     */
    static interface ProjectionHandler<T extends Query.Projection> {
        String COUNT = "count(*)"
        String handle(PersistentEntity entity, T projection, CypherBuilder builder)
    }

    /**
     * Interface for handling criterion when building Cypher queries
     *
     * @param < T > The criterion type
     */
    static interface CriterionHandler<T extends Query.Criterion> {
        char PATTERN_CHAR = '%'
        String COUNT = "count"
        String OPERATOR_EQUALS = '='
        String OPERATOR_NOT_EQUALS = '<>'
        String OPERATOR_LIKE = "=~"
        String OPERATOR_CONTAINS = " CONTAINS "
        String OPERATOR_STARTS_WITH = " STARTS WITH "
        String OPERATOR_ENDS_WITH = " ENDS WITH "
        String OPERATOR_IN = " IN "
        String OPERATOR_AND = " AND "
        String OPERATOR_OR = " OR "
        String OPERATOR_GREATER_THAN = ">"
        String OPERATOR_LESS_THAN = "<"
        String OPERATOR_GREATER_THAN_EQUALS = ">="
        String OPERATOR_LESS_THAN_EQUALS = "<="

        CypherExpression handle(GraphPersistentEntity entity, T criterion, CypherBuilder builder, String prefix)
    }

    /**
     * Handles AssociationQuery instances
     */
    @CompileStatic
    static class AssociationQueryHandler implements CriterionHandler<AssociationQuery> {
        @Override
        CypherExpression handle(GraphPersistentEntity entity, AssociationQuery criterion, CypherBuilder builder, String prefix) {
            AssociationQuery aq = (AssociationQuery)criterion
            if(entity.isRelationshipEntity()) {
                def s = CRITERION_HANDLERS.get(aq.criteria.getClass()).handle(entity, aq.criteria, builder, aq.association.name)
                return new CypherExpression(s)
            }
            else {
                String targetNodeName = "m_${builder.getNextMatchNumber()}"
                String nextPrefix = targetNodeName
                String relationship = ""
                Association association = (Association)aq.association
                if (criterion.entity instanceof RelationshipPersistentEntity) {
                    String type = (criterion.entity as RelationshipPersistentEntity).type()
                    relationship = "$nextPrefix:$type"
                    targetNodeName = "m_${builder.getNextMatchNumber() + 1}"
                }
                builder.addMatch(
                    entity.formatAssociationPatternFromExisting(association, relationship, prefix, targetNodeName)
                )
                def s = CRITERION_HANDLERS.get(aq.criteria.getClass()).handle(entity, aq.criteria, builder, nextPrefix)
                return new CypherExpression(s)
            }

        }
    }

    /**
     * A criterion handler for comparison criterion
     *
     * @param < T >
     */
    @CompileStatic
    static class ComparisonCriterionHandler<T extends Query.PropertyCriterion> implements CriterionHandler<T> {
        public static final ComparisonCriterionHandler<Query.GreaterThanEquals> GREATER_THAN_EQUALS = new ComparisonCriterionHandler<Query.GreaterThanEquals>(CriterionHandler.OPERATOR_GREATER_THAN_EQUALS)
        public static final ComparisonCriterionHandler<Query.GreaterThan> GREATER_THAN = new ComparisonCriterionHandler<Query.GreaterThan>(CriterionHandler.OPERATOR_GREATER_THAN)
        public static final ComparisonCriterionHandler<Query.LessThan> LESS_THAN = new ComparisonCriterionHandler<Query.LessThan>(CriterionHandler.OPERATOR_LESS_THAN)
        public static final ComparisonCriterionHandler<Query.LessThanEquals> LESS_THAN_EQUALS = new ComparisonCriterionHandler<Query.LessThanEquals>(CriterionHandler.OPERATOR_LESS_THAN_EQUALS)
        public static final ComparisonCriterionHandler<Query.NotEquals> NOT_EQUALS = new ComparisonCriterionHandler<Query.NotEquals>(CriterionHandler.OPERATOR_NOT_EQUALS)
        public static final ComparisonCriterionHandler<Query.Equals> EQUALS = new ComparisonCriterionHandler<Query.Equals>(CriterionHandler.OPERATOR_EQUALS)

        final String operator

        ComparisonCriterionHandler(String operator) {
            this.operator = operator
        }

        @Override
        CypherExpression handle(GraphPersistentEntity graphEntity, T criterion, CypherBuilder builder, String prefix) {
            int paramNumber = addBuildParameterForCriterion(builder, graphEntity, criterion)
            String lhs
            PersistentProperty association = graphEntity.getPropertyByName(criterion.property)
            if (association instanceof Association && !(association instanceof Basic)) {
                GraphPersistentEntity associatedGraphEntity = (GraphPersistentEntity) ((Association) association).associatedEntity
                if(graphEntity.isRelationshipEntity() && RelationshipPersistentEntity.isRelationshipAssociation((Association)association)) {
                    lhs = associatedGraphEntity.formatId(association.name)
                }
                else {
                    String targetNodeName = "m_${builder.getNextMatchNumber()}"
                    builder.addMatch(
                            graphEntity.formatAssociationPatternFromExisting((Association)association, "", prefix, targetNodeName)
                    )

                    lhs = graphEntity.formatId(targetNodeName)
                }
            }
            else {
                if(graphEntity.isRelationshipEntity() && criterion.property == RelationshipPersistentEntity.TYPE) {
                    builder.replaceFirstRelationshipMatch( ((RelationshipPersistentEntity)graphEntity).buildRelationshipMatchTo(null, CypherBuilder.REL_VAR))
                    lhs = "TYPE(r)"
                }
                else {
                    lhs = graphEntity.formatProperty(prefix, criterion.property)
                }

            }
            return new CypherExpression(lhs, "\$$paramNumber", operator)
        }
    }


    /**
     * A criterion handler for comparison criterion
     *
     * @param < T >
     */
    @CompileStatic
    static class PropertyComparisonCriterionHandler<T extends Query.PropertyComparisonCriterion> implements CriterionHandler<T> {
        public static final PropertyComparisonCriterionHandler<Query.GreaterThanEqualsProperty> GREATER_THAN_EQUALS = new PropertyComparisonCriterionHandler<Query.GreaterThanEqualsProperty>(CriterionHandler.OPERATOR_GREATER_THAN_EQUALS)
        public static final PropertyComparisonCriterionHandler<Query.GreaterThanProperty> GREATER_THAN = new PropertyComparisonCriterionHandler<Query.GreaterThanProperty>(CriterionHandler.OPERATOR_GREATER_THAN)
        public static final PropertyComparisonCriterionHandler<Query.LessThanProperty> LESS_THAN = new PropertyComparisonCriterionHandler<Query.LessThanProperty>(CriterionHandler.OPERATOR_LESS_THAN)
        public static final PropertyComparisonCriterionHandler<Query.LessThanEqualsProperty> LESS_THAN_EQUALS = new PropertyComparisonCriterionHandler<Query.LessThanEqualsProperty>(CriterionHandler.OPERATOR_LESS_THAN_EQUALS)
        public static final PropertyComparisonCriterionHandler<Query.NotEqualsProperty> NOT_EQUALS = new PropertyComparisonCriterionHandler<Query.NotEqualsProperty>(CriterionHandler.OPERATOR_NOT_EQUALS)
        public static final PropertyComparisonCriterionHandler<Query.EqualsProperty> EQUALS = new PropertyComparisonCriterionHandler<Query.EqualsProperty>(CriterionHandler.OPERATOR_EQUALS)

        final String operator

        PropertyComparisonCriterionHandler(String operator) {
            this.operator = operator
        }

        @Override
        CypherExpression handle(GraphPersistentEntity entity, T criterion, CypherBuilder builder, String prefix) {
            def operator = COMPARISON_OPERATORS.get(criterion.getClass())
            if(operator == null) {
                throw new UnsupportedOperationException("Unsupported Neo4j property comparison: ${criterion}")
            }
            return new CypherExpression("$prefix.${criterion.property}${operator}n.${criterion.otherProperty}")
        }
    }
    /**
     * A citerion handler for size related queries
     *
     * @param < T >
     */
    @CompileStatic
    static class SizeCriterionHandler<T extends Query.PropertyCriterion> implements CriterionHandler<T> {

        public static final SizeCriterionHandler<Query.SizeEquals> EQUALS = new SizeCriterionHandler<Query.SizeEquals>(CriterionHandler.OPERATOR_EQUALS)
        public static final SizeCriterionHandler<Query.SizeNotEquals> NOT_EQUALS = new SizeCriterionHandler<Query.SizeNotEquals>(CriterionHandler.OPERATOR_NOT_EQUALS)
        public static final SizeCriterionHandler<Query.SizeGreaterThan> GREATER_THAN= new SizeCriterionHandler<Query.SizeGreaterThan>(CriterionHandler.OPERATOR_GREATER_THAN)
        public static final SizeCriterionHandler<Query.SizeGreaterThanEquals> GREATER_THAN_EQUALS = new SizeCriterionHandler<Query.SizeGreaterThanEquals>(CriterionHandler.OPERATOR_GREATER_THAN_EQUALS)
        public static final SizeCriterionHandler<Query.SizeLessThan> LESS_THAN = new SizeCriterionHandler<Query.SizeLessThan>(CriterionHandler.OPERATOR_LESS_THAN)
        public static final SizeCriterionHandler<Query.SizeLessThanEquals> LESS_THAN_EQUALS = new SizeCriterionHandler<Query.SizeLessThanEquals>(CriterionHandler.OPERATOR_LESS_THAN_EQUALS)

        final String operator

        SizeCriterionHandler(String operator) {
            this.operator = operator
        }

        @Override
        CypherExpression handle(GraphPersistentEntity entity, T criterion, CypherBuilder builder, String prefix) {
            int paramNumber = addBuildParameterForCriterion(builder, entity, criterion)
            Association association = entity.getPropertyByName(criterion.property) as Association
            builder.addMatch("(${prefix})${RelationshipUtils.matchForAssociation(association)}() WITH ${prefix},count(*) as count")
            return new CypherExpression(CriterionHandler.COUNT, "\$$paramNumber", operator)
        }
    }

    @CompileStatic
    @EqualsAndHashCode
    static class CypherExpression {

        @Delegate final CharSequence expression

        CypherExpression(String lhs, String rhs, String operator) {
            this.expression = "$lhs$operator$rhs".toString()
        }

        CypherExpression(CharSequence expression) {
            this.expression = expression
        }

        @Override
        String toString() {
            this.expression
        }
    }
}



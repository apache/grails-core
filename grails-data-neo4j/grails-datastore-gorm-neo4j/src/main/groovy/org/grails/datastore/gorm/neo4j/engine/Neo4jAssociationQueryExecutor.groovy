/*
 * Copyright 2015 original authors
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
import groovy.util.logging.Slf4j
import org.grails.datastore.gorm.neo4j.CypherBuilder
import org.grails.datastore.gorm.neo4j.GraphPersistentEntity
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.gorm.neo4j.RelationshipPersistentEntity
import org.grails.datastore.gorm.neo4j.RelationshipUtils
import org.grails.datastore.gorm.neo4j.collection.Neo4jResultList
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.engine.AssociationQueryExecutor
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.neo4j.driver.Session
import org.neo4j.driver.Result
import org.neo4j.driver.QueryRunner

import javax.persistence.FetchType


/**
 * Responsible for lazy loading associations
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
@Slf4j
class Neo4jAssociationQueryExecutor implements AssociationQueryExecutor<Serializable, Object> {

    final Neo4jSession session
    final PersistentEntity indexedEntity
    final Association association
    final boolean lazy
    final boolean singleResult

    Neo4jAssociationQueryExecutor(Neo4jSession session, Association association, boolean lazy = (association.mapping.mappedForm as Property).isLazy(), boolean singleResult = false) {
        this.session = session
        PersistentEntity associatedEntity = association.associatedEntity
        if(associatedEntity != null) {
            this.indexedEntity = associatedEntity
        }
        else if(association instanceof Basic) {
            this.indexedEntity = session.mappingContext.getPersistentEntity(((Basic)association).componentType.name)
        }
        if(this.indexedEntity == null) {
            throw new IllegalStateException("Cannot create association query loader for ${association}. No mapped associated entity could be located.")
        }
        this.association = association
        this.lazy = lazy
        this.singleResult = singleResult
    }

    @Override
    boolean doesReturnKeys() {
        return false
    }

    @Override
    List<Object> query(Serializable primaryKey) {

        QueryRunner statementRunner = session.hasTransaction() ? session.getTransaction().getNativeTransaction() : (Session)session.nativeInterface
        String relType

        GraphPersistentEntity parent = (GraphPersistentEntity)association.owner
        GraphPersistentEntity related = (GraphPersistentEntity)indexedEntity

        boolean isRelationship = related.isRelationshipEntity()

        if(isRelationship) {
            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity)related
            GraphPersistentEntity fromEntity = (GraphPersistentEntity) relEntity.getFrom().getAssociatedEntity()
            GraphPersistentEntity toEntity = (GraphPersistentEntity) relEntity.getTo().getAssociatedEntity()
            if(parent == fromEntity) {
                relType = "-[rel]->"
                related = toEntity
            }
            else {
                relType = "<-[rel]-"
                parent = toEntity
                related = fromEntity
            }
        }
        else {
            relType = RelationshipUtils.matchForAssociation(association)
        }

        String relationship = CypherBuilder.buildRelationship(parent.labelsAsString, relType, related.labelsAsString)

        StringBuilder cypher = new StringBuilder(CypherBuilder.buildRelationshipMatch(parent.labelsAsString, relType, related.labelsAsString))
        cypher.append('( ')
              .append(parent.formatId(RelationshipPersistentEntity.FROM))
              .append(" = \$id )")

        boolean isLazyToMany = lazy && !isRelationship && association instanceof ToMany
        if(isLazyToMany) {
            cypher.append(related.formatId(RelationshipPersistentEntity.TO))
                  .append("RETURN as id")
        }
        else {
            if(!isRelationship) {

                StringBuilder returnString = new StringBuilder("\nRETURN to as data")

                Set<Association> associations = new TreeSet<Association>((Comparator<Association>){ Association a1, Association a2 -> a1.name <=> a2.name })
                PersistentEntity entity = association.associatedEntity
                if (entity) {
                    Collection<PersistentEntity> childEntities = entity.mappingContext.getChildEntities(entity)
                    if (!childEntities.empty) {
                        for (PersistentEntity childEntity : childEntities) {
                            associations.addAll(childEntity.associations)
                        }
                    }
                    associations.addAll(entity.associations)

                    if(associations.size() > 0) {
                        int i = 0
                        List previousAssociations = []

                        for(Association association in associations) {
                            if(association.isBasic()) continue

                            boolean isEager = ((Property) association.mapping.mappedForm).isLazy()

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
                            String withMatch = "WITH to, ${previousAssociations.size() > 0 ? previousAssociations.join(", ") + ", " : ""}"
                            String associationIdsRef = "${associationName}Ids"
                            String associationNodeRef = "${associationName}Node"
                            String associationNodesRef = "${associationName}Nodes"

                            boolean addOptionalMatch = false
                            // If it is a one-to-many and lazy=true
                            // Or it is a one-to-one where the association is nullable or not lazy
                            // then just collect the identifiers and not the nodes
                            if((isToMany && lazy) || (isToOne && !isEager && (isNullable || !lazy ) )) {
                                withMatch += "collect(DISTINCT ${associatedGraphEntity.formatId(associationNodeRef)}) as ${associationIdsRef}"
                                returnString.append(", ").append(associationIdsRef)
                                previousAssociations << associationIdsRef
                                addOptionalMatch = true
                            }
                            else if(isEager) {
                                withMatch += "collect(DISTINCT $associationNodeRef) as $associationNodesRef"
                                returnString.append(", ").append(associationNodesRef)
                                if(isAssociationRelationshipEntity) {
                                    withMatch += ", collect($r) as ${associationName}Rels"
                                    returnString.append(", ").append("${associationName}Rels")
                                }
                                previousAssociations << associationNodesRef
                                addOptionalMatch = true
                            }

                            if(addOptionalMatch) {

                                String relationshipPattern = related
                                        .formatAssociationPatternFromExisting(
                                        association,
                                        r,
                                        "to",
                                        associationNodeRef
                                )

                                cypher.append(CypherBuilder.NEW_LINE)
                                        .append(CypherBuilder.OPTIONAL_MATCH)
                                        .append(relationshipPattern)
                                        .append(" ")
                                        .append(withMatch)
                            }

                        }
                    }
                }

                cypher.append(returnString.toString())
            }
            else {
                cypher.append('RETURN rel as rel')
            }
        }
        cypher.append(singleResult ? 'LIMIT 1' : '')


        Map<String, Object> params = Collections.singletonMap(GormProperties.IDENTITY, (Object) primaryKey)

        log.debug("Lazy loading association [${association}] using relationship $relationship")
        log.debug("QUERY Cypher [$cypher] for params [$params]")

        Result result = statementRunner.run(cypher.toString(), params)
        if(isLazyToMany) {
            List<Object> results = []
            while(result.hasNext()) {
                def id = result.next().get(GormProperties.IDENTITY).asObject()
                results.add( session.proxy(related.javaClass, id as Serializable) )
            }
            return results
        }
        else {
            def resultList = new Neo4jResultList(0, result, isRelationship ? session.getEntityPersister(indexedEntity) : session.getEntityPersister(related))
            if(association.isBidirectional()) {
                def inverseSide = association.inverseSide
                if(inverseSide instanceof ToOne) {
                    def parentObject = session.getCachedInstance(association.getOwner().getJavaClass(), primaryKey)
                    if(parentObject != null) {
                        resultList.setInitializedAssociations(Collections.<Association,Object>singletonMap(inverseSide, parentObject))
                    }
                }
            }
            return resultList
        }
    }

}

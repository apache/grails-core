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
package org.grails.datastore.gorm.neo4j.engine

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.neo4j.CypherBuilder
import org.grails.datastore.gorm.neo4j.GraphPersistentEntity
import org.grails.datastore.gorm.neo4j.RelationshipPersistentEntity
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.query.AssociationQuery
import org.grails.datastore.mapping.query.Query

/**
 * What a criterion of a Neo4j query is about: the entity whose properties it names, the Cypher variable that
 * entity is matched to, and the query being built. Every decision about what a property name refers to, in the
 * root of a query or in the block of an association, is made here, so a criterion handler only says what it
 * compares and never works out where the property is.
 *
 * <p>A scope is for a node, or for the relationship of an association to a relationship entity, whose properties
 * are the relationship's own and which has no relationships to test.</p>
 *
 * @since 8.0.1
 */
@CompileStatic
final class CriterionScope {

    final GraphPersistentEntity entity
    final CypherBuilder builder
    final String variable

    CriterionScope(GraphPersistentEntity entity, CypherBuilder builder, String variable) {
        this.entity = entity
        this.builder = builder
        this.variable = variable
    }

    /**
     * The same entity, matched to another variable.
     */
    CriterionScope atVariable(String otherVariable) {
        new CriterionScope(entity, builder, otherVariable)
    }

    /**
     * Whether the variable is that of a relationship, as it is in a block on an association to a relationship
     * entity.
     */
    boolean isRelationship() {
        entity.isRelationshipEntity() || builder.isRelationshipVariable(variable)
    }

    /**
     * The scope a criterion is evaluated in. On the variable of a relationship, which a block on an association
     * to a relationship entity declares, the identity is the relationship entity's own, as its mapping may
     * differ from the one of the node that owns the relationship. Every other property stays with the entity of
     * the block, which qualifies it with the variable of the relationship.
     */
    CriterionScope forCriterion(Query.Criterion criterion) {
        PersistentEntity relationship = builder.getRelationshipEntity(variable)
        if (!(relationship instanceof GraphPersistentEntity) || relationship.is(entity)) {
            return this
        }
        boolean identity = criterion instanceof Query.IdEquals ||
                (criterion instanceof Query.PropertyNameCriterion &&
                        ((Query.PropertyNameCriterion) criterion).property == relationship.identity?.name)
        identity ? new CriterionScope((GraphPersistentEntity) relationship, builder, variable) : this
    }

    /**
     * The identity of the entity of this scope.
     */
    String id() {
        entity.formatId(variable)
    }

    /**
     * A plain property of this scope, qualified with its variable.
     */
    String property(String name) {
        entity.formatProperty(variable, name)
    }

    /**
     * What a property name refers to as one side of a comparison: the identity of the node an association
     * reaches, which is matched for it, the identity of an end of a relationship, the type of a relationship, or
     * a plain property.
     */
    String reference(String name) {
        PersistentProperty found = entity.getPropertyByName(name)
        if (found instanceof Association && !(found instanceof Basic)) {
            Association association = (Association) found
            GraphPersistentEntity associated = (GraphPersistentEntity) association.associatedEntity
            if (entity.isRelationshipEntity() && RelationshipPersistentEntity.isRelationshipAssociation(association)) {
                return associated.formatId(association.name)
            }
            String target = "m_${builder.getNextMatchNumber()}"
            builder.addMatch(entity.formatAssociationPatternFromExisting(association, '', variable, target))
            // the node reached is of the associated entity, whose identity may be mapped differently
            return associated.formatId(target)
        }
        if (entity.isRelationshipEntity() && name == RelationshipPersistentEntity.TYPE) {
            builder.replaceFirstRelationshipMatch(((RelationshipPersistentEntity) entity).buildRelationshipMatchTo(null, CypherBuilder.REL_VAR))
            return "TYPE(${CypherBuilder.REL_VAR})".toString()
        }
        property(name)
    }

    /**
     * The pattern that holds when the node has a relationship for the association, or {@code null} when the
     * property is not an association stored as a relationship. An association is not a property of the node, so
     * a null test on it has to look for the relationship and not for a node property. A basic collection and an
     * embedded component are stored on the node itself, and the properties of a relationship have no
     * relationships, so they stay a null test of the property. An association to a relationship entity is the
     * relationships of that entity's type, so the pattern names the type.
     */
    String associationPattern(String name) {
        if (relationship) {
            return null
        }
        PersistentProperty found = entity.getPropertyByName(name)
        if (found instanceof Association && !(found instanceof Basic) && !(found instanceof Embedded)) {
            PersistentEntity associated = ((Association) found).associatedEntity
            String type = associated instanceof RelationshipPersistentEntity ?
                    ':' + ((RelationshipPersistentEntity) associated).type() : ''
            return entity.formatAssociationPatternFromExisting((Association) found, type, variable, '')
        }
        null
    }

    /**
     * The scope of the block of an association, whose pattern is matched into the query. The criteria of the
     * block are about the associated entity, so a property is looked up there: a plain property of the same name as
     * an association of the entity the query started from is still a plain property, and an association of the
     * associated entity is tested as one. An association to a relationship entity keeps the entity it was given,
     * and its variable is recorded as a relationship.
     */
    CriterionScope into(AssociationQuery block) {
        Association association = block.association
        String target = "m_${builder.getNextMatchNumber()}"
        String variable = target
        String relationship = ''
        boolean viaRelationship = block.entity instanceof RelationshipPersistentEntity
        if (viaRelationship) {
            relationship = "$variable:${(block.entity as RelationshipPersistentEntity).type()}"
            target = "m_${builder.getNextMatchNumber() + 1}"
        }
        builder.addMatch(entity.formatAssociationPatternFromExisting(association, relationship, this.variable, target))
        PersistentEntity associated = association.associatedEntity
        GraphPersistentEntity nested = associated instanceof GraphPersistentEntity &&
                !((GraphPersistentEntity) associated).isRelationshipEntity() ? (GraphPersistentEntity) associated : entity
        if (viaRelationship) {
            builder.addRelationshipVariable(variable, (PersistentEntity) block.entity)
        }
        new CriterionScope(nested, builder, variable)
    }
}

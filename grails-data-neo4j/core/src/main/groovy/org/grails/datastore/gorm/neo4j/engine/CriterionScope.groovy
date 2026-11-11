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
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.query.AssociationQuery

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
    /** The variables of the nodes at the ends of the relationship, when this is the scope of a relationship block. */
    final String fromVariable
    final String toVariable

    CriterionScope(GraphPersistentEntity entity, CypherBuilder builder, String variable,
                   String fromVariable = null, String toVariable = null) {
        this.entity = entity
        this.builder = builder
        this.variable = variable
        this.fromVariable = fromVariable
        this.toVariable = toVariable
    }

    /**
     * The variable of the node a {@code from} or {@code to} block of a relationship refers to: the end of the
     * relationship the block was entered through, or the one of the root query when the relationship is the root.
     */
    String endVariable(String end) {
        String found = end == RelationshipPersistentEntity.TO ? toVariable : fromVariable
        found ?: end
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
     * The scope of a block on an end of the relationship of this scope, a {@code from} or {@code to} association:
     * the node at that end, which is already matched, so its criteria are about the entity the end refers to.
     */
    CriterionScope forEnd(Association end) {
        PersistentEntity node = end.associatedEntity
        if (fromVariable == null || !(node instanceof GraphPersistentEntity)) {
            return atVariable(end.name)
        }
        new CriterionScope((GraphPersistentEntity) node, builder, endVariable(end.name))
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
            if (builder.inDisjunction && found instanceof ToOne) {
                // in a disjunction the node reached is read where it stands: a match would drop the nodes that have
                // none before the other branches could keep them, and a missing node is a null the branch does not match
                String local = "e_${builder.nextLocalNumber()}"
                String pattern = entity.formatAssociationPatternFromExisting(association, '', variable, local)
                return "head([${pattern} | ${associated.formatId(local)}])".toString()
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
     * associated entity is tested as one. An association to a relationship entity is about the relationship, so its
     * scope is the relationship entity, and its variable is recorded as a relationship.
     */
    CriterionScope into(AssociationQuery block) {
        String target = "m_${builder.getNextMatchNumber()}"
        String secondTarget = "m_${builder.getNextMatchNumber() + 1}"
        Existence block1 = blockScope(block, target, secondTarget)
        builder.addMatch(block1.pattern)
        block1.scope
    }

    /**
     * The scope of the block of an association that is tested for where it stands, as a block in a disjunction is,
     * with the pattern to look for. Nothing is added to the matches of the query: the variables are local to the test.
     */
    Existence intoExistence(AssociationQuery block) {
        blockScope(block, "e_${builder.nextLocalNumber()}", "e_${builder.nextLocalNumber()}")
    }

    private Existence blockScope(AssociationQuery block, String variable, String secondVariable) {
        Association association = block.association
        String target = variable
        String relationship = ''
        boolean viaRelationship = block.entity instanceof RelationshipPersistentEntity
        if (viaRelationship) {
            relationship = "$variable:${(block.entity as RelationshipPersistentEntity).type()}"
            target = secondVariable
        }
        String pattern = entity.formatAssociationPatternFromExisting(association, relationship, this.variable, target)
        if (viaRelationship) {
            RelationshipPersistentEntity relationshipEntity = (RelationshipPersistentEntity) block.entity
            builder.addRelationshipVariable(variable, relationshipEntity)
            // the node that owns the block is the end its entity is the relationship's, unless it is the other way round
            boolean incoming = entity.is(relationshipEntity.to.associatedEntity) && !entity.is(relationshipEntity.from.associatedEntity)
            return new Existence(pattern, new CriterionScope(relationshipEntity, builder, variable,
                    incoming ? target : this.variable, incoming ? this.variable : target))
        }
        PersistentEntity associated = association.associatedEntity
        GraphPersistentEntity nested = associated instanceof GraphPersistentEntity ? (GraphPersistentEntity) associated : entity
        new Existence(pattern, new CriterionScope(nested, builder, variable))
    }

    /** A pattern to look for, and the scope of the criteria about what it finds. */
    static final class Existence {
        final String pattern
        final CriterionScope scope

        Existence(String pattern, CriterionScope scope) {
            this.pattern = pattern
            this.scope = scope
        }
    }
}

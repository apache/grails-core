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

package org.grails.datastore.gorm.neo4j

import grails.neo4j.Direction
import grails.neo4j.Relationship
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.mapping.config.Attribute
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicAssociation
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany

/**
 * Utility methods for manipulating relationships
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class RelationshipUtils {
    static final char INCOMING_CHAR = '<'
    static final char OUTGOING_CHAR = '>'
    private static final char OPEN_BRACE = '{'
    private static final char CLOSE_BRACE = '}'
    private static final char COMMA = ','
    private static final char COLON = ':'
    private static final String SINGLE_QUOTE = "'"
    private static final String START_RELATIONSHIP = "-["
    private static final String END_RELATIONSHIP = "]-"

    /**
     * Whether the association is inverse
     *
     * @param association The association
     * @return True if it is
     */
    static boolean useReversedMappingFor(Association association) {
        GraphPersistentEntity entity = (GraphPersistentEntity)association.getOwner()
        if(entity.isRelationshipEntity()) {
            return false
        }
        else {
            Attribute attr = (Attribute)association.getMapping()?.getMappedForm()
            return isIncomingRelationship(association, attr)
        }
    }

    /**
     * Obtain the Neo4j relationship type for the giveen association
     * @param association The association
     * @return The Neo4j relationship type
     */
    static String relationshipTypeUsedFor(Association association) {
        Attribute mappedForm = (Attribute) association.getMapping()?.getMappedForm()
        return getRelationshipType( association, mappedForm )
    }

    /**
     * Build an association match for the given association, variable name and attributes
     *
     * @param association The association
     * @param var The variable name
     * @param attributes The attributes
     * @return
     */
    static String matchForAssociation(Association association, String var = "", Map<String, String> attributes = Collections.emptyMap()) {
        Attribute mappedForm = (Attribute) association.getMapping()?.getMappedForm()
        final String relationshipType = getRelationshipType(association, mappedForm)
        final boolean reversed = isIncomingRelationship(association, mappedForm)
        StringBuilder sb = new StringBuilder()
        if (reversed) {
            sb.append(INCOMING_CHAR)
        }
        sb.append(START_RELATIONSHIP).append(var).append(COLON).append(relationshipType)
        if(!attributes.isEmpty()) {
            sb.append(OPEN_BRACE)
            def i = attributes.entrySet().iterator()
            while(i.hasNext()) {
                def entry = i.next()
                sb.append(entry.key)
                        .append(COLON).append(SINGLE_QUOTE)
                        .append(entry.value)
                        .append(SINGLE_QUOTE)

                if(i.hasNext()) {
                    sb.append(COMMA)
                }
            }
            sb.append(CLOSE_BRACE)
        }

        sb.append(END_RELATIONSHIP)
        Direction direction = mappedForm?.direction
        if (direction == Direction.OUTGOING || direction == Direction.BOTH || !reversed) {
            sb.append(OUTGOING_CHAR)
        }
        sb.toString()
    }


    /**
     * Build an association match for the given association, variable name and attributes
     *
     * @param association The association
     * @param var The variable name
     * @param attributes The attributes
     * @return
     */
    static String toMatch(Association association, Relationship relationship, String var = "r") {
        Attribute mappedForm = (Attribute) association.getMapping()?.getMappedForm()
        final String relationshipType = relationship.type()
        Direction direction = mappedForm.direction ?: Direction.OUTGOING
        final boolean reversed = direction == Direction.INCOMING || direction == Direction.BOTH
        StringBuilder sb = new StringBuilder()
        if (reversed) {
            sb.append(INCOMING_CHAR)
        }
        sb.append(START_RELATIONSHIP).append(var).append(COLON).append(relationshipType)
        sb.append(END_RELATIONSHIP)
        if (direction == Direction.OUTGOING || direction == Direction.BOTH ) {
            sb.append(OUTGOING_CHAR)
        }
        sb.toString()
    }

    /**
     * Build an association match for the given association, variable name and attributes
     *
     * @param association The association
     * @param var The variable name
     * @param attributes The attributes
     * @return
     */
    static String matchForRelationshipEntity(Association association, RelationshipPersistentEntity entity, String var = "r") {
        Attribute mappedForm = (Attribute) association.getMapping()?.getMappedForm()
        PersistentEntity owningEntity = association.getOwner()
        Direction direction = mappedForm.direction ?: owningEntity == entity.getTo().associatedEntity ? Direction.INCOMING : Direction.OUTGOING
        final boolean reversed = direction == Direction.INCOMING || direction == Direction.BOTH
        StringBuilder sb = new StringBuilder()
        if (reversed) {
            sb.append(INCOMING_CHAR)
        }
        sb.append(START_RELATIONSHIP)
          .append(var)
          .append(END_RELATIONSHIP)
        if (direction == Direction.OUTGOING || direction == Direction.BOTH ) {
            sb.append(OUTGOING_CHAR)
        }
        sb.toString()
    }

    protected static boolean isIncomingRelationship(Association association, Attribute mappedForm) {
        def direction = mappedForm?.direction
        if (direction == Direction.INCOMING || direction == Direction.BOTH) {
            return true
        } else {
            return association.isBidirectional() &&
                    ((association instanceof ManyToOne) ||
                            ((association instanceof ManyToMany) && (association.isOwningSide())))
        }
    }

    protected static String getRelationshipType(Association association, Attribute mappedForm) {
        String relationshipType = mappedForm?.getType()
        if (relationshipType != null) {
            return relationshipType
        }
        else if (association instanceof DynamicAssociation) {
            return association.getName()
        }
        else {
            boolean reversed = useReversedMappingFor(association)
            String name = reversed ?
                    association.getReferencedPropertyName() :
                    association.getName()
            if(name != null) {
                return name.toUpperCase(Locale.ENGLISH)
            }
            else {
                return association.getName().toUpperCase(Locale.ENGLISH)
            }
        }
    }
}

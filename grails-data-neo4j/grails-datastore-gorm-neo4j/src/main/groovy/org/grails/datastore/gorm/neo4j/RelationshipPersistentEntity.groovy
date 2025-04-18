package org.grails.datastore.gorm.neo4j

import grails.neo4j.Direction
import grails.neo4j.Relationship
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.mapping.config.NodeConfig
import org.grails.datastore.gorm.neo4j.mapping.config.RelationshipConfig
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.IllegalMappingException
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic

/**
 * Represents a relationship
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class RelationshipPersistentEntity extends GraphPersistentEntity {
    /**
     * The name of the from property
    */
    public static final String FROM = "from"
    /**
    * The name of the to property
    */
    public static final String TO = "to"

    /**
     * The relationship type
     */
    public static final String TYPE = "type"

    protected String type
    protected Direction direction = Direction.OUTGOING

    RelationshipPersistentEntity(Class javaClass, MappingContext context, boolean external = false) {
        super(javaClass, context, external)
        this.type = javaClass.simpleName.toUpperCase()
    }

    static boolean isRelationshipAssociation(PersistentProperty association) {
        return TO == association.name || (FROM == association.name)
    }



    @Override
    void initialize() {
        if(!isInitialized()) {

            super.initialize()
            for(association in associations) {
                if(!isRelationshipAssociation(association) && !(association instanceof Basic)) {
                    throw new IllegalMappingException("Invalid association $association. You cannot have associations to other nodes within a relationship entity")
                }
            }
            NodeConfig mappedForm = getMapping().getMappedForm()
            if(mappedForm instanceof RelationshipConfig) {
                RelationshipConfig rc = (RelationshipConfig) mappedForm
                this.type = rc.type ?: type
                this.direction = rc.direction
            }
            ((Property) getFrom().getMapping().getMappedForm()).setLazy(true)
            ((Property) getTo().getMapping().getMappedForm()).setLazy(true)
        }
    }

    @Override
    String getVariableName() {
        return CypherBuilder.REL_VAR
    }

    String formatBatchCreate(String batchId, String type = this.type()) {
        fromEntity.initialize()
        toEntity.initialize()
        """UNWIND ${batchId} as row
MATCH ${fromEntity.formatNode(FROM)} WHERE ${fromEntity.formatId(FROM)} = row.from
MATCH ${toEntity.formatNode(TO)} WHERE ${toEntity.formatId(TO)} IN row.to
MERGE ($FROM)${buildRelationshipMatch(type)}($TO) 
ON CREATE SET r = row.props"""
    }

    /**
     * Formats an association match from an existing matched node
     *
     * @param association The association
     * @param var The variable name to use for the relationship. Defaults to 'r"
     * @param start The start variable name
     * @param end The relationship variable name
     * @return The match
     */
    @Override
    String formatAssociationPatternFromExisting(Association association, String var = CypherBuilder.REL_VAR, String start = FROM, String end = TO) {
        String associationMatch
        if(association.name == FROM || association.name == TO) {
            associationMatch = RelationshipUtils.matchForRelationshipEntity(association, this,var)
            return "(${start})${associationMatch}${toEntity.formatNode(end)}"
        }
        else {
            throw new IllegalStateException("Relationship entities cannot have associations")
        }
    }

    @Override
    String formatProperty(String variable, String property) {
        if(property.startsWith(FROM) || property.startsWith(TO)) {
            return property
        }
        else {
            return super.formatProperty(variable, property)
        }
    }

    @Override
    String formatMatchId(String variable) {
        return String.format(MATCH_ID, buildMatch(type(), CypherBuilder.NODE_VAR), formatId())
    }

    @Override
    boolean isVersioned() {
        return false
    }

    @Override
    boolean hasDynamicAssociations() {
        return false
    }

    @Override
    boolean hasDynamicLabels() {
        return false
    }

    Association getFrom() {
        (Association)getPropertyByName(FROM)
    }

    Association getTo() {
        (Association)getPropertyByName(TO)
    }

    GraphPersistentEntity getFromEntity() {
        (GraphPersistentEntity)getFrom().getAssociatedEntity()
    }

    GraphPersistentEntity getToEntity() {
        (GraphPersistentEntity)getTo().getAssociatedEntity()
    }


    String buildMatch(String type = this.type(), String var = "r") {
        String toMatch = buildRelationshipMatchTo(type, var)
        return fromEntity.formatNode(FROM) + toMatch
    }

    String buildToMatch(String var = "r") {
        String type = type()
        return buildRelationshipMatchTo(type, var)
    }

    String type() {
        return this.type
    }

    String buildRelationshipMatchTo(String type, String var = "r") {
        buildRelationshipMatch(type ,var) + toEntity.formatNode(TO)
    }

    String buildRelationshipMatch(String type, String var = "r") {
        boolean incoming = direction.isIncoming()
        boolean outgoing = direction.isOutgoing()
        if(type != null) {
            return "${incoming ? RelationshipUtils.INCOMING_CHAR : ''}-[$var:${type}]-${outgoing ? RelationshipUtils.OUTGOING_CHAR : ''}"
        }
        else {
            return "${incoming ? RelationshipUtils.INCOMING_CHAR : ''}-[$var]-${outgoing ? RelationshipUtils.OUTGOING_CHAR : ''}"
        }
    }
}

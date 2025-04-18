package grails.neo4j

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.neo4j.RelationshipPersistentEntity
import org.grails.datastore.gorm.schemaless.DynamicAttributes

/**
 * Represents a Neo4j relationship
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait Relationship<F,T> implements DynamicAttributes, Serializable {

    /**
     * The relationship type
     */
    private String theType

    /**
     * The relationship id
     */
    Long id

    /**
     * The node where relationship originates from
     */
    F from

    /**
     * The node where the relationship goes to
     */
    T to


    /**
     * @return The type of the relationship
     */
    String type() {
        if(this.theType == null) {
            theType = ((RelationshipPersistentEntity)GormEnhancer.findEntity(getClass())).type()
        }
        return theType
    }

    /**
     * @return The type of the relationship
     */
    String getType() {
        return type()
    }

    /**
     * Sets the type of the relationship
     * @param type
     */
    void setType(String type) {
        this.theType = type
    }

    /**
     * @return True if they are equal
     */
    boolean equals(Object object) {
        if(object instanceof Relationship) {
            Relationship other = (Relationship)object
            GormEntity fromEntity = (GormEntity)this.from
            GormEntity toEntity = (GormEntity)this.to
            return fromEntity?.ident() == ((GormEntity)other.from)?.ident() && toEntity?.ident() == ((GormEntity)other.to)?.ident()
        }
        return false
    }

    /**
     * @return hashCode
     */
    int hashCode() {
        GormEntity fromEntity = (GormEntity)this.from
        GormEntity toEntity = (GormEntity)this.to

        int result = type != null ? type.hashCode() : 0
        def fromId = fromEntity?.ident()
        if(fromId != null) {
            result = 31 * result + fromId.hashCode()
        }
        def toId = toEntity?.ident()
        if(toId != null) {
            result = 31 * result + toId.hashCode()
        }
        return result
    }
}

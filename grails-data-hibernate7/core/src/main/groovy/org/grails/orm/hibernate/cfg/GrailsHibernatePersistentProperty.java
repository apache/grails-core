package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;

import java.util.Optional;

import org.hibernate.MappingException;
import org.hibernate.usertype.UserCollectionType;

/**
 * Interface for Hibernate persistent properties
 */
public interface GrailsHibernatePersistentProperty extends PersistentProperty<PropertyConfig> {

    /**
     * @return The type name
     */
    default String getTypeName() {
        GrailsHibernatePersistentEntity owner = getHibernateOwner();
        return getTypeName(owner != null ? owner.getMappedForm() : null);
    }

    /**
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(Mapping mapping) {
        return getTypeName(getMappedForm(), mapping);
    }

    /**
     * @param config The property config
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(PropertyConfig config, Mapping mapping) {
        return Optional.ofNullable(config)
                .map(PropertyConfig::getType)
                .map(typeObj -> typeObj instanceof Class<?> clazz ? clazz.getName() : typeObj.toString())
                .orElseGet(() -> mapping != null ? mapping.getTypeName(getType()) : null);
    }

    default GrailsHibernatePersistentEntity getHibernateOwner() {
        return getOwner() instanceof GrailsHibernatePersistentEntity ghpe ? ghpe : null;
    }

    default Class<?> getUserType() {
        PropertyConfig config = getMappedForm();
        if (config == null) return null;
        Object typeObj = config.getType();
        Class<?> userType = null;
        if (typeObj instanceof Class<?>) {
            userType = (Class<?>)typeObj;
        } else if (typeObj != null) {
            String typeName = typeObj.toString();
            try {
                userType = Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException e) {

            }
        }
        return userType;
    }

    default boolean isUserButNotCollectionType(){
       return getUserType() != null && !UserCollectionType.class.isAssignableFrom(getUserType());
    }

   default boolean isEnumType() {
       return Optional.ofNullable(getType()).map(Class::isEnum).orElse(false);
   }
   default boolean isHibernateOneToOne() {
       validateAssociation();
       return this instanceof org.grails.datastore.mapping.model.types.OneToOne association
               &&
               ( association.canBindOneToOneWithSingleColumnAndForeignKey() ||
                       (
                               association.isHasOne() && association.isBidirectional() && association.getInverseSide() != null
                       )
               );

   }

   default boolean isHibernateManyToOne() {
       validateAssociation();
       if(!(this instanceof Association)) {
           return  false;
       }
       return this instanceof org.grails.datastore.mapping.model.types.ManyToOne || (this instanceof org.grails.datastore.mapping.model.types.OneToOne && !isHibernateOneToOne());
   }

   default boolean isEmbedded() {
       validateAssociation();
        return this instanceof Embedded;
   }

    default void validateAssociation() {
        if (this instanceof Association && getUserType() != null) {
            throw new MappingException("Cannot bind association property [" + getName() + "] of type [" + getType() + "] to a user type");
        }
        if(this instanceof org.grails.datastore.mapping.model.types.OneToOne oneToOne){
            if(oneToOne.isHasOne() && !oneToOne.isBidirectional()) {
                throw new MappingException("hasOne property [" + getName() + "] is not bidirectional. Specify the other side of the relationship!");
            }
        }
    }

    default boolean isSerializableType() {
        return "serializable".equals(getTypeName());
    }
}
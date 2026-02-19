package org.grails.orm.hibernate.cfg;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Embedded;

import java.util.List;
import java.util.Optional;

import org.hibernate.MappingException;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.usertype.UserCollectionType;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.Mapping;

/**
 * Interface for Hibernate persistent properties
 */
public interface GrailsHibernatePersistentProperty extends PersistentProperty<PropertyConfig> {

    default Optional<CompositeIdentity> getCompositeIdentity(Mapping mapping) {
        return Optional.ofNullable(mapping)
                .filter(m -> m.hasCompositeIdentifier() && supportsJoinColumnMapping())
                .map(Mapping::getIdentity)
                .filter(CompositeIdentity.class::isInstance)
                .map(CompositeIdentity.class::cast);
    }

    default boolean isOwningSide() {
        return this instanceof Association<?> association && association.isOwningSide();
    }

    default boolean isBidirectional() {
        return this instanceof Association<?> association && association.isBidirectional();
    }

    default GrailsHibernatePersistentProperty getHibernateInverseSide() {
        return this instanceof Association<?> association ? (GrailsHibernatePersistentProperty) association.getInverseSide() : null;
    }

    default boolean isCircular() {
        return this instanceof Association<?> association && association.isCircular();
    }

    default GrailsHibernatePersistentEntity getHibernateAssociatedEntity() {
        return this instanceof Association<?> association ? (GrailsHibernatePersistentEntity) association.getAssociatedEntity() : null;
    }

    default boolean isBidirectionalOneToManyMap() {
        return this instanceof Association<?> association && association.isBidirectionalOneToManyMap();
    }

    /**
     * @return The type name
     */
    default String getTypeName() {
        return getTypeName(getType());
    }

    /**
     * @param propertyType The property type
     * @return The type name
     */
    default String getTypeName(Class<?> propertyType) {
        return getTypeName(propertyType, getMappedForm(), getHibernateOwner().getMappedForm());
    }

    /**
     * @param config The property config
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(PropertyConfig config, Mapping mapping) {
        return getTypeName(getType(), config, mapping);
    }

    /**
     * @param propertyType The property type
     * @param config The property config
     * @param mapping The mapping
     * @return The type name
     */
    default String getTypeName(Class<?> propertyType, PropertyConfig config, Mapping mapping) {
        if (this instanceof Association && propertyType == getType() && getHibernateAssociatedEntity() != null) {
            return null;
        }
        String typeName = Optional.ofNullable(config)
                .map(PropertyConfig::getType)
                .map(typeObj -> typeObj instanceof Class<?> clazz ? clazz.getName() : typeObj.toString())
                .orElseGet(() -> mapping != null ? mapping.getTypeName(propertyType) : null);

        if (typeName == null && propertyType != null && getHibernateAssociatedEntity() == null && !propertyType.isEnum()) {
            return propertyType.getName();
        }
        return typeName;
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



    default String getIndexColumnType(String defaultType) {
        return Optional.ofNullable(getMappedForm())
                .map(PropertyConfig::getIndexColumn)
                .map(ic -> getTypeName(ic, getHibernateOwner().getMappedForm()))
                .orElse(defaultType);
    }

    default String getIndexColumnName(PersistentEntityNamingStrategy namingStrategy) {
        return Optional.ofNullable(getMappedForm())
                .map(PropertyConfig::getIndexColumn)
                .map(PropertyConfig::getColumn)
                .orElseGet(() -> namingStrategy.resolveColumnName(getName()) + GrailsDomainBinder.UNDERSCORE + IndexedCollection.DEFAULT_INDEX_COLUMN_NAME);
    }

    default String getMapElementName(PersistentEntityNamingStrategy namingStrategy) {
        return Optional.ofNullable(getMappedForm())
                .map(PropertyConfig::getJoinTable)
                .map(JoinTable::getColumn)
                .map(ColumnConfig::getName)
                .orElseGet(() -> namingStrategy.resolveColumnName(getName()) + GrailsDomainBinder.UNDERSCORE + IndexedCollection.DEFAULT_ELEMENT_COLUMN_NAME);
    }

    default boolean isJoinKeyMapped() {
        return getMappedForm() != null  && getMappedForm().hasJoinKeyMapping() && supportsJoinColumnMapping();
    }

    default String getMappedColumnName() {
        if( getMappedForm() != null) {
            return getMappedForm().getColumn();
        }
        return null;
    }

    default String getColumnName(ColumnConfig cc) {
        return Optional.of(this)
                .filter(GrailsHibernatePersistentProperty::isJoinKeyMapped)
                .map(p -> p.getMappedForm().getJoinTable().getKey().getName())
                .orElseGet(() -> Optional.ofNullable(cc)
                        .map(ColumnConfig::getName)
                        .orElseGet(this::getMappedColumnName));
    }

    default boolean isBidirectionalManyToOneWithListMapping(Property prop) {
        if (this instanceof Association<?> association) {
            return association.isBidirectional()
                    && association.getInverseSide() != null
                    && List.class.isAssignableFrom(this.getType())
                    && prop != null
                    && prop.getValue() instanceof ManyToOne;
        }
        return false;
    }

    /**
     * @param simpleValue The Hibernate simple value
     * @return The type name
     */
    default String getTypeName(SimpleValue simpleValue) {
        return getTypeProperty(simpleValue).getTypeName();
    }

    /**
     * @param simpleValue The Hibernate simple value
     * @return The type parameters
     */
    default java.util.Properties getTypeParameters(SimpleValue simpleValue) {
        if (getTypeName(simpleValue) != null) {
            return Optional.ofNullable(getTypeProperty(simpleValue).getMappedForm()).map(PropertyConfig::getTypeParams).orElse(null);
        }
        return null;
    }

    /**
     * @param simpleValue The Hibernate simple value
     * @return The property that defines the type
     */
    default GrailsHibernatePersistentProperty getTypeProperty(SimpleValue simpleValue) {
        if (simpleValue instanceof DependantValue) {
            return Optional.ofNullable(getHibernateOwner().getIdentity()).orElse(this);
        }
        return this;
    }
}
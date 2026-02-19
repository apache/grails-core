package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import jakarta.annotation.Nonnull;
import org.hibernate.MappingException;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

public class SimpleIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final JdbcEnvironment jdbcEnvironment;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;
    private final BasicValueIdCreator basicValueIdCreator;

    public SimpleIdBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            JdbcEnvironment jdbcEnvironment,
            BasicValueIdCreator basicValueIdCreator,
            SimpleValueBinder simpleValueBinder,
            PropertyBinder propertyBinder)  {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueIdCreator = basicValueIdCreator;
    }

    public MetadataBuildingContext getMetadataBuildingContext() {
        return metadataBuildingContext;
    }

    protected SimpleIdBinder(MetadataBuildingContext metadataBuildingContext, JdbcEnvironment jdbcEnvironment, BasicValueIdCreator basicValueIdCreator, SimpleValueBinder simpleValueBinder, PropertyBinder propertyBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueIdCreator = basicValueIdCreator;
    }


    public void bindSimpleId(@Nonnull GrailsHibernatePersistentEntity domainClass, RootClass entity, Identity mappedId, Table table) {

        Mapping result = domainClass.getMappedForm();
        boolean useSequence = result != null && result.isTablePerConcreteClass();
        // create the id value

        BasicValue id = basicValueIdCreator.getBasicValueId(metadataBuildingContext, table, mappedId, domainClass, useSequence);

        var identifier = domainClass.getIdentity();
        if (mappedId != null) {
            String propertyName = mappedId.getName();
            if (propertyName != null && !propertyName.equals(domainClass.getName())) {
                var namedIdentityProp = (GrailsHibernatePersistentProperty) domainClass.getPropertyByName(propertyName);
                if (namedIdentityProp == null) {
                    throw new MappingException("Mapping specifies an identifier property name that doesn't exist [" + propertyName + "]");
                }
                if (!namedIdentityProp.equals(identifier)) {
                    identifier = namedIdentityProp;
                }
            }
        }

        Property idProperty  = new Property();
        idProperty.setName(identifier.getName());
        idProperty.setValue(id);
        entity.setDeclaredIdentifierProperty(idProperty);
        entity.setIdentifier(id);
        // set type
        simpleValueBinder.bindSimpleValue(identifier, null, id, EMPTY_PATH);

        // bind property
        Property prop = propertyBinder.bindProperty(identifier, id);
        // set identifier property
        entity.setIdentifierProperty(prop);

        Table pkTable = id.getTable();
        pkTable.setPrimaryKey(new PrimaryKey(pkTable));
    }
}

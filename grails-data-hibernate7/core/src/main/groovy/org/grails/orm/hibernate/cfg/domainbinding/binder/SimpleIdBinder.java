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

import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

public class SimpleIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final JdbcEnvironment jdbcEnvironment;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;
    private final BasicValueIdCreator basicValueIdCreator;

    public SimpleIdBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment)  {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.simpleValueBinder = new SimpleValueBinder(namingStrategy);
        this.propertyBinder = new PropertyBinder();
        this.basicValueIdCreator = null;
    }

    protected SimpleIdBinder(BasicValueIdCreator basicValueIdCreator, SimpleValueBinder simpleValueBinder, PropertyBinder propertyBinder) {
        this.metadataBuildingContext = null;
        this.jdbcEnvironment = null;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueIdCreator = basicValueIdCreator;
    }


    public void bindSimpleId(@Nonnull GrailsHibernatePersistentEntity domainClass, RootClass entity, Identity mappedId) {

        Mapping result = domainClass.getMappedForm();
        boolean useSequence = result != null && result.isTablePerConcreteClass();
        // create the id value

        BasicValueIdCreator idCreator = this.basicValueIdCreator != null ? this.basicValueIdCreator : new BasicValueIdCreator(metadataBuildingContext, jdbcEnvironment, domainClass, entity);
        BasicValue id = idCreator.getBasicValueId(mappedId, useSequence);

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

        Table table = id.getTable();
        table.setPrimaryKey(new PrimaryKey(table));
    }
}

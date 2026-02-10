package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

public class SimpleIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final JdbcEnvironment jdbcEnvironment;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;
    private final BasicValueIdCreator basicValueIdCreator;

    public SimpleIdBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment)  {
        this(metadataBuildingContext, jdbcEnvironment, new SimpleValueBinder(namingStrategy), new PropertyBinder(), null);
    }

    public SimpleIdBinder(BasicValueIdCreator basicValueIdCreator, SimpleValueBinder simpleValueBinder, PropertyBinder propertyBinder) {
        this.metadataBuildingContext = null;
        this.jdbcEnvironment = null;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueIdCreator = basicValueIdCreator;
    }

    protected SimpleIdBinder(MetadataBuildingContext metadataBuildingContext, JdbcEnvironment jdbcEnvironment, SimpleValueBinder simpleValueBinder, PropertyBinder propertyBinder, BasicValueIdCreator basicValueIdCreator) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.jdbcEnvironment = jdbcEnvironment;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueIdCreator = basicValueIdCreator;
    }


    public void bindSimpleId(PersistentProperty identifier, RootClass entity, Identity mappedId) {

        Mapping result = null;
        GrailsHibernatePersistentEntity domainClass = null;
        if (identifier.getOwner() instanceof GrailsHibernatePersistentEntity) {
            domainClass = (GrailsHibernatePersistentEntity) identifier.getOwner();
            result = domainClass.getMappedForm();
        }
        boolean useSequence = result != null && result.isTablePerConcreteClass();
        // create the id value

        BasicValueIdCreator idCreator = this.basicValueIdCreator != null ? this.basicValueIdCreator : new BasicValueIdCreator(metadataBuildingContext, jdbcEnvironment, domainClass, entity);
        BasicValue id = idCreator.getBasicValueId(mappedId, useSequence);

        Property idProperty  = new Property();
        idProperty.setName(identifier.getName());
        idProperty.setValue(id);
        entity.setDeclaredIdentifierProperty(idProperty);
        entity.setIdentifier(id);
        // set type
        simpleValueBinder.bindSimpleValue((GrailsHibernatePersistentProperty) identifier, null, id, EMPTY_PATH);

        // create property
        Property prop = new Property();
        prop.setValue(id);

        // bind property
        propertyBinder.bindProperty(identifier, prop);
        // set identifier property
        entity.setIdentifierProperty(prop);

        Table table = id.getTable();
        table.setPrimaryKey(new PrimaryKey(table));
    }
}

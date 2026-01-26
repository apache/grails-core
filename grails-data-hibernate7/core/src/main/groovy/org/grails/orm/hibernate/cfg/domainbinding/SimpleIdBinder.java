package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

public class SimpleIdBinder {

    private final BasicValueIdCreator basicValueIdCreator;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;

    public SimpleIdBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment, GrailsHibernatePersistentEntity domainClass, RootClass entity)  {
        this.basicValueIdCreator = new BasicValueIdCreator(metadataBuildingContext, jdbcEnvironment, domainClass, entity);
        this.simpleValueBinder =new SimpleValueBinder(namingStrategy);
        this.propertyBinder = new PropertyBinder();
    }

    protected SimpleIdBinder(BasicValueIdCreator basicValueIdCreate, SimpleValueBinder simpleValueBinder, PropertyBinder propertyBinder) {
        this.basicValueIdCreator = basicValueIdCreate;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
    }


    public void bindSimpleId(PersistentProperty identifier, RootClass entity, Identity mappedId) {

        Mapping result = null;
        if (identifier.getOwner() instanceof GrailsHibernatePersistentEntity) {
            result = ((GrailsHibernatePersistentEntity) identifier.getOwner()).getMappedForm();
        }
        boolean useSequence = result != null && result.isTablePerConcreteClass();
        // create the id value

        BasicValue id = basicValueIdCreator.getBasicValueId(entity, mappedId, useSequence);

        Property idProperty  = new Property();
        idProperty.setName(identifier.getName());
        idProperty.setValue(id);
        entity.setDeclaredIdentifierProperty(idProperty);
        entity.setIdentifier(id);
        // set type
        simpleValueBinder.bindSimpleValue(identifier, null, id, EMPTY_PATH);

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

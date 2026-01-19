package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

public class SimpleIdBinder {

    private final BasicValueIdCreator basicValueIdCreator;
    private final HibernateEntityWrapper hibernateEntityWrapper;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;

    public SimpleIdBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment)  {
        this.basicValueIdCreator = new BasicValueIdCreator(metadataBuildingContext, jdbcEnvironment);
        this.hibernateEntityWrapper = new HibernateEntityWrapper();
        this.simpleValueBinder =new SimpleValueBinder(namingStrategy);
        this.propertyBinder = new PropertyBinder();
    }

    protected SimpleIdBinder(BasicValueIdCreator basicValueIdCreate, HibernateEntityWrapper hibernateEntityWrapper, SimpleValueBinder simpleValueBinder, PropertyBinder propertyBinder) {
        this.basicValueIdCreator = basicValueIdCreate;
        this.hibernateEntityWrapper = hibernateEntityWrapper;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
    }


    public void bindSimpleId(PersistentProperty identifier, RootClass entity, Identity mappedId, JdbcEnvironment jdbcEnvironment) {

        boolean useSequence = hibernateEntityWrapper.getMappedForm(identifier.getOwner()).isTablePerConcreteClass();
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

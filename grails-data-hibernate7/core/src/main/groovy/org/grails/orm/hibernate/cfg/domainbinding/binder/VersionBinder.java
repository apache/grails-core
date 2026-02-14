package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

import java.util.function.BiFunction;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH;

public class VersionBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;
    private final BiFunction<MetadataBuildingContext, Table, BasicValue> basicValueFactory;

    public VersionBinder(MetadataBuildingContext metadataBuildingContext,
                            SimpleValueBinder simpleValueBinder,
                            PropertyBinder propertyBinder,
                            BiFunction<MetadataBuildingContext, Table, BasicValue> basicValueFactory) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueFactory = basicValueFactory;
    }

    public VersionBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
        this(metadataBuildingContext,
             new SimpleValueBinder(namingStrategy, jdbcEnvironment),
             new PropertyBinder(),
             BasicValue::new);
    }

    public void bindVersion(GrailsHibernatePersistentProperty version, RootClass entity) {

        if (version != null) {

            BasicValue val = basicValueFactory.apply(metadataBuildingContext, entity.getTable());

            // set type
            simpleValueBinder.bindSimpleValue(version, null, val, EMPTY_PATH);

            if (!val.isTypeSpecified()) {
                val.setTypeName("version".equals(version.getName()) ? "integer" : "timestamp");
            }
            Property prop = propertyBinder.bindProperty(version, val);
            prop.setLazy(false);
            val.setNullValue("undefined");
            entity.setVersion(prop);
            entity.setDeclaredVersion(prop);
            entity.setOptimisticLockStyle(OptimisticLockStyle.VERSION);
            entity.addProperty(prop);
        }
        else {
            entity.setOptimisticLockStyle(OptimisticLockStyle.NONE);
        }
    }
}

package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;

import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class SimpleValueBinder {

    private final ColumnConfigToColumnBinder columnConfigToColumnBinder ;
    private final ColumnBinder columnBinder;
    private final PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig;
    private final HibernateEntityWrapper hibernateEntityWrapper;
    private final TypeNameProvider typeNameProvider;


    private static final String SEQUENCE_KEY = "sequence";

    public SimpleValueBinder(PersistentEntityNamingStrategy namingStrategy) {
        this.columnConfigToColumnBinder = new ColumnConfigToColumnBinder();
        this.columnBinder = new ColumnBinder(namingStrategy);
        this.persistentPropertyToPropertyConfig = new PersistentPropertyToPropertyConfig();
        this.hibernateEntityWrapper = new HibernateEntityWrapper();
        this.typeNameProvider = new TypeNameProvider();

    }

    protected SimpleValueBinder(ColumnConfigToColumnBinder columnConfigToColumnBinder
            , ColumnBinder columnBinder
            , PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig
    , HibernateEntityWrapper hibernateEntityWrapper
    ,TypeNameProvider typeNameProvider) {
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
        this.columnBinder = columnBinder;
        this.persistentPropertyToPropertyConfig = persistentPropertyToPropertyConfig;
        this.typeNameProvider = typeNameProvider;
        this.hibernateEntityWrapper = hibernateEntityWrapper;
    }


    /**
     * Binds a simple value to the Hibernate metamodel. A simple value is
     * any type within the Hibernate type system
     *
     * @param property
     * @param parentProperty
     * @param simpleValue    The simple value to bind
     * @param path
     */

    public void bindSimpleValue(
            PersistentProperty property
            , PersistentProperty parentProperty
            , SimpleValue simpleValue
            , String path
    ) {
        PropertyConfig propertyConfig = persistentPropertyToPropertyConfig.toPropertyConfig(property);
        Mapping mapping = hibernateEntityWrapper.getMappedForm(property.getOwner());
        final String typeName = typeNameProvider.getTypeName(property, mapping);
        if (typeName == null) {
            simpleValue.setTypeName(property.getType().getName());
        }
        else {
            simpleValue.setTypeName(typeName);
            simpleValue.setTypeParameters(propertyConfig.getTypeParams());
        }

        String generator = propertyConfig.getGenerator();
        if (generator != null && simpleValue instanceof BasicValue) {
            BasicValue basicValue = (BasicValue) simpleValue;
            Properties params = propertyConfig.getTypeParams();
            final Properties generatorProps = new Properties();
            if (params != null) {
                generatorProps.putAll(params);
            }

            if (SEQUENCE_KEY.equals(generator) && generatorProps.containsKey(SEQUENCE_KEY)) {
                generatorProps.put(SequenceStyleGenerator.SEQUENCE_PARAM, generatorProps.getProperty(SEQUENCE_KEY));
            }

            switch (generator) {
                case "identity" -> basicValue.setCustomIdGeneratorCreator(context -> {
                    var gen = new org.hibernate.id.IdentityGenerator();
                    context.getProperty().getValue().getColumns().get(0).setIdentity(true);
                    return gen;
                });
                case "sequence", "sequence-identity" -> basicValue.setCustomIdGeneratorCreator(context -> {
                    var gen = new org.hibernate.id.enhanced.SequenceStyleGenerator();
                    gen.configure(context.getType(), generatorProps, context.getServiceRegistry());
                    return gen;
                });
            }
        }

        if ( propertyConfig.isDerived() && !(property instanceof TenantId)) {
            Formula formula = new Formula();
            formula.setFormula(propertyConfig.getFormula());
            simpleValue.addFormula(formula);
        } else {
            Table table = simpleValue.getTable();



            // Add the column definitions for this value/property. Note that
            // not all custom mapped properties will have column definitions,
            // in which case we still need to create a Hibernate column for
            // this value.
            Optional.ofNullable(propertyConfig.getColumns()).
                    filter(list-> !list.isEmpty())
                    .orElse(Arrays.asList(new ColumnConfig[] { null }))
                    .forEach( cc -> {
                        Column column = new Column();
                        columnConfigToColumnBinder.bindColumnConfigToColumn(column,cc,propertyConfig);
                        columnBinder.bindColumn(property, parentProperty, column, cc, path, table);
                        if (table != null) {
                            table.addColumn(column);
                        }
                        simpleValue.addColumn(column);
                    });
        }
    }
}

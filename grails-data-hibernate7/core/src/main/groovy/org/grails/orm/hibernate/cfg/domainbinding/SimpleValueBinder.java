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
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum;

public class SimpleValueBinder {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;
    private final ColumnBinder columnBinder;

    private static final String SEQUENCE_KEY = GrailsSequenceGeneratorEnum.SEQUENCE.toString();

    /**
     * Convenience constructor for namingStrategy.
     */
    public SimpleValueBinder(PersistentEntityNamingStrategy namingStrategy) {
        this(namingStrategy, new ColumnConfigToColumnBinder(), new ColumnBinder(namingStrategy));
    }

    /**
     * Public constructor that accepts all collaborators.
     */
    public SimpleValueBinder(
            PersistentEntityNamingStrategy namingStrategy,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            ColumnBinder columnBinder) {
        this.namingStrategy = namingStrategy;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
        this.columnBinder = columnBinder;
    }

    /**
     * Protected constructor for testing purposes.
     */
    protected SimpleValueBinder() {
        this.namingStrategy = null;
        this.columnConfigToColumnBinder = null;
        this.columnBinder = null;
    }

    public void bindSimpleValue(
            @jakarta.annotation.Nonnull PersistentProperty property,
            PersistentProperty parentProperty,
            SimpleValue simpleValue,
            String path) {
        bindSimpleValue(property, property, parentProperty, simpleValue, path);
    }

    public void bindSimpleValue(
            @jakarta.annotation.Nonnull PersistentProperty property,
            @jakarta.annotation.Nonnull PersistentProperty typeProperty,
            PersistentProperty parentProperty,
            SimpleValue simpleValue,
            String path) {

        PropertyConfig propertyConfig = ((GrailsHibernatePersistentProperty) property).getMappedForm();
        Mapping mapping = null;
        if (property.getOwner() instanceof GrailsHibernatePersistentEntity persistentEntity) {
            mapping = persistentEntity.getMappedForm();
        }

        final String typeName = typeProperty instanceof GrailsHibernatePersistentProperty ghpp ? ghpp.getTypeName() : null;
        if (typeName == null) {
            if (!(typeProperty instanceof org.grails.datastore.mapping.model.types.Association)) {
                Class<?> type = typeProperty.getType();
                if (type != null) {
                    simpleValue.setTypeName(type.getName());
                }
            }
        } else {
            simpleValue.setTypeName(typeName);
            simpleValue.setTypeParameters(propertyConfig.getTypeParams());
        }

        String generator = propertyConfig.getGenerator();
        if (generator != null && simpleValue instanceof BasicValue basicValue) {
            Properties params = propertyConfig.getTypeParams();
            final Properties generatorProps = new Properties();
            if (params != null) {
                generatorProps.putAll(params);
            }

            if (SEQUENCE_KEY.equals(generator) && generatorProps.containsKey(SEQUENCE_KEY)) {
                generatorProps.put(SequenceStyleGenerator.SEQUENCE_PARAM, generatorProps.getProperty(SEQUENCE_KEY));
            }

            if (GrailsSequenceGeneratorEnum.IDENTITY.toString().equals(generator)) {
                basicValue.setCustomIdGeneratorCreator(context -> {
                    var gen = new org.hibernate.id.IdentityGenerator();
                    context.getProperty().getValue().getColumns().get(0).setIdentity(true);
                    return gen;
                });
            } else if (GrailsSequenceGeneratorEnum.SEQUENCE.toString().equals(generator) || GrailsSequenceGeneratorEnum.SEQUENCE_IDENTITY.toString().equals(generator)) {
                basicValue.setCustomIdGeneratorCreator(context -> {
                    var gen = new org.hibernate.id.enhanced.SequenceStyleGenerator();
                    gen.configure(context.getType(), generatorProps, context.getServiceRegistry());
                    return gen;
                });
            }
        }

        if (propertyConfig.isDerived() && !(property instanceof TenantId)) {
            Formula formula = new Formula();
            formula.setFormula(propertyConfig.getFormula());
            simpleValue.addFormula(formula);
        } else {
            Table table = simpleValue.getTable();

            Optional.ofNullable(propertyConfig.getColumns())
                    .filter(list -> !list.isEmpty())
                    .orElse(Arrays.asList(new ColumnConfig[]{null}))
                    .forEach(cc -> {
                        Column column = new Column();
                        columnConfigToColumnBinder.bindColumnConfigToColumn(column, cc, propertyConfig);
                        columnBinder.bindColumn(property, parentProperty, column, cc, path, table);
                        if (simpleValue instanceof org.hibernate.mapping.DependantValue) {
                            column.setNullable(true);
                        }
                        if (table != null) {
                            table.addColumn(column);
                        }
                        simpleValue.addColumn(column);
                    });
        }
    }
}

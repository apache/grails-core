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
    private final PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig;

    private static final String SEQUENCE_KEY = GrailsSequenceGeneratorEnum.SEQUENCE.toString();

    /**
     * Convenience constructor for namingStrategy and persistentPropertyToPropertyConfig.
     */
    public SimpleValueBinder(PersistentEntityNamingStrategy namingStrategy, PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig) {
        this(namingStrategy, new ColumnConfigToColumnBinder(), new ColumnBinder(namingStrategy), persistentPropertyToPropertyConfig);
    }

    /**
     * Convenience constructor for namingStrategy.
     */
    public SimpleValueBinder(PersistentEntityNamingStrategy namingStrategy) {
        this(namingStrategy, new ColumnConfigToColumnBinder(), new ColumnBinder(namingStrategy), new PersistentPropertyToPropertyConfig());
    }

    /**
     * Public constructor that accepts all collaborators.
     */
    public SimpleValueBinder(
            PersistentEntityNamingStrategy namingStrategy,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            ColumnBinder columnBinder,
            PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig) {
        this.namingStrategy = namingStrategy;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
        this.columnBinder = columnBinder;
        this.persistentPropertyToPropertyConfig = persistentPropertyToPropertyConfig;
    }

    /**
     * Protected constructor for testing purposes.
     */
    protected SimpleValueBinder() {
        this.namingStrategy = null;
        this.columnConfigToColumnBinder = null;
        this.columnBinder = null;
        this.persistentPropertyToPropertyConfig = null;
    }

    public void bindSimpleValue(
            @jakarta.annotation.Nonnull PersistentProperty property,
            PersistentProperty parentProperty,
            SimpleValue simpleValue,
            String path) {
        
        PropertyConfig propertyConfig = persistentPropertyToPropertyConfig.toPropertyConfig(property);
        Mapping mapping = null;
        if (property.getOwner() instanceof GrailsHibernatePersistentEntity persistentEntity) {
            mapping = persistentEntity.getMappedForm();
        }
        
        final String typeName = property instanceof GrailsHibernatePersistentProperty ghpp ? ghpp.getTypeName(mapping) : null;
        if (typeName == null) {
            Class<?> type = property.getType();
            if (type != null) {
                simpleValue.setTypeName(type.getName());
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
                        if (table != null) {
                            table.addColumn(column);
                        }
                        simpleValue.addColumn(column);
                    });
        }
    }
}

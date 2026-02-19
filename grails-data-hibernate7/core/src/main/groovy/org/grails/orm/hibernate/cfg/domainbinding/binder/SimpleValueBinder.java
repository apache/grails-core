package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Optional;

import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

public class SimpleValueBinder {
    private final MetadataBuildingContext metadataBuildingContext;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;
    private final ColumnBinder columnBinder;
    private final JdbcEnvironment jdbcEnvironment;
    private final GrailsSequenceWrapper grailsSequenceWrapper;


    /**
     * Public constructor that accepts all collaborators.
     */
    public SimpleValueBinder(
            MetadataBuildingContext metadataBuildingContext,
            PersistentEntityNamingStrategy namingStrategy,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            ColumnBinder columnBinder,
            JdbcEnvironment jdbcEnvironment,
            GrailsSequenceWrapper grailsSequenceWrapper) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.namingStrategy = namingStrategy;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
        this.columnBinder = columnBinder;
        this.jdbcEnvironment = jdbcEnvironment;
        this.grailsSequenceWrapper = grailsSequenceWrapper;
    }

    /**
     * Convenience constructor for namingStrategy.
     */
    public SimpleValueBinder(MetadataBuildingContext metadataBuildingContext, PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
        this(metadataBuildingContext, namingStrategy, new ColumnConfigToColumnBinder(), new ColumnBinder(namingStrategy), jdbcEnvironment, new GrailsSequenceWrapper());
    }

    /**
     * Protected constructor for testing purposes.
     */
    protected SimpleValueBinder() {
        this.metadataBuildingContext = null;
        this.namingStrategy = null;
        this.columnConfigToColumnBinder = null;
        this.columnBinder = null;
        this.jdbcEnvironment = null;
        this.grailsSequenceWrapper = null;
    }

    public BasicValue bindSimpleValue(
            @jakarta.annotation.Nonnull GrailsHibernatePersistentProperty property,
            GrailsHibernatePersistentProperty parentProperty,
            Table table,
            String path) {
        BasicValue basicValue = new BasicValue(metadataBuildingContext, table);
        bindSimpleValue(property, parentProperty, (SimpleValue) basicValue, path);
        return basicValue;
    }

    public void bindSimpleValue(
            @jakarta.annotation.Nonnull GrailsHibernatePersistentProperty property,
            GrailsHibernatePersistentProperty parentProperty,
            SimpleValue simpleValue,
            String path) {

        PropertyConfig propertyConfig = property.getMappedForm();
        simpleValue.setTypeName(property.getTypeName(simpleValue));
        simpleValue.setTypeParameters(property.getTypeParameters(simpleValue));

        String generator = propertyConfig.getGenerator();
        if (generator != null && simpleValue instanceof BasicValue basicValue) {
            basicValue.setCustomIdGeneratorCreator(context -> createGenerator(property, context, generator));
        }

        if (propertyConfig.isDerived() && !(property instanceof TenantId)) {
            Formula formula = new Formula();
            formula.setFormula(propertyConfig.getFormula());
            simpleValue.addFormula(formula);
        } else {
            Table table = simpleValue.getTable();

            Optional.ofNullable(propertyConfig.getColumns())
                    .filter(list -> !list.isEmpty())
                    .orElse(java.util.Arrays.asList(new ColumnConfig[]{null}))
                    .forEach(cc -> {
                        Column column = new Column();
                        columnConfigToColumnBinder.bindColumnConfigToColumn(column, cc, propertyConfig);
                        columnBinder.bindColumn(property, parentProperty, column, cc, path, table);
                        if (simpleValue instanceof DependantValue) {
                            column.setNullable(true);
                        }
                        if (table != null) {
                            table.addColumn(column);
                        }
                        simpleValue.addColumn(column);
                    });
        }
    }

    private Generator createGenerator(GrailsHibernatePersistentProperty property, GeneratorCreationContext context, String generatorName) {
        return grailsSequenceWrapper.getGenerator(generatorName, context, null, (GrailsHibernatePersistentEntity) property.getHibernateOwner(), jdbcEnvironment);
    }

}

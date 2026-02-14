package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.util.Optional;
import java.util.Properties;

import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.generator.Generator;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.DependantValue;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum;
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

public class SimpleValueBinder {

    private final PersistentEntityNamingStrategy namingStrategy;
    private final ColumnConfigToColumnBinder columnConfigToColumnBinder;
    private final ColumnBinder columnBinder;
    private final JdbcEnvironment jdbcEnvironment;
    private final GrailsSequenceWrapper grailsSequenceWrapper;

    private static final String SEQUENCE_KEY = GrailsSequenceGeneratorEnum.SEQUENCE.toString();

    /**
     * Public constructor that accepts all collaborators.
     */
    public SimpleValueBinder(
            PersistentEntityNamingStrategy namingStrategy,
            ColumnConfigToColumnBinder columnConfigToColumnBinder,
            ColumnBinder columnBinder,
            JdbcEnvironment jdbcEnvironment,
            GrailsSequenceWrapper grailsSequenceWrapper) {
        this.namingStrategy = namingStrategy;
        this.columnConfigToColumnBinder = columnConfigToColumnBinder;
        this.columnBinder = columnBinder;
        this.jdbcEnvironment = jdbcEnvironment;
        this.grailsSequenceWrapper = grailsSequenceWrapper;
    }

    /**
     * Convenience constructor for namingStrategy.
     */
    public SimpleValueBinder(PersistentEntityNamingStrategy namingStrategy, JdbcEnvironment jdbcEnvironment) {
        this(namingStrategy, new ColumnConfigToColumnBinder(), new ColumnBinder(namingStrategy), jdbcEnvironment, new GrailsSequenceWrapper());
    }

    /**
     * Protected constructor for testing purposes.
     */
    protected SimpleValueBinder() {
        this.namingStrategy = null;
        this.columnConfigToColumnBinder = null;
        this.columnBinder = null;
        this.jdbcEnvironment = null;
        this.grailsSequenceWrapper = null;
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

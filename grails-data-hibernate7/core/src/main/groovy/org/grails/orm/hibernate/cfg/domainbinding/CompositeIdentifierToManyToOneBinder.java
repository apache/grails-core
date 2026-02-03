package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.List;

import org.hibernate.mapping.SimpleValue;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ToOne;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.UNDERSCORE;

public class CompositeIdentifierToManyToOneBinder {
    private final ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator;
    private final TableNameFetcher tableNameFetcher;
    private final PersistentEntityNamingStrategy namingStrategy;
    private final DefaultColumnNameFetcher defaultColumnNameFetcher;
    private final BackticksRemover backticksRemover;
    private final SimpleValueBinder simpleValueBinder;

    public CompositeIdentifierToManyToOneBinder(PersistentEntityNamingStrategy namingStrategy){
        this.foreignKeyColumnCountCalculator = new ForeignKeyColumnCountCalculator();
        this.tableNameFetcher = new TableNameFetcher(namingStrategy);
        this.namingStrategy = namingStrategy;
        this.defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy);
        this.backticksRemover = new BackticksRemover();
        this.simpleValueBinder = new SimpleValueBinder(namingStrategy);

    }

    protected CompositeIdentifierToManyToOneBinder(ForeignKeyColumnCountCalculator foreignKeyColumnCountCalculator
    , TableNameFetcher tableNameFetcher
    , PersistentEntityNamingStrategy namingStrategy
    , DefaultColumnNameFetcher defaultColumnNameFetcher
    , BackticksRemover backticksRemover, SimpleValueBinder simpleValueBinder) {
        this.foreignKeyColumnCountCalculator = foreignKeyColumnCountCalculator;
        this.tableNameFetcher =tableNameFetcher;
        this.namingStrategy = namingStrategy;
        this.defaultColumnNameFetcher = defaultColumnNameFetcher;
        this.backticksRemover = backticksRemover;
        this.simpleValueBinder = simpleValueBinder;
    }

    public void bindCompositeIdentifierToManyToOne(Association property,
                                                    SimpleValue value, 
                                                   CompositeIdentity compositeId, 
                                                   PersistentEntity refDomainClass,
                                                    String path) {
        String[] propertyNames = compositeId.getPropertyNames();

        List<ColumnConfig> columns = ((GrailsHibernatePersistentProperty) property).getMappedForm().getColumns();
        int i = columns.size();
        int expectedForeignKeyColumnLength = foreignKeyColumnCountCalculator.calculateForeignKeyColumnCount(refDomainClass, propertyNames);
        if (i != expectedForeignKeyColumnLength) {
            int j = 0;
            for (String propertyName : propertyNames) {
                ColumnConfig cc;
                // if a column configuration exists in the mapping use it
                if (j < i) {
                    cc = columns.get(j++);
                }
                // otherwise create a new one to represent the composite column
                else {
                    cc = new ColumnConfig();
                }
                // if the name is null then configure the name by convention
                if (cc.getName() == null) {
                    // use the referenced table name as a prefix
                    String prefix = tableNameFetcher.getTableName(refDomainClass);
                    PersistentProperty referencedProperty = refDomainClass.getPropertyByName(propertyName);

                    // if the referenced property is a ToOne and it has a composite id
                    // then a column is needed for each property that forms the composite id
                    if (referencedProperty instanceof ToOne toOne) {
                        PersistentProperty[] compositeIdentity = toOne.getAssociatedEntity().getCompositeIdentity();
                        if (compositeIdentity != null) {
                            for (PersistentProperty cip : compositeIdentity) {
                                // for each property of a composite id by default we use the table name and the property name as a prefix
                                String string = namingStrategy.resolveColumnName(referencedProperty.getName());
                                String compositeIdPrefix = backticksRemover.apply(prefix) + UNDERSCORE + backticksRemover.apply(string);

                                String suffix = defaultColumnNameFetcher.getDefaultColumnName(cip);
                                String finalColumnName = backticksRemover.apply(compositeIdPrefix) + UNDERSCORE + backticksRemover.apply(suffix);
                                cc = new ColumnConfig();
                                cc.setName(finalColumnName);
                                columns.add(cc);
                            }
                            continue;
                        }
                    }

                    String suffix = defaultColumnNameFetcher.getDefaultColumnName(referencedProperty);
                    String finalColumnName = backticksRemover.apply(prefix) + UNDERSCORE + backticksRemover.apply(suffix);
                    cc.setName(finalColumnName);
                    columns.add(cc);
                }
            }
        }
        // set type
        simpleValueBinder.bindSimpleValue(property, null, value, path);
    }
}

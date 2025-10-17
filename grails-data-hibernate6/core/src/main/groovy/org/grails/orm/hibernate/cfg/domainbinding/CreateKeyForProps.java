package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.orm.hibernate.cfg.PropertyConfig;

public class CreateKeyForProps {

    private final ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher;
    private final UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator;
    private final PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig;

    public CreateKeyForProps(ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher) {
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.uniqueKeyForColumnsCreator = new UniqueKeyForColumnsCreator();
        this.persistentPropertyToPropertyConfig = new PersistentPropertyToPropertyConfig();
    }
    protected CreateKeyForProps(ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher
            , UniqueKeyForColumnsCreator uniqueKeyForColumnsCreator
    , PersistentPropertyToPropertyConfig persistentPropertyToPropertyConfig) {
        this.columnNameForPropertyAndPathFetcher = columnNameForPropertyAndPathFetcher;
        this.uniqueKeyForColumnsCreator = uniqueKeyForColumnsCreator;
        this.persistentPropertyToPropertyConfig = persistentPropertyToPropertyConfig;
    }

    public void createKeyForProps(PersistentProperty grailsProp, String path, Table table,
                                   String columnName) {
        PropertyConfig mappedForm = persistentPropertyToPropertyConfig.toPropertyConfig(grailsProp);

        if (mappedForm.isUnique() && mappedForm.isUniqueWithinGroup()) {

            List<Column> keyList = new ArrayList<>();
            keyList.add(new Column(columnName));
            List<String> propertyNames = mappedForm.getUniquenessGroup();
            PersistentEntity owner = grailsProp.getOwner();
            for (Iterator<?> i = propertyNames.iterator(); i.hasNext();) {
                String propertyName = (String) i.next();
                PersistentProperty otherProp = owner.getPropertyByName(propertyName);
                if (otherProp == null) {
                    throw new MappingException(owner.getJavaClass().getName() + " references an unknown property " + propertyName);
                }
                String otherColumnName = columnNameForPropertyAndPathFetcher.getColumnNameForPropertyAndPath(otherProp, path, null);
                keyList.add(new Column(otherColumnName));
            }

            uniqueKeyForColumnsCreator.createUniqueKeyForColumns(table, keyList);
        }
    }
}

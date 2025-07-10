package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.mapping.Column;
import org.hibernate.mapping.SimpleValue;

import static java.util.Optional.ofNullable;

public class SimpleValueBinder {

    /**
     * Binds a value for the specified parameters to the meta model.
     *
     * @param simpleValue The simple value instance
     * @param type        The type of the property
     * @param columnName  The property name
     * @param nullable    Whether it is nullable
     */
    public void bindSimpleValue(SimpleValue simpleValue, String type, String columnName, boolean nullable) {
        Column column = new Column();
        column.setNullable(nullable);
        column.setValue(simpleValue);
        column.setName(columnName);
        ofNullable(simpleValue.getTable())
                .ifPresent(
                        table -> table.addColumn(column)
                );
        simpleValue.getSelectables().add(column);
        simpleValue.setTypeName(type);
    }
}

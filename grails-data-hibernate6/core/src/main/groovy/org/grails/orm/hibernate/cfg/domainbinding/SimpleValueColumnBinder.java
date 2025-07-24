package org.grails.orm.hibernate.cfg.domainbinding;

import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.SimpleValue;

import java.util.Optional;

public class SimpleValueColumnBinder {

    /**
     * Binds a value for the specified parameters to the meta model.
     *
     * @param simpleValue The simple value instance
     * @param type        The type of the property
     * @param columnName  The property name
     * @param nullable    Whether it is nullable
     */
    public void bindSimpleValue(SimpleValue simpleValue, String type, String columnName, boolean nullable) {
        Optional.ofNullable(simpleValue.getTable())
                .ifPresentOrElse( table -> {
                    var column = new Column();
                    column.setNullable(nullable);
                    column.setValue(simpleValue);
                    column.setName(columnName);
                    table.addColumn(column);
                    simpleValue.addColumn(column);
                    simpleValue.setTypeName(type);
                }, () -> { throw new MappingException("SimpleValue must have a table");});
    }
}

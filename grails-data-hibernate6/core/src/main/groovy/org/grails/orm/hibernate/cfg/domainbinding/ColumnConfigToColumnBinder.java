package org.grails.orm.hibernate.cfg.domainbinding;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.mapping.Column;

public class ColumnConfigToColumnBinder {

    public void bindColumnConfigToColumn(Column column, ColumnConfig columnConfig, PropertyConfig mappedForm) {
        if (columnConfig == null) {
            return;
        }
        if (columnConfig.getLength() != -1) {
            column.setLength(columnConfig.getLength());
        }
        if (columnConfig.getPrecision() != -1) {
            column.setPrecision(columnConfig.getPrecision());
        }
        if (columnConfig.getScale() != -1) {
            column.setScale(columnConfig.getScale());
        }
        if (columnConfig.getSqlType() != null && !columnConfig.getSqlType().isEmpty()) {
            column.setSqlType(columnConfig.getSqlType());
        }
        if(mappedForm != null && !mappedForm.isUniqueWithinGroup()) {
            column.setUnique(columnConfig.getUnique());
        }
    }
}

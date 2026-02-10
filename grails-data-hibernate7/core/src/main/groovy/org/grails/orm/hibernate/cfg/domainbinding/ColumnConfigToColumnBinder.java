package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.mapping.Column;

import java.util.Optional;

public class ColumnConfigToColumnBinder {

    public void bindColumnConfigToColumn(
             @Nonnull Column column,
             @Nonnull ColumnConfig columnConfig,
            PropertyConfig mappedForm
        ) {
        Optional.of(columnConfig.getLength())
                .filter(l -> l != -1)
                .ifPresent(column::setLength);

        Optional.of(columnConfig.getPrecision())
                .filter(p -> p != -1)
                .ifPresent(column::setPrecision);

        Optional.of(columnConfig.getScale())
                .filter(s -> s != -1)
                .ifPresent(column::setScale);

        Optional.ofNullable(columnConfig.getSqlType())
                .filter(s -> !s.isEmpty())
                .ifPresent(column::setSqlType);

        Optional.ofNullable(mappedForm)
                .filter(mf -> !mf.isUniqueWithinGroup())
                .ifPresent(mf -> column.setUnique(columnConfig.getUnique()));
    }
}

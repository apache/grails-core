package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.hibernate.mapping.Column;

import java.util.Optional;

public class ColumnConfigToColumnBinder {

    public void bindColumnConfigToColumn(
             @Nonnull Column column,
             ColumnConfig columnConfig,
            PropertyConfig mappedForm
        ) {
        Optional.ofNullable(columnConfig).ifPresent(config -> {
            Optional.of(config.getLength())
                    .filter(l -> l != -1)
                    .ifPresent(column::setLength);

            Optional.of(config.getPrecision())
                    .filter(p -> p != -1)
                    .ifPresent(column::setPrecision);

            Optional.of(config.getScale())
                    .filter(s -> s != -1)
                    .ifPresent(column::setScale);

            Optional.ofNullable(config.getSqlType())
                    .filter(s -> !s.isEmpty())
                    .ifPresent(column::setSqlType);

            Optional.ofNullable(mappedForm)
                    .filter(mf -> !mf.isUniqueWithinGroup())
                    .ifPresent(mf -> column.setUnique(config.getUnique()));
        });
    }
}

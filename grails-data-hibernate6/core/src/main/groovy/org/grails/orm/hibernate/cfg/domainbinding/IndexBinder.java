package org.grails.orm.hibernate.cfg.domainbinding;

import jakarta.annotation.Nonnull;
import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import static java.lang.String.format;
import static java.util.Optional.*;

public class IndexBinder {
    public void bindIndex(
           @Nonnull String columnName,
           @Nonnull Column column,
           ColumnConfig cc,
           @Nonnull Table table
    ) {
       ofNullable(cc)
                .map(ColumnConfig::getIndex)
                .flatMap(indexObj -> {
                    if (indexObj instanceof Boolean b) {
                        return b ? of(format("%s_%s_idx", table.getName(), columnName)) : empty();
                    }
                    return of(indexObj.toString());
                })
                .map(def -> def.split(","))
                .ifPresent(indices -> {
                    for (String index : indices) {
                        table.getOrCreateIndex(index.trim()).addColumn(column);
                    }
                });
    }
}

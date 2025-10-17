package org.grails.orm.hibernate.cfg.domainbinding;

import java.util.Collections;
import java.util.List;

import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.orm.hibernate.cfg.GrailsDomainBinder;

public class UniqueKeyForColumnsCreator {

    private final UniqueNameGenerator uniqueNameGenerator;

    public UniqueKeyForColumnsCreator() {
        uniqueNameGenerator = new UniqueNameGenerator();
    }

    protected UniqueKeyForColumnsCreator(UniqueNameGenerator uniqueNameGenerator) {
        this.uniqueNameGenerator = uniqueNameGenerator;
    }

    private static final Logger LOG = LoggerFactory.getLogger(UniqueKeyForColumnsCreator.class);

    public void createUniqueKeyForColumns(Table table, List<Column> columns) {
        Collections.reverse(columns);

        UniqueKey uk = new UniqueKey();
        uk.setTable(table);
        for(Column column : columns) {
            uk.addColumn(column);
        }

        if(LOG.isDebugEnabled()) {
            LOG.debug("create unique key for {} columns = {}", table.getName(), columns);
        }
        uniqueNameGenerator.setGeneratedUniqueName(uk);
        table.addUniqueKey(uk);
    }
}

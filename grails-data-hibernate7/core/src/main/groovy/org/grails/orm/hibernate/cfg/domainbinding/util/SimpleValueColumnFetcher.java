package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.mapping.Column;
import org.hibernate.mapping.SimpleValue;

public class SimpleValueColumnFetcher {
    public Column getColumnForSimpleValue(SimpleValue element) {
        return element.getColumns().isEmpty() ? null : element.getColumns().iterator().next();
    }
}

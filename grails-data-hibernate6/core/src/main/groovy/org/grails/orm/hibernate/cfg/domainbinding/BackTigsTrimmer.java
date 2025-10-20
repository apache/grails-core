package org.grails.orm.hibernate.cfg.domainbinding;

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.BACKTICK;

public class BackTigsTrimmer {

    public String trimBackTigs(String tableName) {
        if (tableName != null && tableName.length() >= 2 && tableName.startsWith(BACKTICK) && tableName.endsWith(BACKTICK)) {
            return tableName.substring(1, tableName.length() - 1);
        }
        return tableName;
    }
}

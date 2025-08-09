package org.grails.orm.hibernate.query;

import jakarta.persistence.criteria.CriteriaQuery;

class CriteriaAndAlias {
    protected CriteriaQuery criteria;
    protected String alias;
    protected String associationPath;


    public CriteriaAndAlias(CriteriaQuery criteria, String alias, String associationPath) {
        this.criteria = criteria;
        this.alias = alias;
        this.associationPath = associationPath;
    }
}

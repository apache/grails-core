package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.grails.orm.hibernate.cfg.NaturalId;

/**
 * A marker interface for single and composite identity configurations in GORM for Hibernate.
 */
public interface HibernateIdentity {
    /**
     * @return The natural id definition
     */
    NaturalId getNatural();

    /**
     * Sets the natural id definition
     * @param natural The natural id definition
     */
    void setNatural(NaturalId natural);
}

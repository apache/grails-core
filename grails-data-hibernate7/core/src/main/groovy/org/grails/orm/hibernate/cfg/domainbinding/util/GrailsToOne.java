package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.mapping.Fetchable;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.SortableValue;


/**
 * Grails interface for Hibernate ToOne mapping.
 * This interface declares the public API expected from a Grails abstraction of ToOne,
 * intended to be implemented by concrete wrapper classes.
 */
public interface GrailsToOne extends KeyValue, Fetchable, SortableValue {


}

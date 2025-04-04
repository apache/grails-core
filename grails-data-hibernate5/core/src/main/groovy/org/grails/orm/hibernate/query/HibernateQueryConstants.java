package org.grails.orm.hibernate.query;

/**
 * Constants used for query arguments etc.
 *
 * @since 3.0.7
 * @author Graeme Rocher
 */
public interface HibernateQueryConstants {

    String ARGUMENT_FETCH_SIZE = "fetchSize";
    String ARGUMENT_TIMEOUT = "timeout";
    String ARGUMENT_READ_ONLY = "readOnly";
    String ARGUMENT_FLUSH_MODE = "flushMode";
    String ARGUMENT_MAX = "max";
    String ARGUMENT_OFFSET = "offset";
    String ARGUMENT_ORDER = "order";
    String ARGUMENT_SORT = "sort";
    String ORDER_DESC = "desc";
    String ORDER_ASC = "asc";
    String ARGUMENT_FETCH = "fetch";
    String ARGUMENT_IGNORE_CASE = "ignoreCase";
    String ARGUMENT_CACHE = "cache";
    String ARGUMENT_LOCK = "lock";
    String CONFIG_PROPERTY_CACHE_QUERIES = "grails.hibernate.cache.queries";
    String CONFIG_PROPERTY_OSIV_READONLY = "grails.hibernate.osiv.readonly";
    String CONFIG_PROPERTY_PASS_READONLY_TO_HIBERNATE = "grails.hibernate.pass.readonly";
}

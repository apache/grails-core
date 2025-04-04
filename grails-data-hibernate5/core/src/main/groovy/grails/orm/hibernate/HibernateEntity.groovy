package grails.gorm.hibernate

import groovy.transform.CompileStatic
import groovy.transform.Generated
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.orm.hibernate.AbstractHibernateGormStaticApi

/**
 * Extends the {@link GormEntity} trait adding additional Hibernate specific methods
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait HibernateEntity<D> extends GormEntity<D> {

    /**
     * Finds all objects for the given string-based query
     *
     * @param sql The query
     *
     * @return The object
     */
    @Generated
    static List<D> findAllWithSql(CharSequence sql) {
        currentHibernateStaticApi().findAllWithSql sql, Collections.emptyMap()
    }

    /**
     * Finds an entity for the given SQL query
     *
     * @param sql The sql query
     * @return The entity
     */
    @Generated
    static D findWithSql(CharSequence sql) {
        currentHibernateStaticApi().findWithSql(sql, Collections.emptyMap())
    }

    /**
     * Finds all objects for the given string-based query
     *
     * @param sql The query
     *
     * @return The object
     */
    @Generated
    static List<D> findAllWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findAllWithSql sql, args
    }

    /**
     * Finds an entity for the given SQL query
     *
     * @param sql The sql query
     * @return The entity
     */
    @Generated
    static D findWithSql(CharSequence sql, Map args) {
        currentHibernateStaticApi().findWithSql(sql, args)
    }

    @Generated
    private static AbstractHibernateGormStaticApi currentHibernateStaticApi() {
        (AbstractHibernateGormStaticApi)GormEnhancer.findStaticApi(this)
    }
}
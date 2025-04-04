package org.grails.gorm.rx.config

/**
 * Additional settings specific to RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface Settings extends org.grails.datastore.mapping.config.Settings{

    /**
     * Whether blocking operations are allowed
     */
    String SETTING_ALLOW_BLOCKING = "${PREFIX}.rx.allowBlocking"
}

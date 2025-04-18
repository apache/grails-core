package org.grails.gorm.graphql.fetcher.context

/**
 * Interface to describe objects that have a locale
 *
 * @author James Kleeh
 * @since 1.0.0
 */
interface LocaleAwareContext {

    Locale getLocale()
}

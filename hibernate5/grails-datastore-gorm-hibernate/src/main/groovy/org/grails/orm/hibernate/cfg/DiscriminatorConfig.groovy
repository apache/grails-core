package org.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

/**
 * Configurations the discriminator
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class DiscriminatorConfig {
    /**
     * The discriminator value
     */
    String value

    /**
     * The column configuration
     */
    ColumnConfig column

    /**
     * The type
     */
    Object type

    /**
     * Whether it is insertable
     */
    Boolean insertable

    /**
     * The formula to use
     */
    String formula

    /**
     * Whether it is insertable
     *
     * @param insertable True if it is insertable
     */
    void setInsert(boolean insertable) {
        this.insertable = insertable
    }

    /**
     * Configures the column
     * @param columnConfig The column config
     * @return This discriminator config
     */
    DiscriminatorConfig column(@DelegatesTo(ColumnConfig) Closure columnConfig) {
        column = new ColumnConfig()
        columnConfig.setDelegate(column)
        columnConfig.setResolveStrategy(Closure.DELEGATE_ONLY)
        columnConfig.call()
        return this
    }
}

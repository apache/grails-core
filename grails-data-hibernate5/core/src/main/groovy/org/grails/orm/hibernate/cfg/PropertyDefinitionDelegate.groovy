package org.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.DatastoreConfigurationException

/**
 * Builder delegate that handles multiple-column definitions for a
 * single domain property, e.g.
 * <pre>
 *   amount type: MonetaryAmountUserType, {
 *       column name: "value"
 *       column name: "currency_code", sqlType: "text"
 *   }
 * </pre>
 *
 */
@CompileStatic
class PropertyDefinitionDelegate {
    PropertyConfig config

    private int index = 0

    PropertyDefinitionDelegate(PropertyConfig config) {
        this.config = config
    }

    ColumnConfig column(Map args) {
        // Check that this column has a name
        if (!args["name"]) {
            throw new DatastoreConfigurationException("Column definition must have a name!")
        }

        // Create a new column configuration based on the mapping for this column.
        ColumnConfig column
        if(index < config.columns.size()) {
            // configure existing
            column = config.columns[0]
        }
        else {
            column = new ColumnConfig()
            // Append the new column configuration to the property config.
            config.columns << column
        }
        column.name = args["name"]
        column.sqlType = args["sqlType"]
        column.enumType = args["enumType"] ?: column.enumType
        column.index = args["index"]
        column.unique = args["unique"] ?: false
        column.length = args["length"] ? args["length"] as Integer : -1
        column.precision = args["precision"] ? args["precision"] as Integer  : -1
        column.scale = args["scale"] ? args["scale"] as Integer : -1

        index++
        return column
    }
}

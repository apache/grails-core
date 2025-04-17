package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer

import java.sql.Blob

/**
 * The default renderer for rendering byte[] or Blob properties
 *
 * @author James Kleeh
 */
class FileInputRenderer implements DomainInputRenderer {

    @Override
    boolean supports(DomainProperty property) {
        property.type in [byte[], Byte[], Blob]
    }

    @Override
    Closure renderInput(Map defaultAttributes, DomainProperty property) {
        defaultAttributes.type = "file"
        return { ->
            input(defaultAttributes)
        }
    }
}

package org.grails.scaffolding.markup

import org.grails.scaffolding.model.property.DomainProperty
import grails.util.GrailsNameUtils
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.context.MessageSource

import jakarta.annotation.Resource

/**
 * @see {@link ContextMarkupRenderer}
 * @author James Kleeh
 */
class ContextMarkupRendererImpl implements ContextMarkupRenderer {

    @Resource
    MessageSource messageSource

    @CompileStatic
    protected String getDefaultTableHeader(DomainProperty property) {
        property.defaultLabel
    }

    @CompileStatic
    protected String getLabelText(DomainProperty property) {
        String labelText
        if (property.labelKeys) {
            labelText = resolveMessage(property.labelKeys, property.defaultLabel)
        }
        if (!labelText) {
            labelText = property.defaultLabel
        }
        labelText
    }

    @CompileStatic
    protected String resolveMessage(List<String> keysInPreferenceOrder, String defaultMessage) {
        def message = keysInPreferenceOrder.findResult { key ->
            messageSource.getMessage(key, [].toArray(), defaultMessage, Locale.default) ?: null
        }
        message ?: defaultMessage
    }

    @CompileStatic
    protected String toPropertyNameFormat(Class type) {
        String propertyNameFormat = GrailsNameUtils.getLogicalPropertyName(type.canonicalName, '')
        if (propertyNameFormat.endsWith('[]')) {
            propertyNameFormat = propertyNameFormat - '[]' + 'Array'
        }
        propertyNameFormat
    }

    @Override
    Closure listOutputContext(PersistentEntity domainClass, List<DomainProperty> properties, Closure content) {
        { ->
            table {
                thead {
                    tr {
                        properties.each {
                            th(getDefaultTableHeader(it))
                        }
                    }
                }
                tbody {
                    tr {
                        properties.each { property ->
                            td(content.call(property))
                        }
                    }
                }
            }
        }
    }

    @Override
    Closure inputContext(PersistentEntity domainClass, Closure content) {
        { ->
            fieldset([class: "form"], content)
        }
    }

    @Override
    Closure inputContext(DomainProperty property, Closure content) {
        List classes = ['fieldcontain']
        if (property.required) {
            classes << 'required'
        }
        { ->
            content.delegate = delegate
            div(class: classes.join(' ')) {
                label([for: property.pathFromRoot], getLabelText(property)) {
                    if (property.required) {
                        span(class: 'required-indicator', '*')
                    }
                }
                content.call()
            }
        }
    }

    @Override
    Closure outputContext(PersistentEntity domainClass, Closure content) {
        { ->
            ol([class: "property-list ${domainClass.decapitalizedName}"], content)
        }
    }

    @Override
    Closure outputContext(DomainProperty property, Closure content) {
        { ->
            li(class: 'fieldcontain') {
                span([id: "${property.pathFromRoot}-label", class: "property-label"], getLabelText(property))
                div([class: "property-value", "aria-labelledby": "${property.pathFromRoot}-label"], content)
            }
        }
    }

    @Override
    Closure embeddedOutputContext(DomainProperty property, Closure content) {
        embeddedInputContext(property, content)
    }

    @Override
    Closure embeddedInputContext(DomainProperty property, Closure content) {
        return { ->
            content.delegate = delegate
            fieldset(class: "embedded ${toPropertyNameFormat(property.type)}") {
                legend(getLabelText(property))
                content.call()
            }
        }
    }

}

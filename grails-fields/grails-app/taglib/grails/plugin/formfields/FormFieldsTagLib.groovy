/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.plugin.formfields

import grails.util.GrailsStringUtils
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.xml.MarkupBuilder
import groovy.xml.MarkupBuilderHelper
import jakarta.servlet.http.HttpServletRequest
import org.grails.buffer.FastStringWriter
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.encoder.CodecLookup
import org.grails.gsp.GroovyPage
import org.grails.scaffolding.model.DomainModelService
import org.grails.scaffolding.model.property.Constrained
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException
import org.springframework.web.servlet.LocaleResolver

import java.sql.Blob
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime

import static FormFieldsTemplateService.toPropertyNameFormat

@Slf4j
class FormFieldsTagLib {

    static final namespace = 'f'

    static final String STACK_PAGE_SCOPE_VARIABLE = 'f:with:stack'
    private static final List<String> DECIMAL_TYPES = ['double', 'float', 'bigdecimal']

    private static final String THEME_ATTR = "theme"
    private static final String WIDGET_ATTR = "widget"
    private static final String WRAPPER_ATTR = "wrapper"
    private static final String TEMPLATES_ATTR = "templates"
    private static final String PROPERTY_ATTR = "property"
    private static final String BEAN_ATTR = "bean"
    public static final String DISPLAY_STYLE = 'displayStyle'

    @Value('${grails.plugin.fields.localizeNumbers:true}')
    Boolean localizeNumbers

    @Value('${grails.plugin.fields.exclusions.list:#{T(java.util.Arrays).asList("id", "dateCreated", "lastUpdated")}}')
    List<String> exclusionsList
    @Value('${grails.plugin.fields.exclusions.input:#{T(java.util.Arrays).asList("version", "dateCreated", "lastUpdated")}}')
    List<String> exclusionsInput
    @Value('${grails.plugin.fields.exclusions.display:#{T(java.util.Arrays).asList("version", "dateCreated", "lastUpdated")}}')
    List<String> exclusionsDisplay

    enum ExclusionType {

        List, Display, Input
    }

    FormFieldsTemplateService formFieldsTemplateService
    BeanPropertyAccessorFactory beanPropertyAccessorFactory
    DomainPropertyFactory fieldsDomainPropertyFactory

    @Autowired(required = false)
    Collection<MappingContext> mappingContexts

    DomainModelService domainModelService
    LocaleResolver localeResolver
    CodecLookup codecLookup
    MessageSource messageSource

    static defaultEncodeAs = [taglib: 'raw']

    class BeanAndPrefix {

        Object bean
        String prefix
        Map<String, Object> innerAttributes = [:]
    }

    class BeanAndPrefixStack extends Stack<BeanAndPrefix> {

        Object getBean() {
            empty() ? null : peek().bean
        }

        String getPrefix() {
            empty() ? null : peek().prefix
        }

        Map<String, Object> getInnerAttributes() {
            empty() ? [:] : peek().innerAttributes
        }

        String toString() {
            bean?.toString() ?: ''
        }
    }

    BeanAndPrefixStack getBeanStack() {
        if (!pageScope.hasVariable(STACK_PAGE_SCOPE_VARIABLE)) {
            pageScope.setVariable(STACK_PAGE_SCOPE_VARIABLE, new BeanAndPrefixStack())
        }
        pageScope.variables[STACK_PAGE_SCOPE_VARIABLE]
    }

    /**
     * @attr bean REQUIRED Name of the source bean in the GSP model.
     * @attr prefix Prefix to add to input element names.
     */
    def with = { attrs, body ->
        if (!attrs[BEAN_ATTR]) throwTagError("Tag [with] is missing required attribute [$BEAN_ATTR]")
        BeanAndPrefix beanPrefix = resolveBeanAndPrefix(attrs[BEAN_ATTR], attrs.prefix, attrs)
        try {
            beanStack.push(beanPrefix)
            out << body()
        } finally {
            beanStack.pop()
        }
    }

    /**
     * @attr bean REQUIRED Name of the source bean in the GSP model.
     * @attr order A comma-separated list of properties to include in provided order
     * @attr except A comma-separated list of properties to exclude from the generated list of input fields
     * @attr prefix Prefix to add to input element names.
     */
    def all = { attrs ->
        if (!attrs[BEAN_ATTR]) throwTagError("Tag [all] is missing required attribute [$BEAN_ATTR]")
        BeanAndPrefix beanPrefix = resolveBeanAndPrefix(attrs[BEAN_ATTR], attrs.prefix, attrs)
        try {
            beanStack.push(beanPrefix)
            def bean = resolveBean(attrs[BEAN_ATTR])
            def prefix = resolvePrefix(attrs.prefix)
            def domainClass = resolveDomainClass(bean)
            if (domainClass) {
                for (property in resolvePersistentProperties(domainClass, attrs)) {
                    out << field([bean: bean, property: property.name, prefix: prefix])
                }
            } else {
                throwTagError('Tag [all] currently only supports domain types')
            }
        } finally {
            beanStack.pop()
        }
    }

    /**
     * @attr bean Name of the source bean in the GSP model.
     * @attr property REQUIRED The name of the property to display. This is resolved
     * against the specified bean or the bean in the current scope.
     * @attr value Specifies the initial value to display in the field. The default is
     * the current value of the property.
     * @attr default A default initial value to display if the actual property value
     * evaluates to {@code false}.
     * @attr required Specifies whether the user is required to enter a value for this
     * property. By default, this is determined by the constraints of the property.
     * @attr invalid Specifies whether this property is invalid or not. By default, this
     * is determined by whether there are any errors associated with it.
     * @attr label Overrides the default label displayed next to the input field.
     * @attr prefix Prefix to add to input element names.
     * @attr wrapper Specify the folder inside _fields where to look up for the wrapper template.
     * @attr widget Specify the folder inside _fields where to look up for the widget template.
     * @attr templates Specify the folder inside _fields where to look up for the wrapper and widget template.
     * @attr theme Theme name
     * @attr css html css attribute for wrapper (default: field-contain)
     * @attr divClass class for optional div that will be added if set and contain the widget (default: no div created)
     * @attr invalidClass class added to wrapper that if property is invalid. (default: error) Also available for widget.
     * @attr requiredClass class added to wrapper when property is required. (default: required) Also available for widget.
     */
    def field = { attrs, body ->
        attrs = beanStack.innerAttributes + attrs
        if (attrs.containsKey(BEAN_ATTR) && !attrs[BEAN_ATTR]) throwTagError("Tag [field] requires a non-null value for attribute [$BEAN_ATTR]")
        if (!attrs[PROPERTY_ATTR]) throwTagError("Tag [field] is missing required attribute [$PROPERTY_ATTR]")

        def bean = resolveBean(attrs.remove(BEAN_ATTR))
        String property = attrs.remove(PROPERTY_ATTR)
        String templatesFolder = attrs.remove(TEMPLATES_ATTR)
        String fieldFolder = attrs.remove(WRAPPER_ATTR)
        String widgetFolder = attrs.remove(WIDGET_ATTR)
        String theme = attrs.remove(THEME_ATTR)

        BeanPropertyAccessor propertyAccessor = resolveProperty(bean, property)
        if (propertyAccessor.domainProperty instanceof Embedded) {
            renderEmbeddedProperties(bean, propertyAccessor, attrs + [templates: templatesFolder, field: fieldFolder, input: widgetFolder, theme: theme])
        } else {
            Map model = buildModel(propertyAccessor, attrs)
            Map wrapperAttrs = [:]
            Map widgetAttrs = [:]

            String prefixAttribute = formFieldsTemplateService.getWidgetPrefix() ?: 'widget-'
            attrs.each { k, v ->
                if (k?.startsWith(prefixAttribute)) {
                    widgetAttrs[k.replace(prefixAttribute, '')] = v
                } else {
                    wrapperAttrs[k] = v
                }
            }

            List widgetClasses = [widgetAttrs['class'] ?: '']
            String cssClass = widgetAttrs.remove('invalidClass')
            if (cssClass && model.invalid) {
                widgetClasses << cssClass
            }
            cssClass = widgetAttrs.remove('requiredClass')
            if (cssClass && model.required) {
                widgetClasses << cssClass
            }
            widgetAttrs['class'] = widgetClasses.join(' ').trim()
            if (widgetAttrs['class'].isEmpty()) {
                widgetAttrs.remove('class')
            }
            if (hasBody(body)) {
                model.widget = raw(body(model + [attrs: widgetAttrs] + widgetAttrs))
            } else {
                model.widget = renderWidget(propertyAccessor, model, widgetAttrs, widgetFolder ?: templatesFolder, theme)
            }

            String templateName = formFieldsTemplateService.getTemplateFor("wrapper")
            Map template = formFieldsTemplateService.findTemplate(propertyAccessor, templateName, fieldFolder ?: templatesFolder, theme)
            if (template) {
                out << render(template: template.path, plugin: template.plugin, model: model + [attrs: wrapperAttrs] + wrapperAttrs)
            } else {
                out << renderDefaultField(model, wrapperAttrs)
            }
        }
    }
    /**
     * Renders a collection of beans in a table
     *
     * @attr collection REQUIRED The collection of beans to render
     * @attr domainClass The FQN of the domain class of the elements in the collection.
     * Defaults to the class of the first element in the collection.
     * @attr properties OPTIONAL The list of properties to be shown (table columns).
     * @attr maxProperties OPTIONAL The number of properties displayed when no explicit properties are given, defaults to 7.
     * @attr displayStyle OPTIONAL Determines the display template used for the bean's properties.
     * Defaults to 'table', meaning that 'display-table' templates will be used when available.
     * @attr order A comma-separated list of properties to include in provided order
     * @attr except A comma-separated list of properties to exclude
     * @attr theme Theme name
     * @attr template OPTIONAL The template used when rendering the collection
     */
    def table = { attrs, body ->
        def collection = resolveBean(attrs.remove('collection'))
        PersistentEntity domainClass
        if (attrs.containsKey('domainClass')) {
            domainClass = resolveDomainClass((String) attrs.remove('domainClass'))
        } else {
            domainClass = (collection instanceof Collection) && collection ? resolveDomainClass(collection.iterator().next()) : null
        }

        if (domainClass) {
            List<BeanPropertyAccessor> properties = resolveProperties(domainClass, attrs)

            List<Map> columnProperties = properties.collect { propertyAccessor ->
                buildColumnModel(propertyAccessor, attrs)
            }

            String displayStyle = attrs.remove(DISPLAY_STYLE)
            String theme = attrs.remove(THEME_ATTR)
            String template = attrs.remove('template') ?: 'table'

            out << render(
                    template: "/templates/_fields/$template",
                    model: attrs + [domainClass     : domainClass,
                                    domainProperties: columnProperties,
                                    columnProperties: columnProperties,
                                    collection      : collection,
                                    displayStyle    : displayStyle,
                                    theme           : theme])
        }
    }

    /**
     * @deprecated since version 1.5 - Use widget instead
     * @attr bean Name of the source bean in the GSP model.
     * @attr property REQUIRED The name of the property to display. This is resolved
     * against the specified bean or the bean in the current scope.
     */
    def input = { attrs ->
        out << widget(attrs)
    }

    /**
     * @attr bean Name of the source bean in the GSP model.
     * @attr property REQUIRED The name of the property to display. This is resolved
     * against the specified bean or the bean in the current scope.
     * @attr theme Theme name
     */
    def widget = { attrs ->
        def bean = resolveBean(attrs.remove(BEAN_ATTR))
        if (!bean) throwTagError("Tag [input] is missing required attribute [$BEAN_ATTR]")
        if (!attrs.property) throwTagError("Tag [input] is missing required attribute [property]")

        attrs = getPrefixedAttributes(beanStack.innerAttributes) + attrs

        String property = attrs.remove(PROPERTY_ATTR)
        String widgetFolder = attrs.remove(WIDGET_ATTR)
        String theme = attrs.remove(THEME_ATTR)

        BeanPropertyAccessor propertyAccessor = resolveProperty(bean, property)
        Map model = buildModel(propertyAccessor, attrs)

        out << renderWidget(propertyAccessor, model, attrs, widgetFolder, theme)
    }

    /**
     * @attr bean Name of the source bean in the GSP model.
     * @attr property REQUIRED The name of the property to display. This is resolved
     * against the specified bean or the bean in the current scope.
     * @attr theme Theme name
     */
    def displayWidget = { attrs ->
        def bean = resolveBean(attrs.remove(BEAN_ATTR))
        if (!bean) throwTagError("Tag [displayWidget] is missing required attribute [$BEAN_ATTR]")
        if (!attrs[PROPERTY_ATTR]) throwTagError("Tag [displayWidget] is missing required attribute [property]")

        attrs = getPrefixedAttributes(beanStack.innerAttributes) + attrs

        String property = attrs.remove(PROPERTY_ATTR)
        String widgetFolder = attrs.remove(WIDGET_ATTR)
        String theme = attrs.remove(THEME_ATTR)

        BeanPropertyAccessor propertyAccessor = resolveProperty(bean, property)
        Map model = buildModel(propertyAccessor, attrs, 'HTML')

        out << renderDisplayWidget(propertyAccessor, model, attrs, widgetFolder, theme)
    }

    /**
     * @attr bean Name of the source bean in the GSP model.
     * @attr property The name of the property to display. This is resolved against the specified bean or the bean in the current scope.
     * @attr order A comma-separated list of properties to include in provided order
     * @attr except A comma-separated list of properties to exclude
     * @attr theme Theme name
     * @attr template OPTIONAL The template used when rendering the domainClass
     */
    def display = { attrs, body ->
        attrs = beanStack.innerAttributes + attrs
        def bean = resolveBean(attrs.remove(BEAN_ATTR))
        if (!bean) throwTagError("Tag [display] is missing required attribute [$BEAN_ATTR]")

        String property = attrs.remove(PROPERTY_ATTR)
        String templatesFolder = attrs.remove(TEMPLATES_ATTR)
        String theme = attrs.remove(THEME_ATTR)

        if (property == null) {
            PersistentEntity domainClass = resolveDomainClass(bean)
            if (domainClass) {
                String template = attrs.remove('template') ?: 'list'

                List properties = resolvePersistentProperties(domainClass, attrs, ExclusionType.Display)
                out << render(template: "/templates/_fields/$template", model: attrs + [domainClass: domainClass, domainProperties: properties]) { prop ->
                    BeanPropertyAccessor propertyAccessor = resolveProperty(bean, prop.name)
                    Map model = buildModel(propertyAccessor, attrs, 'HTML')
                    out << raw(renderDisplayWidget(propertyAccessor, model, attrs, templatesFolder, theme))
                }
            }
        } else {
            String displayFolder = attrs.remove(WRAPPER_ATTR)
            String widgetFolder = attrs.remove(WIDGET_ATTR)

            BeanPropertyAccessor propertyAccessor = resolveProperty(bean, property)
            Map model = buildModel(propertyAccessor, attrs, 'HTML')

            Map wrapperAttrs = [:]
            Map widgetAttrs = [:]

            String prefixAttribute = formFieldsTemplateService.getWidgetPrefix() ?: 'widget-'
            attrs.each { k, v ->
                if (k?.startsWith(prefixAttribute)) {
                    widgetAttrs[k.replace(prefixAttribute, '')] = v
                } else {
                    wrapperAttrs[k] = v
                }
            }


            String wrapperFolderTouse = displayFolder ?: templatesFolder
            String widgetsFolderToUse = widgetFolder ?: templatesFolder

            if (hasBody(body)) {
                model.widget = raw(body(model + [attrs: widgetAttrs] + widgetAttrs))
                model.value = body(model)
            } else {
                model.widget = renderDisplayWidget(propertyAccessor, model, widgetAttrs, widgetsFolderToUse, theme)
            }


            def displayWrapperTemplateName = formFieldsTemplateService.getTemplateFor("displayWrapper")
            def template = formFieldsTemplateService.findTemplate(propertyAccessor, displayWrapperTemplateName, wrapperFolderTouse, theme)
            if (template) {
                out << render(template: template.path, plugin: template.plugin, model: model + [attrs: wrapperAttrs] + wrapperAttrs)
            } else {
                out << raw(renderDisplayWidget(propertyAccessor, model, attrs, widgetsFolderToUse, theme))
            }
        }

    }

    private Map getPrefixedAttributes(attrs) {
        Map widgetAttrs = [:]
        attrs.each { k, v ->
            String prefixAttribute = formFieldsTemplateService.getWidgetPrefix()
            if (k?.startsWith(prefixAttribute)) {
                widgetAttrs[k.replace(prefixAttribute, '')] = v
            }
        }
        return widgetAttrs
    }

    private void renderEmbeddedProperties(bean, BeanPropertyAccessor propertyAccessor, attrs) {
        def legend = resolveMessage(propertyAccessor.labelKeys, propertyAccessor.defaultLabel)
        out << applyLayout(name: '_fields/embedded', params: [type: toPropertyNameFormat(propertyAccessor.propertyType), legend: legend]) {
            Embedded prop = (Embedded) propertyAccessor.domainProperty
            for (embeddedProp in resolvePersistentProperties(prop.associatedEntity, attrs)) {
                def propertyPath = "${propertyAccessor.pathFromRoot}.${embeddedProp.name}"
                out << field(bean: bean, property: propertyPath, prefix: attrs.prefix, default: attrs.default, field: attrs.field, widget: attrs.widget, theme: attrs[THEME_ATTR])
            }
        }
    }

    private BeanPropertyAccessor resolveProperty(bean, String propertyPath) {
        beanPropertyAccessorFactory.accessorFor(bean, propertyPath)
    }

    private List<BeanPropertyAccessor> resolveProperties(PersistentEntity domainClass, Map attrs) {
        // Starting point for the bean property resolver
        def bean = domainClass.javaClass.newInstance()

        List<BeanPropertyAccessor> propertyAccessorList = resolvePropertyNames(domainClass, attrs).collect { String propertyPath ->
            beanPropertyAccessorFactory.accessorFor(bean, propertyPath)
        }

        return propertyAccessorList

    }

    private List<String> resolvePropertyNames(PersistentEntity domainClass, Map attrs) {
        if (attrs.containsKey('order')) {
            return getList(attrs.order)
        } else if (attrs.containsKey('properties')) {
            return getList(attrs.remove('properties'))
        } else {
            List<String> properties = resolvePersistentProperties(domainClass, attrs, ExclusionType.List)*.name
            int maxProperties = attrs.containsKey('maxProperties') ? attrs.remove('maxProperties').toInteger() : 7
            if (maxProperties && properties.size() > maxProperties) {
                properties = properties[0..<maxProperties]
            }
            return properties
        }
    }

    private Map buildColumnModel(BeanPropertyAccessor propertyAccessor, Map attrs) {
        def labelText = resolveLabelText(propertyAccessor, attrs)
        return [
                bean        : propertyAccessor.rootBean,
                name        : propertyAccessor.pathFromRoot, // To please legacy _table.gsp
                property    : propertyAccessor.pathFromRoot,
                type        : propertyAccessor.propertyType,
                defaultLabel: labelText, // To please legacy _table.gsp
                label       : labelText,
                constraints : propertyAccessor.constraints,
                required    : propertyAccessor.required,
        ]
    }


    private Map buildModel(BeanPropertyAccessor propertyAccessor, Map attrs, String encoding = null) {
        def value = attrs.containsKey('value') ? attrs.remove('value') : propertyAccessor.value
        def valueDefault = attrs.remove('default')
        if (value instanceof String && encoding) {
            value = codecLookup.lookupEncoder(encoding).encode(value)
        }

        [
                bean              : propertyAccessor.rootBean,
                property          : propertyAccessor.pathFromRoot,
                type              : propertyAccessor.propertyType,
                beanClass         : propertyAccessor.entity,
                label             : resolveLabelText(propertyAccessor, attrs),
                value             : (value instanceof Number || value instanceof Boolean || value) ? value : valueDefault,
                constraints       : propertyAccessor.constraints,
                persistentProperty: propertyAccessor.domainProperty,
                errors            : propertyAccessor.errors.collect { error ->
                    String errorMsg = null
                    try {
                        errorMsg = error instanceof MessageSourceResolvable ? messageSource.getMessage(error, locale) : messageSource.getMessage(error.toString(), null, locale)
                    } catch (NoSuchMessageException ignored) {
                        // no-op
                    }
                    // unresolved message codes fallback to the defaultMessage and this should
                    // be escaped as it could be an error message with user input
                    errorMsg && errorMsg == error.defaultMessage ? message(error: error, encodeAs: "HTML") : message(error: error)
                },
                required          : attrs.containsKey("required") ? Boolean.valueOf(attrs.remove('required')) : propertyAccessor.required,
                invalid           : attrs.containsKey("invalid") ? Boolean.valueOf(attrs.remove('invalid')) : propertyAccessor.invalid,
                prefix            : resolvePrefix(attrs.remove('prefix')),
        ]
    }

    private CharSequence renderWidget(BeanPropertyAccessor propertyAccessor, Map model, Map attrs = [:], String widgetFolder, String theme) {
        String widgetTemplateName = formFieldsTemplateService.getTemplateFor("widget")
        Map template = formFieldsTemplateService.findTemplate(propertyAccessor, widgetTemplateName, widgetFolder, theme)
        if (template) {
            render template: template.path, plugin: template.plugin, model: model + [attrs: attrs] + attrs
        } else {
            renderDefaultInput propertyAccessor, model, attrs
        }
    }

    private CharSequence renderDisplayWidget(BeanPropertyAccessor propertyAccessor, Map model, Map attrs = [:], String widgetFolder, String theme) {
        String displayStyle = attrs.displayStyle

        Map template = null
        if (displayStyle && displayStyle != 'default') {
            template = formFieldsTemplateService.findTemplate(propertyAccessor, "displayWidget-$attrs.displayStyle", widgetFolder, theme)
        }
        if (!template) {
            def displayWidgetTemplateName = formFieldsTemplateService.getTemplateFor("displayWidget")
            template = formFieldsTemplateService.findTemplate(propertyAccessor, displayWidgetTemplateName, widgetFolder, theme)
        }

        if (template) {
            render template: template.path, plugin: template.plugin, model: model + [attrs: attrs] + attrs
        } else if (!(model.value instanceof CharSequence)) {
            renderDefaultDisplay(model, attrs, widgetFolder, theme)
        } else {
            model.value
        }
    }

    private resolvePageScopeVariable(attributeName) {
        // Tomcat throws NPE if you query pageScope for null/empty values
        attributeName?.toString() ? pageScope.variables[attributeName] : null
    }

    private BeanAndPrefix resolveBeanAndPrefix(beanAttribute, prefixAttribute, attributes) {
        def bean = resolvePageScopeVariable(beanAttribute) ?: beanAttribute
        def prefix = resolvePageScopeVariable(prefixAttribute) ?: prefixAttribute
        def innerAttributes = attributes.clone()
        innerAttributes.remove('bean')
        innerAttributes.remove('prefix')
        //'except' is a reserved word for the 'all' tag: https://github.com/grails-fields-plugin/grails-fields/issues/12
        innerAttributes.remove('except')
        new BeanAndPrefix(bean: bean, prefix: prefix, innerAttributes: innerAttributes)
    }

    private Object resolveBean(beanAttribute) {
        resolvePageScopeVariable(beanAttribute) ?: beanAttribute ?: beanStack.bean
    }

    private String resolvePrefix(prefixAttribute) {
        def prefix = resolvePageScopeVariable(prefixAttribute) ?: prefixAttribute ?: beanStack.prefix
        if (prefix && !prefix.endsWith('.')) {
            prefix = prefix + '.'
        }
        prefix ?: ''
    }

    private PersistentEntity resolveDomainClass(bean) {
        resolveDomainClass(bean.getClass())
    }

    private PersistentEntity resolveDomainClass(Class beanClass) {
        resolveDomainClass(beanClass.name)
    }

    private PersistentEntity resolveDomainClass(String beanClassName) {
        mappingContexts.findResult { MappingContext mappingContext ->
            mappingContext.getPersistentEntity(beanClassName)
        }
    }

    private List<PersistentProperty> resolvePersistentProperties(PersistentEntity domainClass, Map attrs, ExclusionType exclusionType = ExclusionType.Input) {
        List<PersistentProperty> properties

        boolean list = exclusionType == ExclusionType.List
        if (attrs.order) {
            def orderBy = getList(attrs.order)
            if (attrs.except) {
                orderBy = orderBy - getList(attrs.except)
            }
            properties = orderBy.collect { propertyName ->
                fieldsDomainPropertyFactory.build(domainClass.getPropertyByName(propertyName))
            }
        } else {
            properties = list ? domainModelService.getListOutputProperties(domainClass) : domainModelService.getInputProperties(domainClass,
                    exclusionType == ExclusionType.Input ? exclusionsInput : exclusionsDisplay)
            // If 'except' is not set, but 'list' is, exclude 'id', 'dateCreated' and 'lastUpdated' by default
            List<String> blacklist = attrs.containsKey('except') ? getList(attrs.except) : (list ? exclusionsList : [])

            properties.removeAll { it.name in blacklist }
        }

        return properties
    }

    private static List<String> getList(def except, List<String> defaultList = []) {
        if (!except) {
            return defaultList
        } else if (except instanceof String) {
            except?.tokenize(',')*.trim()
        } else if (except instanceof Collection) {
            return except as List<String>
        } else {
            throw new IllegalArgumentException("Must either be null, comma separated string or java.util.Collection")
        }
    }

    private boolean hasBody(Closure body) {
        return body && !body.is(GroovyPage.EMPTY_BODY_CLOSURE)
    }

    private CharSequence resolveLabelText(BeanPropertyAccessor propertyAccessor, Map attrs) {
        def labelText
        def label = attrs.remove('label')
        if (label) {
            labelText = message(code: label, default: label)
        }
        if (!labelText && propertyAccessor.labelKeys) {
            labelText = resolveMessage(propertyAccessor.labelKeys, propertyAccessor.defaultLabel)
        }
        if (!labelText) {
            labelText = propertyAccessor.defaultLabel
        }
        return labelText
    }

    private CharSequence resolveMessage(List<String> keysInPreferenceOrder, String defaultMessage) {
        def message = keysInPreferenceOrder.findResult { key ->
            message(code: key, default: null) ?: null
        }
        if (log.traceEnabled && !message) {
            log.trace("i18n missing translation for one of ${keysInPreferenceOrder}")
        }
        message ?: defaultMessage
    }

    protected CharSequence renderDefaultField(Map model, Map attrs = [:]) {
        List classes = [attrs['class'] ?: 'fieldcontain']
        if (model.invalid) classes << (attrs.remove('invalidClass') ?: 'error')
        if (model.required) classes << (attrs.remove('requiredClass') ?: 'required')
        attrs['class'] = classes.join(' ').trim()
        Writer writer = new FastStringWriter()
        def mb = new MarkupBuilder(writer)
        mb.setDoubleQuotes(true)
        mb.div(class: attrs['class']) {
            label(class: attrs.labelClass, for: (model.prefix ?: '') + model.property, model.label) {
                if (model.required) {
                    span(class: 'required-indicator', '*')
                }
            }
            if (attrs.divClass) {
                div(class: attrs.divClass) {
                    renderWidget(mkp, model)
                }
            } else {
                renderWidget(mkp, model)
            }
        }
        writer.buffer
    }

    private void renderWidget(MarkupBuilderHelper mkp, Map model) {
        // TODO: encoding information of widget gets lost - don't use MarkupBuilder
        def widget = model.widget
        if (widget != null) {
            mkp.yieldUnescaped widget
        }
    }

    CharSequence renderDefaultInput(Map model, Map attrs = [:]) {
        renderDefaultInput(null, model, attrs)
    }

    CharSequence renderDefaultInput(BeanPropertyAccessor propertyAccessor, Map model, Map attrs = [:]) {
        Constrained constrained = (Constrained) model.constraints
        attrs.name = (model.prefix ?: '') + model.property
        attrs.value = model.value
        if (model.required) attrs.required = ""
        // TODO: configurable how this gets output? Some people prefer required="required"
        if (model.invalid) attrs.invalid = ""
        if (!isEditable(constrained)) attrs.readonly = ""

        boolean oneToOne = false
        boolean manyToOne = false
        boolean manyToMany = false
        boolean oneToMany = false
        if (model.containsKey('persistentProperty')) {
            if (model.persistentProperty instanceof PersistentProperty) {
                PersistentProperty prop = (PersistentProperty) model.persistentProperty
                oneToOne = prop instanceof OneToOne
                manyToOne = prop instanceof ManyToOne
                manyToMany = prop instanceof ManyToMany
                oneToMany = prop instanceof OneToMany
            }
        }

        // TODO: https://github.com/gpc/fields/issues/392
        boolean datePicker = model.type in [Date, Calendar, java.sql.Date, java.sql.Time, LocalDate, LocalDateTime]
        if (!datePicker) {
            attrs.remove('selectDateClass')
        }
        boolean checkBox = model.type in [boolean, Boolean]
        String checkBoxClass = attrs.remove('checkBoxClass')
        if (checkBox && checkBoxClass) {
            attrs['class'] = checkBoxClass
        }

        if (model.type in [String, null]) {
            return renderStringInput(model, attrs)
        } else if (checkBox) {
            return g.checkBox(attrs)
        } else if (model.type.isPrimitive() || model.type in Number) {
            return renderNumericInput(propertyAccessor, model, attrs)
        } else if (model.type in URL) {
            return g.field(attrs + [type: "url"])
        } else if (model.type.isEnum()) {
            return renderEnumInput(model, attrs)
        } else if (oneToOne || manyToOne || manyToMany) {
            return renderAssociationInput(model, attrs)
        } else if (oneToMany) {
            return renderOneToManyInput(model, attrs)
        } else if (datePicker) {
            return renderDateTimeInput(model, attrs)
        } else if (model.type in [byte[], Byte[], Blob]) {
            return g.field(attrs + [type: "file"])
        } else if (model.type in [TimeZone, Currency, Locale]) {
            if (!model.required) attrs.noSelection = ["": ""]
            return g."${GrailsStringUtils.uncapitalize(model.type.simpleName)}Select"(attrs)
        } else {
            return null
        }
    }


    CharSequence renderDateTimeInput(Map model, Map attrs) {
        attrs.precision = model.type in [java.sql.Time, LocalDateTime] ? "minute" : "day"
        if (!model.required) {
            attrs.noSelection = ["": ""]
            attrs.default = "none"
        }
        return g.datePicker(attrs)
    }

    CharSequence renderStringInput(Map model, Map attrs) {
        Constrained constrained = (Constrained) model.constraints

        if (!attrs.type) {
            if (constrained?.inList) {
                attrs.from = constrained.inList
                if (!model.required) {
                    attrs.noSelection = ["": ""]
                }
                return g.select(attrs)
            } else if (constrained?.password) {
                attrs.type = "password"
                attrs.remove('value')
            } else if (constrained?.email) {
                attrs.type = "email"
            } else if (constrained?.url) {
                attrs.type = "url"
            } else {
                attrs.type = "text"
            }
        }

        if (constrained?.matches) {
            attrs.pattern = constrained.matches
        }
        if (constrained?.maxSize) {
            attrs.maxlength = constrained.maxSize
        }

        if (constrained?.widget == 'textarea') {
            attrs.remove('type')
            return g.textArea(attrs)
        }
        return g.field(attrs)
    }

    CharSequence renderNumericInput(BeanPropertyAccessor propertyAccessor, Map model, Map attrs) {
        Constrained constrained = (Constrained) model.constraints

        if (!attrs.type && constrained?.inList) {
            attrs.from = constrained.inList
            if (!model.required) attrs.noSelection = ["": ""]
            return g.select(attrs)
        } else if (constrained?.range) {
            attrs.type = attrs.type ?: "range"
            attrs.min = constrained.range.from
            attrs.max = constrained.range.to
        } else {
            attrs.type = attrs.type ?: getDefaultNumberType(model)
            if (constrained?.scale != null) attrs.step = "0.${'0' * (constrained.scale - 1)}1"
            if (constrained?.min != null) attrs.min = constrained.min
            if (constrained?.max != null) attrs.max = constrained.max
        }

        if (localizeNumbers && propertyAccessor?.value != null) {
            attrs.value = numberFormatter.format(propertyAccessor.value)
        }

        return g.field(attrs)
    }

    @CompileStatic
    protected NumberFormat getNumberFormatter() {
        NumberFormat.getInstance(getLocale())
    }

    @CompileStatic
    protected Locale getLocale() {
        def locale
        def request = GrailsWebRequest.lookup()?.currentRequest
        if (request instanceof HttpServletRequest) {
            locale = localeResolver?.resolveLocale(request)
        }
        if (locale == null) {
            locale = Locale.default
        }
        return locale
    }

    @CompileStatic
    protected String getDefaultNumberType(Map model) {
        Class modelType = (Class) model.type

        def typeName = modelType.simpleName.toLowerCase()
        if (typeName in DECIMAL_TYPES) {
            return "number decimal"
        } else {
            return "number"
        }

    }

    private CharSequence renderEnumInput(Map model, Map attrs) {
        Constrained constrained = (Constrained) model.constraints

        if (attrs.value instanceof Enum)
            attrs.value = attrs.value.name()
        if (!model.required) attrs.noSelection = ["": ""]

        if (constrained?.inList) {
            attrs.keys = constrained.inList*.name()
            attrs.from = constrained.inList
        } else {
            attrs.keys = model.type.values()*.name()
            attrs.from = model.type.values()
        }
        return g.select(attrs)
    }

    private CharSequence renderAssociationInput(Map model, Map attrs) {
        attrs.id = (model.prefix ?: '') + model.property
        def persistentProperty = model.persistentProperty
        Class referencedPropertyType
        boolean manyToMany = false
        if (persistentProperty instanceof Association) {
            Association prop = ((Association) persistentProperty)
            PersistentEntity entity = prop.getAssociatedEntity()
            if (entity != null) {
                referencedPropertyType = entity.getJavaClass()
            } else if (prop.isBasic()) {
                referencedPropertyType = ((Basic) prop).getComponentType()
            }
            manyToMany = prop instanceof ManyToMany
        }
        attrs.from = null != attrs.from ? attrs.from : referencedPropertyType.list()
        attrs.optionKey = "id" // TODO: handle alternate id names
        if (manyToMany) {
            attrs.multiple = ""
            attrs.value = model.value*.id
            attrs.name = "${model.prefix ?: ''}${model.property}"
        } else {
            if (!model.required) attrs.noSelection = ["null": ""]
            attrs.value = model.value?.id
            attrs.name = "${model.prefix ?: ''}${model.property}.id"
        }
        return g.select(attrs)
    }

    private CharSequence renderOneToManyInput(Map model, Map attrs) {
        Writer buffer = new FastStringWriter()
        buffer << '<ul>'
        def persistentProperty = model.persistentProperty
        def controllerName
        def shortName
        if (persistentProperty instanceof Association) {
            Association prop = ((Association) persistentProperty)
            controllerName = prop.associatedEntity.decapitalizedName
            shortName = prop.associatedEntity.javaClass.simpleName
        }

        attrs.value.each {
            buffer << '<li>'
            buffer << g.link(controller: controllerName, action: "show", id: it.id, it.toString().encodeAsHTML())
            buffer << '</li>'
        }
        buffer << '</ul>'
        def referencedTypeLabel = message(code: "${controllerName}.label", default: shortName)
        def addLabel = g.message(code: 'default.add.label', args: [referencedTypeLabel])
        PersistentEntity beanClass = (PersistentEntity) model.beanClass
        buffer << g.link(controller: controllerName, action: "create", params: [("${beanClass.decapitalizedName}.id".toString()): model.bean.id], addLabel)
        buffer.buffer
    }

    private CharSequence renderDefaultDisplay(Map model, Map attrs = [:], String templatesFolder, String theme) {
        def persistentProperty = model.persistentProperty
        if (persistentProperty instanceof Association) {
            if (persistentProperty.embedded) {
                return (attrs.displayStyle == 'table') ? model.value?.toString().encodeAsHTML() :
                        displayEmbedded(model.value, persistentProperty.associatedEntity, attrs, templatesFolder, theme)
            } else if (persistentProperty instanceof OneToMany || persistentProperty instanceof ManyToMany) {
                return displayAssociationList(model.value, ((Association) persistentProperty).associatedEntity)
            } else {
                return displayAssociation(model.value, ((Association) persistentProperty).associatedEntity)
            }
        }

        switch (model.type) {
            case Boolean.TYPE:
            case Boolean:
                g.formatBoolean(boolean: model.value)
                break
            case Calendar:
            case Date:
            case java.sql.Date:
            case java.sql.Time:
            case LocalDate:
            case LocalDateTime:
                g.formatDate(date: model.value)
                break
            default:
                g.fieldValue bean: model.bean, field: model.property
        }
    }

    private CharSequence displayEmbedded(bean, PersistentEntity domainClass, Map attrs, String templatesFolder, String theme) {
        Writer buffer = new FastStringWriter()
        def properties = domainModelService.getOutputProperties(domainClass)
        buffer << render(template: "/templates/_fields/list", model: [domainClass: domainClass, domainProperties: properties]) { prop ->
            def propertyAccessor = resolveProperty(bean, prop.name)
            def model = buildModel(propertyAccessor, attrs)
            out << raw(renderDisplayWidget(propertyAccessor, model, attrs, templatesFolder, theme))
        }
        buffer.buffer
    }

    private CharSequence displayAssociationList(values, PersistentEntity referencedDomainClass) {
        Writer buffer = new FastStringWriter()
        buffer << '<ul>'
        for (value in values) {
            buffer << '<li>'
            buffer << displayAssociation(value, referencedDomainClass)
            buffer << '</li>'
        }
        buffer << '</ul>'
        buffer.buffer
    }

    private CharSequence displayAssociation(value, PersistentEntity referencedDomainClass) {
        if (value && referencedDomainClass) {
            g.link(controller: referencedDomainClass.decapitalizedName, action: "show", id: value.id, value.toString().encodeAsHTML())
        } else if (value) {
            value.toString()
        }
    }

    private boolean isEditable(Constrained constraints) {
        !constraints || constraints.editable
    }

}

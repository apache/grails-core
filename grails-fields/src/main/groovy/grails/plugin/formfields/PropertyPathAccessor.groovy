package grails.plugin.formfields

import grails.core.GrailsDomainClass
import grails.gorm.validation.DefaultConstrainedProperty
import grails.util.GrailsNameUtils
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.scaffolding.model.property.Constrained
import org.springframework.context.support.StaticMessageSource
import org.springframework.validation.FieldError

import static grails.plugin.formfields.BeanPropertyAccessorFactory.stripIndex
import static java.util.Collections.EMPTY_LIST
import static grails.util.GrailsStringUtils.substringAfterLast

@CompileStatic
@Canonical
@ToString(includes = ['beanType', 'propertyName', 'propertyType'])
class PropertyPathAccessor implements BeanPropertyAccessor {

    final String pathFromRoot
    final String propertyName = stripIndex pathFromRoot.contains('.') ? substringAfterLast(pathFromRoot, '.') : pathFromRoot
    final Class beanType = null
    final Class propertyType = Object

    PropertyPathAccessor(String pathFromRoot) {
        this.pathFromRoot = pathFromRoot
    }

    String getDefaultLabel() {
        GrailsNameUtils.getNaturalName(propertyName)
    }

    Object getRootBean() { null }

    Class getRootBeanType() { null }

    GrailsDomainClass getBeanClass() { null }

    PersistentEntity getEntity() { null }

    List<Class> getBeanSuperclasses() { EMPTY_LIST }

    List<Class> getPropertyTypeSuperclasses() { EMPTY_LIST }

    Object getValue() { null }

    Constrained getConstraints() {
        new Constrained(new DefaultConstrainedProperty(Object, propertyName, String, new DefaultConstraintRegistry(new StaticMessageSource())))
    }

    PersistentProperty getDomainProperty() { null }

    List<String> getLabelKeys() { EMPTY_LIST }

    List<FieldError> getErrors() { EMPTY_LIST }

    boolean isRequired() { false }

    boolean isInvalid() { false }
}

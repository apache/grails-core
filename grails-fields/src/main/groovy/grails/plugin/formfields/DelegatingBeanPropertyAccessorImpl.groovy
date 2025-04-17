package grails.plugin.formfields

import grails.core.GrailsDomainClass
import grails.util.GrailsNameUtils
import grails.validation.Validateable
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.scaffolding.model.property.Constrained
import org.grails.scaffolding.model.property.DomainProperty
import org.springframework.validation.FieldError

@CompileStatic
@Canonical
@ToString(includes = ['beanType', 'propertyName', 'propertyType'])
class DelegatingBeanPropertyAccessorImpl implements BeanPropertyAccessor {

    private DomainProperty domainProperty
    private Object rootBean
    private Object value
    private String pathFromRoot
    final Class beanType
    final String propertyName
    final Class propertyType
    final boolean addPathFromRoot

    DelegatingBeanPropertyAccessorImpl(Object rootBean, Object value, Class propertyType, String pathFromRoot, DomainProperty domainProperty, boolean addPathFromRoot) {
        this.rootBean = rootBean
        this.value = value
        this.pathFromRoot = pathFromRoot
        this.domainProperty = domainProperty
        this.propertyType = propertyType
        this.propertyName = domainProperty.name
        this.beanType = domainProperty.beanType
        this.addPathFromRoot = addPathFromRoot
    }

    @Override
    Object getRootBean() {
        rootBean
    }

    @Override
    Class getRootBeanType() {
        rootBean.getClass()
    }

    @Override
    String getPathFromRoot() {
        pathFromRoot
    }

    @Override
    @Deprecated
    GrailsDomainClass getBeanClass() {
        throw new UnsupportedOperationException()
    }

    @Override
    PersistentEntity getEntity() {
        domainProperty.domainClass
    }

    @Override
    List<Class> getBeanSuperclasses() {
        BeanPropertyAccessorImpl.getSuperclassesAndInterfaces(beanType)
    }

    @Override
    List<Class> getPropertyTypeSuperclasses() {
        BeanPropertyAccessorImpl.getSuperclassesAndInterfaces(propertyType)
    }

    @Override
    Object getValue() {
        value
    }

    @Override
    PersistentProperty getDomainProperty() {
        domainProperty.persistentProperty
    }

    @Override
    Constrained getConstraints() {
        domainProperty.constrained
    }

    @Override
    List<String> getLabelKeys() {
        List labelKeys = []
        if (rootBean) {
            labelKeys.add("${GrailsNameUtils.getPropertyName(rootBeanType.simpleName)}.${pathFromRoot}.label".replaceAll(/\[(.+)\]/, ''))
            if (addPathFromRoot) {
                labelKeys.add("${pathFromRoot}.label".replaceAll(/\[(.+)\]/, ''))
            }
        }
        labelKeys.addAll(domainProperty.labelKeys)
        labelKeys.unique() as List<String>
    }

    @Override
    String getDefaultLabel() {
        domainProperty.defaultLabel
    }

    @Override
    List<FieldError> getErrors() {
        if (rootBean instanceof Validateable) {
            return (rootBean as Validateable).errors.getFieldErrors(pathFromRoot)
        } else if (rootBean instanceof GormValidateable) {
            return (rootBean as GormValidateable).errors.getFieldErrors(pathFromRoot)
        }
        return []
    }

    @Override
    boolean isRequired() {
        domainProperty.required
    }

    @Override
    boolean isInvalid() {
        !errors.isEmpty()
    }

    @Override
    int hashCode() {
        return Objects.hash(beanType, propertyName, propertyType)
    }

    @Override
    boolean equals(Object obj) {
        this.hashCode() == obj?.hashCode()
    }
}

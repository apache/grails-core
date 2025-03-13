package org.grails.orm.hibernate.support

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.datastore.mapping.validation.ValidationErrors
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.codehaus.groovy.runtime.StringGroovyMethods
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.OneToOne
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.core.convert.ConversionService
import org.springframework.validation.Errors
import org.springframework.validation.FieldError

/**
 * Utility methods used at runtime by the GORM for Hibernate implementation
 *
 * @author Graeme Rocher
 * @since 4.0
 */
@CompileStatic
class HibernateRuntimeUtils {
    private static ProxyHandler proxyHandler = new HibernateProxyHandler();

    private static final String DYNAMIC_FILTER_ENABLER = "dynamicFilterEnabler";

    @SuppressWarnings("rawtypes")
    public static void enableDynamicFilterEnablerIfPresent(SessionFactory sessionFactory, Session session) {
        if (sessionFactory != null && session != null) {
            final Set definedFilterNames = sessionFactory.getDefinedFilterNames();
            if (definedFilterNames != null && definedFilterNames.contains(DYNAMIC_FILTER_ENABLER))
                session.enableFilter(DYNAMIC_FILTER_ENABLER); // work around for HHH-2624
        }
    }

    /**
     * Initializes the Errors property on target.  The target will be assigned a new
     * Errors property.  If the target contains any binding errors, those binding
     * errors will be copied in to the new Errors property.
     *
     * @param target object to initialize
     * @return the new Errors object
     */
    public static Errors setupErrorsProperty(Object target) {

        boolean isGormValidateable = target instanceof GormValidateable

        MetaClass mc = isGormValidateable ? null : GroovySystem.metaClassRegistry.getMetaClass(target.getClass())
        def errors = new ValidationErrors(target)

        Errors originalErrors = isGormValidateable ? ((GormValidateable)target).getErrors() : (Errors) mc.getProperty(target, GormProperties.ERRORS)
        for (Object o in originalErrors.fieldErrors) {
            FieldError fe = (FieldError)o
            if (fe.isBindingFailure()) {
                errors.addError(new FieldError(fe.getObjectName(),
                        fe.field,
                        fe.rejectedValue,
                        fe.bindingFailure,
                        fe.codes,
                        fe.arguments,
                        fe.defaultMessage))
            }
        }

        if(isGormValidateable) {
            ((GormValidateable)target).setErrors(errors)
        }
        else {
            mc.setProperty(target, GormProperties.ERRORS, errors);
        }
        return errors;
    }

    public static void autoAssociateBidirectionalOneToOnes(PersistentEntity entity, Object target) {
        def mappingContext = entity.mappingContext
        for (Association association :  entity.associations) {
            if (!(association instanceof OneToOne) || !association.bidirectional || !association.owningSide) {
                continue
            }

            def propertyName = association.name
            if (!proxyHandler.isInitialized(target, propertyName)) {
                continue
            }

            def otherSide = association.inverseSide

            if (otherSide == null) {
                continue
            }


            def entityReflector = mappingContext.getEntityReflector(entity)
            Object inverseObject = entityReflector.getProperty(target, propertyName)
            if (inverseObject == null) {
                continue
            }

            def otherSidePropertyName = otherSide.getName()
            if (!proxyHandler.isInitialized(inverseObject, otherSidePropertyName)) {
                continue
            }

            def associationReflector = mappingContext.getEntityReflector(association.associatedEntity)
            def propertyValue = associationReflector.getProperty(inverseObject, otherSidePropertyName)
            if (propertyValue == null) {
                associationReflector.setProperty(inverseObject, otherSidePropertyName, target)
            }
        }
    }

    static Object convertValueToType(Object passedValue, Class targetType, ConversionService conversionService) {
        // workaround for GROOVY-6127, do not assign directly in parameters before it's fixed
        Object value = passedValue
        if(targetType != null && value != null && !(value in targetType)) {
            if (value instanceof CharSequence) {
                value = value.toString()
                if(value in targetType) {
                    return value
                }
            }
            try {
                if (value instanceof Number && (targetType==Long || targetType==Integer)) {
                    if(targetType == Long) {
                        value = ((Number)value).toLong()
                    } else {
                        value = ((Number)value).toInteger()
                    }
                } else if (value instanceof String && targetType in Number) {
                    String strValue = value.trim()
                    if(targetType == Long) {
                        value = Long.parseLong(strValue)
                    } else if (targetType == Integer) {
                        value = Integer.parseInt(strValue)
                    } else {
                        value = StringGroovyMethods.asType(strValue, targetType)
                    }
                } else {
                    value = conversionService.convert(value, targetType)
                }
            } catch (e) {
                // ignore
            }
        }
        return value
    }
}

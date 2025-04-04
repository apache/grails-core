package org.grails.datastore.gorm.services.implementers

import grails.gorm.multitenancy.TenantService
import grails.gorm.transactions.TransactionService
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.transform.trait.Traits
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.multitenancy.transform.TenantTransform
import org.grails.datastore.gorm.services.ServiceImplementer
import org.grails.datastore.gorm.transactions.transform.TransactionalTransform
import org.grails.datastore.gorm.transform.AstMethodDispatchUtils
import org.grails.datastore.gorm.transform.AstPropertyResolveUtils
import org.grails.datastore.mapping.core.Ordered
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.MultiTenantCapableDatastore
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.datastore.mapping.services.ServiceRegistry
import org.grails.datastore.mapping.transactions.TransactionCapableDatastore

import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.grails.datastore.gorm.transform.AstMethodDispatchUtils.*
/**
 * Abstract implementation of the {@link ServiceImplementer} interface
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractServiceImplementer implements PrefixedServiceImplementer, Ordered {

    @Override
    int getOrder() {
        return 0
    }

    @Override
    boolean doesImplement(ClassNode domainClass, MethodNode methodNode) {
        def alreadyImplemented = methodNode.getNodeMetaData(IMPLEMENTED)

        String prefix = resolvePrefix(methodNode)
        if(!alreadyImplemented && prefix) {
            ClassNode returnType = methodNode.returnType
            return isCompatibleReturnType(domainClass, methodNode, returnType, prefix)
        }
        return false
    }

    @Override
    String resolvePrefix(MethodNode mn) {
        return handledPrefixes.find() { String it -> mn.name.startsWith(it) }
    }
    /**
     * Return true if the provided return type is compatible with this implementor.
     *
     * @param domainClass The domain class this method applies to
     * @param methodNode The method
     * @param returnType The return type
     * @return True if it is a compatible return type
     */
    protected abstract boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix)


    /**
     * Copies annotation from the abstract method to the implementation method
     *
     * @param abstractMethod the abstract method
     * @param impl the implementation method
     */
    protected void copyClassAnnotations(final MethodNode abstractMethod, final MethodNode impl) {
        List<AnnotationNode> annotations = abstractMethod.getAnnotations()
        for (AnnotationNode annotation in annotations) {
            if (annotation.getClassNode() != Traits.TRAIT_CLASSNODE) {
                impl.addAnnotation(annotation)
            }
        }
    }

    /**
     * Check whether the given parameter is a valid property of the domain class
     *
     * @param domainClassNode The domain class
     * @param parameter The parameter
     * @param parameterName The parameter name
     * @return True if it is
     */
    protected boolean isValidParameter(ClassNode domainClassNode, Parameter parameter, String parameterName) {
        if(GormProperties.IDENTITY.equals(parameterName)) {
            return true
        }
        else {
            ClassNode propertyType = AstPropertyResolveUtils.getPropertyType(domainClassNode, parameterName)
            if(propertyType != null && (propertyType == parameter.type || AstUtils.isSubclassOf(parameter.type, propertyType.name))) {
                return true
            }
        }
        return false
    }

    /**
     * @return The datastore expression
     */
    protected Expression datastore() {
        return propX(varX("this"), "targetDatastore")
    }

    /**
     * @return The datastore expression
     */
    protected Expression transactionalDatastore() {
        return castX( ClassHelper.make(TransactionCapableDatastore), propX(varX("this"), "targetDatastore"))
    }

    /**
     * @return The datastore expression
     */
    protected Expression multiTenantDatastore() {
        return castX( ClassHelper.make(MultiTenantCapableDatastore), propX(varX("this"), "targetDatastore"))
    }

    /**
     * @return The tenant service
     */
    protected Expression tenantService() {
        return callD(ServiceRegistry, "targetDatastore", "getService", classX(make(TenantService)) )
    }

    /**
     * @return The transaction service
     */
    protected Expression transactionService() {
        return callD(ServiceRegistry, "targetDatastore", "getService", classX(make(TransactionService)) )
    }

    protected Expression findConnectionId(MethodNode methodNode) {
        if(TenantTransform.hasTenantAnnotation(methodNode)) {
            return callD(classX(ClassHelper.make(MultiTenancySettings)), "resolveConnectionForTenantId", args(
                propX(multiTenantDatastore(), "multiTenancyMode"), callD(tenantService(), "currentId")
            ))
        }
        else {
            AnnotationNode ann = TransactionalTransform.findTransactionalAnnotation(methodNode)
            Expression connectionId = ann?.getMember("connection")
            if(connectionId == null) {
                connectionId= ann?.getMember("value")
            }
            return connectionId
        }
    }

    protected Expression buildInstanceApiLookup(ClassNode domainClass, Expression connectionId) {
        return AstMethodDispatchUtils.callD(
            classX(GormEnhancer), "findInstanceApi", args(classX(domainClass), connectionId)
        )
    }

    protected Expression buildStaticApiLookup(ClassNode domainClass, Expression connectionId) {
        return AstMethodDispatchUtils.callD(
                classX(GormEnhancer), "findStaticApi", args(classX(domainClass), connectionId)
        )
    }

    protected Expression findInstanceApiForConnectionId(ClassNode domainClass, MethodNode methodNode) {
        Expression connectionId = findConnectionId(methodNode)
        if(connectionId != null) {
            return buildInstanceApiLookup(domainClass, connectionId)
        }
        else {
            return classX(domainClass.plainNodeReference)
        }
    }

    protected Expression findStaticApiForConnectionId(ClassNode domainClass, MethodNode methodNode) {
        Expression connectionId = findConnectionId(methodNode)
        if(connectionId != null) {
            return buildStaticApiLookup(domainClass, connectionId)
        }
        else {
            return classX(domainClass.plainNodeReference)
        }
    }
}

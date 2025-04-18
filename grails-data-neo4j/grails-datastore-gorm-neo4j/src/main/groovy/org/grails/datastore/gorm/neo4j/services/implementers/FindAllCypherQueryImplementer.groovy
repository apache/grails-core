package org.grails.datastore.gorm.neo4j.services.implementers

import grails.neo4j.services.Cypher
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.control.SourceUnit
import org.grails.datastore.gorm.neo4j.services.transform.CypherQueryStringTransformer
import org.grails.datastore.gorm.services.implementers.AnnotatedServiceImplementer
import org.grails.datastore.gorm.services.implementers.FindAllStringQueryImplementer
import org.grails.datastore.gorm.services.transform.QueryStringTransformer

import java.lang.annotation.Annotation

/**
 * A cypher query implementer
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class FindAllCypherQueryImplementer extends FindAllStringQueryImplementer implements AnnotatedServiceImplementer<Cypher> {

    @Override
    protected Class<? extends Annotation> getAnnotationType() {
        Cypher
    }

    @Override
    protected boolean isCompatibleReturnType(ClassNode domainClass, MethodNode methodNode, ClassNode returnType, String prefix) {
        return super.isCompatibleReturnType(domainClass, methodNode, returnType, prefix)
    }

    @Override
    protected QueryStringTransformer createQueryStringTransformer(SourceUnit sourceUnit, VariableScope scope) {
        return new CypherQueryStringTransformer(sourceUnit, scope)
    }
}

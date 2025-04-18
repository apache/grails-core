package org.grails.datastore.gorm.neo4j.services.transform

import grails.neo4j.Relationship
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.control.SourceUnit
import org.grails.datastore.gorm.neo4j.RelationshipPersistentEntity
import org.grails.datastore.gorm.services.transform.QueryStringTransformer
import org.grails.datastore.mapping.reflect.AstUtils

/**
 * Customized transformer that works with Cypher queries
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class CypherQueryStringTransformer extends QueryStringTransformer {
    CypherQueryStringTransformer(SourceUnit sourceUnit, VariableScope variableScope) {
        super(sourceUnit, variableScope)
    }

    @Override
    protected String formatDomainClassVariable(ClassNode domainType, String variableName) {
        if(AstUtils.implementsInterface(domainType, Relationship.name)) {
            ClassNode relInterface = domainType.getInterfaces().find() { ClassNode ifce -> ifce.name == Relationship.name }
            ClassNode from = relInterface.genericsTypes[0].type.plainNodeReference
            ClassNode to = relInterface.genericsTypes[1].type.plainNodeReference
            declaredQueryTargets.put(RelationshipPersistentEntity.FROM, from)
            declaredQueryTargets.put(RelationshipPersistentEntity.TO, to)
            return "${formatNodeVariable(RelationshipPersistentEntity.FROM, from)}-[$variableName]->${formatNodeVariable(RelationshipPersistentEntity.TO, to)}"
        }
        else {
            return formatNodeVariable(variableName, domainType)
        }
    }

    protected String formatNodeVariable(String variableName, ClassNode domainType) {
        "($variableName:$domainType.nameWithoutPackage)"
    }
}

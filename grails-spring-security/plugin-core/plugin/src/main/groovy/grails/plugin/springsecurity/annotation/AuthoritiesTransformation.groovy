/* Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.springsecurity.annotation

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.springframework.util.StringUtils

import groovy.transform.CompileStatic

/**
 * See http://burtbeckwith.com/blog/?p=1398 for the motivation for this.
 *
 * @author Burt Beckwith
 */
@CompileStatic
@GroovyASTTransformation(phase=CompilePhase.CANONICALIZATION)
class AuthoritiesTransformation implements ASTTransformation {

	protected static ClassNode SECURED = new ClassNode(Secured)

	void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
		ASTNode firstNode = astNodes[0]
		ASTNode secondNode = astNodes[1]
		try {
			if (!(firstNode instanceof AnnotationNode) || !(secondNode instanceof AnnotatedNode)) {
				throw new IllegalArgumentException("Internal error: wrong types: ${firstNode.getClass().name} / ${secondNode.getClass().name}")
			}

			AnnotationNode rolesAnnotationNode = (AnnotationNode) firstNode
			AnnotatedNode annotatedNode = (AnnotatedNode) secondNode

			AnnotationNode secured = createAnnotation(rolesAnnotationNode, sourceUnit)
			if (secured) {
				annotatedNode.addAnnotation secured
			}
		}
		catch (e) {
			reportError e.message, sourceUnit, firstNode
		}
	}

	protected AnnotationNode createAnnotation(AnnotationNode rolesNode, SourceUnit sourceUnit) throws IOException {
		Expression value = rolesNode.members.value
		if (!(value instanceof ConstantExpression)) {
			reportError("annotation @Authorities value isn't a ConstantExpression: $value", sourceUnit, rolesNode)
			return null
		}

		String fieldName = value.text
		String[] authorityNames = getAuthorityNames(fieldName, rolesNode, sourceUnit)
		if (authorityNames == null) {
			return null
		}

		buildAnnotationNode authorityNames
	}

	protected AnnotationNode buildAnnotationNode(String[] authorityNames) {
		AnnotationNode securedAnnotationNode = new AnnotationNode(SECURED)
		List<Expression> nameExpressions = authorityNames.collect { String authorityName ->
			new ConstantExpression(authorityName)
		} as List<Expression>
		securedAnnotationNode.addMember 'value', new ListExpression(nameExpressions)
		securedAnnotationNode
	}

	protected String[] getAuthorityNames(String fieldName, AnnotationNode rolesNode, SourceUnit sourceUnit) throws IOException {

		Properties properties = new Properties()
		File propertyFile = new File('roles.properties')
		if (!propertyFile.exists()) {
			reportError('Property file roles.properties not found', sourceUnit, rolesNode)
			return null
		}

		properties.load new FileReader(propertyFile)

		def value = properties.getProperty(fieldName)
		if (value == null) {
			reportError("No value for property '$fieldName'", sourceUnit, rolesNode)
			return null
		}

		List<String> names = []
		for (String auth in StringUtils.commaDelimitedListToStringArray(value.toString())) {
			auth = auth.trim()
			if (auth) {
				names << auth
			}
		}

		names as String[]
	}

	protected void reportError(String message, SourceUnit sourceUnit, ASTNode node) {
		SyntaxException se = new SyntaxException(message, node.lineNumber, node.columnNumber)
		sourceUnit.errorCollector.addErrorAndContinue new SyntaxErrorMessage(se, sourceUnit)
	}
}

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

package org.grails.databinding.compiler;

import java.util.Map;

import org.apache.grails.common.compiler.GroovyTransformOrder;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.TransformWithPriority;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class BindingFormatASTTransformation implements ASTTransformation, TransformWithPriority {

	@Override
	public void visit(final ASTNode[] astNodes, final SourceUnit source) {
		if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof FieldNode)) {
			throw new RuntimeException("Internal error: wrong types: $node.class / $parent.class");
		}

		final AnnotationNode annotationNode = (AnnotationNode) astNodes[0];
		final FieldNode fieldNode = (FieldNode) astNodes[1];
		final Map<String, Expression> members = annotationNode.getMembers();
		if(members == null || (!members.containsKey("code") && !members.containsKey("value"))) {
			final String message = "The @BindingFormat annotation on the field ["
					+ fieldNode.getName() +
					"] in class [" +
					fieldNode.getDeclaringClass().getName() +
					"] must provide a value for either the value() or code() attribute.";
			
			error(source, fieldNode, message);
		}
	}
	
	protected void error(final SourceUnit sourceUnit, final ASTNode astNode, final String message) {
		final SyntaxException syntaxException = new SyntaxException(message, astNode.getLineNumber(), astNode.getColumnNumber());
		final SyntaxErrorMessage syntaxErrorMessage = new SyntaxErrorMessage(syntaxException, sourceUnit);
		sourceUnit.getErrorCollector().addError(syntaxErrorMessage, false);
	}

	@Override
	public int priority() {
		return GroovyTransformOrder.BINDING_FORMAT_ORDER;
	}
}

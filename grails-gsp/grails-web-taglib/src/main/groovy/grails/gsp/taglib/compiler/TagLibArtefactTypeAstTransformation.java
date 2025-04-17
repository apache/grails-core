package grails.gsp.taglib.compiler;

import grails.gsp.TagLib;

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.grails.compiler.injection.ArtefactTypeAstTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class TagLibArtefactTypeAstTransformation extends ArtefactTypeAstTransformation {
    private static final ClassNode MY_TYPE = new ClassNode(TagLib.class);
    
    @Override
    protected String resolveArtefactType(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode) {
        return "TagLibrary";
    }

    @Override
    protected ClassNode getAnnotationType() {
        return MY_TYPE;
    }
}

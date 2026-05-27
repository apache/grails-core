package org.grails.datastore.mapping.config.groovy

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.document.config.Attribute
import org.grails.datastore.mapping.document.config.Collection
import spock.lang.Specification

class DefaultMappingConfigurationBuilderSpec extends Specification {

    void "mapping property in builder.properties is preserved when target.propertyConfigs has a different instance for the same key"() {
        // Exercises the changed code path in getProperties() directly. When
        // builder.properties (populated by the mapping closure via invokeMethod)
        // and target.propertyConfigs (populated by an alternate path such as
        // entity.property(name, map), or a constraint evaluator that writes
        // directly to propertyConfigs) both have an entry for the same key,
        // the builder's mapping-configured instance must win — otherwise
        // mapping-set fields like index=true and indexAttributes are silently
        // lost on the way out of getProperties().
        given:
        def entity = new Collection()
        def builder = new DefaultMappingConfigurationBuilder(entity, TestAttribute)

        and: "the mapping closure has configured 'name' with index:true"
        builder.evaluate {
            name index: true, indexAttributes: [unique: true]
        }
        TestAttribute mappingSet = builder.@properties['name'] as TestAttribute
        assert mappingSet.isIndex()
        assert mappingSet.indexAttributes == [unique: true]

        and: "an alternate path independently populates target.propertyConfigs for the same key"
        // Simulates: entity.property('name', [unique: true]) — direct Entity API,
        // or a constraint evaluator (e.g., ConstrainedPropertyBuilder) that
        // writes to propertyConfigs without entering invokeMethod.
        def constraintInstance = new TestAttribute()
        constraintInstance.unique = true
        entity.propertyConfigs['name'] = constraintInstance

        when:
        def properties = builder.getProperties()
        TestAttribute nameConfig = properties['name'] as TestAttribute

        then: "the mapping-configured instance wins (index and indexAttributes preserved)"
        nameConfig != null
        nameConfig.is(mappingSet)
        nameConfig.isIndex()
        nameConfig.indexAttributes == [unique: true]
    }

    void "target.propertyConfigs entries fill in for keys the mapping closure never touched"() {
        // The fix uses putIfAbsent semantics — propertyConfigs entries for keys
        // NOT present in builder.properties must still be carried through, so
        // that constraint-only configuration (validation rules, etc.) is not
        // dropped when the mapping closure didn't touch that property at all.
        given:
        def entity = new Collection()
        def builder = new DefaultMappingConfigurationBuilder(entity, TestAttribute)

        and: "the mapping closure touched 'name' but not 'email'"
        builder.evaluate {
            name index: true, indexAttributes: [unique: true]
        }

        and: "an alternate path populated propertyConfigs for the untouched 'email'"
        def emailFromConstraint = new TestAttribute()
        emailFromConstraint.unique = true
        entity.propertyConfigs['email'] = emailFromConstraint

        when:
        def properties = builder.getProperties()

        then: "'name' is still the mapping-configured instance"
        properties['name'] != null
        properties['name'].isIndex()

        and: "'email' is the propertyConfigs instance (not overwritten by an empty default)"
        properties['email'] != null
        properties['email'].is(emailFromConstraint)
        properties['email'].unique
    }

    void "constraints closure does not overwrite mapping properties (standard DSL flow)"() {
        // The original-bug-reproduction case: both closures go through the
        // builder's invokeMethod and reuse the same instance, so target.propertyConfigs
        // typically stays empty for this flow. Included as a regression guard
        // to ensure the standard mapping+constraints path keeps working.
        given:
        def entity = new Collection()
        def builder = new DefaultMappingConfigurationBuilder(entity, TestAttribute)

        when:
        builder.evaluate {
            name index: true, indexAttributes: [unique: true]
        }
        builder.evaluate {
            name unique: true
        }
        def properties = builder.getProperties()
        TestAttribute nameConfig = properties['name'] as TestAttribute

        then:
        nameConfig != null
        nameConfig.isIndex()
        nameConfig.indexAttributes == [unique: true]
        nameConfig.unique
    }

    void "mapping properties survive when no constraint or propertyConfigs entry exists"() {
        given:
        def entity = new Collection()
        def builder = new DefaultMappingConfigurationBuilder(entity, TestAttribute)

        Closure mapping = {
            name index: true, indexAttributes: [unique: true]
        }

        when:
        builder.evaluate(mapping)
        def properties = builder.getProperties()
        TestAttribute nameConfig = properties['name'] as TestAttribute

        then:
        nameConfig != null
        nameConfig.isIndex()
        nameConfig.indexAttributes == [unique: true]
    }

    @groovy.transform.CompileStatic
    @groovy.transform.builder.Builder(builderStrategy = groovy.transform.builder.SimpleStrategy, prefix = '')
    static class TestAttribute extends Attribute {
        private Map indexAttributes

        Map getIndexAttributes() { indexAttributes }

        void setIndexAttributes(Map indexAttributes) {
            if (this.indexAttributes == null) {
                this.indexAttributes = indexAttributes
            } else {
                this.indexAttributes.putAll(indexAttributes)
            }
        }
    }
}

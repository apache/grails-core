package org.grails.datastore.gorm.neo4j

import grails.neo4j.Relationship
import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.HandleMetaClass
import org.grails.datastore.gorm.neo4j.mapping.config.NodeConfig
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.AbstractPersistentEntity
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.model.types.Association
import org.neo4j.driver.types.Entity
import org.springframework.util.ClassUtils

import java.beans.Introspector
import static org.grails.datastore.gorm.neo4j.RelationshipPersistentEntity.*


/**
 * Represents an entity mapped to the Neo4j graph, adding support for dynamic labelling
 *
 * @author Stefan Armbruster
 * @author Graeme Rocher
 *
 * @since 1.0
 *
 */
@CompileStatic
class GraphPersistentEntity extends AbstractPersistentEntity<NodeConfig> {

    public static final String LABEL_SEPARATOR = ':'
    protected static final String MATCH = "MATCH %s"
    protected static final String MATCH_ID = "$MATCH WHERE %s = \$id"
    protected final NodeConfig mappedForm
    protected final Collection<String> staticLabels = []
    protected Collection<Object> labelObjects
    protected final boolean hasDynamicLabels
    protected final boolean hasDynamicAssociations
    protected final boolean relationshipEntity
    protected final GraphClassMapping classMapping
    protected final String batchId
    protected final String variableId
    protected String batchCreateStatement
    protected IdGenerator idGenerator
    protected IdGenerator.Type idGeneratorType
    protected boolean assignedId = false
    protected boolean nativeId = false
    protected PersistentProperty nodeId

    GraphPersistentEntity(Class javaClass, MappingContext context) {
        this(javaClass, context, false)
    }

    GraphPersistentEntity(Class javaClass, MappingContext context, boolean external) {
        super(javaClass, context)
        if(isExternal()) {
            this.mappedForm = null
        }
        else {
            this.mappedForm = (NodeConfig) context.getMappingFactory().createMappedForm(this)
        }
        this.relationshipEntity = this instanceof RelationshipPersistentEntity
        this.hasDynamicAssociations = mappedForm.isDynamicAssociations()
        this.hasDynamicLabels = establishLabels()
        this.external = external
        this.classMapping = new GraphClassMapping(this, context)
        this.variableId = Introspector.decapitalize(javaClass.simpleName)
        this.batchId = "${variableId}Batch"
    }


    @Override
    void initialize() {
        if(!isInitialized()) {

            super.initialize()

            if(!isExternal()) {
                PersistentProperty identity = getIdentity()
                if(identity != null) {
                    String generatorType = ((Property) identity.getMapping().getMappedForm()).getGenerator()
                    this.idGenerator = createIdGenerator(generatorType)
                    if(identity.name != GormProperties.IDENTITY) {
                        Class clazz = getJavaClass()
                        MetaClass metaClass = clazz.getMetaClass()
                        MetaProperty idProp = metaClass.getMetaProperty(GormProperties.IDENTITY)
                        if(idProp != null && Long.class.isAssignableFrom(idProp.getType())) {
                            MappingFactory mappingFactory = mappingContext.mappingFactory
                            nodeId = mappingFactory.createSimple(this, context, mappingFactory.createPropertyDescriptor(javaClass, idProp))
                            propertiesByName.put(GormProperties.IDENTITY, nodeId)
                        }
                    }
                }
            }
        }
    }

    boolean isRelationshipEntity() {
        return relationshipEntity
    }

    protected IdGenerator createIdGenerator(String generatorType) {
        try {
            IdGenerator.Type type = generatorType == null ? IdGenerator.Type.NATIVE : IdGenerator.Type.valueOf(generatorType.toUpperCase())
            idGeneratorType = type

            switch(type) {
                case IdGenerator.Type.NATIVE:
                    // for the native generator use null to indicate that generation requires an insert
                    nativeId = true
                    return null
                case IdGenerator.Type.ASSIGNED:
                    assignedId = true
                    ((Property) getIdentity().getMapping().getMappedForm()).setUnique(true)
                    return null
                case IdGenerator.Type.SNOWFLAKE:
                    return ((Neo4jMappingContext)mappingContext).getSnowflakeIdGenerator()
                default:
                    def generator = ((Neo4jMappingContext) mappingContext).getIdGenerator()
                    if(generator == null) {
                        nativeId = true
                    }
                    return generator
            }
        } catch (IllegalArgumentException e) {
            try {
                def generatorClass = ClassUtils.forName(generatorType)
                if(IdGenerator.isAssignableFrom(generatorClass)) {
                    idGeneratorType = IdGenerator.Type.CUSTOM
                    return (IdGenerator)generatorClass.newInstance()
                }
                else {
                    throw new DatastoreConfigurationException("Entity $javaClass defines an invalid id generator [$generatorClass]. The class must implement the IdGenerator interface")
                }
            } catch (Throwable e2) {
                def types = IdGenerator.Type.values().collect() { Enum en -> "'${en.name()}'" }.join(',')
                throw new DatastoreConfigurationException("Entity $javaClass defines an invalid id generator [$generatorType]. Should be one of ${types} or a class that implements the IdGenerator interface",e2 )
            }
        }
    }

    @Override
    ClassMapping<NodeConfig> getMapping() {
        return this.classMapping
    }

    /**
     * @return The ID generator to use
     */
    IdGenerator getIdGenerator() {
        return idGenerator
    }

    /**
     * @return The ID generator type
     */
    IdGenerator.Type getIdGeneratorType() {
        return idGeneratorType
    }

    /**
     * @return Whether the ID is assigned
     */
    boolean isAssignedId() {
        return assignedId
    }

    /**
     * @return The id used for batch parameters
     */
    String getBatchId() {
        return batchId
    }

    /**
     * @return The batch create statement
     */
    String getBatchCreateStatement() {
        if(this.batchCreateStatement == null) {
            this.batchCreateStatement = formatBatchCreate("\$${batchId}")
        }
        return batchCreateStatement
    }
    /**
     * Format a reference to the ID for cypher queries
     * @param variable The name of the variable for the id
     * @return The formatted id
     */
    String formatId(String variable = CypherBuilder.NODE_VAR) {
        switch (idGeneratorType) {
            case IdGenerator.Type.NATIVE:
                return "ID($variable)"
            case IdGenerator.Type.SNOWFLAKE:
                return "${variable}.${CypherBuilder.IDENTIFIER}"
            default:
                return "${variable}.${identity.name}"
        }
    }

    /**
     * Format a reference to the ID for cypher queries
     * @param variable The name of the variable for the id
     * @return The formatted id
     */
    String formatReturnId(String variable = CypherBuilder.NODE_VAR) {
        return " RETURN ${formatId(variable)} as ${GormProperties.IDENTITY}"
    }

    /**
     * Formats a dynamic association query
     * @param variable The variable to use
     * @return The query which accepts an $id argument
     */
    String formatDynamicAssociationQuery(String variable = CypherBuilder.NODE_VAR) {
        """${formatMatch(variable)}-[r]-(o) WHERE ${formatId(variable)} = \$${GormProperties.IDENTITY} RETURN type(r) as relType, startNode(r) = $variable as out, r.sourceType as sourceType, r.targetType as targetType, {ids: collect(${formatId("o")}), labels: collect(labels(o))} as values"""
    }

    /**
     * Reads the id from given Neo4j entity
     *
     * @param entity The entity
     * @return the id
     */
    Serializable readId(Entity entity) {
        switch (idGeneratorType) {
            case IdGenerator.Type.NATIVE:
                return entity.id() as Serializable
            case IdGenerator.Type.SNOWFLAKE:
                return entity.get(CypherBuilder.IDENTIFIER).asNumber()
            default:
                return entity.get(identity.name).asObject() as Serializable
        }
    }

    /**
     * @return The property that is the node id
     */
    PersistentProperty getNodeId() {
        return nodeId
    }
    /**
     * Format a reference to the ID for cypher queries
     * @param variable The name of the variable for the id
     * @return The formatted id
     */
    String formatProperty(String variable, String property) {
        if(property == identity.name) {
            return formatId(variable)
        }
        else if(nodeId != null && property == nodeId.name) {
            return "ID($variable)"
        }
        else {
            return "${variable}.${property}"
        }
    }

    /**
     * Format a match for a node to this entity node
     * @param variable The name of the variable for the id of the node
     * @return The formatted match
     */
    String formatNode(String variable, Object o = null) {
        String labels = hasDynamicLabels() && o != null ? getLabelsAsString(o) : labelsAsString
        return "(${variable}${labels})"
    }

    /**
     * Formats a match for the ID for this entity
     *
     * @param variable The ID
     * @return The match for the ID
     */
    String formatMatchId(String variable = CypherBuilder.NODE_VAR, Object o = null) {
        return String.format(MATCH_ID, formatNode(variable, o), formatId(variable))
    }

    /**
     * Formats a match for the ID for this entity
     *
     * @param variable The ID
     * @return The match for the ID
     */
    String formatMatch(String variable = CypherBuilder.NODE_VAR, Object o = null) {
        return String.format(MATCH, formatNode(variable, o), formatId(variable))
    }
    /**
     * Formats a match for the ID for this entity
     *
     * @param variable The ID
     * @return The match for the ID
     */
    String formatMatchAndUpdate(String variable, Map<String, Object> props) {
        StringBuilder builder = new StringBuilder( formatMatchId(variable) )
        Class clazz = Long
        if(isVersioned() && hasProperty(GormProperties.VERSION, clazz)) {
            builder.append(" AND ${variable}.version=\$version")
        }
        builder.append(" SET ").append(variable).append(" +=\$props")
        Set keysToRemove = []
        for(key in props.keySet()) {
            Object v = props.get(key)
            if(v == null) {
                builder.append(", ${variable}.").append(key).append(" = NULL")
            }
            else if(v instanceof Collection && ((Collection)v).isEmpty()) {
                keysToRemove.add(key)
            }
        }
        if(!keysToRemove.isEmpty()) {
            builder.append(" REMOVE ")
            def i = keysToRemove.iterator()
            while(i.hasNext()) {
                String key = i.next()
                builder.append(variable).append(".").append(key)
                props.remove(key)
                if(i.hasNext()) {
                    builder.append(", ")
                }
            }
        }

        builder.append(CypherBuilder.RETURN).append(formatId(variable))
    }
    /**
     * Formats a batch UNWIND statement for the given id
     *
     * @param batchId The batch id
     * @return The formatted id
     */
    String formatBatchCreate(String batchId) {
        """UNWIND ${batchId} as row
CREATE ($variableId$labelsAsStringWithInheritance)
SET $variableId += row.${CypherBuilder.PROPS}
"""
    }

    /**
     * Formats a batch FOREACH statement for populating association data
     *
     * @param parentVariable The parent variable
     * @param association The association
     * @return The formatted FOREACH statement
     */
    String formatBatchCreate(String parentVariable, Association association) {
        String batchId = association.name
        String variableId = association.isCircular() ? 'child' : variableId
        """FOREACH (${batchId} IN row.${batchId} |
CREATE ($variableId$labelsAsStringWithInheritance)
SET $variableId += ${batchId}.${CypherBuilder.PROPS}
${formatAssociationMerge(association, parentVariable, variableId)})"""
    }

    /**
     * Formats an association merge
     * @param association The association
     * @param start The start variable
     * @param end The end variable
     * @return The MERGE statement
     */
    String formatAssociationMerge(Association association, String start, String end) {
        "MERGE ($start)${RelationshipUtils.matchForAssociation(association)}($end)\n"
    }

    /**
     * Formats an association match
     *
     * @param association The association
     * @param var The variable name to use for the relationship. Defaults to 'r"
     * @param start The start variable name
     * @param end The relationship variable name
     * @return The match
     */
    String formatAssociationMatch(Association association, String var = CypherBuilder.REL_VAR, String start = FROM, String end = TO) {
        GraphPersistentEntity parent = (GraphPersistentEntity)association.owner
        GraphPersistentEntity child = (GraphPersistentEntity)association.associatedEntity

        String associationMatch = calculateAssociationMatch(parent, child, association, var)
        return "MATCH ${parent.formatNode(start)}${associationMatch}${child.formatNode(end)}"
    }

    /**
     * Formats an association match from an existing matched node
     *
     * @param association The association
     * @param var The variable name to use for the relationship. Defaults to 'r"
     * @param start The start variable name
     * @param end The relationship variable name
     * @return The match
     */
    String formatAssociationMatchFromExisting(Association association, String var = CypherBuilder.REL_VAR, String start = FROM, String end = TO) {
        return "MATCH ${formatAssociationMatchFromExisting(association, var, start, end)}"
    }

    /**
     * Formats an association match from an existing matched node
     *
     * @param association The association
     * @param var The variable name to use for the relationship. Defaults to 'r"
     * @param start The start variable name
     * @param end The relationship variable name
     * @return The match
     */
    String formatAssociationPatternFromExisting(Association association, String var = CypherBuilder.REL_VAR, String start = FROM, String end = TO) {
        GraphPersistentEntity parent = (GraphPersistentEntity)association.owner
        GraphPersistentEntity child = (GraphPersistentEntity)association.associatedEntity
        String associationMatch = calculateAssociationMatch(parent,child, association, var)
        if(child.isRelationshipEntity()) {
            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity)child
            child = (GraphPersistentEntity) (relEntity.from.associatedEntity == parent ? relEntity.to.associatedEntity : relEntity.from.associatedEntity)
        }
        return "(${start})${associationMatch}${child.formatNode(end)}"
    }

    protected String calculateAssociationMatch(GraphPersistentEntity parent, GraphPersistentEntity child,Association association, String var) {
        String associationMatch
        if (parent.isRelationshipEntity()) {
            if (association.name == FROM || association.name == TO) {
                RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) parent
                associationMatch = RelationshipUtils.matchForRelationshipEntity(association, relEntity)
            } else {
                throw new IllegalStateException("Relationship entities cannot have associations")
            }

        }
        else if(child.isRelationshipEntity()) {
            RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) child
            associationMatch = RelationshipUtils.matchForRelationshipEntity(association, relEntity, var)
        }
        else {
            associationMatch = RelationshipUtils.matchForAssociation(association, var)
        }
        return associationMatch
    }

    /**
     * Formats an association merge
     * @param association The association
     * @param start The start variable
     * @param end The end variable
     * @return The DELETE statement or null if it isn't possible
     */
    String formatAssociationDelete(Association association, Object entity = null) {

        GraphPersistentEntity parent = (GraphPersistentEntity)association.owner
        GraphPersistentEntity child = (GraphPersistentEntity)association.associatedEntity
        String associationMatch
        if(parent.isRelationshipEntity() && entity instanceof Relationship) {
            if(association.name == FROM) {
                RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity)parent
                child = relEntity.getToEntity()
                parent = relEntity.getFromEntity()
                associationMatch = RelationshipUtils.toMatch(association, (Relationship) entity)
            }
            else {
                return null
            }

        }
        else {
            associationMatch = RelationshipUtils.matchForAssociation(association, CypherBuilder.REL_VAR)
        }

        if(RelationshipUtils.useReversedMappingFor(association)) {
            return """MATCH ${parent.formatNode(FROM)}${associationMatch}${child.formatNode(TO)}
WHERE ${parent.formatId(FROM)} = {${GormProperties.IDENTITY}}
DELETE r"""
        }
        else {
            return """MATCH ${parent.formatNode(FROM)}${associationMatch}${child.formatNode(TO)}
WHERE ${parent.formatId(FROM)} = {${CypherBuilder.START}} AND ${parent.formatId(TO)} IN {${CypherBuilder.END}}
DELETE r"""
        }
    }
    /**
     * @return Whether the ID is native
     */
    boolean isNativeId() {
        return nativeId
    }

    /**
     * recursively join all discriminators up the class hierarchy
     * @return
     */
    String getLabelsWithInheritance(domainInstance) {
        StringBuilder sb = new StringBuilder()
        appendRecursive(sb, domainInstance)
        return sb.toString()
    }
    /**
     * @return Returns only the statically defined labels
     */
    Collection<String> getLabels() {
        return this.staticLabels
    }

    /**
     * Get labels specific to the given instance
     *
     * @param domainInstance The domain instance
     * @return the abels
     */
    Collection<String> getLabels(Object domainInstance) {
        if(hasDynamicLabels) {
            Collection<String> labels = []
            for(obj in labelObjects) {
                String label = getLabelFor(obj, domainInstance)
                if(label) {
                    labels.add(label)
                }
            }
            return labels
        }
        else {
            return staticLabels
        }
    }

    /**
     * @return Return only the statically defined labels as a string usable by cypher, concatenated by ":"
     */
    String getLabelsAsString() {
        return ":${staticLabels.join(LABEL_SEPARATOR)}"
    }

    /**
     * return all labels as string usable for cypher, concatenated by ":"
     * @return
     */
    String getLabelsAsString(Object domainInstance) {
        if(hasDynamicLabels) {
            return ":${getLabels(domainInstance).join(LABEL_SEPARATOR)}"
        }
        else {
            return getLabelsAsString()
        }
    }

    /**
     * @return Return only the statically defined labels as a string usable by cypher, concatenated by ":"
     */
    String getLabelsAsStringWithInheritance() {
        List<String> staticLabels = new ArrayList(this.staticLabels)
        PersistentEntity parent = this.parentEntity
        while (parent != null) {
            if (parent instanceof GraphPersistentEntity) {
                staticLabels.addAll(((GraphPersistentEntity) parent).labels)
            }
            parent = parent.parentEntity
        }
        return ":${staticLabels.join(LABEL_SEPARATOR)}"
    }

    /**
     * @return The variable name used in queries to query this entity
     */
    String getVariableName() {
        CypherBuilder.NODE_VAR
    }

    /**
     * @return A unique variable name for this entity
     */
    String getVariableId() {
        return variableId
    }

    protected boolean establishLabels() {
        labelObjects = establishLabelObjects()
        boolean hasDynamicLabels = labelObjects.any() { it instanceof Closure }
        for (obj in labelObjects) {
            String label = getLabelFor(obj)
            if (label != null) {
                staticLabels.add(label)
            }
        }
        return hasDynamicLabels
    }

    protected Collection<Object> establishLabelObjects() {
        Object labels = mappedForm.getLabels();

        List objs = labels instanceof Object[] ? labels as List : [labels]

        // if labels consists solely of instance-dependent labels, add default label based on class name
        if (objs.every { (it instanceof Closure) && (it.maximumNumberOfParameters == 2) }) {
            objs << null // adding -> label defaults to discriminator
        }
        return objs
    }

    private String getLabelFor(Object obj, domainInstance = null) {
        switch (obj) {
            case null:
                return discriminator
            case CharSequence:
                return ((CharSequence)obj).toString()
            case Closure:
                Closure closure = (Closure)obj
                Object result = null
                switch (closure.maximumNumberOfParameters) {
                    case 1:
                        result = closure(this)
                        break
                    case 2:
                        result = domainInstance == null ? null : closure(this, domainInstance)
                        break
                    default:
                        throw new IllegalArgumentException("closure specified in labels is unsupported, it expects $closure.maximumNumberOfParameters parameters.")
                }
                return result?.toString()
            default:
                return obj.toString()
        }
    }

    private void appendRecursive(StringBuilder sb, domainInstance){
        sb.append(getLabelsAsString(domainInstance))

        GraphPersistentEntity parentEntity = (GraphPersistentEntity) getParentEntity()
        if (parentEntity != null) {
            Boolean label = parentEntity.mapping?.mappedForm?.autoLabel
            if (label || (label == null && !GormMappingConfigurationStrategy.isAbstract(parentEntity))) {
                parentEntity.appendRecursive(sb, domainInstance)
            }
        }
    }

    /**
     * @return Whether there are dynamic associations. Dynamic associations reduce read performance so are discouraged
     */
    boolean hasDynamicAssociations() {
        return this.hasDynamicAssociations
    }

    /**
     * @return Whether there are dynamic labels. Dynamic labels prevent optimization of write operations so are discouraged
     */
    boolean hasDynamicLabels() {
        return hasDynamicLabels
    }
}

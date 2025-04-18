package org.grails.datastore.gorm.neo4j;

import java.util.*;

/**
 * A builder for Cypher queries
 *
 * @since 3.0
 * @author Stefan
 * @author Graeme Rocher
 *
 */
public class CypherBuilder {

    public final static String TYPE = "type";
    public final static String END = "end";
    public final static String START = "start";
    public static final String IDENTIFIER = "__id__";
    public static final String PROPS = "props";
    public static final String RELATED = "related";
    public static final String WHERE = " WHERE ";
    public static final String RETURN = " RETURN ";
    public static final String COMMAND_SEPARATOR = ", ";
    public static final String DEFAULT_RETURN_TYPES = "n as data\n";
    public static final String DEFAULT_RETURN_STATEMENT = RETURN + DEFAULT_RETURN_TYPES;
    public static final String DEFAULT_REL_RETURN_STATEMENT = "r as rel, from as from, to as to \n";
    public static final String NEW_LINE = " \n";
    public static final String START_MATCH = "MATCH (";
    public static final String SPACE = " ";
    public static final String OPTIONAL_MATCH = "OPTIONAL MATCH";
    public static final String CYPHER_CREATE = "CREATE ";
    public static final String CYPHER_MATCH_ID = "MATCH (n%s) WHERE %s = {id}";
    public static final String CYPHER_FROM_TO_NODES_MATCH = "MATCH (from%s),(to%s) WHERE ";
    public static final String NODE_LABELS = "labels";
    public static final String NODE_DATA = "data";
    public static final String REL_DATA = "rel";
    public final static String NODE_VAR = "n";
    public final static String REL_VAR = "r";
    public static final String DELETE = "\n DETACH DELETE ";
    private static final String CYPHER_RELATIONSHIP_MATCH = "MATCH %s WHERE ";
    private static final String CYPHER_RELATIONSHIP = "(from%s)%s(to%s)";


    private String forLabels;
    private List<String> matches = new ArrayList<>();
    private List<String> relationshipMatches = new ArrayList<>();
    private List<String> optionalMatches = new ArrayList<String>();
    private String conditions;
    private String orderAndLimits;
    private List<String> returnColumns = new ArrayList<String>();
    private List<String> deleteColumns = new ArrayList<String>();
    private Map<String, Object> sets = null;
    private Map<String, Object> params = new LinkedHashMap<String, Object>();
    private int setIndex;
    private String startNode = NODE_VAR;
    private String defaultReturnStatement = DEFAULT_RETURN_STATEMENT;

    public CypherBuilder(String forLabels) {
        this.forLabels = forLabels;
    }

    /**
     * Sets the node name to start matching from (defaults to 'n')
     *
     * @param startNode The start node
     */
    public void setStartNode(String startNode) {
        if(startNode != null) {
            this.startNode = startNode;
            this.defaultReturnStatement = RETURN + startNode + " as data\n";
        }
    }

    public void addMatch(String match) {
        if(!matches.contains(match)) {
            // Add empty match if the target entity is already defined
            final String lastNode = match.substring(match.lastIndexOf('('));
            final boolean alreadyDefined = matches.stream().anyMatch(m -> m.endsWith(lastNode));
            matches.add(alreadyDefined ? "" : match);
        }
    }

    public void addRelationshipMatch(String match) {
        if(!relationshipMatches.contains(match)) {
            relationshipMatches.add(match);
        }
    }

    public void replaceFirstRelationshipMatch(String match) {
        if(relationshipMatches.isEmpty()) {
            relationshipMatches.add(match);
        }
        else {
            relationshipMatches.set(0,match);
        }
    }
    /**
     * Optional matches are added to do joins for relationships
     *
     * @see <a href="http://neo4j.com/docs/stable/query-optional-match.html">http://neo4j.com/docs/stable/query-optional-match.html</a>
     *
     * @param match The optional match
     */
    public void addOptionalMatch(String match) {
        optionalMatches.add(match);
    }

    public int getNextMatchNumber() {
        return matches.size();
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public void setOrderAndLimits(String orderAndLimits) {
        this.orderAndLimits = orderAndLimits;
    }

    public int addParam(Object value) {
        params.put(String.valueOf(params.size() + 1), value);
        return params.size();
    }

    /**
     *
     * @param position first element is 1
     * @param value
     */
    public void replaceParamAt(int position, Object value) {
        params.put(String.valueOf(position), value);
    }

    /**
     * @return The parameters to the query
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Adds a variable to be returned by a RETURN statement
     *
     * @param returnColumn The name of the variable in the cypher query
     */
    public void addReturnColumn(String returnColumn) {
        returnColumns.add(returnColumn);
    }

    /**
     * Adds a variable to be deleted by a DELETE statement
     *
     * @param deleteColumn The name of the variable in the cypher query
     */
    public void addDeleteColumn(String deleteColumn) {
        deleteColumns.add(deleteColumn);
    }

    /**
     * Adds the property to be set using SET statement
     * @param sets The property to be set
     */
    public void addPropertySet(Map<String, Object> sets) {
        if(sets != null) {
            final int index = addParam(sets);
            this.setIndex = index;
            this.sets = sets;
        }
    }

    public String build() {
        StringBuilder cypher = new StringBuilder();
        cypher.append(START_MATCH).append(startNode).append(forLabels).append(")");

        for(String r : relationshipMatches) {
            cypher.append(r);
        }

        for (String m : matches) {
            // Skip empty matches
            if (m.isEmpty()) continue;
            cypher.append(COMMAND_SEPARATOR).append(m);
        }


        if ((conditions!=null) && (!conditions.isEmpty())) {
            cypher.append(WHERE).append(conditions);
        }

        if(!optionalMatches.isEmpty()) {
            for (String m : optionalMatches) {
                cypher.append(NEW_LINE)
                      .append(OPTIONAL_MATCH)
                      .append(m);

            }
        }

        if(!deleteColumns.isEmpty()) {
            cypher.append(DELETE);
            Iterator<String> iter = deleteColumns.iterator();   // same as Collection.join(String separator)
            if (iter.hasNext()) {
                cypher.append(iter.next());
                while (iter.hasNext()) {
                    cypher.append(COMMAND_SEPARATOR).append(iter.next());
                }
            }
            return cypher.toString();
        }

        if(sets != null) {
            cypher.append("\nSET n += {").append(setIndex).append("}\n");
        }

        if (returnColumns.isEmpty()) {
            cypher.append(defaultReturnStatement);
            if (orderAndLimits!=null) {
                cypher.append(orderAndLimits).append(NEW_LINE);
            }
        } else {
            cypher.append(RETURN);
            Iterator<String> iter = returnColumns.iterator();   // same as Collection.join(String separator)
            if (iter.hasNext()) {
                cypher.append(iter.next());
                while (iter.hasNext()) {
                    cypher.append(COMMAND_SEPARATOR).append(iter.next());
                }
            }
            if (orderAndLimits!=null) {
                cypher.append(SPACE);
                cypher.append(orderAndLimits);
            }
        }

        return cypher.toString();
    }

    /**
     * Produces "MATCH (from%s)%s(to%s) WHERE "
     * @param fromLabels The from node labels
     * @param toLabels The to node labels
     * @param relationship The relationship match
     * @return The MATCH
     */
    public static String buildRelationshipMatch(String fromLabels, String relationship, String toLabels) {
        return String.format( CYPHER_RELATIONSHIP_MATCH, buildRelationship(fromLabels, relationship, toLabels));
    }

    /**
     * Produces "MATCH %s WHERE "
     * @param relationship The relationship match
     * @return The MATCH
     */
    public static String buildRelationshipMatch(String relationship) {
        return String.format( CYPHER_RELATIONSHIP_MATCH, relationship);
    }

    /**
     * Produces "(from%s)%s(to%s)"
     * @param fromLabels The from node labels
     * @param toLabels The to node labels
     * @param relationship The relationship match
     * @return The MATCH
     */
    public static String buildRelationship(String fromLabels, String relationship, String toLabels) {
        return String.format(CypherBuilder.CYPHER_RELATIONSHIP, fromLabels, relationship, toLabels);
    }
}

package grails.neo4j

/**
 * Represents a Neo4j path
 *
 * @author Graeme Rocher
 * @see org.neo4j.driver.types.Path
 * @since 6.1
 */
interface Path<S, E> extends Iterable<Segment<S,E>> {

    /**
     * A segment
     *
     * @see org.neo4j.driver.types.Path.Segment
     */
    interface Segment<S, E> {
        Relationship<S, E> relationship()

        S start()

        E end()
    }


    /** @return the start node of this path */
    S start()

    /** @return the end node of this path */
    E end()

    /** @return the number of segments in this path, which will be the same as the number of relationships */
    int length()

    /**
     * @return The domain instances that make up all the nodes
     */
    Iterable nodes()

    /**
     * Whether the path contains the given object. The entity should correctly implement equals/hashCode
     * @param o The object
     * @return True if it does
     */
    boolean contains(Object o)
}

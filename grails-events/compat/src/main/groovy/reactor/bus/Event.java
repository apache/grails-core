package reactor.bus;

/**
 * @deprecated Here for compatibility only. Do not use directly
 */
@Deprecated
public class Event<T> extends grails.events.Event<T> {
    private volatile        Object              replyTo = null;

    public Event(String id, T data, Object replyTo) {
        super(id, data);
        this.replyTo = replyTo;
    }

    public Event(String id, T data) {
        super(id, data);
    }

    /**
     * Get the key to send replies to.
     *
     * @return The reply-to key
     */
    public Object getReplyTo() {
        return replyTo;
    }

    /**
     * Set the {@code key} that interested parties should send replies to.
     *
     * @param replyTo
     *     The key to use to notify sender of replies.
     *
     * @return {@literal this}
     */
    public Event<T> setReplyTo(Object replyTo) {
        assert replyTo != null : "ReplyTo cannot be null.";
        this.replyTo = replyTo;
        return this;
    }

}

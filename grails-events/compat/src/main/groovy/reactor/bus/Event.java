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

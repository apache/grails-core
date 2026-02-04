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

package functionaltests.commanddi

/**
 * Service for notification handling that can be injected into command objects.
 */
class NotificationService {

    // Track sent notifications for testing
    private List<Map> sentNotifications = []

    /**
     * Send a notification (simulated).
     */
    boolean sendNotification(String recipient, String message, String type = 'email') {
        if (!recipient || !message) return false
        sentNotifications << [
            recipient: recipient,
            message: message,
            type: type,
            timestamp: System.currentTimeMillis()
        ]
        return true
    }

    /**
     * Get count of sent notifications.
     */
    int getNotificationCount() {
        return sentNotifications.size()
    }

    /**
     * Clear sent notifications (for testing).
     */
    void clearNotifications() {
        sentNotifications.clear()
    }

    /**
     * Get last notification.
     */
    Map getLastNotification() {
        return sentNotifications ? sentNotifications.last() : null
    }

    /**
     * Validate notification preferences.
     */
    Map<String, Object> validatePreferences(Map preferences) {
        def errors = []
        if (!preferences) {
            return [valid: false, errors: ['Preferences cannot be null']]
        }
        if (preferences.email && !preferences.email.contains('@')) {
            errors << 'Invalid email format'
        }
        if (preferences.frequency && !['daily', 'weekly', 'monthly'].contains(preferences.frequency)) {
            errors << 'Invalid frequency. Must be daily, weekly, or monthly'
        }
        return [valid: errors.isEmpty(), errors: errors]
    }

    /**
     * Get service identifier for testing.
     */
    String getServiceId() {
        return 'NotificationService-v1'
    }
}

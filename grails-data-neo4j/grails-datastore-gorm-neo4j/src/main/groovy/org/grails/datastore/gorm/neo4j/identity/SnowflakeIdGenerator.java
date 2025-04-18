/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.neo4j.identity;

import org.grails.datastore.gorm.neo4j.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Random;

/**
 *
 * Generate unique IDs using the Twitter Snowflake algorithm (see https://github.com/twitter/snowflake). Snowflake IDs are 64 bit positive longs composed of: - 41 bits time stamp - 10 bits machine id - 12 bits sequence number
 *
 * @author Stefan
 */

public class SnowflakeIdGenerator implements IdGenerator {

    private static Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    private final long datacenterIdBits = 10L;
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    private final long timestampBits = 41L;

    private final long datacenterIdShift = 64L - datacenterIdBits;
    private final long timestampLeftShift = 64L - datacenterIdBits - timestampBits;
    private final long sequenceMax = 4096;
    private final long twepoch = 1288834974657L;
    private final long datacenterId;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;

    public SnowflakeIdGenerator() {
        datacenterId = getDatacenterId();
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalStateException("datacenterId > maxDatacenterId");
        }
    }

    @Override
    public synchronized Serializable nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalArgumentException("Clock moved backwards.  Refusing to generate id for " + (
                    lastTimestamp - timestamp) + " milliseconds.");
        }
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) % sequenceMax;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - twepoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                sequence;
    }

    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    protected long getDatacenterId() { // TODO: unit test with empty array for nic.getHwAddress
        byte[] hardwareAddress = null;
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (isInvalidHardwareAddress(hardwareAddress) && (nics.hasMoreElements())) {
                NetworkInterface nic = nics.nextElement();
                hardwareAddress = nic.getHardwareAddress();
            }
        } catch (SocketException e) {
            log.error(e.getMessage(), e);
        }
        if (isInvalidHardwareAddress(hardwareAddress)) {
            // no hardware address found, generate 6 random bytes
            hardwareAddress = new byte[6];
            new Random().nextBytes(hardwareAddress);
        }

        long id = ((0x000000FF & (long) hardwareAddress[hardwareAddress.length - 1]) | (0x0000FF00 & (((long) hardwareAddress[hardwareAddress.length - 2]) << 8))) >> 6;
        return id;
    }

    private boolean isInvalidHardwareAddress(byte[] hardwareAddress) {
        return ((hardwareAddress == null) || (hardwareAddress.length<6));
    }

}
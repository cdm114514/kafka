/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.common.requests;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.message.OffsetFetchResponseData;
import org.apache.kafka.common.message.OffsetFetchResponseData.OffsetFetchResponsePartition;
import org.apache.kafka.common.message.OffsetFetchResponseData.OffsetFetchResponseTopic;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.types.Struct;
import org.apache.kafka.common.record.RecordBatch;
import org.apache.kafka.common.requests.OffsetFetchResponse.PartitionData;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.apache.kafka.common.protocol.CommonFields.ERROR_CODE;
import static org.apache.kafka.common.requests.AbstractResponse.DEFAULT_THROTTLE_TIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OffsetFetchResponseTest
{

    private final int throttleTimeMs = 10;
    private final int offset = 100;
    private final String metadata = "metadata";

    private final String topicOne = "topic1";
    private final int partitionOne = 1;
    private final Optional<Integer> leaderEpochOne = Optional.of(1);
    private final String topicTwo = "topic2";
    private final int partitionTwo = 2;
    private final Optional<Integer> leaderEpochTwo = Optional.of(2);

    private Map<TopicPartition, PartitionData> partitionDataMap;

    @Before
    public void setUp()
    {
        partitionDataMap = new HashMap<>();
        partitionDataMap.put(new TopicPartition(topicOne, partitionOne), new PartitionData(offset, leaderEpochOne, metadata, Errors.TOPIC_AUTHORIZATION_FAILED));
        partitionDataMap.put(new TopicPartition(topicTwo, partitionTwo), new PartitionData(offset, leaderEpochTwo, metadata, Errors.UNKNOWN_TOPIC_OR_PARTITION));
    }

    @Test
    public void testConstructor()
    {
        OffsetFetchResponse response = new OffsetFetchResponse(throttleTimeMs, Errors.NOT_COORDINATOR, partitionDataMap);
        assertEquals(Errors.NOT_COORDINATOR, response.error());
        assertEquals(Collections.singletonMap(Errors.NOT_COORDINATOR, 1), response.errorCounts());

        assertEquals(throttleTimeMs, response.throttleTimeMs());

        Map<TopicPartition, PartitionData> responseData = response.responseData();
        assertEquals(partitionDataMap, responseData);
        responseData.forEach((tp, data) -> assertTrue(data.hasError()));
    }

    /**
     * Test behavior changes over the versions. Refer to resources.common.messages.OffsetFetchResponse.json
     */
    @Test
    public void testStructBuild()
    {
        partitionDataMap.put(new TopicPartition(topicTwo, partitionTwo), new PartitionData(offset, leaderEpochTwo, metadata, Errors.GROUP_AUTHORIZATION_FAILED));

        OffsetFetchResponse latestResponse = new OffsetFetchResponse(throttleTimeMs, Errors.NONE, partitionDataMap);

        for (short version = 0; version <= ApiKeys.OFFSET_FETCH.latestVersion(); version++)
        {
            Struct struct = latestResponse.data.toStruct(version);

            OffsetFetchResponse oldResponse = new OffsetFetchResponse(struct, version);

            if (version <= 1)
            {
                assertFalse(struct.hasField(ERROR_CODE));

                // Partition level error populated in older versions.
                assertEquals(Errors.GROUP_AUTHORIZATION_FAILED, oldResponse.error());
                assertEquals(Collections.singletonMap(Errors.GROUP_AUTHORIZATION_FAILED, 1), oldResponse.errorCounts());

            } else
            {
                assertTrue(struct.hasField(ERROR_CODE));

                assertEquals(Errors.NONE, oldResponse.error());
                assertEquals(Collections.singletonMap(Errors.NONE, 1), oldResponse.errorCounts());
            }

            if (version <= 2)
            {
                assertEquals(DEFAULT_THROTTLE_TIME, oldResponse.throttleTimeMs());
            } else
            {
                assertEquals(throttleTimeMs, oldResponse.throttleTimeMs());
            }

            Map<TopicPartition, PartitionData> expectedDataMap = new HashMap<>();
            for (Map.Entry<TopicPartition, PartitionData> entry : partitionDataMap.entrySet())
            {
                PartitionData partitionData = entry.getValue();
                expectedDataMap.put(entry.getKey(), new PartitionData(partitionData.offset, version <= 4 ? Optional.empty() : partitionData.leaderEpoch, partitionData.metadata, partitionData.error));
            }

            Map<TopicPartition, PartitionData> responseData = oldResponse.responseData();
            assertEquals(expectedDataMap, responseData);

            responseData.forEach((tp, data) -> assertTrue(data.hasError()));
        }
    }

    @Test
    public void testShouldThrottle()
    {
        OffsetFetchResponse response = new OffsetFetchResponse(throttleTimeMs, Errors.NONE, partitionDataMap);
        for (short version = 0; version <= ApiKeys.OFFSET_FETCH.latestVersion(); version++)
        {
            if (version >= 4)
            {
                assertTrue(response.shouldClientThrottle(version));
            } else
            {
                assertFalse(response.shouldClientThrottle(version));
            }
        }
    }

    @Test
    public void testNullableMetadata()
    {
        partitionDataMap.clear();
        partitionDataMap.put(new TopicPartition(topicOne, partitionOne), new PartitionData(offset, leaderEpochOne, null, Errors.UNKNOWN_TOPIC_OR_PARTITION));

        OffsetFetchResponse response = new OffsetFetchResponse(throttleTimeMs, Errors.GROUP_AUTHORIZATION_FAILED, partitionDataMap);
        OffsetFetchResponseData expectedData = new OffsetFetchResponseData().setErrorCode(Errors.GROUP_AUTHORIZATION_FAILED.code()).setThrottleTimeMs(throttleTimeMs).setTopics(Collections.singletonList(new OffsetFetchResponseTopic().setName(topicOne).setPartitions(Collections.singletonList(new OffsetFetchResponsePartition().setPartitionIndex(partitionOne).setCommittedOffset(offset).setCommittedLeaderEpoch(leaderEpochOne.orElse(-1)).setErrorCode(Errors.UNKNOWN_TOPIC_OR_PARTITION.code()).setMetadata(null)))));
        assertEquals(expectedData, response.data);
    }

    @Test
    public void testUseDefaultLeaderEpoch()
    {
        final Optional<Integer> emptyLeaderEpoch = Optional.empty();
        partitionDataMap.clear();

        partitionDataMap.put(new TopicPartition(topicOne, partitionOne), new PartitionData(offset, emptyLeaderEpoch, metadata, Errors.UNKNOWN_TOPIC_OR_PARTITION));

        OffsetFetchResponse response = new OffsetFetchResponse(throttleTimeMs, Errors.NOT_COORDINATOR, partitionDataMap);
        OffsetFetchResponseData expectedData = new OffsetFetchResponseData().setErrorCode(Errors.NOT_COORDINATOR.code()).setThrottleTimeMs(throttleTimeMs).setTopics(Collections.singletonList(new OffsetFetchResponseTopic().setName(topicOne).setPartitions(Collections.singletonList(new OffsetFetchResponsePartition().setPartitionIndex(partitionOne).setCommittedOffset(offset).setCommittedLeaderEpoch(RecordBatch.NO_PARTITION_LEADER_EPOCH).setErrorCode(Errors.UNKNOWN_TOPIC_OR_PARTITION.code()).setMetadata(metadata)))));
        assertEquals(expectedData, response.data);
    }
}

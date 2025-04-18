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
package org.apache.kafka.clients.consumer;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.record.TimestampType;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class MockConsumerTest
{

    private MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);

    @Test
    public void testSimpleMock()
    {
        consumer.subscribe(Collections.singleton("test"));
        assertEquals(0, consumer.poll(Duration.ZERO).count());
        consumer.rebalance(Arrays.asList(new TopicPartition("test", 0), new TopicPartition("test", 1)));
        // Mock consumers need to seek manually since they cannot automatically reset offsets
        HashMap<TopicPartition, Long> beginningOffsets = new HashMap<>();
        beginningOffsets.put(new TopicPartition("test", 0), 0L);
        beginningOffsets.put(new TopicPartition("test", 1), 0L);
        consumer.updateBeginningOffsets(beginningOffsets);
        consumer.seek(new TopicPartition("test", 0), 0);
        ConsumerRecord<String, String> rec1 = new ConsumerRecord<>("test", 0, 0, 0L, TimestampType.CREATE_TIME, 0L, 0, 0, "key1", "value1");
        ConsumerRecord<String, String> rec2 = new ConsumerRecord<>("test", 0, 1, 0L, TimestampType.CREATE_TIME, 0L, 0, 0, "key2", "value2");
        consumer.addRecord(rec1);
        consumer.addRecord(rec2);
        ConsumerRecords<String, String> recs = consumer.poll(Duration.ofMillis(1));
        Iterator<ConsumerRecord<String, String>> iter = recs.iterator();
        assertEquals(rec1, iter.next());
        assertEquals(rec2, iter.next());
        assertFalse(iter.hasNext());
        final TopicPartition tp = new TopicPartition("test", 0);
        assertEquals(2L, consumer.position(tp));
        consumer.commitSync();
        assertEquals(2L, consumer.committed(Collections.singleton(tp)).get(tp).offset());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSimpleMockDeprecated()
    {
        consumer.subscribe(Collections.singleton("test"));
        assertEquals(0, consumer.poll(1000).count());
        consumer.rebalance(Arrays.asList(new TopicPartition("test", 0), new TopicPartition("test", 1)));
        // Mock consumers need to seek manually since they cannot automatically reset offsets
        HashMap<TopicPartition, Long> beginningOffsets = new HashMap<>();
        beginningOffsets.put(new TopicPartition("test", 0), 0L);
        beginningOffsets.put(new TopicPartition("test", 1), 0L);
        consumer.updateBeginningOffsets(beginningOffsets);
        consumer.seek(new TopicPartition("test", 0), 0);
        ConsumerRecord<String, String> rec1 = new ConsumerRecord<>("test", 0, 0, 0L, TimestampType.CREATE_TIME, 0L, 0, 0, "key1", "value1");
        ConsumerRecord<String, String> rec2 = new ConsumerRecord<>("test", 0, 1, 0L, TimestampType.CREATE_TIME, 0L, 0, 0, "key2", "value2");
        consumer.addRecord(rec1);
        consumer.addRecord(rec2);
        ConsumerRecords<String, String> recs = consumer.poll(1);
        Iterator<ConsumerRecord<String, String>> iter = recs.iterator();
        assertEquals(rec1, iter.next());
        assertEquals(rec2, iter.next());
        assertFalse(iter.hasNext());
        final TopicPartition tp = new TopicPartition("test", 0);
        assertEquals(2L, consumer.position(tp));
        consumer.commitSync();
        assertEquals(2L, consumer.committed(Collections.singleton(tp)).get(tp).offset());
        assertThat(consumer.groupMetadata(), equalTo(new ConsumerGroupMetadata("dummy.group.id", 1, "1", Optional.empty())));
    }

    @Test
    public void testConsumerRecordsIsEmptyWhenReturningNoRecords()
    {
        TopicPartition partition = new TopicPartition("test", 0);
        consumer.assign(Collections.singleton(partition));
        consumer.addRecord(new ConsumerRecord<>("test", 0, 0, null, null));
        consumer.updateEndOffsets(Collections.singletonMap(partition, 1L));
        consumer.seekToEnd(Collections.singleton(partition));
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1));
        assertThat(records.count(), is(0));
        assertThat(records.isEmpty(), is(true));
    }

    @Test
    public void shouldNotClearRecordsForPausedPartitions()
    {
        TopicPartition partition0 = new TopicPartition("test", 0);
        Collection<TopicPartition> testPartitionList = Collections.singletonList(partition0);
        consumer.assign(testPartitionList);
        consumer.addRecord(new ConsumerRecord<>("test", 0, 0, null, null));
        consumer.updateBeginningOffsets(Collections.singletonMap(partition0, 0L));
        consumer.seekToBeginning(testPartitionList);

        consumer.pause(testPartitionList);
        consumer.poll(Duration.ofMillis(1));
        consumer.resume(testPartitionList);
        ConsumerRecords<String, String> recordsSecondPoll = consumer.poll(Duration.ofMillis(1));
        assertThat(recordsSecondPoll.count(), is(1));
    }

    @Test
    public void endOffsetsShouldBeIdempotent()
    {
        TopicPartition partition = new TopicPartition("test", 0);
        consumer.updateEndOffsets(Collections.singletonMap(partition, 10L));
        // consumer.endOffsets should NOT change the value of end offsets
        Assert.assertEquals(10L, (long) consumer.endOffsets(Collections.singleton(partition)).get(partition));
        Assert.assertEquals(10L, (long) consumer.endOffsets(Collections.singleton(partition)).get(partition));
        Assert.assertEquals(10L, (long) consumer.endOffsets(Collections.singleton(partition)).get(partition));
        consumer.updateEndOffsets(Collections.singletonMap(partition, 11L));
        // consumer.endOffsets should NOT change the value of end offsets
        Assert.assertEquals(11L, (long) consumer.endOffsets(Collections.singleton(partition)).get(partition));
        Assert.assertEquals(11L, (long) consumer.endOffsets(Collections.singleton(partition)).get(partition));
        Assert.assertEquals(11L, (long) consumer.endOffsets(Collections.singleton(partition)).get(partition));
    }

}

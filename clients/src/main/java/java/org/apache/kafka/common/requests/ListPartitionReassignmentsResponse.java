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

import org.apache.kafka.common.message.ListPartitionReassignmentsResponseData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.types.Struct;

import java.nio.ByteBuffer;
import java.util.Map;

public class ListPartitionReassignmentsResponse extends AbstractResponse
{

    private final ListPartitionReassignmentsResponseData data;

    public ListPartitionReassignmentsResponse(Struct struct)
    {
        this(struct, ApiKeys.LIST_PARTITION_REASSIGNMENTS.latestVersion());
    }

    public ListPartitionReassignmentsResponse(ListPartitionReassignmentsResponseData responseData)
    {
        this.data = responseData;
    }

    ListPartitionReassignmentsResponse(Struct struct, short version)
    {
        this.data = new ListPartitionReassignmentsResponseData(struct, version);
    }

    public static ListPartitionReassignmentsResponse parse(ByteBuffer buffer, short version)
    {
        return new ListPartitionReassignmentsResponse(ApiKeys.LIST_PARTITION_REASSIGNMENTS.responseSchema(version).read(buffer), version);
    }

    public ListPartitionReassignmentsResponseData data()
    {
        return data;
    }

    @Override
    public boolean shouldClientThrottle(short version)
    {
        return true;
    }

    @Override
    public int throttleTimeMs()
    {
        return data.throttleTimeMs();
    }

    @Override
    public Map<Errors, Integer> errorCounts()
    {
        return errorCounts(Errors.forCode(data.errorCode()));
    }

    @Override
    protected Struct toStruct(short version)
    {
        return data.toStruct(version);
    }
}

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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.message.AlterReplicaLogDirsResponseData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.apache.kafka.common.protocol.types.Struct;

/**
 * Possible error codes:
 * <p>
 * {@link Errors#LOG_DIR_NOT_FOUND}
 * {@link Errors#KAFKA_STORAGE_ERROR}
 * {@link Errors#REPLICA_NOT_AVAILABLE}
 * {@link Errors#UNKNOWN_SERVER_ERROR}
 */
public class AlterReplicaLogDirsResponse extends AbstractResponse
{

    private final AlterReplicaLogDirsResponseData data;

    public AlterReplicaLogDirsResponse(Struct struct)
    {
        this(struct, ApiKeys.ALTER_REPLICA_LOG_DIRS.latestVersion());
    }

    public AlterReplicaLogDirsResponse(Struct struct, short version)
    {
        this.data = new AlterReplicaLogDirsResponseData(struct, version);
    }

    public AlterReplicaLogDirsResponse(AlterReplicaLogDirsResponseData data)
    {
        this.data = data;
    }

    public AlterReplicaLogDirsResponseData data()
    {
        return data;
    }

    @Override
    protected Struct toStruct(short version)
    {
        return data.toStruct(version);
    }

    @Override
    public int throttleTimeMs()
    {
        return data.throttleTimeMs();
    }

    @Override
    public Map<Errors, Integer> errorCounts()
    {
        Map<Errors, Integer> errorCounts = new HashMap<>();
        data.results().forEach(topicResult -> topicResult.partitions().forEach(partitionResult -> updateErrorCounts(errorCounts, Errors.forCode(partitionResult.errorCode()))));
        return errorCounts;
    }

    public static AlterReplicaLogDirsResponse parse(ByteBuffer buffer, short version)
    {
        return new AlterReplicaLogDirsResponse(ApiKeys.ALTER_REPLICA_LOG_DIRS.responseSchema(version).read(buffer));
    }

    @Override
    public boolean shouldClientThrottle(short version)
    {
        return version >= 1;
    }
}

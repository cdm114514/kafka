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

import org.apache.kafka.common.message.EndTxnResponseData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.Errors;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EndTxnResponseTest
{

    @Test
    public void testConstructorWithStruct()
    {
        int throttleTimeMs = 10;

        EndTxnResponseData data = new EndTxnResponseData().setErrorCode(Errors.NOT_COORDINATOR.code()).setThrottleTimeMs(throttleTimeMs);

        Map<Errors, Integer> expectedErrorCounts = Collections.singletonMap(Errors.NOT_COORDINATOR, 1);

        for (short version = 0; version <= ApiKeys.END_TXN.latestVersion(); version++)
        {
            EndTxnResponse response = new EndTxnResponse(data.toStruct(version), version);
            assertEquals(expectedErrorCounts, response.errorCounts());

            assertEquals(throttleTimeMs, response.throttleTimeMs());

            assertEquals(version >= 1, response.shouldClientThrottle(version));
        }
    }
}

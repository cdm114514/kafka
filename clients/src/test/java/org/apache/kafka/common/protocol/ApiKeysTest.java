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
package org.apache.kafka.common.protocol;

import org.apache.kafka.common.protocol.types.BoundField;
import org.apache.kafka.common.protocol.types.Schema;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApiKeysTest
{

    @Test(expected = IllegalArgumentException.class)
    public void testForIdWithInvalidIdLow()
    {
        ApiKeys.forId(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testForIdWithInvalidIdHigh()
    {
        ApiKeys.forId(10000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void schemaVersionOutOfRange()
    {
        ApiKeys.PRODUCE.requestSchema((short) ApiKeys.PRODUCE.requestSchemas.length);
    }

    @Test
    public void testAlterIsrIsClusterAction()
    {
        assertTrue(ApiKeys.ALTER_ISR.clusterAction);
    }

    /**
     * All valid client responses which may be throttled should have a field named
     * 'throttle_time_ms' to return the throttle time to the client. Exclusions are
     * <ul>
     *   <li> Cluster actions used only for inter-broker are throttled only if unauthorized
     *   <li> SASL_HANDSHAKE and SASL_AUTHENTICATE are not throttled when used for authentication
     *        when a connection is established or for re-authentication thereafter; these requests
     *        return an error response that may be throttled if they are sent otherwise.
     * </ul>
     */
    @Test
    public void testResponseThrottleTime()
    {
        Set<ApiKeys> authenticationKeys = EnumSet.of(ApiKeys.SASL_HANDSHAKE, ApiKeys.SASL_AUTHENTICATE);
        // Newer protocol apis include throttle time ms even for cluster actions
        Set<ApiKeys> clusterActionsWithThrottleTimeMs = EnumSet.of(ApiKeys.ALTER_ISR);
        for (ApiKeys apiKey : ApiKeys.values())
        {
            Schema responseSchema = apiKey.responseSchema(apiKey.latestVersion());
            BoundField throttleTimeField = responseSchema.get(CommonFields.THROTTLE_TIME_MS.name);
            if ((apiKey.clusterAction && !clusterActionsWithThrottleTimeMs.contains(apiKey)) || authenticationKeys.contains(apiKey))
            {
                assertNull("Unexpected throttle time field: " + apiKey, throttleTimeField);
            } else
            {
                assertNotNull("Throttle time field missing: " + apiKey, throttleTimeField);
            }
        }
    }
}

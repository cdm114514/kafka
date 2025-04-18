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
package org.apache.kafka.common.security.auth;

import java.util.Map;
import java.security.Principal;

import org.apache.kafka.common.network.TransportLayer;
import org.apache.kafka.common.network.Authenticator;
import org.apache.kafka.common.KafkaException;

/**
 * DefaultPrincipalBuilder which return transportLayer's peer Principal
 *
 * @deprecated As of Kafka 1.0.0. This will be removed in a future major release.
 **/
@Deprecated
public class DefaultPrincipalBuilder implements PrincipalBuilder
{

    public void configure(Map<String, ?> configs)
    {
    }

    public Principal buildPrincipal(TransportLayer transportLayer, Authenticator authenticator) throws KafkaException
    {
        try
        {
            return transportLayer.peerPrincipal();
        } catch (Exception e)
        {
            throw new KafkaException("Failed to build principal due to: ", e);
        }
    }

    public void close() throws KafkaException
    {
    }

}

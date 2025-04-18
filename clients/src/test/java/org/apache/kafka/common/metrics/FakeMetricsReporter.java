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
package org.apache.kafka.common.metrics;

import java.util.List;
import java.util.Map;

public class FakeMetricsReporter implements MetricsReporter
{

    @Override
    public void configure(Map<String, ?> configs)
    {
    }

    @Override
    public void init(List<KafkaMetric> metrics)
    {
    }

    @Override
    public void metricChange(KafkaMetric metric)
    {
    }

    @Override
    public void metricRemoval(KafkaMetric metric)
    {
    }

    @Override
    public void close()
    {
    }

}

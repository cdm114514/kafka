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
package org.apache.kafka.common.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler implementation that uses the system clock.
 * <p>
 * Use Scheduler.SYSTEM instead of constructing an instance of this class.
 */
public class SystemScheduler implements Scheduler
{
    SystemScheduler()
    {
    }

    @Override
    public Time time()
    {
        return Time.SYSTEM;
    }

    @Override
    public <T> Future<T> schedule(final ScheduledExecutorService executor, final Callable<T> callable, long delayMs)
    {
        return executor.schedule(callable, delayMs, TimeUnit.MILLISECONDS);
    }
}

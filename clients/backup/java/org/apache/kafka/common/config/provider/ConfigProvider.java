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
package org.apache.kafka.common.config.provider;

import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.ConfigChangeCallback;
import org.apache.kafka.common.config.ConfigData;

import java.io.Closeable;
import java.util.Set;

/**
 * A provider of configuration data, which may optionally support subscriptions to configuration changes.
 * Implementations are required to safely support concurrent calls to any of the methods in this interface.
 */
public interface ConfigProvider extends Configurable, Closeable
{

    /**
     * Retrieves the data at the given path.
     *
     * @param path the path where the data resides
     * @return the configuration data
     */
    ConfigData get(String path);

    /**
     * Retrieves the data with the given keys at the given path.
     *
     * @param path the path where the data resides
     * @param keys the keys whose values will be retrieved
     * @return the configuration data
     */
    ConfigData get(String path, Set<String> keys);

    /**
     * Subscribes to changes for the given keys at the given path (optional operation).
     *
     * @param path     the path where the data resides
     * @param keys     the keys whose values will be retrieved
     * @param callback the callback to invoke upon change
     * @throws {@link UnsupportedOperationException} if the subscribe operation is not supported
     */
    default void subscribe(String path, Set<String> keys, ConfigChangeCallback callback)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsubscribes to changes for the given keys at the given path (optional operation).
     *
     * @param path     the path where the data resides
     * @param keys     the keys whose values will be retrieved
     * @param callback the callback to be unsubscribed from changes
     * @throws {@link UnsupportedOperationException} if the unsubscribe operation is not supported
     */
    default void unsubscribe(String path, Set<String> keys, ConfigChangeCallback callback)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Clears all subscribers (optional operation).
     *
     * @throws {@link UnsupportedOperationException} if the unsubscribeAll operation is not supported
     */
    default void unsubscribeAll()
    {
        throw new UnsupportedOperationException();
    }
}

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
package org.apache.kafka.common.security.scram;

import org.apache.kafka.common.security.scram.internals.ScramSaslClientProvider;
import org.apache.kafka.common.security.scram.internals.ScramSaslServerProvider;

import java.util.Collections;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.spi.LoginModule;

public class ScramLoginModule implements LoginModule
{

    private static final String USERNAME_CONFIG = "username";
    private static final String PASSWORD_CONFIG = "password";
    public static final String TOKEN_AUTH_CONFIG = "tokenauth";

    static
    {
        ScramSaslClientProvider.initialize();
        ScramSaslServerProvider.initialize();
    }

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options)
    {
        String username = (String) options.get(USERNAME_CONFIG);
        if (username != null)
        {
            subject.getPublicCredentials().add(username);
        }
        String password = (String) options.get(PASSWORD_CONFIG);
        if (password != null)
        {
            subject.getPrivateCredentials().add(password);
        }

        Boolean useTokenAuthentication = "true".equalsIgnoreCase((String) options.get(TOKEN_AUTH_CONFIG));
        if (useTokenAuthentication)
        {
            Map<String, String> scramExtensions = Collections.singletonMap(TOKEN_AUTH_CONFIG, "true");
            subject.getPublicCredentials().add(scramExtensions);
        }
    }

    @Override
    public boolean login()
    {
        return true;
    }

    @Override
    public boolean logout()
    {
        return true;
    }

    @Override
    public boolean commit()
    {
        return true;
    }

    @Override
    public boolean abort()
    {
        return false;
    }
}

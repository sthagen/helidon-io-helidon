/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.integrations.microstream.cdi;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Qualifier annotation for Microstream EmbeddedStorageManager.
 *
 * @deprecated Microstream is renamed to Eclipse store and no longer updated
 */
@Deprecated(forRemoval = true, since = "4.2.1")
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, PARAMETER})
public @interface MicrostreamStorage {
    /**
     * Specifies the configuration node used to configure the EmbeddedStorageManager instance to be created.
     *
     * @return the config node
     */
    String configNode();
}

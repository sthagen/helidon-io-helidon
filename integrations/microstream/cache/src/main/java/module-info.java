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

/**
 * Provides support for Microstream-Cache features integration.
 * @deprecated Microstream is renamed to Eclipse store and no longer updated
 */
@Deprecated(forRemoval = true, since = "4.2.1")
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.integrations.microstream.cache {

    requires transitive cache.api;
    requires transitive io.helidon.integrations.microstream;
    requires transitive microstream.cache;

    exports io.helidon.integrations.microstream.cache;

}
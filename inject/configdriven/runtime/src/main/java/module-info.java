/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
 * Injection Config-Driven Services Module.
 */
module io.helidon.inject.configdriven.runtime {
    requires static jakarta.inject;
    requires static jakarta.annotation;
    requires static io.helidon.config.metadata;

    // required for compilation of generated types
    requires transitive io.helidon.inject.configdriven.api;
    requires transitive io.helidon.builder.api;
    requires transitive io.helidon.common.types;
    requires transitive io.helidon.config;
    requires transitive io.helidon.inject.api;
    requires transitive io.helidon.inject.runtime;

    exports io.helidon.inject.configdriven.runtime;
}
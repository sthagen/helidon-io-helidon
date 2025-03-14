/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.micronaut.cdi.data;

import java.sql.Connection;

import io.micronaut.context.ApplicationContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * CDI Extension that adds Micronaut data specific features.
 * Currently adds support for injecting {@link java.sql.Connection}.
 */
public class MicronautDataCdiExtension implements Extension {
    /**
     * Default constructor required by {@link java.util.ServiceLoader}.
     */
    public MicronautDataCdiExtension() {
    }

    void afterBeanDiscovery(@Priority(PLATFORM_BEFORE + 10) @Observes AfterBeanDiscovery event) {
        event.addBean()
                .addType(Connection.class)
                .id("micronaut-sql-connection")
                .scope(Dependent.class)
                .produceWith(instance -> instance.select(ApplicationContext.class)
                        .get()
                        .getBean(Connection.class));
    }
}

/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.jpa.h2;

import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.tests.integration.jpa.common.InsertTest;
import io.helidon.tests.integration.jpa.common.InsertTestImpl;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Local insert test.
 */
@HelidonTest
class H2InsertLocalTestIT extends H2LocalTest implements InsertTest {

    @Inject
    private InsertTestImpl delegate;

    @Test
    @Override
    public void testInsertType() {
        delegate.testInsertType();
    }

    @Test
    @Override
    public void testInsertTrainerWithPokemons() {
        delegate.testInsertTrainerWithPokemons();
    }

    @Test
    @Override
    public void testTownWithStadium() {
        delegate.testTownWithStadium();
    }
}

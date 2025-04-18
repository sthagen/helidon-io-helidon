/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.lookup;

import java.util.List;

import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.Service.QualifiedInstance;

@Service.PerLookup
@NoScopeServicesProviderExample.FirstQuali
@NoScopeServicesProviderExample.SecondQuali
class NoScopeServicesProviderExample implements Service.ServicesFactory<ContractNoScopeNoIpProvider> {
    static final Qualifier FIRST_QUALI = Qualifier.create(FirstQuali.class);
    static final Qualifier SECOND_QUALI = Qualifier.create(SecondQuali.class);

    @Override
    public List<QualifiedInstance<ContractNoScopeNoIpProvider>> services() {
        return List.of(
                QualifiedInstance.create(new FirstClass(), FIRST_QUALI),
                QualifiedInstance.create(new SecondClass(), SECOND_QUALI)
        );
    }

    @Service.Qualifier
    @interface FirstQuali {
    }

    @Service.Qualifier
    @interface SecondQuali {
    }

    static class FirstClass implements ContractNoScopeNoIpProvider {

    }

    static class SecondClass implements ContractNoScopeNoIpProvider {

    }
}

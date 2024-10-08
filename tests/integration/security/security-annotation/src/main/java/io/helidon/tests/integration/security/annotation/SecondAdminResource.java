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

package io.helidon.tests.integration.security.annotation;

import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * An admin resource that should not be accessible without proper credentials.
 */
@Authenticated
@Authorized(false)
@Path("/admin2")
public class SecondAdminResource {


    /**
     * The resource.
     *
     * @return admin secret.
     */
    @GET
    @Authorized
    @RolesAllowed("admin")
    public String admin() {
        return "admin";
    }


    /**
     * The resource.
     *
     * @return admin secret.
     */
    @GET
    @Path("/unauthorized")
    @RolesAllowed("admin")
    public String unauthorizedAdmin() {
        return "unauthorized admin";
    }

}

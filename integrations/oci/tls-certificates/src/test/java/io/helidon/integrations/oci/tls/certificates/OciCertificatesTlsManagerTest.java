/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.util.Optional;

import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

// see pom.xml for system properties that can be used in these tests
class OciCertificatesTlsManagerTest {
    @AfterEach
    void reset() {
        TestOciCertificatesDownloader.callCount_loadCertificates = 0;
        TestOciCertificatesDownloader.callCount_loadCACertificate = 0;
        TestOciPrivateKeyDownloader.callCount = 0;
    }

    @Test
    void managerCreation() {
        Config tlsManagerConfig = Config.create()
                .get("server.sockets.0.tls.manager.oci-certificates-tls-manager");
        OciCertificatesTlsManagerConfig cfg = OciCertificatesTlsManagerConfig
                .create(tlsManagerConfig);
        OciCertificatesTlsManager tlsManager = OciCertificatesTlsManager.create(cfg);
        assertThat(tlsManager,
                   notNullValue());
    }

    @Test
    void configIsMonitoredForChange() throws Exception {
        TestingConfigSource testingConfigSource =
                new TestingConfigSource(
                        "server.sockets.0.tls.manager.oci-certificates-tls-manager.key-password");
        Config config = Config.just(testingConfigSource,
                                    ConfigSources.systemProperties(),
                                    ConfigSources.classpath("application.yaml"));
        assertThat(config.exists(),
                   is(true));
        Config tlsConfig = config.get("server.sockets.0.tls");
        assertThat(tlsConfig.exists(),
                   is(true));

        int certDownloadCountBaseline0 = TestOciCertificatesDownloader.callCount_loadCertificates;
        int caCertDownloadCountBaseline0 = TestOciCertificatesDownloader.callCount_loadCACertificate;
        int pkDownloadCountBaseLine0 = TestOciPrivateKeyDownloader.callCount;
        assertThat("sanity",
                   certDownloadCountBaseline0,
                   equalTo(0));
        assertThat("sanity",
                   caCertDownloadCountBaseline0,
                   equalTo(0));
        assertThat("sanity",
                   pkDownloadCountBaseLine0,
                   equalTo(0));

        Tls tls = Tls.create(tlsConfig);
        assertThat(tls.prototype().manager(),
                   instanceOf(DefaultOciCertificatesTlsManager.class));

        int certDownloadCountBaseline = TestOciCertificatesDownloader.callCount_loadCertificates;
        int caCertDownloadCountBaseline = TestOciCertificatesDownloader.callCount_loadCACertificate;
        int pkDownloadCountBaseLine = TestOciPrivateKeyDownloader.callCount;
        assertThat(certDownloadCountBaseline,
                   equalTo(1));
        assertThat(caCertDownloadCountBaseline,
                   equalTo(1));
        assertThat(pkDownloadCountBaseLine,
                   equalTo(1));

        Config pwdConfig = tlsConfig.get("manager.oci-certificates-tls-manager.key-password");
        assertThat(pwdConfig.exists(),
                   is(true));

        // mutate it
        testingConfigSource.update("changed");
        assertThat(config.context().last()
                           .get("server.sockets.0.tls.manager.oci-certificates-tls-manager.key-password").asString().asOptional(),
                   is(Optional.of("changed")));
    }
}

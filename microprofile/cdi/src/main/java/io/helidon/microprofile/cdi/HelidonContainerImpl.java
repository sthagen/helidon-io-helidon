/*
 * Copyright (c) 2019, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.Main;
import io.helidon.common.SerializationConfig;
import io.helidon.common.Version;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.features.HelidonFeatures;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.config.mp.MpConfig;
import io.helidon.config.mp.MpConfigProviderResolver;
import io.helidon.logging.common.LogConfig;
import io.helidon.spi.HelidonShutdownHandler;

import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.weld.AbstractCDI;
import org.jboss.weld.bean.builtin.BeanManagerProxy;
import org.jboss.weld.bootstrap.WeldBootstrap;
import org.jboss.weld.bootstrap.api.Environments;
import org.jboss.weld.bootstrap.spi.BeanDeploymentArchive;
import org.jboss.weld.bootstrap.spi.Deployment;
import org.jboss.weld.config.ConfigurationKey;
import org.jboss.weld.configuration.spi.ExternalConfiguration;
import org.jboss.weld.configuration.spi.helpers.ExternalConfigurationBuilder;
import org.jboss.weld.environment.deployment.WeldDeployment;
import org.jboss.weld.environment.deployment.WeldResourceLoader;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.environment.se.events.ContainerBeforeShutdown;
import org.jboss.weld.environment.se.events.ContainerInitialized;
import org.jboss.weld.environment.se.events.ContainerShutdown;
import org.jboss.weld.environment.se.logging.WeldSELogger;
import org.jboss.weld.manager.BeanManagerImpl;
import org.jboss.weld.resources.spi.ResourceLoader;
import org.jboss.weld.serialization.spi.ProxyServices;

import static org.jboss.weld.config.ConfigurationKey.EXECUTOR_THREAD_POOL_TYPE;
import static org.jboss.weld.executor.ExecutorServicesFactory.ThreadPoolType.COMMON;

/**
 * Helidon CDI implementation.
 * This class inherits most of its functionality from {@link org.jboss.weld.environment.se.Weld}.
 * This is a needed extension to support ahead of time (AOT) compilation when
 * using GraalVM native-image.
 * It separates the {@link #init()} sequence from the {@link #start()} sequence.
 * <p>Initialization should happen statically and is part of the compiled native image.
 * Start happens at runtime with current configuration.
 * <p>When running in JIT mode (or on any regular JDK), this works as if Weld is used directly.
 * <p>Important note - you need to explicitly use this class. Using {@link jakarta.enterprise.inject.se.SeContainerInitializer} will
 * boot Weld.
 */
final class HelidonContainerImpl extends Weld implements HelidonContainer {
    private static final Logger LOGGER = Logger.getLogger(HelidonContainerImpl.class.getName());
    private static final AtomicBoolean IN_RUNTIME = new AtomicBoolean();
    private static final String EXIT_ON_STARTED_KEY = "exit.on.started";
    private static final boolean EXIT_ON_STARTED = "!".equals(System.getProperty(EXIT_ON_STARTED_KEY));
    // whether the current shutdown was invoked by the shutdown hook
    private static final AtomicBoolean FROM_SHUTDOWN_HOOK = new AtomicBoolean();
    // Default Weld container id. TCKs assumes this value.
    private static final String STATIC_INSTANCE = "STATIC_INSTANCE";

    static {
        HelidonFeatures.flavor(HelidonFlavor.MP);

        CDI.setCDIProvider(new HelidonCdiProvider());
    }

    private static volatile HelidonShutdownHandler shutdownHandler;
    private final WeldBootstrap bootstrap;
    private final String id;
    private final Context rootContext;

    private HelidonCdi cdi;

    HelidonContainerImpl() {
        this.bootstrap = new WeldBootstrap();
        this.id = STATIC_INSTANCE;
        this.rootContext = Context.builder()
                .id("helidon-cdi")
                .update(it ->
                                Contexts.context()
                                        .ifPresent(it::parent))
                .build();
    }

    /**
     * Creates and initializes the CDI container.
     * @return a new initialized CDI container
     */
    static HelidonContainerImpl create() {

        HelidonContainerImpl container = new HelidonContainerImpl();

        container.initInContext();

        return container;
    }

    void initInContext() {
        long time = System.nanoTime();

        Contexts.runInContext(rootContext, this::init);

        time = System.nanoTime() - time;
        long t = TimeUnit.MILLISECONDS.convert(time, TimeUnit.NANOSECONDS);
        LOGGER.fine("Container initialized in " + t + " millis");
    }

    private HelidonContainerImpl init() {
        LOGGER.fine(() -> "Initializing CDI container " + id);

        addHelidonBeanDefiningAnnotations("jakarta.ws.rs.Path",
                                          "jakarta.ws.rs.ext.Provider",
                                          "jakarta.websocket.server.ServerEndpoint",
                                          "org.eclipse.microprofile.graphql.GraphQLApi",
                                          "org.eclipse.microprofile.graphql.Input",
                                          "org.eclipse.microprofile.graphql.Interface",
                                          "org.eclipse.microprofile.graphql.Type");

        ResourceLoader resourceLoader = new WeldResourceLoader() {
            @Override
            public Collection<URL> getResources(String name) {
                Collection<URL> resources = super.getResources(name);
                return new HashSet<>(resources);    // drops duplicates when using patch-module
            }
        };
        setResourceLoader(resourceLoader);

        Config mpConfig = ConfigProvider.getConfig();
        io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);

        Map<String, String> properties = config.get("cdi")
                .detach()
                .asMap()
                .orElseGet(Map::of);

        setProperties(new HashMap<>(properties));

        // at least one extension must be added for correct startup, if the two extensions are removed
        // uncomment the next line
        // addExtension(new Extension() {});
        addExtension(new ExecuteOnExtension());
        addExtension(new ServiceRegistryExtension());

        Deployment deployment = createDeployment(resourceLoader, bootstrap);
        // we need to configure custom proxy services to
        // load classes in module friendly way
        deployment.getServices().add(ProxyServices.class, new HelidonProxyServices());

        ExternalConfigurationBuilder configurationBuilder = new ExternalConfigurationBuilder()
                // weld-se uses CommonForkJoinPoolExecutorServices by default
                .add(EXECUTOR_THREAD_POOL_TYPE.get(), COMMON.toString())
                // weld-se uses relaxed construction by default
                .add(ConfigurationKey.RELAXED_CONSTRUCTION.get(), true)
                // allow optimized cleanup by default
                .add(ConfigurationKey.ALLOW_OPTIMIZED_CLEANUP.get(), isEnabled(ALLOW_OPTIMIZED_CLEANUP, true));
        for (Map.Entry<String, String> property : properties.entrySet()) {
            String key = property.getKey();
            if (SHUTDOWN_HOOK_SYSTEM_PROPERTY.equals(key) || ARCHIVE_ISOLATION_SYSTEM_PROPERTY
                    .equals(key) || DEV_MODE_SYSTEM_PROPERTY.equals(key)
                    || SCAN_CLASSPATH_ENTRIES_SYSTEM_PROPERTY.equals(key) || JAVAX_ENTERPRISE_INJECT_SCAN_IMPLICIT.equals(key)) {
                continue;
            }
            configurationBuilder.add(key, property.getValue());
        }
        deployment.getServices().add(ExternalConfiguration.class, configurationBuilder.build());

        // CDI TCK tests expects SE to be excluded, which means Helidon may require to do things that Weld is supposed to do.
        bootstrap.startContainer(id, Environments.SE, deployment);

        bootstrap.startInitialization();

        Collection<BeanDeploymentArchive> archives = deployment.getBeanDeploymentArchives();
        if (archives.isEmpty()) {
            throw new IllegalStateException("No deployment archive");
        }
        BeanManagerImpl beanManager = bootstrap.getManager(archives.iterator().next());

        beanManager.getEvent().select(BuildTimeStart.Literal.INSTANCE).fire(id);

        bootstrap.deployBeans();

        cdi = new HelidonCdi(id, bootstrap, deployment);
        HelidonCdiProvider.setCdi(cdi);

        beanManager.getEvent().select(BuildTimeEnd.Literal.INSTANCE).fire(id);

        return this;
    }

    @SuppressWarnings("unchecked")
    private void addHelidonBeanDefiningAnnotations(String... classNames) {
        // I have to do this using reflection since annotation may not be in classpath
        for (String className : classNames) {
            try {
                Class<? extends Annotation> clazz = (Class<? extends Annotation>) Class.forName(className);
                addBeanDefiningAnnotations(clazz);
            } catch (Throwable e) {
                LOGGER.log(Level.FINEST, e, () -> className + " is not in the classpath, it will be ignored by CDI");
            }
        }
    }

    /**
     * Start this container.
     * @return container instance
     */
    @Override
    public SeContainer start() {
        if (IN_RUNTIME.get()) {
            // already started
            return cdi;
        }
        SerializationConfig.configureRuntime();
        LogConfig.configureRuntime();
        try {
            Contexts.runInContext(rootContext, this::doStart);
        } catch (Exception e) {
            try {
                // we must clean up
                shutdown();
            } catch (Exception exception) {
                e.addSuppressed(exception);
            }
            throw e;
        }

        if (EXIT_ON_STARTED) {
            exitOnStarted();
        }
        return cdi;
    }

    @Override
    public Context context() {
        return rootContext;
    }

    @Override
    public void shutdown() {
        cdi.close();
    }

    private HelidonContainerImpl doStart() {
        long now = System.currentTimeMillis();

        IN_RUNTIME.set(true);

        BeanManager bm = null;
        try {
            bm = CDI.current().getBeanManager();
        } catch (IllegalStateException e) {
            LOGGER.log(Level.FINEST, "Cannot get current CDI, probably restarted", e);
            // cannot access CDI - CDI is not yet initialized (probably shut down and started again)
            initInContext();
            bm = CDI.current().getBeanManager();
        }

        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();

        MpConfigProviderResolver.runtimeStart(config);

        bm.getEvent().select(RuntimeStart.Literal.INSTANCE).fire(config);

        bootstrap.validateBeans();
        bootstrap.endInitialization();

        shutdownHandler = new CdiShutdownHandler(cdi);

        bm.getEvent().select(Initialized.Literal.APPLICATION).fire(new ContainerInitialized(id));
        bm.getEvent().select(Any.Literal.INSTANCE).fire(new Startup());

        now = System.currentTimeMillis() - now;
        LOGGER.fine("Container started in " + now + " millis (this excludes the initialization time)");

        HelidonFeatures.print(HelidonFlavor.MP,
                              Version.VERSION,
                              config.getOptionalValue("features.print-details", Boolean.class).orElse(false));

        Main.addShutdownHandler(shutdownHandler);
        return this;
    }

    private void exitOnStarted() {
        LOGGER.info(String.format("Exiting, -D%s set.", EXIT_ON_STARTED_KEY));
        System.exit(0);
    }

    /**
     * Are we in runtime or build time.
     * @return {@code true} if this is runtime, {@code false} if this is build time
     */
    public static boolean isRuntime() {
        return IN_RUNTIME.get();
    }

    private static final class HelidonCdi extends AbstractCDI<Object> implements SeContainer {
        private final AtomicBoolean isRunning = new AtomicBoolean(true);
        private final String id;
        private final WeldBootstrap bootstrap;
        private final Deployment deployment;

        private HelidonCdi(String id, WeldBootstrap bootstrap, Deployment deployment) {
            this.id = id;
            this.bootstrap = bootstrap;
            this.deployment = deployment;
        }

        @Override
        public void close() {
            if (isRunning.compareAndSet(true, false)) {
                try {
                    beanManager().getEvent().select(BeforeDestroyed.Literal.APPLICATION).fire(new ContainerBeforeShutdown(id));
                } finally {
                    // Destroy all the dependent beans correctly
                    try {
                        beanManager().getEvent().select(Destroyed.Literal.APPLICATION).fire(new ContainerShutdown(id));
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, e, () -> "Failed to fire ApplicationScoped Destroyed event");
                    }
                    bootstrap.shutdown();
                    WeldSELogger.LOG.weldContainerShutdown(id);
                }
            }
            IN_RUNTIME.set(false);
            // need to reset - if somebody decides to restart CDI (such as a test)
            ContainerInstanceHolder.reset();

            if (!FROM_SHUTDOWN_HOOK.get()) {
                var usedShutdownHandler = shutdownHandler;
                if (usedShutdownHandler != null) {
                    Main.removeShutdownHandler(usedShutdownHandler);
                }
            }
        }

        @Override
        public boolean isRunning() {
            return isRunning.get();
        }

        @Override
        public BeanManager getBeanManager() {
            if (!isRunning.get()) {
                LOGGER.warning("BeanManager requested during container shutdown. This may be caused by observer methods "
                                       + "that use CDI.current(). Switch to finest logging to see stack trace.");
                if (LOGGER.isLoggable(Level.FINEST)) {
                    // this should not be common, but guarding, so we do not fill stack trace unless necessary
                    LOGGER.log(Level.FINEST,
                               "Invocation of container method during shutdown",
                               new IllegalStateException("Container not running"));
                }
            }

            return new BeanManagerProxy(beanManager());
        }

        private BeanManagerImpl beanManager() {
            return BeanManagerProxy.unwrap(bootstrap.getManager(getArchive(deployment)));
        }

        private BeanDeploymentArchive getArchive(Deployment deployment) {
            Collection<BeanDeploymentArchive> beanDeploymentArchives = deployment.getBeanDeploymentArchives();
            if (beanDeploymentArchives.size() == 1) {
                // Only one bean archive or isolation is disabled
                return beanDeploymentArchives.iterator().next();
            }
            for (BeanDeploymentArchive beanDeploymentArchive : beanDeploymentArchives) {
                if (WeldDeployment.SYNTHETIC_BDA_ID.equals(beanDeploymentArchive.getId())) {
                    // Synthetic bean archive takes precedence
                    return beanDeploymentArchive;
                }
            }
            for (BeanDeploymentArchive beanDeploymentArchive : beanDeploymentArchives) {
                if (!WeldDeployment.ADDITIONAL_BDA_ID.equals(beanDeploymentArchive.getId())) {
                    // Get the first non-additional bean deployment archive
                    return beanDeploymentArchive;
                }
            }
            return deployment.loadBeanDeploymentArchive(WeldContainer.class);
        }
    }

    @Weight(Weighted.DEFAULT_WEIGHT + 100) // higher than inject
    private static final class CdiShutdownHandler implements HelidonShutdownHandler {
        private final HelidonCdi cdi;

        private CdiShutdownHandler(HelidonCdi cdi) {
            this.cdi = cdi;
        }

        @Override
        public void shutdown() {
            FROM_SHUTDOWN_HOOK.set(true);
            cdi.close();
        }

        @Override
        public String toString() {
            return "Helidon CDI shutdown handler";
        }
    }
}

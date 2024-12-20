/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.datasource.ucp.cdi;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.sql.DataSource;

import io.helidon.integrations.datasource.cdi.AbstractDataSourceExtension;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.CreationException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Named;
import oracle.ucp.UniversalConnectionPoolException;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import oracle.ucp.jdbc.PoolDataSourceImpl;
import oracle.ucp.jdbc.PoolXADataSource;
import oracle.ucp.jdbc.PoolXADataSourceImpl;

/**
 * An {@link AbstractDataSourceExtension} that arranges for {@linkplain Named named} {@link DataSource} injection points
 * to be satisfied by the <a
 * href="https://docs.oracle.com/en/database/oracle/oracle-database/21/jjucp/index.html">Oracle Universal Connection
 * Pool</a>.
 *
 * <p>As with all portable extensions, to begin to make use of the features enabled by this class, ensure its containing
 * artifact (normally a jar file) is on the runtime classpath of your CDI-enabled application.</p>
 *
 * <p>In accordance with the CDI specification, instances of this class are not necessarily safe for concurrent use by
 * multiple threads.</p>
 *
 * <p>To support injection of a {@linkplain PoolDataSource Universal Connection Pool-backed <code>PoolDataSource</code>}
 * named {@code test}, first ensure that enough MicroProfile Config configuration is present to create a valid {@link
 * PoolDataSource}. For example, the following sample system properties are sufficient for a {@link PoolDataSource}
 * named {@code test} to be created:</p>
 *
 * {@snippet lang="properties" :
 *   # (Note that "oracle.ucp.jdbc.PoolDataSource" below could be "javax.sql.DataSource" instead if you prefer
 *   # that your configuration not refer directly to Oracle-specific classnames in its keys.)
 *   # @link substring="oracle.ucp.jdbc.PoolDataSource" target="PoolDataSource" @link substring="oracle.jdbc.pool.OracleDataSource" target="oracle.jdbc.pool.OracleDataSource" :
 *   oracle.ucp.jdbc.PoolDataSource.test.connectionFactoryClassName=oracle.jdbc.pool.OracleDataSource
 *   oracle.ucp.jdbc.PoolDataSource.test.URL=jdbc:oracle:thin://@localhost:1521/XE
 *   oracle.ucp.jdbc.PoolDataSource.test.user=scott
 *   oracle.ucp.jdbc.PoolDataSource.test.password=tiger
 *   }
 *
 * <p>With configuration such as the above, you can now inject the implicit {@link PoolDataSource} it defines:</p>
 *
 * {@snippet :
 *   // Inject a PoolDataSource qualified with the name test into a private javax.sql.DataSource-typed field named ds:
 *   @jakarta.inject.Inject // @link substring="jakarta.inject.Inject" target="jakarta.inject.Inject"
 *   @jakarta.inject.Named("test") // @link substring="jakarta.inject.Named" target="jakarta.inject.Named"
 *   private javax.sql.DataSource ds; // @link substring="javax.sql.DataSource" target="DataSource"
 *   }
 */
public class UCPBackedDataSourceExtension extends AbstractDataSourceExtension {

    /**
     * A {@link Pattern} capable of producing {@link Matcher} instances that identify certain portions of a
     * configuration property name.
     */
    static final Pattern DATASOURCE_NAME_PATTERN =
        Pattern.compile("^(?:javax\\.sql\\.|oracle\\.ucp\\.jdbc\\.Pool)(XA)?DataSource\\.([^.]+)\\.(.*)$");
    // Capturing groups:                                               (1 )              (2    )   (3 )
    //                                                                 Are we XA?        DS name   DS property name

    private final Map<String, Boolean> xa;

    /**
     * Creates a new {@link UCPBackedDataSourceExtension}.
     *
     * @deprecated For use by CDI only.
     */
    @Deprecated // For use by CDI only
    public UCPBackedDataSourceExtension() {
        super();
        this.xa = new HashMap<>();
    }

    @Override
    protected final Matcher getDataSourcePropertyPatternMatcher(String configPropertyName) {
        return configPropertyName == null ? null : DATASOURCE_NAME_PATTERN.matcher(configPropertyName);
    }

    @Override
    protected final String getDataSourceName(Matcher dataSourcePropertyPatternMatcher) {
        String returnValue;
        if (dataSourcePropertyPatternMatcher == null) {
            returnValue = null;
        } else {
            returnValue = dataSourcePropertyPatternMatcher.group(2);
            // While we have the Matcher available, store whether this is XA or not.
            this.xa.put(returnValue, dataSourcePropertyPatternMatcher.group(1) != null);
        }
        return returnValue;
    }

    @Override
    protected final String getDataSourcePropertyName(Matcher dataSourcePropertyPatternMatcher) {
        return dataSourcePropertyPatternMatcher == null ? null : dataSourcePropertyPatternMatcher.group(3);
    }

    @Override
    protected final void addBean(BeanConfigurator<DataSource> beanConfigurator,
                                 Named dataSourceName,
                                 Properties dataSourceProperties) {
        boolean xa = this.xa.get(dataSourceName.value());
        beanConfigurator
            .addQualifier(dataSourceName)
            .addTransitiveTypeClosure(xa ? PoolXADataSourceImpl.class : PoolDataSourceImpl.class)
            .scope(ApplicationScoped.class)
            .produceWith(instance -> {
                    try {
                        return createDataSource(instance, dataSourceName, xa, dataSourceProperties);
                    } catch (IntrospectionException
                             | ReflectiveOperationException
                             | SQLException
                             | UniversalConnectionPoolException exception) {
                        throw new CreationException(exception.getMessage(), exception);
                    }
                })
            .disposeWith((dataSource, ignored) -> {
                    if (dataSource instanceof AutoCloseable autoCloseable) {
                        try {
                            autoCloseable.close();
                        } catch (RuntimeException runtimeException) {
                            throw runtimeException;
                        } catch (Exception exception) {
                            throw new CreationException(exception.getMessage(), exception);
                        }
                    }
                });
    }

    private static PoolDataSource createDataSource(Instance<Object> instance,
                                                   Named dataSourceName,
                                                   boolean xa,
                                                   Properties properties)
        throws IntrospectionException, ReflectiveOperationException, SQLException, UniversalConnectionPoolException {
        // See
        // https://docs.oracle.com/en/database/oracle/oracle-database/19/jjucp/get-started.html#GUID-2CC8D6EC-483F-4942-88BA-C0A1A1B68226
        // for the general pattern.
        PoolDataSource returnValue =
            xa ? PoolDataSourceFactory.getPoolXADataSource() : PoolDataSourceFactory.getPoolDataSource();
        Set<String> propertyNames = properties.stringPropertyNames();
        if (!propertyNames.isEmpty()) {
            Properties connectionFactoryProperties = new Properties();
            BeanInfo beanInfo = Introspector.getBeanInfo(returnValue.getClass());
            PropertyDescriptor[] pds = beanInfo.getPropertyDescriptors();
            for (String propertyName : propertyNames) {
                if (propertyName != null) {
                    boolean handled = false;
                    for (PropertyDescriptor pd : pds) {
                        if (propertyName.equals(pd.getName())) {
                            // We have matched a Java Beans property on the PoolDataSource implementation class.  Set it
                            // if we can. PoolDataSourceImpl Java Beans properties happen to be either String-, int-,
                            // long-, or boolean-typed properties only.
                            //
                            // Note that these properties are NOT those of the PoolDataSource's *underlying* "real"
                            // connection factory (usually a DataSource that provides the actual connections ultimately
                            // pooled by the Universal Connection Pool). Those are handled in a manner unfortunately
                            // restricted by the limited configuration mechanism belonging to the PoolDataSource
                            // implementation itself via the connectionFactoryProperties object. See below.
                            Method writeMethod = pd.getWriteMethod();
                            if (writeMethod != null) {
                                Class<?> type = pd.getPropertyType();
                                if (type.equals(String.class)) {
                                    writeMethod.invoke(returnValue, properties.getProperty(propertyName));
                                    handled = true;
                                } else if (type.equals(Integer.TYPE)) {
                                    writeMethod.invoke(returnValue, Integer.parseInt(properties.getProperty(propertyName)));
                                    handled = true;
                                } else if (type.equals(Long.TYPE)) {
                                    writeMethod.invoke(returnValue, Long.parseLong(properties.getProperty(propertyName)));
                                    handled = true;
                                } else if (type.equals(Boolean.TYPE)) {
                                    writeMethod.invoke(returnValue, Boolean.parseBoolean(properties.getProperty(propertyName)));
                                    handled = true;
                                }
                            }
                        }
                    }
                    if (!handled) {
                        // We have found a property that is not a writable Java Beans property of the PoolDataSource,
                        // but is supposed to be a writable Java Beans property of the connection factory that it wraps.
                        //
                        // Sadly, the Universal Connection Pool lacks a mechanism to send *arbitrarily-typed* Java
                        // Beans-conformant property values destined for the underlying connection factory (which is
                        // usually a DataSource or ConnectionPoolDataSource implementation, but may be other things) to
                        // that underlying connection factory. Because the PoolDataSource is in charge of instantiating
                        // the connection factory, you can't pass a fully configured DataSource into it, nor can you
                        // access an unconfigured instance of it that you can work with. The only configuration the
                        // Universal Connection Pool supports sending to the connection factory is via a Properties
                        // object, whose values are retrieved by the PoolDataSource implementation, as Strings. This
                        // limits the kinds of underlying connection factories (DataSource implementations, usually)
                        // that can be fully configured with the Universal Connection Pool to Strings and those Strings
                        // which can be converted by the PoolDataSourceImpl#toBasicType(String, String) method.
                        connectionFactoryProperties.setProperty(propertyName, properties.getProperty(propertyName));
                    }
                }
            }
            if (!connectionFactoryProperties.stringPropertyNames().isEmpty()) {
                // We found some String-typed properties that are destined for the underlying connection factory to
                // hopefully fully configure it. Apply them here.
                returnValue.setConnectionFactoryProperties(connectionFactoryProperties);
            }
        }
        if (returnValue.getConnectionPoolName() == null) {
            String proposedConnectionPoolName = dataSourceName.value();
            String[] existingConnectionPoolNames =
                UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager().getConnectionPoolNames();
            for (String existingConnectionPoolName : existingConnectionPoolNames) {
                if (proposedConnectionPoolName.equals(existingConnectionPoolName)) {
                    // If the return value of an invocation of PoolDataSource#getConnectionPoolName() equals the name of
                    // an already existing UniversalConnectionPool instance, the first invocation of
                    // PoolDataSource#getConnection(), or any other operation that requires pool creation, will throw a
                    // SQLException (!). In this case only we let the auto-generated name (!) be used instead.
                    proposedConnectionPoolName = null;
                    break;
                }
            }
            if (proposedConnectionPoolName != null) {
                returnValue.setConnectionPoolName(proposedConnectionPoolName);
            }
        }
        Instance<SSLContext> sslContextInstance = instance.select(SSLContext.class, dataSourceName);
        if (!sslContextInstance.isUnsatisfied()) {
            returnValue.setSSLContext(sslContextInstance.get());
        }
        // Permit further customization before the bean is actually created
        if (xa) {
            instance.select(new TypeLiteral<Event<PoolXADataSource>>() {}, dataSourceName)
                .get()
                .fire((PoolXADataSource) returnValue);
        } else {
            instance.select(new TypeLiteral<Event<PoolDataSource>>() {}, dataSourceName)
                .get()
                .fire(returnValue);
        }
        return returnValue;
    }

}

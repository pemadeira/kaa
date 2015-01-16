/*
 * Copyright 2014 CyberVision, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaaproject.kaa.client.channel;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.Assert;
import org.junit.Test;
import org.kaaproject.kaa.client.AbstractKaaClient;
import org.kaaproject.kaa.client.channel.impl.channels.DefaultBootstrapChannel;
import org.kaaproject.kaa.client.persistence.KaaClientState;
import org.kaaproject.kaa.client.transport.AbstractHttpClient;
import org.kaaproject.kaa.common.TransportType;
import org.kaaproject.kaa.common.endpoint.security.KeyUtil;
import org.mockito.Mockito;

public class DefaultBootstrapChannelTest {

    public ExecutorService fakeExecutor = new FakeExecutorService();

    class DefaultBootstrapChannelFake extends DefaultBootstrapChannel {

        private final int wantedNumberOfInvocations;

        public DefaultBootstrapChannelFake(AbstractKaaClient client, KaaClientState state, int wantedNumberOfInvocations) {
            super(client, state);
            this.wantedNumberOfInvocations = wantedNumberOfInvocations;
        }

        @Override
        protected ExecutorService createExecutor() {
            super.createExecutor();
            return fakeExecutor;
        }

        public void verify() throws Exception {
            Mockito.verify(getMultiplexer(), Mockito.times(wantedNumberOfInvocations)).compileRequest(Mockito.anyMap());
            Mockito.verify(getDemultiplexer(), Mockito.times(wantedNumberOfInvocations))
                    .processResponse(Mockito.eq(new byte[] { 5, 5, 5 }));
        }
    }

    @Test
    public void testChannelGetters() {
        AbstractKaaClient client = Mockito.mock(AbstractKaaClient.class);
        KaaClientState state = Mockito.mock(KaaClientState.class);
        KaaDataChannel channel = new DefaultBootstrapChannel(client, state);

        Assert.assertEquals(ChannelDirection.BIDIRECTIONAL, channel.getSupportedTransportTypes().get(TransportType.BOOTSTRAP));
        Assert.assertEquals(TransportProtocolIdConstants.HTTP_TRANSPORT_ID, channel.getTransportProtocolId());
        Assert.assertEquals("default_bootstrap_channel", channel.getId());
    }

    @Test
    public void testChannelSync() throws Exception {
        KaaChannelManager manager = Mockito.mock(KaaChannelManager.class);
        AbstractHttpClient httpClient = Mockito.mock(AbstractHttpClient.class);
        Mockito.when(httpClient.executeHttpRequest(Mockito.anyString(), Mockito.any(LinkedHashMap.class), Mockito.anyBoolean()))
                .thenReturn(new byte[] { 5, 5, 5 });

        AbstractKaaClient client = Mockito.mock(AbstractKaaClient.class);
        Mockito.when(
                client.createHttpClient(Mockito.anyString(), Mockito.any(PrivateKey.class), Mockito.any(PublicKey.class),
                        Mockito.any(PublicKey.class))).thenReturn(httpClient);

        Mockito.when(client.getChannelMananager()).thenReturn(manager);

        KaaClientState state = Mockito.mock(KaaClientState.class);
        KaaDataMultiplexer multiplexer = Mockito.mock(KaaDataMultiplexer.class);
        KaaDataDemultiplexer demultiplexer = Mockito.mock(KaaDataDemultiplexer.class);
        DefaultBootstrapChannelFake channel = new DefaultBootstrapChannelFake(client, state, 2);

        TransportConnectionInfo server = IPTransportInfoTest.createTestServerInfo(ServerType.BOOTSTRAP, TransportProtocolIdConstants.HTTP_TRANSPORT_ID,
                "localhost", 9889, KeyUtil.generateKeyPair().getPublic());

        channel.setServer(server);

        channel.sync(TransportType.BOOTSTRAP);
        channel.setDemultiplexer(demultiplexer);
        channel.setDemultiplexer(null);
        channel.sync(TransportType.BOOTSTRAP);
        channel.setMultiplexer(multiplexer);
        channel.setMultiplexer(null);
        channel.sync(TransportType.CONFIGURATION);
        channel.sync(TransportType.BOOTSTRAP);

        channel.syncAll();

        channel.verify();
    }

    @Test
    public void testServerFailed() throws Exception {
        KaaChannelManager manager = Mockito.mock(KaaChannelManager.class);
        AbstractHttpClient httpClient = Mockito.mock(AbstractHttpClient.class);
        Mockito.when(httpClient.executeHttpRequest(Mockito.anyString(), Mockito.any(LinkedHashMap.class), Mockito.anyBoolean())).thenThrow(
                new Exception());

        AbstractKaaClient client = Mockito.mock(AbstractKaaClient.class);
        Mockito.when(
                client.createHttpClient(Mockito.anyString(), Mockito.any(PrivateKey.class), Mockito.any(PublicKey.class),
                        Mockito.any(PublicKey.class))).thenReturn(httpClient);

        Mockito.when(client.getChannelMananager()).thenReturn(manager);

        KaaClientState state = Mockito.mock(KaaClientState.class);
        KaaDataMultiplexer multiplexer = Mockito.mock(KaaDataMultiplexer.class);
        KaaDataDemultiplexer demultiplexer = Mockito.mock(KaaDataDemultiplexer.class);
        DefaultBootstrapChannelFake channel = new DefaultBootstrapChannelFake(client, state, 1);

        TransportConnectionInfo server = IPTransportInfoTest.createTestServerInfo(ServerType.BOOTSTRAP, TransportProtocolIdConstants.HTTP_TRANSPORT_ID,
                "localhost", 9889, KeyUtil.generateKeyPair().getPublic());

        channel.sync(TransportType.BOOTSTRAP);
        channel.setDemultiplexer(demultiplexer);
        channel.sync(TransportType.BOOTSTRAP);
        channel.setMultiplexer(multiplexer);
        channel.sync(TransportType.CONFIGURATION);
        channel.sync(TransportType.BOOTSTRAP);
        channel.syncAll();

        channel.setServer(server);

        Mockito.verify(manager, Mockito.times(1)).onServerFailed(Mockito.any(TransportConnectionInfo.class));
    }

    @Test
    public void testShutdown() throws Exception {
        KaaChannelManager manager = Mockito.mock(KaaChannelManager.class);
        AbstractHttpClient httpClient = Mockito.mock(AbstractHttpClient.class);
        Mockito.when(httpClient.executeHttpRequest(Mockito.anyString(), Mockito.any(LinkedHashMap.class), Mockito.anyBoolean())).thenThrow(
                new Exception());

        AbstractKaaClient client = Mockito.mock(AbstractKaaClient.class);
        Mockito.when(
                client.createHttpClient(Mockito.anyString(), Mockito.any(PrivateKey.class), Mockito.any(PublicKey.class),
                        Mockito.any(PublicKey.class))).thenReturn(httpClient);

        Mockito.when(client.getChannelMananager()).thenReturn(manager);

        KaaClientState state = Mockito.mock(KaaClientState.class);
        KaaDataMultiplexer multiplexer = Mockito.mock(KaaDataMultiplexer.class);
        KaaDataDemultiplexer demultiplexer = Mockito.mock(KaaDataDemultiplexer.class);
        DefaultBootstrapChannelFake channel = new DefaultBootstrapChannelFake(client, state, 0);
        channel.setDemultiplexer(demultiplexer);
        channel.setMultiplexer(multiplexer);
        channel.shutdown();

        TransportConnectionInfo server = IPTransportInfoTest.createTestServerInfo(ServerType.BOOTSTRAP, TransportProtocolIdConstants.HTTP_TRANSPORT_ID,
                "localhost", 9889, KeyUtil.generateKeyPair().getPublic());
        channel.setServer(server);

        channel.sync(TransportType.BOOTSTRAP);
        channel.syncAll();

        channel.verify();
    }

}

/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaazing.gateway.transport.wsn.logging;

import static org.kaazing.test.util.ITUtil.createRuleChain;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandler;
import org.jmock.Expectations;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

import org.kaazing.test.util.MemoryAppender;
import org.kaazing.gateway.transport.ws.bridge.filter.WsBuffer;
import org.kaazing.gateway.transport.wsn.specification.ws.connector.WsnConnectorRule;
import org.kaazing.gateway.transport.wsn.WsnProtocol;
import org.kaazing.gateway.transport.wsn.WsnSession;
import org.kaazing.mina.core.buffer.IoBufferAllocatorEx;
import org.kaazing.mina.core.buffer.IoBufferEx;
import org.kaazing.mina.core.session.IoSessionEx;

// This is a subset of BaseFramingIT (connector version) used to verify wsn transport level logging
public class WsnConnectorLoggingIT {
    private static String TEXT_FILTER_NAME = WsnProtocol.NAME + "#text";
    private final WsnConnectorRule connector = new WsnConnectorRule();
    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws");

    @Rule
    public JUnitRuleMockery context = new JUnitRuleMockery() {
        {
            setThreadingPolicy(new Synchroniser());
        }
    };

    @Rule
    public final TestRule chain = createRuleChain(connector, k3po);

    @Test
    @Specification({
        "framing/echo.binary.payload.length.125/handshake.response.and.frame"
        })
    public void shouldLogOpenWriteReceivedAndAbruptClose() throws Exception {
        final IoHandler handler = context.mock(IoHandler.class);
        final CountDownLatch received = new CountDownLatch(1);

        context.checking(new Expectations() {
            {
                oneOf(handler).sessionCreated(with(any(IoSessionEx.class)));
                oneOf(handler).sessionOpened(with(any(IoSessionEx.class)));
                oneOf(handler).messageReceived(with(any(IoSessionEx.class)), with(any(Object.class)));
                will(new CustomAction("Latch countdown") {
                    @Override
                    public Object invoke(Invocation invocation) throws Throwable {
                        received.countDown();
                        return null;
                    }
                });
                oneOf(handler).exceptionCaught(with(any(IoSessionEx.class)), with(any(Throwable.class)));
                oneOf(handler).sessionClosed(with(any(IoSessionEx.class)));
            }
        });

        ConnectFuture connectFuture = connector.connect("ws://localhost:8080/echo", null, handler);
        connectFuture.awaitUninterruptibly();

        WsnSession wsnConnectSession = (WsnSession) connectFuture.getSession();
        // ### Issue# 316: Temporary hack till the issue related to Connector writing out TEXT frame
        //                  instead of BINARY is resolved.
        if (wsnConnectSession != null) {
            IoFilterChain parentFilterChain = wsnConnectSession.getParent().getFilterChain();
            if (parentFilterChain.contains(TEXT_FILTER_NAME)) {
                parentFilterChain.remove(TEXT_FILTER_NAME);
            }
        }

        Random random = new Random();
        byte[] bytes = new byte[125];
        random.nextBytes(bytes);

        IoBufferAllocatorEx<? extends WsBuffer> allocator = wsnConnectSession.getBufferAllocator();
        WsBuffer wsBuffer = allocator.wrap(ByteBuffer.wrap(bytes), IoBufferEx.FLAG_SHARED);
        wsBuffer.setKind(WsBuffer.Kind.BINARY);
        wsnConnectSession.write(wsBuffer);
        assertTrue(received.await(10, SECONDS));

        k3po.finish();

        List<String> expectedPatterns = new ArrayList<String>(Arrays.asList(new String[] {
            "tcp#.*OPENED",
            "tcp#.*WRITE",
            "tcp#.*RECEIVED",
            "tcp#.*CLOSED",
            "http#.*OPENED",
            "http#.*CLOSED",
            "wsn#.*OPENED",
            "wsn#.*WRITE",
            "wsn#.*RECEIVED",
            "wsn#.*EXCEPTION", // because the script does not complete the WebSocket close handshake
            "wsn#.*CLOSED"
        }));

        List<String> forbiddenPatterns = null;

        MemoryAppender.assertMessagesLogged(expectedPatterns, forbiddenPatterns, ".*\\[.*#.*].*", true);
    }

    @Test
    @Specification({
        "closing/client.send.close.frame.with.code.1000/handshake.response.and.frame" })
    public void shouldLogOpenAndCleanClose() throws Exception {
        final IoHandler handler = context.mock(IoHandler.class);
        final CountDownLatch close = new CountDownLatch(1);

        context.checking(new Expectations() {
            {
                oneOf(handler).sessionCreated(with(any(IoSessionEx.class)));
                oneOf(handler).sessionOpened(with(any(IoSessionEx.class)));
                oneOf(handler).sessionClosed(with(any(IoSessionEx.class)));
                will(new CustomAction("Latch countdown") {
                    @Override
                    public Object invoke(Invocation invocation) throws Throwable {
                        close.countDown();
                        return null;
                    }
                });
            }
        });

        ConnectFuture connectFuture = connector.connect("ws://localhost:8080/echo", null, handler);
        connectFuture.await(10, SECONDS);
        connectFuture.getSession().close(false);

        k3po.finish();
        assertTrue(close.await(10, SECONDS));

        List<String> expectedPatterns = new ArrayList<String>(Arrays.asList(new String[] {
            "tcp#.* [^/]*:\\d*] OPENED",
            "tcp#.* [^/]*:\\d*] WRITE",
            "tcp#.* [^/]*:\\d*] RECEIVED",
            "tcp#.* [^/]*:\\d*] CLOSED",
            "http#.* [^/]*:\\d*] OPENED",
            "http#.* [^/]*:\\d*] CLOSED",
            "wsn#.* [^/]*:\\d*] OPENED",
            "wsn#.* [^/]*:\\d*] CLOSED"
        }));

        List<String> forbiddenPatterns = Arrays.asList("#.*EXCEPTION");

        MemoryAppender.assertMessagesLogged(expectedPatterns, forbiddenPatterns, ".*\\[.*#.*].*", true);
    }

}
/*
 * Copyright 2009 Mike Cumings
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kenai.jbosh;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocket;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.*;

/**
 * BOSHClient tests.
 */
public class BOSHClientTest extends AbstractBOSHTest {

    private static final Logger LOG =
            Logger.getLogger(BOSHClientTest.class.getName());

    @Test(timeout=5000)
    public void explicitConnectionClose() throws Exception {
        logTestStart();

        final List<BOSHClientConnEvent> events =
                new ArrayList<BOSHClientConnEvent>();
        session.addBOSHClientConnListener(new BOSHClientConnListener() {
            public void connectionEvent(BOSHClientConnEvent connEvent) {
                events.add(connEvent);
            }
        });

        // Session creation
        session.send(ComposableBody.builder()
                .setAttribute(Attributes.WAIT, "1")
                .build());
        StubConnection conn = cm.awaitConnection();
        
        AbstractBody scr = getSessionCreationResponse(conn.getRequest().getBody()).build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        BOSHClientConnEvent event = events.remove(0);
        assertTrue(event.isConnected());
        assertFalse(event.isError());

        // Explicit session termination
        session.disconnect();
        conn = cm.awaitConnection();
        scr = ComposableBody.builder()
                .setAttribute(Attributes.TYPE, "terminate")
                .build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        event = events.remove(0);
        assertFalse(event.isConnected());
        assertFalse(event.isError());
    }

    @Test(timeout=5000)
    public void packetSentAfterClose() throws Exception {
        logTestStart();

        // Session creation
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = getSessionCreationResponse(conn.getRequest().getBody()).build();
        conn.sendResponse(scr);
        session.drain();
        
        // Explicit session termination
        session.close();
      
        try {
            session.send(ComposableBody.builder().build());
        } catch(BOSHException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Cannot send message when session is closed"));
            return;
        }

        fail("Expected BOSHException");
    }

    @Test(timeout=5000)
    public void disconnectAfterClose() throws Exception {
        logTestStart();

        // Session creation
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = getSessionCreationResponse(conn.getRequest().getBody()).build();
        conn.sendResponse(scr);
        session.drain();
        
        // Explicit session termination
        session.close();
      
        try {
            session.disconnect();
        } catch(BOSHException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Cannot send message when session is closed"));
            return;
        }

        fail("Expected BOSHException");
    }
    
    /**
     * Test closing BOSHClient while httpSender is waiting for a DNS response.
     */
    // XXX

    /**
     * Test closing BOSHClient while httpSender is waiting for a server connection
     * to be established.
     */
    // XXX

    /**
     * Test closing BOSHClient while httpSender is waiting for a response to a packet.
     */
    @Test(timeout=5000)
    public void testCloseDuringRead() throws Exception {
        // Send session initialization.
        session.send(ComposableBody.builder().build());
        session.close();
    }

    /** This TestSSLConnector tracks whether it has been used to instantiate a Socket. */
    static class TestSSLConnector extends SSLConnector {
        boolean wasUsed = false;
        public SSLSocket attachSSLConnection(Socket socket, String host, int port) throws IOException {
            wasUsed = true;

            // We don't actually have a CM that supports SSL; throw an error after setting the flag.
            throw new IOException("Dummy error");
        }
    };

    /*
     * Test that the correct supplied SocketFactory is used when making an HTTP connection.
     */
    @Test(timeout=5000)
    public void configSocketFactory() throws Exception {
        logTestStart();

        TestSSLConnector sslConnector = new TestSSLConnector();
        // Create an HTTP session
        BOSHClientConfig cfg = BOSHClientConfig.Builder.create(cm.getURI(), "test@domain")
                .setSSLConnector(sslConnector)
                .build();
        assertEquals(cfg.getURI().getScheme(), "http");
        session = createSession(cfg);

        session.send(ComposableBody.builder().build());
        cm.awaitConnection();
        assertFalse(sslConnector.wasUsed);
    }

    /*
     * Test that the correct supplied SocketFactory is used when making an HTTPS connection.
     */
    @Test(timeout=5000)
    public void configSSLSocketFactory() throws Exception {
        logTestStart();

        TestSSLConnector sslConnector = new TestSSLConnector();
        // Create an HTTPS session
        BOSHClientConfig cfg = BOSHClientConfig.Builder.create(cm.getURIHTTPS(), "test@domain")
                .setSSLConnector(sslConnector)
                .build();
        assertEquals(cfg.getURI().getScheme(), "https");
        session = createSession(cfg);

        session.send(ComposableBody.builder().build());

        // Give the connection a moment to call attachSSLConnection.  The connection will
        // fail due to the error thrown by TestSSLConnector, so we can't use cm.awaitConnection().
        Thread.sleep(250);
        assertTrue(sslConnector.wasUsed);
    }

    @Test(timeout=5000)
    public void connectionCloseOnError() throws Exception {
        logTestStart();

        final List<BOSHClientConnEvent> events =
                new ArrayList<BOSHClientConnEvent>();
        session.addBOSHClientConnListener(new BOSHClientConnListener() {
            public void connectionEvent(BOSHClientConnEvent connEvent) {
                events.add(connEvent);
            }
        });

        // Session creation
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody req = conn.getRequest().getBody();
        AbstractBody scr = getSessionCreationResponse(req).build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        BOSHClientConnEvent event = events.remove(0);
        assertTrue(event.isConnected());
        assertFalse(event.isError());

        // Session termination on error
        session.send(ComposableBody.builder().build());
        conn = cm.awaitConnection();
        scr = ComposableBody.builder()
                .setAttribute(Attributes.SID, "123XYZ")
                .setAttribute(Attributes.TYPE, "terminate")
                .setAttribute(Attributes.CONDITION, "item-not-found")
                .build();
        conn.sendResponse(scr);
        session.drain();
        assertEquals(1, events.size());
        event = events.remove(0);
        assertFalse(event.isConnected());
        assertTrue(event.isError());
        Throwable cause = event.getCause();
        assertNotNull(cause);
        assertTrue(cause instanceof BOSHException);
        BOSHException boshEx = (BOSHException) cause;
        String msg = boshEx.getMessage();
        assertTrue(msg.contains(
                TerminalBindingCondition.ITEM_NOT_FOUND.getMessage()));
    }

    @Test(timeout=10000)
    @SuppressWarnings({"unchecked", "unchecked"})
    public void concurrentSends() throws Exception {
        logTestStart();

        // Session creation.  Disable inactivity checking for this test.
        session.send(ComposableBody.builder().build());
        StubConnection conn = cm.awaitConnection();
        AbstractBody scr = getSessionCreationResponse(conn.getRequest().getBody())
            .setAttribute(Attributes.INACTIVITY, null)
            .build();
        conn.sendResponse(scr);
        session.drain();

        final int threadCount = 5;
        final int messageCount = 50;

        // Configure concurrent threads
        final SynchronousQueue[] queues = new SynchronousQueue[threadCount];
        final Thread[] threads = new Thread[threadCount];
        final CyclicBarrier barrier = new CyclicBarrier(2);
        for (int idx=0; idx<threadCount; idx++) {
            final int id = idx;
            final int nextID = (id + 1) % threadCount;
            queues[idx] = new SynchronousQueue();
            threads[idx] = new Thread() {
                @Override
                public void run() {
                    SynchronousQueue queue = queues[id];
                    try {
                        boolean working = true;
                        do {
                            AtomicInteger aInt = (AtomicInteger) queue.take();
                            int val = aInt.getAndIncrement();
                            if (val < messageCount) {
                                LOG.finest(id + " sending message " + val);
                                ComposableBody msg = ComposableBody.builder()
                                        .setAttribute(Attributes.SID, "123XYZ")
                                        .setNamespaceDefinition("foo", "http://foo/")
                                        .setPayloadXML("<foo:bar>" + val + "</foo:bar>")
                                        .build();
                                session.send(msg);
                                StubConnection respConn = cm.awaitConnection();
                                respConn.sendResponse(ComposableBody.builder()
                                    .build());
                                session.drain();
                            } else {
                                LOG.finest(id + " done");
                                working = false;
                                if (val == messageCount) {
                                    LOG.info(id + " signalling controller");
                                    barrier.await();
                                }
                            }

                            // handoff to the next thread
                            if (!queues[nextID].offer(aInt)) {
                                LOG.info("Last thread reached");
                            }
                        } while(working);
                    } catch (InterruptedException intx) {
                        LOG.log(Level.FINE, id + " Interrupted", intx);
                    } catch (BOSHException boshx) {
                        LOG.log(Level.FINE, id + " Caught exception", boshx);
                    } catch (BrokenBarrierException bbx) {
                        LOG.log(Level.FINE, id + " Caught exception", bbx);
                    } catch (IOException iox) {
                        LOG.log(Level.FINE, id + " Caught exception", iox);
                    } finally {
                        LOG.finest(id + " exiting");
                    }
                }
            };
            threads[idx].setDaemon(true);
            threads[idx].start();
        }

        // Send message sequence
        queues[0].put(new AtomicInteger());
        LOG.info("Controller waiting");
        barrier.await();
        LOG.info("Controller continuing");

        // Explicit session termination
        session.disconnect();
        conn = cm.awaitConnection();
        scr = ComposableBody.builder()
                .setAttribute(Attributes.TYPE, "terminate")
                .build();
        conn.sendResponse(scr);
        session.drain();

        // Stop threads
        for (int idx=0; idx<threadCount; idx++) {
            threads[idx].join();
        }

        // Verify messages were sent in order
        StringBuilder expected = new StringBuilder();
        for (int i=0; i<messageCount; i++) {
            expected.append("<foo:bar>");
            expected.append(i);
            expected.append("</foo:bar>");
        }
        StringBuilder actual = new StringBuilder();
        for (AbstractBody req : reqValidator.getRequests()) {
            ComposableBody body = (ComposableBody) req;
            actual.append(body.getPayloadXML());
        }
        assertEquals(expected.toString(), actual.toString());

        assertValidators(scr);
    }

}

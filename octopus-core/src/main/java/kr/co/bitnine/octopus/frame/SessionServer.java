/*
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

package kr.co.bitnine.octopus.frame;

import kr.co.bitnine.octopus.util.NetUtils;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.AbstractService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SessionServer extends AbstractService
{
    private static final Log LOG = LogFactory.getLog(SessionServer.class);

    private static final int EXECUTOR_MAX_DEFAULT = 5;
    private static final long EXECUTOR_KEEPALIVE_DEFAULT = 60;
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_DEFAULT = 5;

    private volatile boolean running = false;
    private Listener listener = null;
    private ThreadPoolExecutor executor = null;

    public SessionServer()
    {
        super(SessionServer.class.getSimpleName());
    }

    @Override
    protected void serviceInit(Configuration conf) throws Exception
    {
        super.serviceInit(conf);

        listener = new Listener();

        executor = new ThreadPoolExecutor(
                0,
                conf.getInt("master.session.max", EXECUTOR_MAX_DEFAULT),
                EXECUTOR_KEEPALIVE_DEFAULT,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());
    }

    @Override
    protected void serviceStart() throws Exception
    {
        running = true;

        listener.start();

        super.serviceStart();
    }

    @Override
    protected void serviceStop() throws Exception
    {
        running = false;

        listener.interrupt();
        listener.join();
        listener = null;

        executor.shutdownNow();
        boolean terminated = executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_DEFAULT, TimeUnit.SECONDS);
        if (!terminated)
            LOG.warn("there are remaining sessions still running");
        executor = null;

        super.serviceStop();
    }

    private class Listener extends Thread
    {
        private ServerSocketChannel acceptChannel;

        public Listener() throws IOException
        {
            setName("SessionServer Listener");

            acceptChannel = ServerSocketChannel.open();
            InetSocketAddress addr = NetUtils.createSocketAddr(getConfig().get("master.server.address"));
            acceptChannel.bind(addr, 0);
        }

        @Override
        public void run()
        {
            while (running) {
                SocketChannel clientChannel = null;
                try {
                    clientChannel = acceptChannel.accept();
                } catch (IOException e) {
                    if (running)
                        LOG.warn("accept failed: " + ExceptionUtils.getStackTrace(e));
                }
                if (clientChannel == null)
                    continue;

                String clientAddr = "/unknown";
                try {
                    clientAddr = clientChannel.getRemoteAddress().toString();
                } catch (IOException e) { }
                LOG.debug("connection from " + clientAddr + " is accepted");

                Session sess = new Session(clientChannel);
                try {
                    executor.execute(sess);
                } catch (RejectedExecutionException e) {
                    LOG.warn("session full: connection from " + clientAddr + " is rejected");
                    sess.reject();
                }
            }

            try {
                acceptChannel.close();
            } catch (IOException e) { }
        }
    }
}
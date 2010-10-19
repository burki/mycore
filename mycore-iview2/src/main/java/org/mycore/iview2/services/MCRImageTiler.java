package org.mycore.iview2.services;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.mycore.backend.hibernate.MCRHIBConnection;
import org.mycore.common.MCRConfiguration;
import org.mycore.common.MCRSession;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.events.MCRShutdownHandler;
import org.mycore.common.events.MCRShutdownHandler.Closeable;

/**
 * Master image tiler thread.
 * @author Thomas Scheffler (yagee)
 *
 */
public class MCRImageTiler implements Runnable, Closeable {
    private static final SessionFactory sessionFactory = MCRHIBConnection.instance().getSessionFactory();

    private static final String CONFIG_PREFIX = "MCR.Module-iview2.";

    private static MCRImageTiler instance = null;

    private static Logger LOGGER = Logger.getLogger(MCRImageTiler.class);

    private static MCRTilingQueue tq = MCRTilingQueue.getInstance();

    private ThreadPoolExecutor tilingServe;

    private volatile boolean running = true;

    private ReentrantLock runLock;

    private MCRImageTiler() {
        MCRShutdownHandler.getInstance().addCloseable(this);
        runLock = new ReentrantLock();
    }

    /**
     * @return true if image tiler thread is running.
     */
    public static boolean isRunning() {
        if (instance == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * @return an instance of this class.
     */
    public static MCRImageTiler getInstance() {
        if (instance == null) {
            instance = new MCRImageTiler();
        }
        return instance;
    }

    /**
     * Starts local tiler threads ( {@link MCRTilingAction}) and gives {@link MCRTileJob} instances to them.
     * Use property <code>MCR.Module-iview2.TilingThreads</code> to specify how many concurrent threads should be running.
     */
    public void run() {
        Thread.currentThread().setName("TileMaster");
        //get this MCRSession a speaking name
        MCRSession mcrSession = MCRSessionMgr.getCurrentSession();
        mcrSession.setCurrentUserID("SYSTEM");
        mcrSession.setCurrentUserName(Thread.currentThread().getName());
        boolean activated = MCRConfiguration.instance().getBoolean(CONFIG_PREFIX + "LocalTiler.activated", true);
        LOGGER.info("Local Tiling is "+(activated?"activated":"deactivated"));
        if (activated) {
            int tilingThreadCount = Integer.parseInt(MCRIView2Tools.getIView2Property("TilingThreads"));
            ThreadFactory slaveFactory = new ThreadFactory() {
                AtomicInteger tNum = new AtomicInteger();

                ThreadGroup tg = new ThreadGroup("MCR slave tiling thread group");

                public Thread newThread(Runnable r) {
                    Thread t = new Thread(tg, r, "TileSlave#" + tNum.incrementAndGet());
                    return t;
                }
            };
            final AtomicInteger activeThreads = new AtomicInteger();
            final LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
            tilingServe = new ThreadPoolExecutor(tilingThreadCount, tilingThreadCount, 1, TimeUnit.DAYS, workQueue, slaveFactory) {

                @Override
                protected void afterExecute(Runnable r, Throwable t) {
                    super.afterExecute(r, t);
                    activeThreads.decrementAndGet();
                }

                @Override
                protected void beforeExecute(Thread t, Runnable r) {
                    super.beforeExecute(t, r);
                    activeThreads.incrementAndGet();
                }
            };
            LOGGER.info("TilingMaster is started");
            while (running) {
                while (activeThreads.get() < tilingThreadCount) {
                    runLock.lock();
                    try {
                        if (!running)
                            break;
                        Session session = sessionFactory.getCurrentSession();
                        Transaction transaction = session.beginTransaction();
                        MCRTileJob job = null;
                        try {
                            job = tq.poll();
                            transaction.commit();
                        } catch (HibernateException e) {
                            LOGGER.error("Error while getting next tiling job.", e);
                            if (transaction != null) {
                                transaction.rollback();
                            }
                        } finally {
                            session.close();
                        }
                        if (job != null && !tilingServe.isShutdown()) {
                            LOGGER.info("Creating:" + job.getPath());
                            tilingServe.execute(new MCRTilingAction(job));
                        } else {
                            try {
                                synchronized (tq) {
                                    if (running) {
                                        LOGGER.debug("No Picture in TilingQueue going to sleep");
                                        //fixes a race conditioned deadlock situation
                                        //do not wait longer than 60 sec. for a new MCRTileJob
                                        tq.wait(60000);
                                    }
                                }
                            } catch (InterruptedException e) {
                                LOGGER.error("Image Tiling thread was interrupted.", e);
                            }
                        }
                    } finally {
                        runLock.unlock();
                    }
                } // while(tilingServe.getActiveCount() < tilingServe.getCorePoolSize())
                if (activeThreads.get()<tilingThreadCount)
                    try {
                        LOGGER.info("Waiting for a tiling job to finish");
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        LOGGER.error("Image Tiling thread was interrupted.", e);
                    }
            } // while(running)
        }
        LOGGER.info("Tiling thread finished");
        MCRSessionMgr.releaseCurrentSession();
    }

    /**
     * stops transmitting {@link MCRTileJob} to {@link MCRTilingAction} and prepares shutdown.
     */
    public void prepareClose() {
        LOGGER.info("Closing master image tiling thread");
        //signal master thread to stop now
        running = false;
        //Wake up, Neo!
        synchronized (tq) {
            tq.notifyAll();
        }
        runLock.lock();
        try {
            if (tilingServe != null) {
                tilingServe.shutdown();
                try {
                    tilingServe.awaitTermination(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    LOGGER.debug("Could not wait 60 seconds...", e);
                }
            }
        } finally {
            runLock.unlock();
        }
    }

    /**
     * Shuts down this thread and every local tiling threads spawned by {@link #run()}.
     */
    public void close() {
        if (tilingServe != null && !tilingServe.isShutdown()) {
            LOGGER.info("We are in a hurry, closing tiling service right now");
            tilingServe.shutdownNow();
            try {
                tilingServe.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.debug("Could not wait  60 seconds...", e);
            }
        }
    }
}

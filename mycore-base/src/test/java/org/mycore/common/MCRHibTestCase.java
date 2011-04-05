/**
 * 
 * $Revision$ $Date$
 *
 * This file is part of ** M y C o R e **
 * Visit our homepage at http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, normally in the file license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 *
 **/
package org.mycore.common;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.junit.After;
import org.junit.Before;
import org.mycore.backend.hibernate.MCRHIBConnection;

/**
 * @author Thomas Scheffler (yagee)
 * 
 *         Need to insert some things here
 * 
 */
public abstract class MCRHibTestCase extends MCRTestCase {

    protected SessionFactory sessionFactory;

    protected Transaction tx;

    protected Configuration configuration = getHibernateConfiguration();

    @Before
    public void setUp() throws Exception {
        // Configure logging etc.
        super.setUp();
        boolean setProperty = false;
        setProperty = setProperty("log4j.logger.org.hibernate", "WARN", false) ? true : setProperty;
        setProperty = setProperty("log4j.logger.org.hsqldb", "WARN", false) ? true : setProperty;
        if (setProperty) {
            CONFIG.configureLogging();
        }
        try {
            final MCRHIBConnection connection = MCRHIBConnection.instance();
            MCRHIBConnection.instance().buildSessionFactory(configuration);
            SchemaExport export = new SchemaExport(configuration);
            sessionFactory = connection.getSessionFactory();
            export.create(false, true);
            beginTransaction();
            sessionFactory.getCurrentSession().clear();
        } catch (RuntimeException e) {
            Logger.getLogger(MCRHibTestCase.class).error("Error while setting up hibernate JUnit test.", e);
            throw e;
        }
    }

    protected Configuration getHibernateConfiguration() {
        return MCRHIBConnection.instance().getConfiguration();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        endTransaction();
        sessionFactory.getCurrentSession().close();
        sessionFactory.close();
        sessionFactory = null;
    }

    protected void beginTransaction() {
        tx = sessionFactory.getCurrentSession().beginTransaction();
    }

    /**
     * @throws HibernateExceptionException
     * 
     */
    protected void endTransaction() throws HibernateException {
        if (tx != null && tx.isActive()) {
            try {
                tx.commit();
            } catch (HibernateException e) {
                tx.rollback();
                throw e;
            }
        }
    }

    protected void startNewTransaction() {
        endTransaction();
        beginTransaction();
        // clear from cache
        sessionFactory.getCurrentSession().clear();
    }

}

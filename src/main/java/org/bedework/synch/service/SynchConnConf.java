/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.synch.service;

import org.bedework.synch.SynchEngine;
import org.bedework.synch.db.ConnectorConfig;
import org.bedework.synch.db.SynchConfig;
import org.bedework.synch.db.SynchProperty;
import org.bedework.synch.exception.SynchException;

import org.apache.log4j.Logger;
import org.jboss.system.ServiceMBeanSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * @author douglm
 *
 */
public class SynchConnConf extends ServiceMBeanSupport implements SynchConnConfMBean {
  private transient Logger log;

  private String currentConn;

  private ConnectorConfig currentConf;

  /* True if we found an entry for the current name. False if we're building a
   * new one */
  private boolean updateAble;

  private String status;

  /* ========================================================================
   * Status
   * ======================================================================== */

  /**
   * @return status message
   */
  @Override
  public String getStatus() {
    return status;
  }

  /** web service push callback uri - null for no service
   *
   * @return String
   */
  @Override
  public String getConnectorId() {
    return currentConn;
  }

  @Override
  public List<String> getConnectorNames() {
    List<String> l = new ArrayList<String>();

    for (ConnectorConfig cc: getConf().getConnectors()) {
      l.add(cc.getName());
    }

    return l;
  }

  /* ========================================================================
   * Conf properties
   * ======================================================================== */

  /** Class name
   *
   * @param val    String
   */
  @Override
  public void setClassName(final String val) {
    currentConf.setClassName(val);
    update();
  }

  /** Class name
   *
   * @return String
   */
  @Override
  public String getClassName() {
    return currentConf.getClassName();
  }

  /** Read only?
   *
   * @param val    int seconds
   */
  @Override
  public void setReadOnly(final boolean val) {
    currentConf.setReadOnly(val);
    update();
  }

  /** Read only?
   *
   * @return int seconds
   */
  @Override
  public boolean getReadOnly() {
    return currentConf.getReadOnly();
  }

  /** Can we trust the lastmod from this connector?
   *
   * @param val    boolean
   */
  @Override
  public void setTrustLastmod(final boolean val) {
    currentConf.setTrustLastmod(val);
    update();
  }

  /** Can we trust the lastmod from this connector?
   *
   * @return boolean
   */
  @Override
  public boolean getTrustLastmod() {
    return currentConf.getTrustLastmod();
  }

  @Override
  public String getProperties() {
    String plist = "";

    Set<SynchProperty> ps = currentConf.getProperties();
    if (ps == null) {
      return plist;
    }

    for (SynchProperty p: ps) {
      plist += p.getName() + "=\"" + p.getValue() + "\"\n";
    }

    return plist;
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public void getConnector(final String name) {
    currentConn = name;
    currentConf = getConnConf();
    if (currentConf == null) {
      status = "Unknown connector " + name;
      currentConf = new ConnectorConfig();
      updateAble = false;
      currentConf.setName(name);
    } else {
      status = "Retrieved connector " + name;
      updateAble = true;
    }
  }

  @Override
  public void addProperty(final String name, final String value) {
    SynchProperty sp = new SynchProperty();

    sp.setName(name);
    sp.setValue(value);

    currentConf.addProperty(sp);
    update();
  }

  @Override
  public String setProperty(final String name, final String value) {
    try {
      if (currentConn == null) {
        return "No current connector";
      }

      currentConf.setProperty(name, value);
      update();
      return "ok";
    } catch (Throwable t) {
      return "Exception: " + t.getLocalizedMessage();
    }
  }

  @Override
  public void add() {
    if (updateAble) {
      status = "Cannot add";
      return;
    }

    SynchConfig sc = getConf();

    for (ConnectorConfig cc: sc.getConnectors()) {
      if (cc.getName().equals(currentConn)) {
        status = currentConn + " already exists";
        return;
      }
    }

    getConf().getConnectors().add(currentConf);
    updateAble = true;
    update();
  }

  @Override
  public void delete(final String name) {
    SynchConfig sc = getConf();

    for (ConnectorConfig cc: sc.getConnectors()) {
      if (cc.getName().equals(name)) {
        sc.getConnectors().remove(cc);
        status = name + " deleted";
        updateAble = true;
        update();
        updateAble = false;
        currentConf = new ConnectorConfig();
        currentConn = null;
        return;
      }
    }

    status = currentConn + " does not exist";
  }

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  @Override
  protected ObjectName getObjectName(final MBeanServer server,
                                     final ObjectName name)
      throws MalformedObjectNameException {
    if (name == null) {
      return OBJECT_NAME;
    }

    return name;
   }

  @Override
  public void startService() throws Exception {
    currentConf = new ConnectorConfig();
  }

  @Override
  public void stopService() throws Exception {
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private SynchConfig getConf() {
    try {
      return SynchEngine.getSyncher().getConfig();
    } catch (SynchException se) {
      error(se);
      throw new RuntimeException(se);
    }
  }

  private ConnectorConfig getConnConf() {
    try {
      SynchConfig sc = SynchEngine.getSyncher().getConfig();

      if (sc == null) {
        return null;
      }

      for (ConnectorConfig cc: sc.getConnectors()) {
        if (cc.getName().equals(currentConn)) {
          return cc;
        }
      }

      return null;
    } catch (SynchException se) {
      error(se);
      throw new RuntimeException(se);
    }
  }

  private void update() {
    if (!updateAble) {
      return;
    }

    try {
      SynchEngine.getSyncher().updateConfig();
    } catch (SynchException se) {
      error(se);
      throw new RuntimeException(se);
    }
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}

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
package org.bedework.synch.cnctrs.bedework;

import org.bedework.synch.Notification;
import org.bedework.synch.PropertiesInfo;
import org.bedework.synch.SynchDefs.SynchKind;
import org.bedework.synch.SynchEngine;
import org.bedework.synch.cnctrs.AbstractConnector;
import org.bedework.synch.cnctrs.ConnectorInstanceMap;
import org.bedework.synch.db.ConnectorConfig;
import org.bedework.synch.db.Subscription;
import org.bedework.synch.db.SubscriptionInfo;
import org.bedework.synch.exception.SynchException;
import org.bedework.synch.wsmessages.CalProcessingType;
import org.bedework.synch.wsmessages.KeepAliveNotificationType;
import org.bedework.synch.wsmessages.KeepAliveResponseType;
import org.bedework.synch.wsmessages.StartServiceNotificationType;
import org.bedework.synch.wsmessages.StartServiceResponseType;
import org.bedework.synch.wsmessages.SynchEndType;
import org.bedework.synch.wsmessages.SynchIdTokenType;
import org.bedework.synch.wsmessages.SynchRemoteServicePortType;

import org.oasis_open.docs.ws_calendar.ns.soap.GetPropertiesResponseType;
import org.oasis_open.docs.ws_calendar.ns.soap.GetPropertiesType;
import org.oasis_open.docs.ws_calendar.ns.soap.StatusType;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** The synch processor connector for connections to bedework.
 *
 * @author Mike Douglass
 */
public class BedeworkConnector
      extends AbstractConnector<BedeworkConnector,
                                BedeworkConnectorInstance,
                                Notification> {
  private static PropertiesInfo bwPropInfo = new PropertiesInfo();

  static {
    bwPropInfo.requiredUri(null);

    bwPropInfo.requiredPrincipal(null);

    bwPropInfo.optionCalProcessing(SubscriptionInfo.propnameAlarmProcessing,
                                   "",
                                   CalProcessingType.REMOVE.toString());

    bwPropInfo.optionCalProcessing(SubscriptionInfo.propnameSchedulingProcessing,
                                   "",
                                   CalProcessingType.REMOVE.toString());
  }

  /* If non-null this is the token we currently have for bedework */
  private String remoteToken;

  private GetPropertiesResponseType sysInfo;

  private ConnectorInstanceMap<BedeworkConnectorInstance> cinstMap =
      new ConnectorInstanceMap<BedeworkConnectorInstance>();

  /**
   */
  public BedeworkConnector() {
    super(bwPropInfo);
  }

  /** This process will send keep-alive notifications to the remote system.
   * During startup the first notification is sent so this process starts with
   * a wait
   *
   */
  private class PingThread extends Thread {
    boolean showedTrace;

    BedeworkConnector conn;

    /**
     * @param name - for the thread
     * @param conn
     */
    public PingThread(final String name,
                      final BedeworkConnector conn) {
      super(name);
      this.conn = conn;
    }

    @Override
    public void run() {
      while (!conn.isStopped()) {
        if (debug) {
          trace("About to call service - token = " + remoteToken);
        }
        /* First see if we need to reinitialize or ping */

        try {
          if (remoteToken == null) {
            initConnection();
            if (remoteToken != null) {
              running = true;
            }
          } else {
            ping();
          }
        } catch (Throwable t) {
          if (!showedTrace) {
            error(t);
            showedTrace = true;
          } else {
            error(t.getMessage());
          }
        }

        // Wait a bit before trying again

        if (debug) {
          trace("About to pause - token = " + remoteToken);
        }

        try {
          Object o = new Object();
          long waitTime;

          if (remoteToken == null) {
            waitTime = ((BedeworkConnectorConfig)config).getRetryInterval() * 1000;
          } else {
            waitTime = ((BedeworkConnectorConfig)config).getKeepAliveInterval() * 1000;
          }

          synchronized (o) {
            o.wait(waitTime);
          }
        } catch (InterruptedException ie) {
          break;
        } catch (Throwable t) {
          error(t.getMessage());
        }
      }
    }
  }

  private PingThread pinger;

  @Override
  public void start(final String connectorId,
                    final ConnectorConfig conf,
                    final String callbackUri,
                    final SynchEngine syncher) throws SynchException {
    super.start(connectorId, conf, callbackUri, syncher);

    config = new BedeworkConnectorConfig(conf);

    if (pinger == null) {
      pinger = new PingThread(connectorId, this);
      pinger.start();
    }

    stopped = false;
    running = true;
  }

  @Override
  public boolean isManager() {
    return false;
  }

  @Override
  public SynchKind getKind() {
    return SynchKind.notify;
  }

  @Override
  public boolean isReadOnly() {
    return config.getReadOnly();
  }

  @Override
  public boolean getTrustLastmod() {
    return config.getTrustLastmod();
  }

  @Override
  public BedeworkConnectorInstance getConnectorInstance(final Subscription sub,
                                                        final SynchEndType end) throws SynchException {
    if (!running) {
      return null;
    }

    BedeworkConnectorInstance inst = cinstMap.find(sub, end);

    if (inst != null) {
      return inst;
    }

    BedeworkSubscriptionInfo info;

    if (end == SynchEndType.A) {
      info = new BedeworkSubscriptionInfo(sub.getEndAConnectorInfo());
    } else {
      info = new BedeworkSubscriptionInfo(sub.getEndBConnectorInfo());
    }

    inst = new BedeworkConnectorInstance((BedeworkConnectorConfig)config,
                                         this, sub, end, info);
    cinstMap.add(sub, end, inst);

    return inst;
  }

  class BedeworkNotificationBatch extends NotificationBatch<Notification> {
  }

  @Override
  public BedeworkNotificationBatch handleCallback(final HttpServletRequest req,
                                     final HttpServletResponse resp,
                                     final List<String> resourceUri) throws SynchException {
    return null;
  }

  @Override
  public void respondCallback(final HttpServletResponse resp,
                              final NotificationBatch<Notification> notifications)
                                                    throws SynchException {
  }

  @Override
  public void stop() throws SynchException {
    stopped = true;
    if (pinger != null) {
      pinger.interrupt();
    }

    pinger = null;
  }

  /* ====================================================================
   *                         Package methods
   * ==================================================================== */

  SynchRemoteServicePortType getPort() throws SynchException {
    return getPort(((BedeworkConnectorConfig)config).getBwWSDLURI());
  }

  SynchIdTokenType getIdToken(final String principal) throws SynchException {
    if (remoteToken == null) {
      throw new SynchException(SynchException.connectorNotStarted);
    }

    SynchIdTokenType idToken = new SynchIdTokenType();

    idToken.setPrincipalHref(principal);
    idToken.setSubscribeUrl(callbackUri);
    idToken.setSynchToken(remoteToken);

    return idToken;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /**
   * @throws SynchException
   */
  public void ping() throws SynchException {
    KeepAliveNotificationType kan = new KeepAliveNotificationType();

    kan.setSubscribeUrl(callbackUri);
    kan.setToken(remoteToken);

    KeepAliveResponseType kar = getPort().pingService(kan);

    if (kar.getStatus() != StatusType.OK) {
      warn("Received status " + kar.getStatus() + " for ping");
      remoteToken = null; // Force reinit after wait

      running = false;
    }
  }

  private void initConnection() throws SynchException {
    StartServiceNotificationType ssn = new StartServiceNotificationType();

    ssn.setConnectorId(getConnectorId());
    ssn.setSubscribeUrl(callbackUri);

    StartServiceResponseType ssr = getPort().startService(ssn);
    
    if (ssr == null) {
      warn("Received null response to start notification");
      return;
    }

    if (ssr.getStatus() != StatusType.OK) {
      warn("Received status " + ssr.getStatus() + " to start notification");
      return;
    }

    remoteToken = ssr.getToken();

    if (sysInfo == null) {
      // Try to get info
      GetPropertiesType gp = new GetPropertiesType();

      gp.setHref("/");

      sysInfo = getPort().getProperties(getIdToken(null),
                                        gp);
    }
  }
}

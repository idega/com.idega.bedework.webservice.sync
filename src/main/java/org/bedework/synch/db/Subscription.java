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
package org.bedework.synch.db;

import org.bedework.synch.BaseSubscriptionInfo;
import org.bedework.synch.SynchDefs.SynchKind;
import org.bedework.synch.cnctrs.Connector;
import org.bedework.synch.cnctrs.ConnectorInstance;
import org.bedework.synch.exception.SynchException;
import org.bedework.synch.wsmessages.SynchDirectionType;
import org.bedework.synch.wsmessages.SynchMasterType;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.DtStamp;

import java.util.Date;
import java.util.UUID;

/**
 * Represents a subscription for the synch engine.
 * 
 * <p>
 * A subscription has 2 connections, one for each end of the subscription. We
 * will refer to these as endA and endB.
 * 
 * <p>
 * Each connection has a kind which is a name used to retrieve a connector from
 * the synch engine. The retrieved connector implements the Connector interface.
 * This connector object can then be used to retrieve a ConnectionInst
 * implementation which uses information stored in a serializable object to
 * obtain connection specific properties such as id and password.
 * 
 * <p>
 * These properties are obtained by presenting the user with a list of required
 * properties and then encrypting and storing the response. The serialized
 * result is stored as a field in the subscription.
 * 
 * <p>
 * Connections are either polling or notify. Polling means that the host will be
 * polled to see if anything has changed. Notify means that the subscription
 * will be activated when the system is notified of a change.
 * 
 * <p>
 * Connections are also resynch only - that is the far end does not support
 * fetching of individual items but must be completely resynched each time, or
 * the support the full synch feature set.
 * 
 * <p>
 * Resynch connections support relatively simple protocols or file synch.
 * 
 * <p>
 * The full feature connections are used for bedework, Exchange etc.
 * 
 * <h1>Skip Lists</h1>
 * A skip list allows the diffing process to skip properties that are not to be
 * considered, for example lastmod. We create a skip list from 3 lists;
 * <ul>
 * <li>one for each end of the subscription. This marks properties used
 * exclusively by that end, for example x-properties.</li>
 * <li>One for the middle which might skip properties we want to ignore such as
 * alarms</li>
 * </ul>
 * 
 * <p>
 * An empty list means exactly that, no skip properties. A null list means the
 * default diff skip list - probably more useful.
 * 
 * @author Mike Douglass
 */
@SuppressWarnings("rawtypes")
public class Subscription extends DbItem<Subscription> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4874830853511931663L;

	private String owner;

	private String subscriptionId;

	private String lastRefresh;

	private int errorCt;

	private boolean missingTarget;

	private SubscriptionConnectorInfo endAConnectorInfo;

	private SubscriptionConnectorInfo endBConnectorInfo;

	private SubscriptionInfo info;

	private SynchDirectionType direction;

	private SynchMasterType master;

	/* Following not persisted */

	/* Process outstanding after this */
	private Subscription outstandingSubscription;

	private boolean deleted;

	private Connector endAConn;

	private Connector endBConn;

	private ConnectorInstance endAConnInst;

	private ConnectorInstance endBConnInst;

	/**
	 * null constructor for hibernate
	 * 
	 */
	public Subscription() {
	}

	/**
	 * Constructor to create a new subscription.
	 * 
	 * @param subscriptionId
	 *            - null means generate one
	 */
	public Subscription(final String subscriptionId) {
		if (subscriptionId == null) {
			this.subscriptionId = UUID.randomUUID().toString();
		} else {
			this.subscriptionId = subscriptionId;
		}
	}

	/**
	 * Our generated subscriptionId.
	 * 
	 * @param val
	 *            String
	 */
	public void setSubscriptionId(final String val) {
		subscriptionId = val;
	}

	/**
	 * Our generated subscriptionId.
	 * 
	 * @return String
	 */
	public String getSubscriptionId() {
		return subscriptionId;
	}

	/**
	 * A UTC dtstamp value
	 * 
	 * @param val
	 */
	public void setLastRefresh(final String val) {
		lastRefresh = val;
	}

	/**
	 * @return String lastRefresh
	 */
	public String getLastRefresh() {
		return lastRefresh;
	}

	/**
	 * int consecutive errors
	 * 
	 * @param val
	 */
	public void setErrorCt(final int val) {
		errorCt = val;
	}

	/**
	 * @return int consecutive errors
	 */
	public int getErrorCt() {
		return errorCt;
	}

	/**
	 * True if either target is missing
	 * 
	 * @param val
	 */
	public void setMissingTarget(final boolean val) {
		missingTarget = val;
	}

	/**
	 * @return True if either target is missing
	 */
	public boolean getMissingTarget() {
		return missingTarget;
	}

	/**
	 * The owner. This is the (verified) account that set up the subscription.
	 * It is either the authenticated user or provided by a proxy that has
	 * verified the account. The owner is only needed for subcribing,
	 * unsubscribing and display of and updates to the subscription itself.
	 * 
	 * <p>
	 * Interactions with the end points use information contained within the
	 * subscription.
	 * 
	 * @param val
	 *            String
	 */
	public void setOwner(final String val) {
		owner = val;
	}

	/**
	 * Owner
	 * 
	 * @return String
	 */
	public String getOwner() {
		return owner;
	}

	/**
	 * Info for the endA connector.
	 * 
	 * @param val
	 *            SubscriptionConnectorInfo
	 */
	public void setEndAConnectorInfo(final SubscriptionConnectorInfo val) {
		endAConnectorInfo = val;
	}

	/**
	 * @return SubscriptionConnectorInfo
	 */
	public SubscriptionConnectorInfo getEndAConnectorInfo() {
		return endAConnectorInfo;
	}

	/**
	 * Info for the endB connector.
	 * 
	 * @param val
	 *            SubscriptionConnectorInfo
	 */
	public void setEndBConnectorInfo(final SubscriptionConnectorInfo val) {
		endBConnectorInfo = val;
	}

	/**
	 * @return SubscriptionConnectorInfo
	 */
	public SubscriptionConnectorInfo getEndBConnectorInfo() {
		return endBConnectorInfo;
	}

	/**
	 * Info for the subscription.
	 * 
	 * @param val
	 *            SubscriptionInfo
	 */
	public void setInfo(final SubscriptionInfo val) {
		info = val;
	}

	/**
	 * @return SubscriptionInfo
	 */
	public SubscriptionInfo getInfo() {
		return info;
	}

	/**
	 * Which way?
	 * 
	 * @param val
	 */
	public void setDirection(final SynchDirectionType val) {
		direction = val;
	}

	/**
	 * Which way?
	 * 
	 * @return direction
	 */
	public SynchDirectionType getDirection() {
		return direction;
	}

	/**
	 * Which end is master?
	 * 
	 * @param val
	 */
	public void setMaster(final SynchMasterType val) {
		master = val;
	}

	/**
	 * @return who's master
	 */
	public SynchMasterType getMaster() {
		return master;
	}

	/**
	 * An outstanding request that requires an unsubscribe to complete first
	 * 
	 * @param val
	 *            Subscription
	 */
	public void setOutstandingSubscription(final Subscription val) {
		outstandingSubscription = val;
	}

	/**
	 * An outstanding request that requires an unsubscribe to complete first
	 * 
	 * @return Subscription
	 */
	public Subscription getOutstandingSubscription() {
		return outstandingSubscription;
	}

	/**
	 * True if subscription deleted
	 * 
	 * @param val
	 */
	public void setDeleted(final boolean val) {
		deleted = val;
	}

	/**
	 * @return True if deleted
	 */
	public boolean getDeleted() {
		return deleted;
	}

	/**
	 * @param val
	 *            a connection
	 */
	public void setEndAConn(final Connector val) {
		endAConn = val;
	}

	/**
	 * @return a connection or null
	 */
	public Connector getEndAConn() {
		return endAConn;
	}

	/**
	 * @param val
	 *            a connection
	 */
	public void setEndBConn(final Connector val) {
		endBConn = val;
	}

	/**
	 * @return a connection or null
	 */
	public Connector getEndBConn() {
		return endBConn;
	}

	/**
	 * @param val
	 *            a connection instance
	 */
	public void setEndAConnInst(final ConnectorInstance val) {
		endAConnInst = val;
	}

	/**
	 * @return a connection instance or null
	 */
	public ConnectorInstance getEndAConnInst() {
		return endAConnInst;
	}

	/**
	 * @param val
	 *            a connection instance
	 */
	public void setEndBConnInst(final ConnectorInstance val) {
		endBConnInst = val;
	}

	/**
	 * @return a connection instance or null
	 */
	public ConnectorInstance getEndBConnInst() {
		return endBConnInst;
	}

	/**
	 * @return true if any connector info changed
	 */
	public boolean changed() {
		return getEndAConnectorInfo().getChanged()
				|| getEndBConnectorInfo().getChanged();
	}

	/**
	 * reset the changed flag.
	 */
	public synchronized void resetChanged() {
		if (!changed()) {
			return;
		}

		getEndAConnectorInfo().resetChanged();
		getEndBConnectorInfo().resetChanged();
	}

	/*
	 * ====================================================================
	 * Convenience methods
	 * ====================================================================
	 */

	/**
	 * @return true if this has to be put on a poll queue
	 */
	public boolean polling() {
		if (getDirection() == SynchDirectionType.A_TO_B) {
			return getEndAConn().getKind() == SynchKind.poll;
		}

		if (getDirection() == SynchDirectionType.B_TO_A) {
			return getEndBConn().getKind() == SynchKind.poll;
		}

		return (getEndAConn().getKind() == SynchKind.poll)
				|| (getEndBConn().getKind() == SynchKind.poll);
	}

	/**
	 * @return the delay in millisecs.
	 * @throws SynchException
	 */
	public long refreshDelay() throws SynchException {
		String delay = "31536000000"; // About a year

		if (getDirection() == SynchDirectionType.A_TO_B) {
			delay = new BaseSubscriptionInfo(getEndAConnectorInfo())
					.getRefreshDelay();
		} else {
			delay = new BaseSubscriptionInfo(getEndBConnectorInfo())
					.getRefreshDelay();
		}

		return Long.valueOf(delay);
	}

	/**
	 * Set the lastRefresh from the current time
	 * 
	 */
	public void updateLastRefresh() {
		setLastRefresh(new DtStamp(new DateTime(true)).getValue());
	}

	/**
	 * Get a next refresh date based on the last refresh value
	 * 
	 * @return date value incremented by delay.
	 * @throws SynchException
	 */
	public Date nextRefresh() throws SynchException {
		if (getLastRefresh() == null) {
			return new Date();
		}

		try {
			Date dt = new DtStamp(getLastRefresh()).getDate();

			return new Date(dt.getTime()
					+ ((getErrorCt() + 1) * refreshDelay()));
		} catch (Throwable t) {
			throw new SynchException(t);
		}
	}

	/**
	 * Add our stuff to the StringBuilder
	 * 
	 * @param sb
	 *            StringBuilder for result
	 */
	protected void toStringSegment(final StringBuilder sb, final String indent) {
		sb.append("id = ");
		sb.append(getId());
		sb.append(", seq = ");
		sb.append(getSeq());

		sb.append(",\n");
		sb.append(indent);
		sb.append("subscriptionId = ");
		sb.append(getSubscriptionId());

		sb.append(", lastRefresh = ");
		sb.append(getLastRefresh());

		sb.append(",\n");
		sb.append(indent);
		sb.append("errorCt = ");
		sb.append(getErrorCt());
		sb.append(", missingTarget = ");
		sb.append(getMissingTarget());

		sb.append(",\n");
		sb.append(indent);
		sb.append("endAConnectorInfo = ");
		sb.append(getEndAConnectorInfo());

		sb.append(",\n");
		sb.append(indent);
		sb.append("endBConnectorInfo = ");
		sb.append(getEndBConnectorInfo());

		sb.append(",\n");
		sb.append(indent);
		sb.append("info = ");
		sb.append(getInfo());

		sb.append(",\n");
		sb.append(indent);
		sb.append("direction = ");
		sb.append(getDirection());

		sb.append(", master = ");
		sb.append(getMaster());
	}

	/*
	 * ====================================================================
	 * Object methods The following are required for a db object.
	 * ====================================================================
	 */

	@Override
	public int hashCode() {
		return getSubscriptionId().hashCode();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(final Subscription that) {
		if (this == that) {
			return 0;
		}

		return getSubscriptionId().compareTo(that.getSubscriptionId());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getSimpleName())
				.append("{");

		super.toStringSegment(sb);

		toStringSegment(sb, "  ");

		if (getOutstandingSubscription() != null) {
			sb.append(", \n  OustandingSubscription{");

			toStringSegment(sb, "    ");
			sb.append("  }");
		}

		sb.append("}");
		return sb.toString();
	}
}

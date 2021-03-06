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
package org.bedework.util.http.service;

import org.bedework.util.jmx.ConfBaseMBean;
import org.bedework.util.jmx.MBeanInfo;

import org.apache.http.pool.PoolStats;

/** Display usage and set limits for outbound http
 *
 * @author douglm
 */
public interface HttpOutMBean extends ConfBaseMBean, HttpConfig {
  /* ========================================================================
   * Status
   * ======================================================================== */

  /**
   * @return current stats
   */
  PoolStats getConnStats();

  /* *
   * @param val maximum allowable overall
   * /
  void setDefaultMaxPerHost(final int val);

  /* *
   * @return max
   * /
  int getDefaultMaxPerHost();

  /* *
   * @return ct of created pool entries
   * /
  long getCreated();

  /* *
   * @return ct of deleted pool entries
   * /
  long getDeleted();

  /* *
   *
   * @return list of limits
   * /
  List<String> getLimits();

  /* ========================================================================
   * Operations
   * ======================================================================== */

  /* *
   * @param host
   * @param max
   * /
  void setHostLimit(final String host, final int max);

  /* *
   * @param host
   * @return max
   * /
  int getHostLimit(final String host);

  /* * Add a property
   *
   * @param host
   * @param limit
   * /
  public void addHost(String host, int limit);

  /* * Delete the named host from the limits table.
   *
   * @param host the host
   * /
  public void deleteHost(String host);
  */

  /** (Re)load the configuration
   *
   * @return status
   */
  @MBeanInfo("(Re)load the configuration")
  String loadConfig();
}

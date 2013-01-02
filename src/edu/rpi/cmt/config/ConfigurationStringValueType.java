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
package edu.rpi.cmt.config;

import edu.rpi.sss.util.Util;
import edu.rpi.sss.util.xml.XmlEmit;
import edu.rpi.sss.util.xml.XmlUtil;

import org.w3c.dom.Node;

import javax.xml.namespace.QName;

/** Class to represent a string value in a configuration.
 *
 * @author Mike Douglass douglm
 */
public class ConfigurationStringValueType
    extends ConfigurationValueType<String, ConfigurationStringValueType> {
  private QName elementName;
  private String value;

  /**
   * @param elementName
   */
  public ConfigurationStringValueType(final QName elementName) {
    this.elementName = elementName;
  }

  @Override
  public QName getElementName() {
    return elementName;
  }

  @Override
  public String getElementType() {
    return ConfigurationTypes.typeString;
  }

  @Override
  public void setValue(final String val) throws ConfigException {
    value = val;
  }

  @Override
  public String getValue() throws ConfigException {
    return value;
  }

  @Override
  public void valueFromXml(final Node nd) throws ConfigException {
    try {
      String s = XmlUtil.getOneNodeVal(nd);

      if (s != null) {
        setValue(s);
      }
    } catch (Throwable t) {
      throw new ConfigException(t);
    }
  }

  @Override
  public void valueToXml(final XmlEmit xml) throws ConfigException {
    try {
      xml.value(getValue());
    } catch (Throwable t) {
      throw new ConfigException(t);
    }
  }

  @Override
  public int compareTo(final ConfigurationElementType that) {
    try {
      if (that instanceof ConfigurationStringValueType) {
        return Util.cmpObjval(getValue(),
                              ((ConfigurationStringValueType)that).getValue());
      }

      return getClass().getName().compareTo(that.getClass().getName());
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}

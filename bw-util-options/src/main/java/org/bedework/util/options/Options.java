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
package org.bedework.util.options;

import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.XmlUtil;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Access properties in an xml format.
 *
 * @author Mike Douglass    douglm @ bedework.edu
 *
 */
public class Options implements OptionsI {
  /** Global properties have this prefix.
   */
  private String globalPrefix;

  private String appPrefix;

//  private String optionsFile;

  private QName outerTag;

  private static volatile Pattern splitPathPattern = Pattern.compile("\\.");

  /* The default is to use the static values for the system as a whole.
   */
  private boolean useSystemwideValues = true;

  /* The internal representation */
  private OptionElement optionsRoot;

  private OptionElement localOptionsRoot;

  @Override
  public void init(final String globalPrefix,
                   final String appPrefix,
                   final String optionsFile,
                   final String outerTagName) throws OptionsException {
//    this.optionsFile = optionsFile;
    init(globalPrefix,
         appPrefix,
         getStream(optionsFile),
         outerTagName);
  }

  @Override
  public void init(final String globalPrefix,
                   final String appPrefix,
                   final InputStream optionsFileStream,
                   final String outerTagName) throws OptionsException {
    try {
      this.globalPrefix = globalPrefix;
      this.appPrefix = appPrefix;
    //  this.optionsFile = optionsFile;

      outerTag = new QName(null, outerTagName);

      /* We now parse the file into a simple options structure
       */
      optionsRoot = parseOptions(optionsFileStream);
    } finally {
      if (optionsFileStream != null) {
        try {
          optionsFileStream.close();
        } catch (Throwable t1) {}
      }
    }
  }

  private InputStream getStream(final String path) throws OptionsException {
    /* get an input stream for the options file */

    InputStream is = null;

    try {
      try {
        // The jboss?? way - should work for others as well.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        is = cl.getResourceAsStream(path);
      } catch (Throwable clt) {}

      if (is == null) {
        // Try another way
        is = Options.class.getResourceAsStream(path);
      }

      if (is == null) {
        throw new OptionsException("Unable to load options file" +
                                  path);
      }

      return is;
    } catch (OptionsException cee) {
      throw cee;
    } catch (Throwable t) {
      Logger.getLogger(Options.class).error("getEnv error", t);
      throw new OptionsException(t.getMessage());
    }
  }

  @Override
  public OptionElement getOptions() {
    if (!useSystemwideValues) {
      // No screwing round with them
      return null;
    }

    return localOptionsRoot;
  }

  /** Parse the input stream and return the internal representation.
   *
   * @param is         InputStream
   * @return OptionElement root of parsed options.
   * @exception OptionsException Some error occurred.
   */
  public OptionElement parseOptions(final InputStream is) throws OptionsException{
    Reader rdr = null;

    try {
      rdr = new InputStreamReader(is);

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);

      DocumentBuilder builder = factory.newDocumentBuilder();

      Document doc = builder.parse(new InputSource(rdr));

      /* We expect a root element named as specified */

      Element root = doc.getDocumentElement();

      if (!root.getNodeName().equals(outerTag.getLocalPart())) {
        throw new OptionsException("org.bedework.bad.options");
      }

      OptionElement oel = new OptionElement();
      oel.name = "root";

      doChildren(oel, root, new Stack<Object>());

      return oel;
    } catch (OptionsException ce) {
      throw ce;
    } catch (Throwable t) {
      throw new OptionsException(t);
    } finally {
      if (rdr != null) {
        try {
          rdr.close();
        } catch (Throwable t) {}
      }
    }
  }

  /** Emit the options as xml.
   *
   * @param root
   * @param str
   * @throws OptionsException
   */
  public void toXml(final OptionElement root, final OutputStream str) throws OptionsException {
    Writer wtr = null;

    try {
      XmlEmit xml = new XmlEmit(true);

      wtr = new OutputStreamWriter(str);
      xml.startEmit(wtr);

      xml.openTag(outerTag);

      for (OptionElement oe: root.getChildren()) {
        childToXml(oe, xml);
      }

      xml.closeTag(outerTag);
    } catch (OptionsException ce) {
      throw ce;
    } catch (Throwable t) {
      throw new OptionsException(t);
    } finally {
      if (wtr != null) {
        try {
          wtr.close();
        } catch (Throwable t) {}
      }
    }
  }

  private static void childToXml(final OptionElement subRoot,
                                 final XmlEmit xml) throws OptionsException {
    try {
      Object val = subRoot.val;
      QName tag = new QName(null, subRoot.name);

      if ((val instanceof String) ||
          (val instanceof Integer) ||
          (val instanceof Long) ||
          (val instanceof Boolean)) {
        xml.property(tag, String.valueOf(val));

        return;
      }

      /* A bedework config class. Get the class and emit the values.
       */

      Method meth = findMethod(val, "toOptionsXml");

      if (meth != null) {
        Class[] parClasses = meth.getParameterTypes();
        if (parClasses.length != 1) {
          error("Invalid toOptionsXml method");
          throw new OptionsException("org.bedework.calenv.invalid.toxml");
        }

        Object[] pars = new Object[]{xml};

        meth.invoke(val, pars);

        return;
      }

      xml.openTag(tag, "class", val.getClass().getName());

      /* Find all the getters */
      Collection<Method> getters = findGetters(val);

      for (Method m: getters) {
        methodToXml(m, val, xml);
      }

      xml.closeTag(tag);
    } catch (OptionsException ce) {
      throw ce;
    } catch (Throwable t) {
      throw new OptionsException(t);
    }
  }

  private static void methodToXml(final Method meth,
                                  final Object val,
                                  final XmlEmit xml) throws OptionsException {
    try {
      String methodName = meth.getName();

      Class[] parClasses = meth.getParameterTypes();
      if (parClasses.length != 0) {
        error("Invalid getter method " + methodName);
        throw new OptionsException("org.bedework.calenv.invalid.getter");
      }

      String fieldName = methodName.substring(3, 3).toLowerCase() +
      methodName.substring(4);
      Object methVal = meth.invoke(val, (Object[])null);

      xml.property(new QName(null, fieldName), String.valueOf(methVal));
    } catch (OptionsException ce) {
      throw ce;
    } catch (Throwable t) {
      throw new OptionsException(t);
    }
  }

  /** Return current app prefix
   *
   * @return String app prefix
   */
  @Override
  public String getAppPrefix() {
    return appPrefix;
  }

  /** Get required property, throw exception if absent
   *
   * @param name String property name
   * @return Object value
   * @throws OptionsException
   */
  @Override
  public Object getProperty(final String name) throws OptionsException {
    Object val = getOptProperty(name);

    if (val == null) {
      throw new OptionsException("Missing property " + name);
    }

    return val;
  }

  /** Get optional property.
   *
   * @param name String property name
   * @return Object value
   * @throws OptionsException
   */
  @Override
  public Object getOptProperty(final String name) throws OptionsException {
    if (useSystemwideValues) {
      return findValue(optionsRoot, makePathElements(name), -1);
    }

    return findValue(localOptionsRoot, makePathElements(name), -1);
  }

  /** Return the String value of the named property.
   *
   * @param name String property name
   * @return String value of property
   * @throws OptionsException
   */
  @Override
  public String getStringProperty(final String name) throws OptionsException {
    Object val = getProperty(name);

    if (!(val instanceof String)) {
      throw new OptionsException("org.bedework.calenv.bad.option.value");
    }

    return (String)val;
  }

  /** Get optional property.
   *
   * @param name String property name
   * @return String value
   * @throws OptionsException
   */
  @Override
  public String getOptStringProperty(final String name) throws OptionsException {
    Object val = getOptProperty(name);

    if (val == null) {
      return null;
    }

    if (!(val instanceof String)) {
      throw new OptionsException("org.bedework.calenv.bad.option.value");
    }

    return (String)val;
  }

  /** Return the value of the named property.
   *
   * @param name String property name
   * @return boolean value of property
   * @throws OptionsException
   */
  @Override
  public boolean getBoolProperty(final String name) throws OptionsException {
    String val = getStringProperty(name);

    val = val.toLowerCase();

    return "true".equals(val) || "yes".equals(val);
  }

  /** Return the value of the named property.
   *
   * @param name String property name
   * @return int value of property
   * @throws OptionsException
   */
  @Override
  public int getIntProperty(final String name) throws OptionsException {
    String val = getStringProperty(name);

    try {
      return Integer.valueOf(val).intValue();
    } catch (Throwable t) {
      throw new OptionsException("org.bedework.calenv.bad.option.value");
    }
  }

  /* ====================================================================
   *                 Methods returning global properties.
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.env.CalOptionsI#getGlobalProperty(java.lang.String)
   */
  @Override
  public Object getGlobalProperty(final String name) throws OptionsException {
    return getProperty(globalPrefix + name);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.env.CalOptionsI#getGlobalStringProperty(java.lang.String)
   */
  @Override
  public String getGlobalStringProperty(final String name) throws OptionsException {
    return getStringProperty(globalPrefix + name);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.env.CalOptionsI#getGlobalBoolProperty(java.lang.String)
   */
  @Override
  public boolean getGlobalBoolProperty(final String name) throws OptionsException {
    return getBoolProperty(globalPrefix + name);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.env.CalOptionsI#getGlobalIntProperty(java.lang.String)
   */
  @Override
  public int getGlobalIntProperty(final String name) throws OptionsException {
    return getIntProperty(globalPrefix + name);
  }

  /* ====================================================================
   *                 Methods returning application properties.
   * ==================================================================== */

  /** Get required app property, throw exception if absent
   *
   * @param name String property name
   * @return Object value
   * @throws OptionsException
   */
  @Override
  public Object getAppProperty(final String name) throws OptionsException {
    return getProperty(appPrefix + name);
  }

  /** Get required app property, throw exception if absent
   *
   * @param name String property name
   * @return String value
   * @throws OptionsException
   */
  @Override
  public String getAppStringProperty(final String name) throws OptionsException {
    return getStringProperty(appPrefix + name);
  }

  /** Get optional app property.
   *
   * @param name String property name
   * @return Object value or null
   * @throws OptionsException
   */
  @Override
  public Object getAppOptProperty(final String name) throws OptionsException {
    return getOptProperty(appPrefix + name);
  }

  /** Get optional app property.
   *
   * @param name String property name
   * @return String value or null
   * @throws OptionsException
   */
  @Override
  public String getAppOptStringProperty(final String name) throws OptionsException {
    return getOptStringProperty(appPrefix + name);
  }

  /** Return the value of the named property or false if absent.
   *
   * @param name String unprefixed name
   * @return boolean value of global property
   * @throws OptionsException
   */
  @Override
  public boolean getAppBoolProperty(final String name) throws OptionsException {
    return getBoolProperty(appPrefix + name);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.env.CalOptionsI#getAppIntProperty(java.lang.String)
   */
  @Override
  public int getAppIntProperty(final String name) throws OptionsException {
    return getIntProperty(appPrefix + name);
  }

  /* ====================================================================
   *                 Methods setting properties.
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.env.CalOptionsI#setValue(java.lang.String, java.lang.String, java.lang.Object)
   */
  @Override
  public void setValue(final String optionObjectName,
                       final String optionName,
                       final Object val) throws OptionsException {
    if (!useSystemwideValues) {
      // No screwing round with them
      throw new OptionsException("cannot.set.values");
    }

    try {
      Object opts = getProperty(optionObjectName);

      Method meth = findSetter(opts, optionName);

      Object[] pars = new Object[]{val};

      meth.invoke(opts, pars);
    } catch (Throwable t) {
      throw new OptionsException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.env.CalOptionsI#getValue(java.lang.String, java.lang.String)
   */
  @Override
  public Object getValue(final String optionObjectName,
                         final String optionName) throws OptionsException {
    if (useSystemwideValues) {
      // No screwing round with them
      throw new OptionsException("cannot.set.values");
    }

    try {
      /* Find the bean */
      Object opts = getProperty(optionObjectName);

      /* ... and its getter */
      Method meth = findGetter(opts, optionName);

      /* ... now the value */
      return meth.invoke(opts, (Object[])null);
    } catch (OptionsException ce) {
      throw ce;
    } catch (Throwable t) {
      throw new OptionsException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.util.options.OptionsI#getNames(java.lang.String)
   */
  @Override
  public Collection<String> getNames(final String name) throws OptionsException {
    if (useSystemwideValues) {
      return getNames(optionsRoot, makePathElements(name), -1);
    }

    return getNames(localOptionsRoot, makePathElements(name), -1);
  }

  /** Match for values.
   *
   * @param name String property name prefix
   * @return Collection
   * @throws OptionsException
   */
  public Collection match(final String name) throws OptionsException {
    if (useSystemwideValues) {
      return match(optionsRoot, makePathElements(name), -1);
    }

    return match(localOptionsRoot, makePathElements(name), -1);
  }

  /* ====================================================================
   *                 private methods
   * ==================================================================== */

  /** Given a path e.g. org.bedework.global.module return names of children
   *
   * @param subroot
   * @param pathElements
   * @param pos
   * @return Collection<String>
   */
  private static Collection<String> getNames(final OptionElement subroot,
                                             final String[] pathElements, int pos) {
    if (pos >= 0) {
      // Not at root.
      if (!pathElements[pos].equals(subroot.name)) {
        return null;
      }
    }

    if (subroot.isValue) {
      return null;
    }

    pos++;
    ArrayList<String> res = new ArrayList<String>();

    if (pos == pathElements.length) {
      /* return names children of this level */

      for (OptionElement optel: subroot.getChildren()) {
        if (optel.isValue) {
          res.add(optel.name);
        }
      }

      return res;
    }

    for (OptionElement optel: subroot.getChildren()) {
      Collection<String> subres = getNames(optel, pathElements, pos);
      if (subres != null) {
        res.addAll(subres);
      }
    }

    return res;
  }

  /** Given a path e.g. org.bedework.global.module return all children which
   * are leaf nodes, i.e values.
   *
   * @param subroot
   * @param pathElements
   * @param pos
   * @return Collection
   */
  @SuppressWarnings("unchecked")
  private static Collection match(final OptionElement subroot,
                                  final String[] pathElements, int pos) {

    if (pos >= 0) {
      // Not at root.
      if (!pathElements[pos].equals(subroot.name)) {
        return null;
      }
    }

    if (subroot.isValue) {
      // pos must be last entry
      if ((pos + 1) != pathElements.length) {
        return null;
      }
      ArrayList res = new ArrayList();

      res.add(subroot.val);
      return res;
    }

    ArrayList res = new ArrayList();

    pos++;
    if (pos == pathElements.length) {
      /* return all the children of this level */

      for (OptionElement optel: subroot.getChildren()) {
        if (optel.isValue) {
          res.add(optel.val);
        }
      }

      return res;
    }

    for (OptionElement optel: subroot.getChildren()) {
      Collection subres = match(optel, pathElements, pos);
      if (subres != null) {
        res.addAll(subres);
      }
    }

    return res;
  }

  /* Given the root and a path element array find the corresponding value(s)
   *
   */
  private static Object findValue(final OptionElement subroot,
                                  final String[] pathElements, int pos) {
    if (pos >= 0) {
      // Not at root.
      if (!pathElements[pos].equals(subroot.name)) {
        return null;
      }
    }

    if (subroot.isValue) {
      // pos must be last entry
      if ((pos + 1) != pathElements.length) {
        return null;
      }
      return subroot.val;
    }

    pos++;
    if (pos == pathElements.length) {
      return null;
    }

    /* look to children for some value */

    Object singleRes = null;
    ArrayList<Object> multiRes = null;

    for (OptionElement optel: subroot.getChildren()) {
      Object res = findValue(optel, pathElements, pos);
      if (res != null) {
        if (singleRes != null) {
          multiRes = new ArrayList<Object>();
          appendResult(singleRes, multiRes);
          singleRes = null;
          appendResult(res, multiRes);
        } else if (multiRes != null) {
          appendResult(res, multiRes);
        } else {
          singleRes = res;
        }
      }
    }

    if (multiRes != null) {
      return multiRes;
    }

    return singleRes;
  }

  @SuppressWarnings("unchecked")
  private static void appendResult(final Object res, final ArrayList<Object> multiRes) {
    if (res instanceof Collection) {
      multiRes.addAll((Collection<? extends Object>)res);
    } else {
      multiRes.add(res);
    }
  }

  private void doChildren(final OptionElement oel, final Element subroot,
                          final Stack<Object> objStack) throws OptionsException {
    try {
      if (!XmlUtil.hasChildren(subroot)) {
        // Leaf node
        String ndval = XmlUtil.getElementContent(subroot);
        String name = subroot.getNodeName();

        if (objStack.empty()) {
          // Add a leaf node and return
          /*
          OptionElement valnode = new OptionElement();
          valnode.name = name;
          valnode.isValue = true;
          valnode.val = ndval;
          oel.addChild(valnode);
          */
          oel.isValue = true;
          oel.val = ndval;

          return;
        }

        // Val is an object which should have a setter for the property

        Object val = objStack.peek();
        Method meth = findSetter(val, name);

        if (meth == null) {
          // Treat as leaf node
          oel.isValue = true;
          oel.val = ndval;

          return;
        }

        Class[] parClasses = meth.getParameterTypes();
        if (parClasses.length != 1) {
          error("Invalid setter method " + name);
          throw new OptionsException("org.bedework.calenv.invalid.setter");
        }

        Class parClass = parClasses[0];
        Object par = null;
        if (parClass.getName().equals("java.lang.String")) {
          par = ndval;
        } else if (parClass.getName().equals("int") ||
            parClass.getName().equals("java.lang.Integer")) {
          par = Integer.valueOf(ndval);
        } else if (parClass.getName().equals("long") ||
            parClass.getName().equals("java.lang.Long")) {
          par = Long.valueOf(ndval);
        } else if (parClass.getName().equals("boolean") ||
            parClass.getName().equals("java.lang.Boolean")) {
          par = Boolean.valueOf(ndval);
        } else {
          error("Unsupported par class for method " + name);
          throw new OptionsException("org.bedework.calenv.unsupported.setter");
        }

        Object[] pars = new Object[]{par};

        meth.invoke(val, pars);
        return;
      }


      /* Non leaf nodes - call recursively with each of the children
       */
      for (Element el: XmlUtil.getElementsArray(subroot)) {
        String className = XmlUtil.getAttrVal(el, "classname");
        OptionElement valnode = new OptionElement();
        valnode.name = el.getNodeName();
        oel.addChild(valnode);

        if (className != null) {
          /* This counts as a leaf node. All children provide values for the
           * object.
           */
          if (!objStack.empty()) {
            error("Nested classes not yet supported for element " + valnode.name +
                  " and class " + className);
            throw new OptionsException("org.bedework.calenv.nested.classes.unsupported");
          }

          try {
            Object val = Class.forName(className).newInstance();
            valnode.isValue = true;
            valnode.val = val;

            objStack.push(val);
          } catch (Throwable t) {
            info("Unable to instantiate class " + className);
            // Treat it as a non-leaf node
            //throw new OptionsException(t);
            className = null;
          }
        } else {
          /* Just a non-leaf node */
        }

        doChildren(valnode, el, objStack);

        if (className != null) {
          objStack.pop();
        }
      }
    } catch (OptionsException ce) {
      throw ce;
    } catch (Throwable t) {
      throw new OptionsException(t);
    }
  }

  private static void error(final String msg) {
    Logger.getLogger(Options.class).error(msg);
  }

  private static void info(final String msg) {
    Logger.getLogger(Options.class).info(msg);
  }

  /* We've been dealing with property names - convert the dotted notation to a path
   */
  private static String[] makePathElements(final String val) {
    synchronized (splitPathPattern) {
      return splitPathPattern.split(val, 0);
    }
  }

  private Method findSetter(final Object val, final String name) throws OptionsException {
    String methodName = "set" + name.substring(0, 1).toUpperCase() +
                        name.substring(1);
    Method[] meths = val.getClass().getMethods();
    Method meth = null;

    for (int i = 0; i < meths.length; i++) {
      Method m = meths[i];

      if (m.getName().equals(methodName)) {
        if (meth != null) {
          throw new OptionsException("org.bedework.calenv.multiple.setters");
        }
        meth = m;
      }
    }

    if (meth == null) {
      Logger.getLogger(Options.class).error("No setter method for property " +
                                               name + " for class " +
                                               val.getClass().getName());
      return null;
    }

    return meth;
  }

  private static Method findGetter(final Object val, final String name) throws OptionsException {
    String methodName = "get" + name.substring(0, 1).toUpperCase() +
                        name.substring(1);
    Method[] meths = val.getClass().getMethods();
    Method meth = null;

    for (int i = 0; i < meths.length; i++) {
      Method m = meths[i];

      if (m.getName().equals(methodName)) {
        if (meth != null) {
          throw new OptionsException("multiple getters for " +
                                    val.getClass().getName() + "." + name);
        }
        meth = m;
      }
    }

    if (meth == null) {
      Logger.getLogger(Options.class).error("No getter method for property " +
                                               name + " for class " +
                                               val.getClass().getName());
      throw new OptionsException("No getters for " +
                                val.getClass().getName() + "." + name);
    }

    return meth;
  }

  private static Method findMethod(final Object val,
                                   final String methodName) throws OptionsException {
    Method[] meths = val.getClass().getMethods();
    Method meth = null;

    for (int i = 0; i < meths.length; i++) {
      Method m = meths[i];

      if (m.getName().equals(methodName)) {
        if (meth != null) {
          throw new OptionsException("org.bedework.calenv.multiple.setters");
        }
        meth = m;
      }
    }

    if (meth == null) {
      Logger.getLogger(Options.class).error("No method " + methodName +
                                               " for class " +
                                               val.getClass().getName());
      throw new OptionsException("org.bedework.calenv.no.method");
    }

    return meth;
  }

  private static Collection<Method> findGetters(final Object val) throws OptionsException {
    Method[] meths = val.getClass().getMethods();
    Collection<Method> getters = new ArrayList<Method>();

    for (int i = 0; i < meths.length; i++) {
      Method m = meths[i];

      if (m.getName().startsWith("get")) {
        getters.add(m);
      }
    }

    return getters;
  }
}

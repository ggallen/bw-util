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
package edu.rpi.sss.util.servlets;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletException;

/** This filter configures the superclass according to certain dynamic or
 * application parameters. These include some or all of the following:
 * <ul>
 * <li><b>approot</b> Root path to the css styles etc. probably a url</li>
 * <li><b>locale info</b> Allows different language and country versions</li>
 * <li><b>browser type</b> Different styles for different browsers</li>
 * <li><b>skin-name</b> Allow user configurability</li>
 * </ul>
 * <p>The above could be achieved by building a compound name from the various
 * components or by building a path. We choose to build a path.
 *
 * <p>The locale info is two components from the current locale. These are a
 * valid ISO Language Code, the lower-case two-letter codes as defined by
 * ISO-639. These codes can be found at a number of sites, such as
 * http://www.ics.uci.edu/pub/ietf/http/related/iso639.txt
 * <p>
 * The second part is a valid ISO Country Code. These codes are the upper-case
 * two-letter codes as defined by ISO-3166. A full list of these codes can be
 * found at a number of sites, such as <br/>
 * http://www.chemie.fu-berlin.de/diverse/doc/ISO_3166.html
 * <p>
 * The result is a string of the form "en_US"
 *
 * <p>The browser type can be any string. We choose to use the browser types
 * generated by HttpServletUtils.getBrowserType which maps user agents on
 * to a more restricted set of types.
 *
 * <p>The skin name is also an arbitrary string.
 *
 * <p>The values for these fields will be set by the doPreFilter method
 * which can be overriden. This method will be called before each invocation
 * of the filter. The values may be set at each call, however, the filter
 * will not be reinitialised unless the path has changed
 *
 * <p>The obtainConfigInfo method can be overridden to supply this filter
 * with the required information. This will be the more normal way of
 * supplying that information.
 *
 * @author Mike Douglass douglm@rpi.edu
 * @version June 18th 2003
 */
public class ConfiguredXSLTFilter extends XSLTFilter {
  /** Overide this to set the value or turn off presentation support
   * by returning null or the value "NONE".
   *
   * @return attribute name
   */
  public String getPresentationAttrName() {
    return PresentationState.presentationAttrName;
  }

  /** set by init parameter with this name
   */
  private boolean directoryBrowsingDisallowed;

  /** If directory browsing is disallowed then we need a file with the following
   * name in each directory in the stylesheet structure. The file itself need
   * contain nothing.
   */
  private static final String xsltdirMarkerName = "xsltdir.properties";

  /** The globals
   *
   */
  public static class XSLTConfig extends XSLTFilter.XsltGlobals {
    String id; // From sesssion

    /** Path to our current stylesheet. Used as a key to the xslt table */
    String xsltPath;

    /* These are used to set or reset the state for the next transform.
     * cfg is the state for the transform we are currently processing.
     * nextCfg is what we set cfg to before we update it from the
     * incoming presentation state which effectively holds changes to
     * the configuration.
     *
     * To implement a sticky change to a field, we update cfg and nextCfg.
     * For a non-sticky change we only update cfg. The next time through we
     * reset cfg from nextCfg.
     */
    XSLTFilterConfigInfo cfg = new XSLTFilterConfigInfo();
    XSLTFilterConfigInfo nextCfg;
  }

  /**
   * @param req
   * @return configuration object
   */
  public XSLTConfig getConfig(HttpServletRequest req) {
    return (XSLTConfig)getGlobals(req);
  }

  /* (non-Javadoc)
   * @see edu.rpi.sss.util.servlets.AbstractFilter#newFilterGlobals()
   */
  public AbstractFilter.FilterGlobals newFilterGlobals() {
    return new XSLTConfig();
  }

  /** The path we derive from the above
   */
//  private String xsltPath;
//  private boolean pathChanged;

  /** COnstructor
   *
   */
  public ConfiguredXSLTFilter() {
    super();
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);

    String dirBrowse = filterConfig.getInitParameter("directoryBrowsingDisallowed");

    if (dirBrowse != null) {
      dirBrowse = dirBrowse.toLowerCase();

      directoryBrowsingDisallowed = dirBrowse.equals("yes") ||
                                    dirBrowse.equals("true");
    }
  }

  /** This method can be overridden to allow a subclass to set up ready for a
   *  transformation.
   *
   * <p>The default action provided here is to locate the PresentatonState
   * object in the session and use that to configure the filter.
   *
   * @param   request    Incoming HttpServletRequest object
   * @param   xcfg       XSLTConfig Our globals.
   * @throws ServletException
   */
  public void updateConfigInfo(HttpServletRequest request, XSLTConfig xcfg)
                                     throws ServletException {
    PresentationState ps = getPresentationState(request);

    if (ps == null) {
      // Still can't do a thing
      return;
    }

    if (xcfg.nextCfg == null) {
      xcfg.nextCfg = new XSLTFilterConfigInfo();
    } else {
      xcfg.cfg.updateFrom(xcfg.nextCfg);
    }

    xcfg.cfg.setAppRoot(ps.getAppRoot());
    xcfg.nextCfg.setAppRoot(ps.getAppRoot());

    /** Transfer the state */
    if (ps.getNoXSLTSticky()) {
      xcfg.cfg.setDontFilter(true);
      xcfg.nextCfg.setDontFilter(true);
    } else {
      xcfg.cfg.setDontFilter(ps.getNoXSLT());
      ps.setNoXSLT(false);
    }

    /* ============== Don't filter ================= */

    if (xcfg.cfg.getDontFilter()) {
      // I think that's enough
      return;
    }

    /* ============== Locale ================= */

    Locale l = request.getLocale();
    String lang = l.getLanguage();
    if ((lang == null) || (lang.length() == 0)) {
      lang = xcfg.cfg.getDefaultLang();
    }

    String country = l.getCountry();
    if ((country == null) || (country.length() == 0)) {
      country = xcfg.cfg.getDefaultCountry();
    }

    xcfg.cfg.setLocaleInfo(XSLTFilterConfigInfo.makeLocale(lang, country));
    /* locale always sticky */
    xcfg.nextCfg.setLocaleInfo(XSLTFilterConfigInfo.makeLocale(lang, country));

    /* ============== Browser type ================= */

    String temp = ps.getBrowserType();
    if (temp != null) {
      xcfg.cfg.setBrowserType(temp);
    }
    if (!ps.getBrowserTypeSticky()) {
      ps.setBrowserType(null);
    } else {
      xcfg.nextCfg.setBrowserType(temp);
    }

    /* ============== Skin name ================= */

    temp = ps.getSkinName();
    if (temp != null) {
      xcfg.cfg.setSkinName(temp);
    }
    if (!ps.getSkinNameSticky()) {
      ps.setSkinName(null);
    } else {
      xcfg.nextCfg.setSkinName(temp);
    }

    /* ============== Content type ================= */

    xcfg.cfg.setContentType(ps.getContentType());
    if (!ps.getContentTypeSticky()) {
      ps.setContentType(null);
    } else {
      xcfg.nextCfg.setContentType(ps.getContentType());
    }

    /* ============== Refresh ================= */

    xcfg.cfg.setForceReload(ps.getForceXSLTRefresh());
    ps.setForceXSLTRefresh(false);

    /* I don't think we ever want to allow this
    info.setReloadAlways(ps.getForceXSLTRefreshAlways());
    */
  }

  /** This method can be overridden to allow a subclass to determine what the
   * final config was. It will be called after the filter has set the path
   * which may be the default.
   *
   * It will only be called if any of the config elements changed.
   *
   * @param   info       XSLTFilterConfigInfo for the next invocation.
   */
  public void updatedConfigInfo(XSLTFilterConfigInfo info) {
  }

  /** Obtain the presentation state from the session. Override if you want
   * different behaviour.
   *
   * @param request
   * @return PresentationState
   */
  protected PresentationState getPresentationState(HttpServletRequest request) {
    String attrName = getPresentationAttrName();

    if ((attrName == null) ||
         (attrName.equals("NONE"))) {
      return null;
    }

    HttpSession sess = request.getSession(false);

    if (sess == null) {
      return null;
    }

    Object o = sess.getAttribute(attrName);
    if (o == null) {
      return null;
    }

    if (debug) {
      ((PresentationState)o).debugDump("ConfiguredXSLTFilter",
                                       getLogger());
    }

    return (PresentationState)o;
  }

  /* (non-Javadoc)
   * @see edu.rpi.sss.util.servlets.AbstractFilter#doPreFilter(javax.servlet.http.HttpServletRequest)
   */
  public void doPreFilter(HttpServletRequest request)
    throws ServletException {
    XSLTConfig xcfg = getConfig(request);

    /* Update nextCfg to reflect last time through then modify according to
       requested changes */
    updateConfigInfo(request, xcfg);
    setDontFilter(request, xcfg.cfg.getDontFilter());

    if (getDontFilter(request)) {
      return;
    }

    if (xcfg.cfg.getAppRoot() == null) {
      /** Either it hasn't been set - so we can't transform or it's too
          early in the session (login etc)
       */
      if (debug) {
        getLogger().debug("No app root");
      }

      setDontFilter(request, true);
      return;
    }

    xcfg.cfg.setForceDefaultLocale(false);
    xcfg.cfg.setForceDefaultBrowserType(false);
    xcfg.cfg.setForceDefaultSkinName(false);

    if (debug) {
      getLogger().debug("About to try with forceDefaultBrowserType=" +
            xcfg.cfg.getForceDefaultBrowserType() +
            ", forceDefaultSkinName=" +
            xcfg.cfg.getForceDefaultSkinName() +
            ", contentType=" +
            xcfg.cfg.getContentType());
    }

    if (xcfg.cfg.getForceReload() || xcfg.cfg.getReloadAlways()) {
      flushXslt();
    }

    xcfg.cfg.setForceReload(false);

    setContentType(request, xcfg.cfg.getContentType());

    /* This doesn't work if we allow global flushes of the tranformers
    if ((getUrl(request) != null) &&
        (!xsltPathChanged(xcfg.cfg, xcfg.cfg))) {
      /* We're not searching for a path and it's not changed. We should be
         OK * /
      if (getDebug()) {
        getLogger().debug("Path did not change from " + getUrl(request));
      }

      return;
    }*/

    /** Build the 'ideal' path, that is the concatenation of locale,
     * browser and skin name. See if that combination is in the xslt table.
     *
     * If so, set that as the current path. Otherwise, discover the actual
     * path, i.e. the path with elements replaced by default values.
     *
     * Then set the ideal and actual paths ready for the transform.
     */

    StringBuilder idealPath = new StringBuilder(xcfg.cfg.getAppRoot());

    idealPath.append("/");
    idealPath.append(xcfg.cfg.getLocaleInfo());

    idealPath.append("/");
    idealPath.append(xcfg.cfg.getBrowserType());

    idealPath.append("/");
    idealPath.append(xcfg.cfg.getSkinName());
    idealPath.append(".xsl");

    String ideal = idealPath.toString();

    if (lookupPath(ideal) == null) {
      /** Try to discover a valid path. We work our way down the path trying
       * first the current element then the default. There are 3 elements to
       * try, locale, browser type and skin name.
       */

      StringBuilder xsltPath = new StringBuilder(xcfg.cfg.getAppRoot());

      /* ============== Locale ================= */

      if (!tryPath(xsltPath, xcfg.cfg.getLocaleInfo(), true)) {
        //xcfg.cfg.setForceDefaultLocale(true);

        if (!tryPath(xsltPath, xcfg.cfg.getDefaultLocaleInfo(), true)) {
          throw new ServletException("File not found: " + xsltPath);
        }
      }

      /* ============== Browser type ================= */

      if (!tryPath(xsltPath, xcfg.cfg.getBrowserType(), true)) {
//        xcfg.cfg.setForceDefaultBrowserType(true);

//        if (!tryPath(xsltPath, xcfg.cfg.getBrowserType(), true)) {

        if (!tryPath(xsltPath, xcfg.cfg.getDefaultBrowserType(), true)) {
          throw new ServletException("File not found: " + xsltPath);
        }
      }

      /* ============== Skin name ================= */

      if (!tryPath(xsltPath, xcfg.cfg.getSkinName() + ".xsl", false)) {
        //xcfg.cfg.setForceDefaultSkinName(true);

        if (!tryPath(xsltPath, xcfg.cfg.getDefaultSkinName() + ".xsl", false)) {
          throw new ServletException("File not found: " + xsltPath);
        }
      }

      setPath(ideal, xsltPath.toString());
    }

    setUrl(request, ideal);

    try {
      /** To get a transformer and see if everything seems OK
       */
      getXmlTransformer(getUrl(request));
      if (getDebug()) {
        getLogger().debug("Got Transformer OK");
      }

      /** Make any forced defaults stick
       * /

      if (nextCfg.getForceDefaultBrowserType()) {
        /** Switch to defaults and make them stay
         * /
        nextCfg.resetBrowserType();
      }

      if (nextCfg.getForceDefaultSkinName()) {
        /** Switch to defaults and make them stay
         * /
        nextCfg.resetSkinName();
      }
      */

//      xcfg.cfg.updateFrom(xcfg.nextCfg);
//      updatedConfigInfo(xcfg.cfg);
    } catch (Throwable t) {
      getLogger().error("Unable to transform document", t);
      throw new ServletException("Could not initialize transform for " +
                                 getUrl(request), t);
    }
  }

  /* * No longer valid with shared transformer tables which might get flushed.
  This method is called to see if there is any change in the components
   *  which make up the path. If there is none we don't need to (re)configure.
   *  Otherwise we need to point the filter at the new xslt file.
   *
   * <p>We compare the working copy, nextCfg, to cfg which holds the
   * state of the filter across calls and see if any path elements are
   * different.
   *
   * <p>The elements making up the path are:<ul>
   * <li>App root</li>
   * <li>locale</li>
   * <li>browser type</li>
   * <li>skin name</li>
   * </ul>
   *
   * <p>All, except the app root, may be defaulted if an explicit one does
   * not exist. Searching for a stylesheet then becomes an excercise in
   * trying various paths until a stylesheet is located.
   *
   * <p>
   *
   * @param  req       Incoming HttpServletRequest object
   * @param  cfg       XSLTFilterConfigInfo object holding state
   * @param  nextCfg   XSLTFilterConfigInfo working copy
   * @result boolean   true if the path changed.
   * /
  private boolean xsltPathChanged(XSLTFilterConfigInfo cfg,
                                  XSLTFilterConfigInfo nextCfg) {
    if (fieldChanged(cfg.getLocaleInfo(), nextCfg.getLocaleInfo()) ||
        fieldChanged(cfg.getBrowserType(), nextCfg.getBrowserType()) ||
        fieldChanged(cfg.getSkinName(), nextCfg.getSkinName())) {
      return true;
    }

    /** The path didn't change but we might be trying to force a reload
        If so just pretend the path changed.
     * /
    boolean returnVal = nextCfg.getForceReload() ||
                        nextCfg.getReloadAlways();
    nextCfg.setForceReload(false);

    return returnVal;
  }

  /* * This method is called to see if there is any change in the value
   *
   * @param   curVal      Current field value.
   * @param   newVal      Possible new field value.
   * @return  boolean     true if it changed
   * /
  private boolean fieldChanged(String curVal,
                               String newVal) {
    if (curVal == null) {
      return true;
    }

    return !curVal.equals(newVal);
  }*/

  /** Try a path and see if it exists. If so append the element
   *
   * @param   prefix    StringBuilder current path prefix
   * @param   el        String path element to append
   * @param   dir       true if el is a directory
   * @return  boolean   true if path is OK
   */
  private boolean tryPath(StringBuilder prefix, String el, boolean dir) {
    String path = prefix + "/" + el;

    if (dir && directoryBrowsingDisallowed) {
      path += "/" + xsltdirMarkerName;
    }

    if (debug) {
      getLogger().debug("trypath: " + path);
    }

    try {
      URL u = new URL(path);

      URLConnection uc = u.openConnection();

      if (!(uc instanceof HttpURLConnection)) {
        return false;
      }

      HttpURLConnection huc = (HttpURLConnection)uc;

      if (huc.getResponseCode() != 200) {
        return false;
      }

      prefix.append("/");
      prefix.append(el);

      return true;
    } catch (Throwable t) {
      if (debug) {
        getLogger().debug("trypath exception: ", t);
      }
    }

    return false;
  }
}


/*
 *   Copyright 2006 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.felix.framework;

import java.util.*;

import org.apache.felix.framework.searchpolicy.VersionRange;
import org.osgi.framework.*;
import org.osgi.service.packageadmin.*;

class PackageAdminImpl implements PackageAdmin, Runnable
{
    private Felix m_felix = null;
    private Bundle[][] m_reqBundles = null;

    public PackageAdminImpl(Felix felix)
    {
        m_felix = felix;

        // Start a thread to perform asynchronous package refreshes.
        Thread t = new Thread(this, "FelixPackageAdmin");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Returns the bundle associated with this class if the class was
     * loaded from a bundle, otherwise returns null.
     * 
     * @param clazz the class for which to determine its associated bundle.
     * @return the bundle associated with the specified class, otherwise null.
    **/
    public Bundle getBundle(Class clazz)
    {
        return m_felix.getBundle(clazz);
    }

    /**
     * Returns all bundles that have a specified symbolic name and whose
     * version is in the specified version range. If no version range is
     * specified, then all bundles with the specified symbolic name are
     * returned. The array is sorted in descending version order.
     * 
     * @param symbolicName the target symbolic name.
     * @param versionRange the target version range.
     * @return an array of matching bundles sorted in descending version order.
    **/
    public Bundle[] getBundles(String symbolicName, String versionRange)
    {
// TODO: PACKAGEADMIN - This could be made more efficient by reducing object creation.
        VersionRange vr = (versionRange == null)
            ? null : VersionRange.parse(versionRange);
        Bundle[] bundles = m_felix.getBundles();
        List list = new ArrayList();
        for (int i = 0; (bundles != null) && (i < bundles.length); i++)
        {
            String sym = (String) ((BundleImpl) bundles[i])
                .getInfo().getCurrentHeader().get(Constants.BUNDLE_SYMBOLICNAME);
            if ((sym != null) && sym.equals(symbolicName))
            {
                String s = (String) ((BundleImpl) bundles[i])
                    .getInfo().getCurrentHeader().get(Constants.BUNDLE_VERSION);
                Version v = (s == null) ? new Version("0.0.0") : new Version(s);
                if ((vr == null) || vr.isInRange(v))
                {
                    list.add(bundles[i]);
                }
            }
        }
        if (list.size() == 0)
        {
            return null;
        }
        bundles = (Bundle[]) list.toArray(new Bundle[list.size()]);
        Arrays.sort(bundles, new Comparator() {
            public int compare(Object o1, Object o2)
            {
                String s1 = (String) ((BundleImpl) o1)
                    .getInfo().getCurrentHeader().get(Constants.BUNDLE_VERSION);
                String s2 = (String) ((BundleImpl) o2)
                    .getInfo().getCurrentHeader().get(Constants.BUNDLE_VERSION);
                Version v1 = (s1 == null) ? new Version("0.0.0") : new Version(s1);
                Version v2 = (s2 == null) ? new Version("0.0.0") : new Version(s2);
                // Compare in reverse order to get descending sort.
                return v2.compareTo(v1);
            }
        });
        return bundles;
    }

    public int getBundleType(Bundle bundle)
    {
        return 0;
    }

    /**
     * Returns the exported package associated with the specified
     * package name. If there are more than one version of the package
     * being exported, then the highest version is returned.
     *
     * @param name the name of the exported package to find.
     * @return the exported package or null if no matching package was found.
    **/
    public ExportedPackage getExportedPackage(String name)
    {
        // Get all versions of the exported package.
        ExportedPackage[] pkgs = m_felix.getExportedPackages(name);
        // If there are no versions exported, then return null.
        if ((pkgs == null) || (pkgs.length == 0))
        {
            return null;
        }
        // Sort the exported versions.
        Arrays.sort(pkgs, new Comparator() {
            public int compare(Object o1, Object o2)
            {
                // Reverse arguments to sort in descending order.
                return ((ExportedPackage) o2).getVersion().compareTo(
                    ((ExportedPackage) o1).getVersion());
            }
        });
        // Return the highest version.
        return pkgs[0];
    }

    public ExportedPackage[] getExportedPackages(String name)
    {
        ExportedPackage[] pkgs = m_felix.getExportedPackages(name);
        return ((pkgs == null) || pkgs.length == 0) ? null : pkgs;
    }

    /**
     * Returns the packages exported by the specified bundle.
     *
     * @param bundle the bundle whose exported packages are to be returned.
     * @return an array of packages exported by the bundle or null if the
     *         bundle does not export any packages.
    **/
    public ExportedPackage[] getExportedPackages(Bundle b)
    {
        return m_felix.getExportedPackages(b);
    }

    /**
     * The OSGi specification states that refreshing packages is
     * asynchronous; this method simply notifies the package admin
     * thread to do a refresh.
     * @param bundles array of bundles to refresh or <tt>null</tt> to refresh
     *                any bundles in need of refreshing.
    **/
    public synchronized void refreshPackages(Bundle[] bundles)
        throws SecurityException
    {
        // Save our request parameters and notify all.
        if (m_reqBundles == null)
        {
            m_reqBundles = new Bundle[][] { bundles };
        }
        else
        {
            Bundle[][] newReqBundles = new Bundle[m_reqBundles.length + 1][];
            System.arraycopy(m_reqBundles, 0,
                newReqBundles, 0, m_reqBundles.length);
            newReqBundles[m_reqBundles.length] = bundles;
            m_reqBundles = newReqBundles;
        }
        notifyAll();
    }

    /**
     * The OSGi specification states that package refreshes happen
     * asynchronously; this is the run() method for the package
     * refreshing thread.
    **/
    public void run()
    {
        // This thread loops forever, thus it should
        // be a daemon thread.
        Bundle[] bundles = null;
        while (true)
        {
            synchronized (this)
            {
                // Wait for a refresh request.
                while (m_reqBundles == null)
                {
                    try
                    {
                        wait();
                    }
                    catch (InterruptedException ex)
                    {
                    }
                }

                // Get the bundles parameter for the current refresh request.
                bundles = m_reqBundles[0];
            }

            // Perform refresh.
            m_felix.refreshPackages(bundles);

            // Remove the first request since it is now completed.
            synchronized (this)
            {
                if (m_reqBundles.length == 1)
                {
                    m_reqBundles = null;
                }
                else
                {
                    Bundle[][] newReqBundles = new Bundle[m_reqBundles.length - 1][];
                    System.arraycopy(m_reqBundles, 1,
                        newReqBundles, 0, m_reqBundles.length - 1);
                    m_reqBundles = newReqBundles;
                }
            }
        }
    }

    public boolean resolveBundles(Bundle[] bundles)
    {
        return m_felix.resolveBundles(bundles);
    }

    public RequiredBundle[] getRequiredBundles(String symbolicName)
    {
        // TODO: Implement PackageAdmin.getRequiredBundles()
        return null;
    }

    public Bundle[] getFragments(Bundle bundle)
    {
        // TODO: Implement PackageAdmin.getFragments()
        return null;
    }

    public Bundle[] getHosts(Bundle bundle)
    {
        // TODO: Implement PackageAdmin.getHosts()
        return null;
    }
}
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.framework.resolver;

import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

class ResolverWireImpl implements ResolverWire
{
    private final BundleRevision m_requirer;
    private final BundleRequirement m_req;
    private final BundleRevision m_provider;
    private final BundleCapability m_cap;

    public ResolverWireImpl(
        BundleRevision requirer, BundleRequirement req,
        BundleRevision provider, BundleCapability cap)
    {
        m_requirer = requirer;
        m_req = req;
        m_provider = provider;
        m_cap = cap;
    }

    public BundleRevision getRequirer()
    {
        return m_requirer;
    }

    public BundleRequirement getRequirement()
    {
        return m_req;
    }

    public BundleRevision getProvider()
    {
        return m_provider;
    }

    public BundleCapability getCapability()
    {
        return m_cap;
    }

    public String toString()
    {
        return m_req
            + " -> "
            + "[" + m_cap.getRevision() + " VIA " + m_provider + "]";
// TODO: OSGi R4.3 - Simplify this message once we know wiring works.
    }
}
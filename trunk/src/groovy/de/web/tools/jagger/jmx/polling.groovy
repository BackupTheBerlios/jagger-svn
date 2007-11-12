/*  jagger - JMX remote bean polling

    Copyright (c) 2007 by 1&1 Internet AG

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation. A copy of this license is
    included with this software in the file "LICENSE.txt".

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    $Id$
*/

package de.web.tools.jagger.jmx.polling;

import de.web.tools.jagger.jmx.JMXAgentFacade;


/**
 *  Controls polling of remote mbeans.
 *
 *  Has to be thread-safe.
 */
class RemotePoller {
    // cache for connection facades to remote agents, indexed by URL
    private agentCache = [:]


    /**
     *  Return agent facade for the given model instance.
     *
     *  @param instance Instance object in the model.
     *  @return Agent facade (connection).
     */
    public synchronized getAgent(instance) {
        if (!agentCache.containsKey(instance.url)) {
            //println "Connecting to ${instance.toString()}..."
            def agent = new JMXAgentFacade(url: instance.url, username: instance.username, password: instance.password)
            agent.openConnection()
            agentCache[instance.url] = agent
        }

        return agentCache[instance.url]
    }
    
    /**
     *  Poll all defined instances for the values of the given bean attribute
     *  accessor.
     *
     *  @param instances List of instances to poll.
     *  @param accessor RemoteAttributeAccessor object.
     *  @return List of current values.
     */
    public pollInstances(instances, accessor) {
        def reachable = 0
        def values = instances.inject([]) { result, instance ->
            def agent
            try {
                agent = getAgent(instance)
            // XXX need to better handle failed servers, at least provide
            // a list of those as a target bean attribute
            } catch (java.rmi.ConnectException ex) {
            } catch (IOException ex) {
            }

            if (agent) {
                ++reachable
                accessor.injectValues(result, agent)
            }
            return result
        }
        if (!reachable) {
            throw new IOException("All instances unreachable")
        }
        //println values
        return values ? values : [0]
    }
}


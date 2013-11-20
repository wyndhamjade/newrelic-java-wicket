/*
 * Copyright 2013 Wyndham Jade, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wyndhamjade.util.wicket.newrelic;

import com.newrelic.api.agent.NewRelic;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Session;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.component.IRequestableComponent;
import org.apache.wicket.request.cycle.AbstractRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.handler.IComponentRequestHandler;
import org.apache.wicket.request.handler.IPageClassRequestHandler;

/**
 * Integrate Wicket with New Relic application monitoring.
 *
 * {@link RequestCycle} events are handled and integration with New Relic is
 * enhanced in the following ways:
 *
 * <ul>
 *     <li>Set user and account names for New Relic browser traces</li>
 *     <li>Set a custom transaction name</li>
 *     <li>Send {@code RuntimeException}s to New Relic</li>
 * </ul>
 *
 * The transaction names generated by default by the New Relic agent are not
 * particularly useful for a Wicket application.  This class generates custom
 * transaction names as so:
 *
 * If the request target is a page, first the package prefix (specified in the
 * NewRelicRequestCycleListener constructor) is removed from the full class
 * name.  Dots are replaced by slashes in the remaining class name.
 * If the request target is a component on a page, then the class name of the
 * page is converted as described above.  Then, a slash and the path of the
 * targeted component are appended to that.
 *
 * If none of the above apply, then the transaction name is simply the request
 * path.
 */
public final class NewRelicRequestCycleListener
        extends AbstractRequestCycleListener {
    private static final MetaDataKey<Boolean> FIRST_HANDLER =
            new MetaDataKey<Boolean>() {};

    private final String packagePrefix;
    private final int packagePrefixLength;

    public NewRelicRequestCycleListener(final String packagePrefix) {
        this.packagePrefix = packagePrefix;
        this.packagePrefixLength = packagePrefix.length();
    }

    @Override
    public void onBeginRequest(final RequestCycle cycle) {
        cycle.setMetaData(FIRST_HANDLER, true);
        final Session session = Session.get();
        if (session != null && session instanceof NewRelicSessionSupport) {
            final NewRelicSessionSupport sessionInfo =
                    (NewRelicSessionSupport) session;
            NewRelic.setUserName(sessionInfo.getUserName());
            NewRelic.setAccountName(sessionInfo.getAccountName());
        }
    }

    @Override
    public void onRequestHandlerResolved(final RequestCycle cycle,
                                         final IRequestHandler handler) {
        if (cycle.getMetaData(FIRST_HANDLER)) {
            cycle.setMetaData(FIRST_HANDLER, false);

            final StringBuilder s = new StringBuilder();

            if (handler instanceof IComponentRequestHandler) {
                final IRequestableComponent c = ((IComponentRequestHandler) handler).getComponent();
                s.append('/');
                s.append(pageClassToPath(c.getPage().getClass()));
                s.append('/');
                s.append(componentToPath(c));
            } else if (handler instanceof IPageClassRequestHandler) {
                s.append('/');
                s.append(pageClassToPath(((IPageClassRequestHandler) handler).getPageClass()));
            } else {
                NewRelic.ignoreTransaction();
                return;
            }

            NewRelic.setTransactionName(null, s.toString());
        }
    }

    @Override
    public IRequestHandler onException(final RequestCycle cycle,
                                       final Exception e) {
        NewRelic.noticeError(e);
        return null;
    }

    private String pageClassToPath(final Class pageClass) {
        final String name = pageClass.getName();
        final String nameWithoutPrefix;
        if (name.startsWith(packagePrefix)) {
            nameWithoutPrefix = name.substring(packagePrefixLength);
        } else {
            nameWithoutPrefix = name;
        }
        return nameWithoutPrefix.replace('.', '/');
    }

    private String componentToPath(final IRequestableComponent c) {
        return c.getPageRelativePath().replace(':', '/');
    }
}

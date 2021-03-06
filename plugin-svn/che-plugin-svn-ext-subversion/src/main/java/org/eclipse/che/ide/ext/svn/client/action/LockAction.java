/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.svn.client.action;

import org.eclipse.che.ide.ext.svn.client.SubversionExtensionLocalizationConstants;
import org.eclipse.che.ide.ext.svn.client.SubversionExtensionResources;
import org.eclipse.che.ide.ext.svn.client.lockunlock.LockUnlockPresenter;
import org.eclipse.che.api.analytics.client.logger.AnalyticsEventLogger;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.selection.SelectionAgent;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Extension of {@link SubversionAction} for implementing the "svn lock" command.
 */
@Singleton
public class LockAction extends SubversionAction {

    private final LockUnlockPresenter presenter;

    /**
     * Constructor.
     */
    @Inject
    public LockAction(final AnalyticsEventLogger eventLogger,
                      final AppContext appContext,
                      final LockUnlockPresenter presenter,
                      final SelectionAgent selectionAgent,
                      final SubversionExtensionLocalizationConstants constants,
                      final SubversionExtensionResources resources) {
        super(constants.lockTitle(), constants.lockDescription(), resources.lock(), eventLogger, appContext,
              constants, resources, selectionAgent);
        this.presenter = presenter;
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
        eventLogger.log(this, "IDE: Subversion 'Lock' action performed");

        this.presenter.showLockDialog();
    }

    @Override
    protected boolean isSelectionRequired() {
        return false;
    }
}

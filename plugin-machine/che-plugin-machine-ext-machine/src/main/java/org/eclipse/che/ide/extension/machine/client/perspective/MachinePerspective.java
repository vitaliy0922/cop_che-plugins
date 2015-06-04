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
package org.eclipse.che.ide.extension.machine.client.perspective;

import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.parts.PartStack;
import org.eclipse.che.ide.extension.machine.client.machine.console.MachineConsolePresenter;
import org.eclipse.che.ide.extension.machine.client.perspective.widgets.machine.appliance.MachineAppliancePresenter;
import org.eclipse.che.ide.extension.machine.client.perspective.widgets.machine.panel.MachinePanelPresenter;
import org.eclipse.che.ide.workspace.PartStackPresenterFactory;
import org.eclipse.che.ide.workspace.PartStackViewFactory;
import org.eclipse.che.ide.workspace.WorkBenchControllerFactory;
import org.eclipse.che.ide.workspace.perspectives.general.AbstractPerspective;
import org.eclipse.che.ide.workspace.perspectives.general.Perspective;
import org.eclipse.che.ide.workspace.perspectives.general.PerspectiveViewImpl;

import javax.annotation.Nonnull;

import static org.eclipse.che.ide.api.parts.PartStackType.EDITING;
import static org.eclipse.che.ide.api.parts.PartStackType.INFORMATION;
import static org.eclipse.che.ide.api.parts.PartStackType.NAVIGATION;
import static org.eclipse.che.ide.workspace.perspectives.general.Perspective.Type.MACHINE;

/**
 * Special view perspective which defines how must main window be displayed when we choose machine perspective.
 *
 * @author Dmitry Shnurenko
 */
@Singleton
public class MachinePerspective extends AbstractPerspective {

    private final MachinePanelPresenter machinePanel;

    @Inject
    public MachinePerspective(PerspectiveViewImpl view,
                              PartStackViewFactory partViewFactory,
                              WorkBenchControllerFactory controllerFactory,
                              PartStackPresenterFactory stackPresenterFactory,
                              MachineConsolePresenter console,
                              MachinePanelPresenter machinePanel,
                              MachineAppliancePresenter infoContainer) {
        super(view, stackPresenterFactory, partViewFactory, controllerFactory);

        this.machinePanel = machinePanel;

        //central panel
        partStacks.put(EDITING, infoContainer);

        openPart(console, INFORMATION);
        openPart(machinePanel, NAVIGATION);

        setActivePart(machinePanel);
    }

    /** {@inheritDoc} */
    @Override
    @Nonnull
    public Perspective.Type getType() {
        return MACHINE;
    }

    /** {@inheritDoc} */
    @Override
    public void go(@Nonnull AcceptsOneWidget container) {
        machinePanel.getMachines();

        PartStack information = getPartStack(INFORMATION);
        PartStack navigation = getPartStack(NAVIGATION);
        PartStack editing = getPartStack(EDITING);

        if (information == null || navigation == null || editing == null) {
            return;
        }

        information.go(view.getInformationPanel());
        navigation.go(view.getNavigationPanel());
        editing.go(view.getEditorPanel());

        container.setWidget(view);
    }
}
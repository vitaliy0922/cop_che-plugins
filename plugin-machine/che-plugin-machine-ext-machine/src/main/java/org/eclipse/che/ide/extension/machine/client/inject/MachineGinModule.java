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
package org.eclipse.che.ide.extension.machine.client.inject;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.gwt.inject.client.multibindings.GinMultibinder;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.extension.ExtensionGinModule;
import org.eclipse.che.ide.extension.machine.client.command.ExecuteCommandView;
import org.eclipse.che.ide.extension.machine.client.command.ExecuteCommandViewImpl;
import org.eclipse.che.ide.extension.machine.client.command.configuration.api.CommandType;
import org.eclipse.che.ide.extension.machine.client.command.configuration.edit.EditConfigurationsView;
import org.eclipse.che.ide.extension.machine.client.command.configuration.edit.EditConfigurationsViewImpl;
import org.eclipse.che.ide.extension.machine.client.command.configuration.gwt.GWTCommandType;
import org.eclipse.che.ide.extension.machine.client.command.configuration.maven.MavenCommandType;
import org.eclipse.che.ide.extension.machine.client.console.MachineConsoleToolbar;
import org.eclipse.che.ide.extension.machine.client.console.MachineConsoleView;
import org.eclipse.che.ide.extension.machine.client.console.MachineConsoleViewImpl;
import org.eclipse.che.ide.ui.toolbar.ToolbarPresenter;

/** @author Artem Zatsarynnyy */
@ExtensionGinModule
public class MachineGinModule extends AbstractGinModule {

    /** {@inheritDoc} */
    @Override
    protected void configure() {
        bind(MachineConsoleView.class).to(MachineConsoleViewImpl.class).in(Singleton.class);
        bind(ToolbarPresenter.class).annotatedWith(MachineConsoleToolbar.class).to(ToolbarPresenter.class).in(Singleton.class);

        bind(ExecuteCommandView.class).to(ExecuteCommandViewImpl.class).in(Singleton.class);
        bind(EditConfigurationsView.class).to(EditConfigurationsViewImpl.class).in(Singleton.class);

        GinMultibinder<CommandType> commandTypeMultibinder = GinMultibinder.newSetBinder(binder(), CommandType.class);
        commandTypeMultibinder.addBinding().to(GWTCommandType.class);
        commandTypeMultibinder.addBinding().to(MavenCommandType.class);
    }
}

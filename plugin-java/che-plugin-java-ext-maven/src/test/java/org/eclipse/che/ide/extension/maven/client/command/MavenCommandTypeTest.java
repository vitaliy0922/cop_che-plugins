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
package org.eclipse.che.ide.extension.maven.client.command;

import org.eclipse.che.ide.extension.machine.client.command.CommandConfiguration;
import org.eclipse.che.ide.extension.machine.client.command.CommandConfigurationPage;
import org.eclipse.che.ide.extension.machine.client.command.valueproviders.CurrentProjectNameProvider;
import org.eclipse.che.ide.extension.maven.client.MavenResources;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collection;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

/** @author Artem Zatsarynnyy */
@RunWith(MockitoJUnitRunner.class)
public class MavenCommandTypeTest {

    @Mock
    private MavenResources             mavenResources;
    @Mock
    private MavenCommandPagePresenter  mavenCommandPagePresenter;
    @Mock
    private CurrentProjectNameProvider currentProjectNameProvider;

    @InjectMocks
    private MavenCommandType mavenCommandType;

    @Test
    public void shouldReturnIcon() throws Exception {
        mavenCommandType.getIcon();

        verify(mavenResources).mavenCommandType();
    }

    @Test
    public void shouldReturnPages() throws Exception {
        final Collection<CommandConfigurationPage<? extends CommandConfiguration>> pages = mavenCommandType.getConfigurationPages();

        assertTrue(pages.contains(mavenCommandPagePresenter));
    }

    @Test
    public void testGettingCommandTemplate() throws Exception {
        mavenCommandType.getCommandTemplate();

        verify(currentProjectNameProvider).getKey();
    }
}

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
package org.eclipse.che.ide.ext.java.client.project.node.jar;

import org.eclipse.che.api.project.shared.dto.ProjectDescriptor;
import org.eclipse.che.ide.api.project.node.settings.NodeSettings;
import org.eclipse.che.ide.ext.java.client.project.node.JavaNodeManager;
import org.eclipse.che.ide.ext.java.shared.JarEntry;

import javax.annotation.Nonnull;

/**
 * @author Vlad Zhukovskiy
 */
public abstract class AbstractJarEntryNode extends AbstractJavaSyntheticNode<JarEntry> {

    protected final int libId;

    public AbstractJarEntryNode(@Nonnull JarEntry jarEntry,
                                int libId,
                                @Nonnull ProjectDescriptor projectDescriptor,
                                @Nonnull NodeSettings nodeSettings,
                                @Nonnull JavaNodeManager nodeManager) {
        super(jarEntry, projectDescriptor, nodeSettings, nodeManager);
        this.libId = libId;
    }
}

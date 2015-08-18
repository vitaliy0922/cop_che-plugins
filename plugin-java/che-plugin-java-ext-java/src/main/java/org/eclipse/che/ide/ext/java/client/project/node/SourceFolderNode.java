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
package org.eclipse.che.ide.ext.java.client.project.node;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.project.shared.dto.ItemReference;
import org.eclipse.che.api.project.shared.dto.ProjectDescriptor;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.js.Promises;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.ext.java.client.project.settings.JavaNodeSettings;
import org.eclipse.che.ide.project.node.FolderReferenceNode;
import org.eclipse.che.ide.project.node.ItemReferenceChainFilter;
import org.eclipse.che.ide.project.node.resource.ItemReferenceProcessor;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author Vlad Zhukovskiy
 */
public class SourceFolderNode extends FolderReferenceNode {

    private final JavaNodeManager nodeManager;

    @Inject
    public SourceFolderNode(@Assisted ItemReference itemReference,
                            @Assisted ProjectDescriptor projectDescriptor,
                            @Assisted JavaNodeSettings nodeSettings,
                            @Nonnull EventBus eventBus,
                            @Nonnull JavaNodeManager nodeManager,
                            @Nonnull ItemReferenceProcessor resourceProcessor) {
        super(itemReference, projectDescriptor, nodeSettings, eventBus, nodeManager, resourceProcessor);
        this.nodeManager = nodeManager;
    }

    @Nonnull
    @Override
    protected Promise<List<Node>> getChildrenImpl() {
//        return nodeManager.getChildren(getData(), getProjectDescriptor(), getSettings(), );
        return Promises.resolve(Collections.<Node>emptyList());
    }


}

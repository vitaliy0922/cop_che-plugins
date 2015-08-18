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
package org.eclipse.che.ide.ext.java.client.project.interceptor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.js.Promises;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.api.project.node.interceptor.NodeInterceptor;
import org.eclipse.che.ide.ext.java.client.project.node.JavaNodeManager;
import org.eclipse.che.ide.project.node.FileReferenceNode;
import org.eclipse.che.ide.util.loging.Log;

import java.util.List;

/**
 * @author Vlad Zhukovskiy
 */
@Singleton
public class JavaClassInterceptor implements NodeInterceptor {

    private JavaNodeManager nodeManager;

    @Inject
    public JavaClassInterceptor(JavaNodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    @Override
    public Promise<List<Node>> intercept(Node parent, List<Node> children) {

        for (Node child : children) {
            if (child instanceof FileReferenceNode) {
                FileReferenceNode fileNode = (FileReferenceNode)child;

                if (!fileNode.getName().endsWith(".java")) {
                    continue;
                }

                fileNode.getPresentation(false).setPresentableText(fileNode.getName().replace(".java", ""));
                fileNode.getPresentation(false).setPresentableIcon(nodeManager.getJavaNodesResources().fileJava());
            }
        }

        return Promises.resolve(children);
    }

    @Override
    public Integer weightOrder() {
        return 52;
    }
}

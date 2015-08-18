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

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.project.gwt.client.ProjectServiceClient;
import org.eclipse.che.api.project.shared.dto.ItemReference;
import org.eclipse.che.api.project.shared.dto.ProjectDescriptor;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.callback.AsyncPromiseHelper;
import org.eclipse.che.api.promises.client.callback.AsyncPromiseHelper.RequestCall;
import org.eclipse.che.ide.api.project.node.HasProjectDescriptor;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.api.project.node.settings.NodeSettings;
import org.eclipse.che.ide.api.project.node.settings.SettingsProvider;
import org.eclipse.che.ide.collections.Array;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.JavaResources;
import org.eclipse.che.ide.ext.java.client.navigation.JavaNavigationService;
import org.eclipse.che.ide.ext.java.client.project.node.jar.JarContainerNode;
import org.eclipse.che.ide.ext.java.client.project.settings.JavaNodeSettings;
import org.eclipse.che.ide.ext.java.client.project.settings.JavaNodeSettingsProvider;
import org.eclipse.che.ide.ext.java.shared.Constants;
import org.eclipse.che.ide.ext.java.shared.Jar;
import org.eclipse.che.ide.ext.java.shared.JarEntry;
import org.eclipse.che.ide.project.node.NodeManager;
import org.eclipse.che.ide.project.node.factory.NodeFactory;
import org.eclipse.che.ide.project.shared.NodesResources;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Vlad Zhukovskiy
 */
@Singleton
public class JavaNodeManager extends NodeManager {
    private JavaNavigationService    javaService;
    private JavaNodeFactory          javaNodeFactory;
    private JavaResources            javaResources;
    private EventBus                 eventBus;
    private JavaNodeSettingsProvider settingsProvider;

    @Inject
    public JavaNodeManager(NodeFactory nodeFactory,
                           ProjectServiceClient projectService,
                           DtoUnmarshallerFactory dtoUnmarshaller,
                           NodesResources nodesResources,
                           SettingsProvider nodeSettingsProvider,
                           DtoFactory dtoFactory,
                           JavaNavigationService javaService,
                           JavaNodeFactory javaNodeFactory,
                           Map<String, SettingsProvider> settingsProviderMap,
                           JavaResources javaResources,
                           EventBus eventBus) {
        super(nodeFactory, projectService, dtoUnmarshaller, nodesResources, nodeSettingsProvider, dtoFactory);

        this.javaService = javaService;
        this.javaNodeFactory = javaNodeFactory;
        this.javaResources = javaResources;
        this.eventBus = eventBus;

        if (!(settingsProviderMap.containsKey("java") || settingsProviderMap.get("java") instanceof JavaNodeSettingsProvider)) {
            throw new IllegalStateException("Java node settings provider was not found");
        }

        this.settingsProvider = (JavaNodeSettingsProvider)settingsProviderMap.get("java");
    }

    /** **************** External Libraries operations ********************* */

    @Nonnull
    public Promise<List<Node>> getExternalLibraries(@Nonnull ProjectDescriptor descriptor) {
        return AsyncPromiseHelper.createFromAsyncRequest(getExternalLibrariesRC(descriptor.getPath()))
                                 .then(createJarNodes(descriptor, settingsProvider.getSettings()));
    }

    @Nonnull
    private RequestCall<Array<Jar>> getExternalLibrariesRC(@Nonnull final String projectPath) {
        return new RequestCall<Array<Jar>>() {
            @Override
            public void makeCall(AsyncCallback<Array<Jar>> callback) {
                javaService.getExternalLibraries(projectPath, _callback(callback, dtoUnmarshaller.newArrayUnmarshaller(Jar.class)));
            }
        };
    }

    @Nonnull
    private Function<Array<Jar>, List<Node>> createJarNodes(@Nonnull final ProjectDescriptor descriptor,
                                                            @Nonnull final NodeSettings nodeSettings) {
        return new Function<Array<Jar>, List<Node>>() {
            @Override
            public List<Node> apply(Array<Jar> jars) throws FunctionException {
                List<Node> nodes = new ArrayList<>(jars.size());

                for (Jar jar : jars.asIterable()) {
                    JarContainerNode jarContainerNode = javaNodeFactory.newJarContainerNode(jar, descriptor, nodeSettings);
                    nodes.add(jarContainerNode);
                }

                return nodes;
            }
        };
    }

    /** **************** Jar Library Children operations ********************* */

    @Nonnull
    public Promise<List<Node>> getJarLibraryChildren(@Nonnull ProjectDescriptor descriptor, int libId, @Nonnull NodeSettings nodeSettings) {
        return AsyncPromiseHelper.createFromAsyncRequest(getLibraryChildrenRC(descriptor.getPath(), libId))
                                 .then(createJarEntryNodes(libId, descriptor, nodeSettings));
    }

    @Nonnull
    private RequestCall<Array<JarEntry>> getLibraryChildrenRC(@Nonnull final String projectPath, final int libId) {
        return new RequestCall<Array<JarEntry>>() {
            @Override
            public void makeCall(AsyncCallback<Array<JarEntry>> callback) {
                javaService
                        .getLibraryChildren(projectPath, libId, _callback(callback, dtoUnmarshaller.newArrayUnmarshaller(JarEntry.class)));
            }
        };
    }

    @Nonnull
    public Promise<List<Node>> getJarChildren(@Nonnull ProjectDescriptor descriptor, int libId, @Nonnull String path, @Nonnull NodeSettings nodeSettings) {
        return AsyncPromiseHelper.createFromAsyncRequest(getChildrenRC(descriptor.getPath(), libId, path))
                                 .then(createJarEntryNodes(libId, descriptor, nodeSettings));
    }

    @Nonnull
    private RequestCall<Array<JarEntry>> getChildrenRC(@Nonnull final String projectPath, final int libId, @Nonnull final String path) {
        return new RequestCall<Array<JarEntry>>() {
            @Override
            public void makeCall(AsyncCallback<Array<JarEntry>> callback) {
                javaService
                        .getChildren(projectPath, libId, path, _callback(callback, dtoUnmarshaller.newArrayUnmarshaller(JarEntry.class)));
            }
        };
    }

    @Nonnull
    private Function<Array<JarEntry>, List<Node>> createJarEntryNodes(final int libId, @Nonnull final ProjectDescriptor descriptor,
                                                                      @Nonnull final NodeSettings nodeSettings) {
        return new Function<Array<JarEntry>, List<Node>>() {
            @Override
            public List<Node> apply(Array<JarEntry> entries) throws FunctionException {

                List<Node> nodes = new ArrayList<>();

                for (JarEntry jarEntry : entries.asIterable()) {
                    Node node = createNode(jarEntry, libId, descriptor, nodeSettings);
                    if (node != null) {
                        nodes.add(node);
                    }
                }

                return nodes;
            }
        };
    }

    private Node createNode(JarEntry entry, int id, ProjectDescriptor descriptor, NodeSettings nodeSettings) {

        if (entry.getType() == JarEntry.JarEntryType.FOLDER || entry.getType() == JarEntry.JarEntryType.PACKAGE) {
            return javaNodeFactory.newJarFolderNode(entry, id, descriptor, nodeSettings);
        } else if (entry.getType() == JarEntry.JarEntryType.FILE || entry.getType() == JarEntry.JarEntryType.CLASS_FILE) {
            return javaNodeFactory.newJarFileNode(entry, id, descriptor, nodeSettings);
        }

        return null;
    }

    /** **************** Common methods ********************* */

    public static boolean isJavaProject(Node node) {
        if (!(node instanceof HasProjectDescriptor)) {
            return false;
        }

        ProjectDescriptor descriptor = ((HasProjectDescriptor)node).getProjectDescriptor();
        Map<String, List<String>> attributes = descriptor.getAttributes();

        return attributes.containsKey(Constants.LANGUAGE)
               && attributes.get(Constants.LANGUAGE) != null
               && "java".equals(attributes.get(Constants.LANGUAGE).get(0));
    }

    public JavaResources getJavaNodesResources() {
        return javaResources;
    }

    public JavaNodeFactory getJavaNodeFactory() {
        return javaNodeFactory;
    }

    public JavaNodeSettingsProvider getJavaSettingsProvider() {
        return settingsProvider;
    }

    @Override
    public Node createNodeByType(ItemReference itemReference, ProjectDescriptor descriptor, NodeSettings settings) {
        if ("folder".equals(itemReference.getType()) || "project".equals(itemReference.getType())) {
            return javaNodeFactory.newPackageNode(itemReference, descriptor, (JavaNodeSettings)settingsProvider.getSettings());
        } else if ("file".equals(itemReference.getType())) {
            return nodeFactory.newFileReferenceNode(itemReference, descriptor, settings);
        }
        return null;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public JavaNavigationService getJavaService() {
        return javaService;
    }
}

/*******************************************************************************
 * Copyright (c) 2004, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.jdt.internal.core;

import org.eclipse.che.api.project.server.Constants;
import org.eclipse.che.api.project.server.ProjectJson;
import org.eclipse.che.api.project.shared.Builders;
import org.eclipse.che.core.internal.resources.ResourcesPlugin;
import org.eclipse.che.ide.ant.tools.AntUtils;
import org.eclipse.che.ide.maven.tools.MavenUtils;
import org.eclipse.che.jdt.core.JavaCore;
import org.eclipse.che.jdt.internal.core.util.JavaElementFinder;
import org.eclipse.che.jdt.internal.core.util.Util;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.eval.IEvaluationContext;
import org.eclipse.jdt.internal.compiler.util.ObjectVector;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;
import org.eclipse.jdt.internal.core.JavaModelStatus;
import org.eclipse.jdt.internal.core.util.MementoTokenizer;
import org.eclipse.jdt.internal.core.util.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.eclipse.che.jdt.internal.core.JavaModelManager.PerProjectInfo;
import static org.eclipse.che.jdt.internal.core.JavaModelManager.getJavaModelManager;
import static org.eclipse.che.jdt.internal.core.JavaModelManager.getTarget;
import static org.eclipse.che.jdt.internal.core.JavaModelManager.isFile;

/**
 * @author Evgen Vidolob
 */
public class JavaProject extends Openable implements IJavaProject, SuffixConstants {
    /**
     * Name of file containing project classpath
     */
    public static final String CLASSPATH_FILENAME = ".codenvy/classpath";
    /**
     * Value of the project's raw classpath if the .classpath file contains invalid entries.
     */
    public static final  IClasspathEntry[]                          INVALID_CLASSPATH = new IClasspathEntry[0];
    private static final Logger                                     LOG               = LoggerFactory.getLogger(JavaProject.class);
    private final        DirectoryStream.Filter<java.nio.file.Path> jarFilter         = new DirectoryStream.Filter<java.nio.file.Path>() {
        @Override
        public boolean accept(java.nio.file.Path entry) throws IOException {
            return entry.getFileName().toString().endsWith("jar");
        }
    };
    private          String                workspacePath;
    private volatile SearchableEnvironment nameEnvironment;
    private          String                projectPath;
    private          String                tempDir;
    private          String                wsId;
    private          File                  projectDir;
    private          Map<String, String>   options;
    private          IClasspathEntry[]     rawClassPath;
    private          ResolvedClasspath     resolvedClasspath;
    protected IProject project;

    public JavaProject(IProject project, JavaElement parent, Map<String, String> options) {
        super(parent);
        this.project = project;
        this.options = options;
    }

//    public JavaProject(File root, String projectPath, String tempDir, String ws, Map<String, String> options, JavaElement parent) {
//        super(parent);
//        this.projectPath = projectPath;
//        this.tempDir = tempDir;
//        wsId = ws;
//        workspacePath = root.getPath();
//        int index = projectPath.lastIndexOf('/');
//        projectName = index < 0 ? projectPath : projectPath.substring(index + 1);
//        this.projectDir = new File(root, projectPath);
//        this.options = options;
//        List<IClasspathEntry> paths = new LinkedList<>();
//        try {
//            if (index <= 0) {
//                // project in root folder
//                addSources(projectDir, paths);
//            } else {
//                // module of project - add this module and all modules in parent projects
//                index = projectPath.indexOf('/', 1);
//                File parent = new File(root, projectPath.substring(1, index));
//                LinkedList<File> q = new LinkedList<>();
//                q.add(parent);
//                while (!q.isEmpty()) {
//                    File f = q.poll();
//                    addSources(f, paths);
//                    File[] l = f.listFiles();
//                    for (File c : l) {
//                        if (c.isDirectory()
//                            && new File(c, Constants.CODENVY_PROJECT_FILE_RELATIVE_PATH).exists()) {
//
//                            q.add(c);
//                        }
//                    }
//                }
//            }
//        } catch (IOException e) {
//            LOG.error("Can't find sources folder attribute", e);
//        }
//
//        paths.add(JavaCore.newContainerEntry(new Path("codenvy:Jre")));
//        File depDir = new File(tempDir, wsId + projectPath);
//        try {
//            if (depDir.exists()) {
//                try (DirectoryStream<java.nio.file.Path> deps =
//                             Files.newDirectoryStream(depDir.toPath(), jarFilter)) {
//
//                    for (java.nio.file.Path dep : deps) {
//                        String name = dep.getFileName().toString();
//                        File srcJar =
//                                new File(dep.getParent().toFile(), "sources/" + name.substring(0, name.lastIndexOf('.')) + "-sources.jar");
//                        IPath srcPath = null;
//                        if (srcJar.exists()) {
//                            srcPath = new Path(srcJar.getAbsolutePath());
//                        }
//                        paths.add(JavaCore.newLibraryEntry(new Path(dep.toAbsolutePath().toString()), srcPath, null));
//                    }
//                }
//            }
//
//        } catch (IOException e) {
//            LOG.error("Can't find jar dependency's: ", e);
//        }
//        rawClassPath = paths.toArray(new IClasspathEntry[paths.size()]);
//    }

    /**
     * Returns true if the given project is accessible and it has
     * a java nature, otherwise false.
     *
     * @param project
     *         IProject
     * @return boolean
     */
    public static boolean hasJavaNature(IProject project) {
//        try {
//            return project.hasNature(JavaCore.NATURE_ID);
//        } catch (CoreException e) {
//            if (ExternalJavaProject.EXTERNAL_PROJECT_NAME.equals(project.getName()))
//                return true;
//            // project does not exist or is not open
//        }
//        return false;
        return true;
    }

    public static boolean areClasspathsEqual(
            IClasspathEntry[] firstClasspath, IClasspathEntry[] secondClasspath,
            IPath firstOutputLocation, IPath secondOutputLocation) {
        int length = firstClasspath.length;
        if (length != secondClasspath.length) return false;
        for (int i = 0; i < length; i++) {
            if (!firstClasspath[i].equals(secondClasspath[i]))
                return false;
        }
        if (firstOutputLocation == null)
            return secondOutputLocation == null;
        return firstOutputLocation.equals(secondOutputLocation);
    }

    private void addSources(File projectDir, List<IClasspathEntry> paths) throws IOException {
        File codenvy = new File(projectDir, Constants.CODENVY_PROJECT_FILE_RELATIVE_PATH);
        List<File> sources = new LinkedList<>();

        final ProjectJson projectJson;
        try (FileInputStream in = new FileInputStream(codenvy)) {
            projectJson = ProjectJson.load(in);
        }

        Builders defBuilder = projectJson.getBuilders();

        if (defBuilder != null) {
            if ("maven".equals(defBuilder.getDefault())) {
                File pom = new File(projectDir, "pom.xml");
                if (pom.exists()) {
                    for (String src : MavenUtils.getSourceDirectories(pom)) {
                        sources.add(new File(projectDir, src));
                    }
                }
            } else if ("ant".equals(defBuilder.getDefault())) {
                File build = new File(projectDir, "build.xml");
                if (build.exists()) {
                    for (String src : AntUtils.getSourceDirectories(build)) {
                        sources.add(new File(projectDir, src));
                    }
                }
            }
        }

        if (sources.isEmpty()) {
            sources.add(projectDir);
        }

        for (File source : sources) {
            if (source.exists()) {
                paths.add(JavaCore.newSourceEntry(new Path(source.getAbsolutePath())));
            }
        }
    }

    /**
     * Computes the package fragment roots identified by the given entry.
     * Only works with resolved entry
     * @param resolvedEntry IClasspathEntry
     * @return IPackageFragmentRoot[]
     */
    public IPackageFragmentRoot[] computePackageFragmentRoots(IClasspathEntry resolvedEntry) {
        try {
            return
                    computePackageFragmentRoots(
                            new IClasspathEntry[]{ resolvedEntry },
                            false, // don't retrieve exported roots
                            null /* no reverse map */
                                               );
        } catch (JavaModelException e) {
            return new IPackageFragmentRoot[] {};
        }
    }

    /**
     * Returns the raw classpath for the project, as a list of classpath
     * entries. This corresponds to the exact set of entries which were assigned
     * using <code>setRawClasspath</code>, in particular such a classpath may
     * contain classpath variable and classpath container entries. Classpath
     * variable and classpath container entries can be resolved using the
     * helper method <code>getResolvedClasspath</code>; classpath variable
     * entries also can be resolved individually using
     * <code>JavaCore#getClasspathVariable</code>).
     * <p>
     * Both classpath containers and classpath variables provides a level of
     * indirection that can make the <code>.classpath</code> file stable across
     * workspaces.
     * As an example, classpath variables allow a classpath to no longer refer
     * directly to external JARs located in some user specific location.
     * The classpath can simply refer to some variables defining the proper
     * locations of these external JARs. Similarly, classpath containers
     * allows classpath entries to be computed dynamically by the plug-in that
     * defines that kind of classpath container.
     * </p>
     * <p>
     * Note that in case the project isn't yet opened, the classpath will
     * be read directly from the associated <tt>.classpath</tt> file.
     * </p>
     *
     * @return the raw classpath for the project, as a list of classpath entries
     * @throws org.eclipse.jdt.core.JavaModelException
     *         if this element does not exist or if an
     *         exception occurs while accessing its corresponding resource
     * @see org.eclipse.jdt.core.IClasspathEntry
     */
    public IClasspathEntry[] getRawClasspath() throws JavaModelException {
        JavaModelManager.PerProjectInfo perProjectInfo = getPerProjectInfo();
        IClasspathEntry[] classpath = perProjectInfo.rawClasspath;
        if (classpath != null) return classpath;

        classpath = perProjectInfo.readAndCacheClasspath(this)[0];

        if (classpath == JavaProject.INVALID_CLASSPATH)
            return defaultClasspath();

        return classpath;
    }

    @Override
    public String[] getRequiredProjectNames() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IClasspathEntry[] getResolvedClasspath(boolean ignoreUnresolvedEntry) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasBuildState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasClasspathCycle(IClasspathEntry[] entries) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOnClasspath(IJavaElement element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOnClasspath(IResource resource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IEvaluationContext newEvaluationContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IRegion region, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IRegion region, WorkingCopyOwner owner, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IType type, IRegion region, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ITypeHierarchy newTypeHierarchy(IType type, IRegion region, WorkingCopyOwner owner, IProgressMonitor monitor)
            throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPath readOutputLocation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IClasspathEntry[] readRawClasspath() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOption(String optionName, String optionValue) {
        options.put(optionName, optionValue);
    }

    @Override
    public void setOptions(Map newOptions) {
        options.putAll(newOptions);
    }

    @Override
    public void setOutputLocation(IPath path, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IPath outputLocation, boolean canModifyResources, IProgressMonitor monitor)
            throws JavaModelException {
        setRawClasspath(entries, null, outputLocation, canModifyResources, monitor);
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, boolean canModifyResources, IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IClasspathEntry[] referencedEntries, IPath outputLocation,
                                IProgressMonitor monitor) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IClasspathEntry[] getReferencedClasspathEntries() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IProgressMonitor monitor) throws JavaModelException {
        setRawClasspath(
                entries,
//                getOutputLocation()/*don't change output*/,
                null,
                true/*can change resource (as per API contract)*/,
                monitor);
    }

    @Override
    public void setRawClasspath(IClasspathEntry[] entries, IPath outputLocation, IProgressMonitor monitor) throws JavaModelException {
        setRawClasspath(
                entries,
                outputLocation,
                true/*can change resource (as per API contract)*/,
                monitor);
    }

    protected void setRawClasspath(IClasspathEntry[] newRawClasspath, IClasspathEntry[] referencedEntries, IPath newOutputLocation,
                                   boolean canModifyResources,	IProgressMonitor monitor) throws JavaModelException {

        try {
            if (newRawClasspath == null) //are we already with the default classpath
                newRawClasspath = defaultClasspath();

            SetClasspathOperation op =
                    new SetClasspathOperation(
                            this,
                            newRawClasspath,
                            referencedEntries,
                            newOutputLocation,
                            canModifyResources);
            op.runOperation(monitor);
        } catch (JavaModelException e) {
            JavaModelManager.getJavaModelManager().getDeltaProcessor().flush();
            throw e;
        }
    }
    /**
     * This is a helper method returning the resolved classpath for the project
     * as a list of simple (non-variable, non-container) classpath entries.
     * All classpath variable and classpath container entries in the project's
     * raw classpath will be replaced by the simple classpath entries they
     * resolve to.
     * <p>
     * The resulting resolved classpath is accurate for the given point in time.
     * If the project's raw classpath is later modified, or if classpath
     * variables are changed, the resolved classpath can become out of date.
     * Because of this, hanging on resolved classpath is not recommended.
     * </p>
     * <p>
     * Note that if the resolution creates duplicate entries
     * (i.e. {@link IClasspathEntry entries} which are {@link Object#equals(Object)}),
     * only the first one is added to the resolved classpath.
     * </p>
     *
     * @see IClasspathEntry
     */
    public IClasspathEntry[] getResolvedClasspath() throws JavaModelException {
        PerProjectInfo perProjectInfo = getPerProjectInfo();
        IClasspathEntry[] resolvedClasspath = perProjectInfo.getResolvedClasspath();
        if (resolvedClasspath == null) {
            resolveClasspath(perProjectInfo, false/*don't use previous session values*/, true/*add classpath change*/);
            resolvedClasspath = perProjectInfo.getResolvedClasspath();
            if (resolvedClasspath == null) {
                // another thread reset the resolved classpath, use a temporary PerProjectInfo
                PerProjectInfo temporaryInfo = newTemporaryInfo();
                resolveClasspath(temporaryInfo, false/*don't use previous session values*/, true/*add classpath change*/);
                resolvedClasspath = temporaryInfo.getResolvedClasspath();
            }
        }
        return resolvedClasspath;
    }
    /*
     * Resolve the given perProjectInfo's raw classpath and store the resolved classpath in the perProjectInfo.
     */
    public void resolveClasspath(PerProjectInfo perProjectInfo, boolean usePreviousSession, boolean addClasspathChange) throws JavaModelException {

        JavaModelManager manager = JavaModelManager.getJavaModelManager();
        boolean isClasspathBeingResolved = manager.isClasspathBeingResolved(this);
        try {
            if (!isClasspathBeingResolved) {
                manager.setClasspathBeingResolved(this, true);
            }

            // get raw info inside a synchronized block to ensure that it is consistent
            IClasspathEntry[][] classpath = new IClasspathEntry[2][];
            int timeStamp;
            synchronized (perProjectInfo) {
                classpath[0] = perProjectInfo.rawClasspath;
                classpath[1] = perProjectInfo.referencedEntries;
                // Checking null only for rawClasspath enough
                if (classpath[0] == null)
                    classpath = perProjectInfo.readAndCacheClasspath(this);
                timeStamp = perProjectInfo.rawTimeStamp;
            }

            ResolvedClasspath result = resolveClasspath(classpath[0], classpath[1], usePreviousSession, true/*resolve chained libraries*/);


            // store resolved info along with the raw info to ensure consistency
            perProjectInfo.setResolvedClasspath(result.resolvedClasspath, result.referencedEntries, result.rawReverseMap, result.rootPathToResolvedEntries, usePreviousSession ? PerProjectInfo.NEED_RESOLUTION : result.unresolvedEntryStatus, timeStamp, addClasspathChange);
        } finally {
            if (!isClasspathBeingResolved) {
                manager.setClasspathBeingResolved(this, false);
            }
        }
    }

    public ResolvedClasspath resolveClasspath(IClasspathEntry[] rawClasspath, IClasspathEntry[] referencedEntries, boolean usePreviousSession, boolean resolveChainedLibraries) throws JavaModelException {
        JavaModelManager manager = JavaModelManager.getJavaModelManager();
//        ExternalFoldersManager externalFoldersManager = JavaModelManager.getExternalManager();
        ResolvedClasspath result = new ResolvedClasspath();
        Map knownDrives = new HashMap();

        Map referencedEntriesMap = new HashMap();
        List rawLibrariesPath = new ArrayList();
        LinkedHashSet resolvedEntries = new LinkedHashSet();

        if(resolveChainedLibraries) {
            for (int index = 0; index < rawClasspath.length; index++) {
                IClasspathEntry currentEntry = rawClasspath[index];
                if (currentEntry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
                    rawLibrariesPath.add(ClasspathEntry.resolveDotDot(getProject().getLocation(), currentEntry.getPath()));
                }
            }
            if (referencedEntries != null) {
                // The Set is required to keep the order intact while the referencedEntriesMap (Map)
                // is used to map the referenced entries with path
                LinkedHashSet referencedEntriesSet = new LinkedHashSet();
                for (int index = 0; index < referencedEntries.length; index++) {
                    IPath path = referencedEntries[index].getPath();
                    if (!rawLibrariesPath.contains(path) && referencedEntriesMap.get(path) == null) {
                        referencedEntriesMap.put(path, referencedEntries[index]);
                        referencedEntriesSet.add(referencedEntries[index]);
                    }
                }
                if (referencedEntriesSet.size() > 0) {
                    result.referencedEntries = new IClasspathEntry[referencedEntriesSet.size()];
                    referencedEntriesSet.toArray(result.referencedEntries);
                }
            }
        }

        int length = rawClasspath.length;
        for (int i = 0; i < length; i++) {

            IClasspathEntry rawEntry = rawClasspath[i];
            IClasspathEntry resolvedEntry = rawEntry;

            switch (rawEntry.getEntryKind()){

                case IClasspathEntry.CPE_VARIABLE :
                    try {
                        resolvedEntry = manager.resolveVariableEntry(rawEntry, usePreviousSession);
                    } catch (ClasspathEntry.AssertionFailedException e) {
                        // Catch the assertion failure and set status instead
                        // see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=55992
                        result.unresolvedEntryStatus = new JavaModelStatus(IJavaModelStatusConstants.INVALID_PATH, e.getMessage());
                        break;
                    }
                    if (resolvedEntry == null) {
                        result.unresolvedEntryStatus = new JavaModelStatus(IJavaModelStatusConstants.CP_VARIABLE_PATH_UNBOUND, this, rawEntry.getPath());
                    } else {
                        // If the entry is already present in the rawReversetMap, it means the entry and the chained libraries
                        // have already been processed. So, skip it.
                        if (resolveChainedLibraries && resolvedEntry.getEntryKind() == IClasspathEntry.CPE_LIBRARY
                            && result.rawReverseMap.get(resolvedEntry.getPath()) == null) {
                            // resolve Class-Path: in manifest
                            ClasspathEntry[] extraEntries = ((ClasspathEntry) resolvedEntry).resolvedChainedLibraries();
                            for (int j = 0, length2 = extraEntries.length; j < length2; j++) {
                                if (!rawLibrariesPath.contains(extraEntries[j].getPath())) {
                                    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305037
                                    // referenced entries for variable entries could also be persisted with extra attributes, so addAsChainedEntry = true
                                    addToResult(rawEntry, extraEntries[j], result, resolvedEntries, /*externalFoldersManager*/ referencedEntriesMap, true, knownDrives);
                                }
                            }
                        }
                        addToResult(rawEntry, resolvedEntry, result, resolvedEntries, /*externalFoldersManager,*/ referencedEntriesMap, false, knownDrives);
                    }
                    break;

                case IClasspathEntry.CPE_CONTAINER :
                    IClasspathContainer container = usePreviousSession ? manager.getPreviousSessionContainer(rawEntry.getPath(), this) : JavaCore.getClasspathContainer(rawEntry.getPath(), this);
                    if (container == null){
                        result.unresolvedEntryStatus = new JavaModelStatus(IJavaModelStatusConstants.CP_CONTAINER_PATH_UNBOUND, this, rawEntry.getPath());
                        break;
                    }

                    IClasspathEntry[] containerEntries = container.getClasspathEntries();
                    if (containerEntries == null) {
                        if (JavaModelManager.CP_RESOLVE_VERBOSE || JavaModelManager.CP_RESOLVE_VERBOSE_FAILURE) {
//                            JavaModelManager.getJavaModelManager().verbose_missbehaving_container_null_entries(this, rawEntry.getPath());
                        }
                        break;
                    }

                    // container was bound
                    for (int j = 0, containerLength = containerEntries.length; j < containerLength; j++){
                        ClasspathEntry cEntry = (ClasspathEntry) containerEntries[j];
                        if (cEntry == null) {
                            if (JavaModelManager.CP_RESOLVE_VERBOSE || JavaModelManager.CP_RESOLVE_VERBOSE_FAILURE) {
//                                JavaModelManager.getJavaModelManager().verbose_missbehaving_container(this, rawEntry.getPath(), containerEntries);
                            }
                            break;
                        }
                        // if container is exported or restricted, then its nested entries must in turn be exported  (21749) and/or propagate restrictions
                        cEntry = cEntry.combineWith((ClasspathEntry) rawEntry);

                        if (cEntry.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
                            // resolve ".." in library path
                            cEntry = cEntry.resolvedDotDot(getProject().getLocation());
                            // https://bugs.eclipse.org/bugs/show_bug.cgi?id=313965
                            // Do not resolve if the system attribute is set to false
                            if (resolveChainedLibraries
                                && JavaModelManager.getJavaModelManager().resolveReferencedLibrariesForContainers
                                && result.rawReverseMap.get(cEntry.getPath()) == null) {
                                // resolve Class-Path: in manifest
                                ClasspathEntry[] extraEntries = cEntry.resolvedChainedLibraries();
                                for (int k = 0, length2 = extraEntries.length; k < length2; k++) {
                                    if (!rawLibrariesPath.contains(extraEntries[k].getPath())) {
                                        addToResult(rawEntry, extraEntries[k], result, resolvedEntries, /*externalFoldersManager,*/ referencedEntriesMap, false, knownDrives);
                                    }
                                }
                            }
                        }
                        addToResult(rawEntry, cEntry, result, resolvedEntries, /*externalFoldersManager,*/ referencedEntriesMap, false, knownDrives);
                    }
                    break;

                case IClasspathEntry.CPE_LIBRARY:
                    // resolve ".." in library path
                    resolvedEntry = ((ClasspathEntry) rawEntry).resolvedDotDot(getProject().getLocation());

                    if (resolveChainedLibraries && result.rawReverseMap.get(resolvedEntry.getPath()) == null) {
                        // resolve Class-Path: in manifest
                        ClasspathEntry[] extraEntries = ((ClasspathEntry) resolvedEntry).resolvedChainedLibraries();
                        for (int k = 0, length2 = extraEntries.length; k < length2; k++) {
                            if (!rawLibrariesPath.contains(extraEntries[k].getPath())) {
                                addToResult(rawEntry, extraEntries[k], result, resolvedEntries, /*externalFoldersManager,*/ referencedEntriesMap, true, knownDrives);
                            }
                        }
                    }

                    addToResult(rawEntry, resolvedEntry, result, resolvedEntries, /*externalFoldersManager,*/ referencedEntriesMap, false, knownDrives);
                    break;
                default :
                    addToResult(rawEntry, resolvedEntry, result, resolvedEntries,/* externalFoldersManager,*/ referencedEntriesMap, false, knownDrives);
                    break;
            }
        }
        result.resolvedClasspath = new IClasspathEntry[resolvedEntries.size()];
        resolvedEntries.toArray(result.resolvedClasspath);
        return result;
    }

    /*
 * Returns a PerProjectInfo that doesn't register classpath change
 * and that should be used as a temporary info.
 */
    public PerProjectInfo newTemporaryInfo() {
        return
                new PerProjectInfo(this.project.getProject()) {
                    protected ClasspathChange addClasspathChange() {
                        return null;
                    }
                };
    }

    /**
     * Returns the classpath entry that refers to the given path
     * or <code>null</code> if there is no reference to the path.
     *
     * @param path
     *         IPath
     * @return IClasspathEntry
     * @throws JavaModelException
     */
    public IClasspathEntry getClasspathEntryFor(IPath path) throws JavaModelException {
        getResolvedClasspath(); // force resolution
        PerProjectInfo perProjectInfo = getPerProjectInfo();
        if (perProjectInfo == null)
            return null;
        Map rootPathToResolvedEntries = perProjectInfo.rootPathToResolvedEntries;
        if (rootPathToResolvedEntries == null)
            return null;
        IClasspathEntry classpathEntry = (IClasspathEntry) rootPathToResolvedEntries.get(path);
        if (classpathEntry == null) {
            path = getProject().getWorkspace().getRoot().getLocation().append(path);
            classpathEntry = (IClasspathEntry) rootPathToResolvedEntries.get(path);
        }
        return classpathEntry;
    }


    @Override
    public String encodeClasspathEntry(IClasspathEntry classpathEntry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IJavaElement findElement(IPath path) throws JavaModelException {
        return findElement(path, DefaultWorkingCopyOwner.PRIMARY);
    }

    @Override
    public IJavaElement findElement(IPath path, WorkingCopyOwner owner) throws JavaModelException {
        if (path == null || path.isAbsolute()) {
            throw new JavaModelException(
                    new JavaModelStatus(IJavaModelStatusConstants.INVALID_PATH, path));
        }
        try {

            String extension = path.getFileExtension();
            if (extension == null) {
                String packageName = path.toString().replace(IPath.SEPARATOR, '.');
                return findPackageFragment(packageName);
            } else if (Util.isJavaLikeFileName(path.lastSegment())
                       || extension.equalsIgnoreCase(EXTENSION_class)) {
                IPath packagePath = path.removeLastSegments(1);
                String packageName = packagePath.toString().replace(IPath.SEPARATOR, '.');
                String typeName = path.lastSegment();
                typeName = typeName.substring(0, typeName.length() - extension.length() - 1);
                String qualifiedName = null;
                if (packageName.length() > 0) {
                    qualifiedName = packageName + "." + typeName; //$NON-NLS-1$
                } else {
                    qualifiedName = typeName;
                }

                // lookup type
                NameLookup lookup = newNameLookup(owner);
                NameLookup.Answer answer = lookup.findType(
                        qualifiedName,
                        false,
                        NameLookup.ACCEPT_ALL,
                        true/* consider secondary types */,
                        false/* do NOT wait for indexes */,
                        false/*don't check restrictions*/,
                        null);

                if (answer != null) {
                    return answer.type.getParent();
                } else {
                    return null;
                }
            } else {
                // unsupported extension
                return null;
            }
        } catch (JavaModelException e) {
            if (e.getStatus().getCode()
                == IJavaModelStatusConstants.ELEMENT_DOES_NOT_EXIST) {
                return null;
            } else {
                throw e;
            }
        }
    }

    @Override
    public IJavaElement findElement(String bindingKey, WorkingCopyOwner owner) throws JavaModelException {
        JavaElementFinder elementFinder = new JavaElementFinder(bindingKey, this, owner);
        elementFinder.parse();
        if (elementFinder.exception != null)
            throw elementFinder.exception;
        return elementFinder.element;
    }

    public IJavaElement findPackageFragment(String packageName)
            throws JavaModelException {
        NameLookup lookup = newNameLookup((WorkingCopyOwner)null/*no need to look at working copies for pkgs*/);
        IPackageFragment[] pkgFragments = lookup.findPackageFragments(packageName, false);
        if (pkgFragments == null) {
            return null;

        } else {
            // try to return one that is a child of this project
            for (int i = 0, length = pkgFragments.length; i < length; i++) {

                IPackageFragment pkgFragment = pkgFragments[i];
                if (equals(pkgFragment.getParent().getParent())) {
                    return pkgFragment;
                }
            }
            // default to the first one
            return pkgFragments[0];
        }
    }

    @Override
    public IPackageFragment findPackageFragment(IPath path) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPackageFragmentRoot findPackageFragmentRoot(IPath path) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPackageFragmentRoot[] findPackageFragmentRoots(IClasspathEntry entry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IType findType(String fullyQualifiedName) throws JavaModelException {
        return findType(fullyQualifiedName, DefaultWorkingCopyOwner.PRIMARY);
    }

    @Override
    public IType findType(String fullyQualifiedName, IProgressMonitor progressMonitor) throws JavaModelException {
        return findType(fullyQualifiedName, DefaultWorkingCopyOwner.PRIMARY, progressMonitor);
    }

    /*
 * Internal findType with instanciated name lookup
 */
    IType findType(String fullyQualifiedName, NameLookup lookup, boolean considerSecondaryTypes, IProgressMonitor progressMonitor)
            throws JavaModelException {
        NameLookup.Answer answer = lookup.findType(
                fullyQualifiedName,
                false,
                org.eclipse.jdt.internal.core.NameLookup.ACCEPT_ALL,
                considerSecondaryTypes,
                true, /* wait for indexes (only if consider secondary types)*/
                false/*don't check restrictions*/,
                progressMonitor);
        if (answer == null) {
            // try to find enclosing type
            int lastDot = fullyQualifiedName.lastIndexOf('.');
            if (lastDot == -1) return null;
            IType type = findType(fullyQualifiedName.substring(0, lastDot), lookup, considerSecondaryTypes, progressMonitor);
            if (type != null) {
                type = type.getType(fullyQualifiedName.substring(lastDot + 1));
                if (!type.exists()) {
                    return null;
                }
            }
            return type;
        }
        return answer.type;
    }

    @Override
    public IType findType(String fullyQualifiedName, WorkingCopyOwner owner) throws JavaModelException {
        NameLookup lookup = newNameLookup(owner);
        return findType(fullyQualifiedName, lookup, false, null);
    }

    @Override
    public IType findType(String fullyQualifiedName, WorkingCopyOwner owner, IProgressMonitor progressMonitor) throws JavaModelException {
        NameLookup lookup = newNameLookup(owner);
        return findType(fullyQualifiedName, lookup, true, progressMonitor);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName) throws JavaModelException {
        return findType(packageName, typeQualifiedName, DefaultWorkingCopyOwner.PRIMARY);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName, IProgressMonitor progressMonitor) throws JavaModelException {
        return findType(packageName, typeQualifiedName, DefaultWorkingCopyOwner.PRIMARY, progressMonitor);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName, WorkingCopyOwner owner) throws JavaModelException {
        NameLookup lookup = newNameLookup(owner);
        return findType(
                packageName,
                typeQualifiedName,
                lookup,
                false, // do not consider secondary types
                null);
    }

    @Override
    public IType findType(String packageName, String typeQualifiedName, WorkingCopyOwner owner, IProgressMonitor progressMonitor)
            throws JavaModelException {
        NameLookup lookup = newNameLookup(owner);
        return findType(
                packageName,
                typeQualifiedName,
                lookup,
                true, // consider secondary types
                progressMonitor);
    }

    /*
 * Internal findType with instanciated name lookup
 */
    IType findType(String packageName, String typeQualifiedName, NameLookup lookup, boolean considerSecondaryTypes,
                   IProgressMonitor progressMonitor) throws JavaModelException {
        NameLookup.Answer answer = lookup.findType(
                typeQualifiedName,
                packageName,
                false,
                NameLookup.ACCEPT_ALL,
                considerSecondaryTypes,
                true, // wait for indexes (in case we need to consider secondary types)
                false/*don't check restrictions*/,
                progressMonitor);
        return answer == null ? null : answer.type;
    }

    /*
 * Returns a new name lookup. This name lookup first looks in the working copies of the given owner.
 */
    public NameLookup newNameLookup(WorkingCopyOwner owner) throws JavaModelException {

        JavaModelManager manager = getJavaModelManager();
        ICompilationUnit[] workingCopies = owner == null ? null : manager.getWorkingCopies(owner, true/*add primary WCs*/);
        return newNameLookup(workingCopies);
    }

    @Override
    public IPackageFragmentRoot[] getAllPackageFragmentRoots() throws JavaModelException {
        return getAllPackageFragmentRoots(null /*no reverse map*/);
    }

    public IPackageFragmentRoot[] getAllPackageFragmentRoots(Map rootToResolvedEntries) throws JavaModelException {

        return computePackageFragmentRoots(getResolvedClasspath(), true/*retrieveExportedRoots*/, rootToResolvedEntries);
    }

    /**
     * Returns (local/all) the package fragment roots identified by the given project's classpath.
     * Note: this follows project classpath references to find required project contributions,
     * eliminating duplicates silently.
     * Only works with resolved entries
     *
     * @param resolvedClasspath
     *         IClasspathEntry[]
     * @param retrieveExportedRoots
     *         boolean
     * @return IPackageFragmentRoot[]
     * @throws JavaModelException
     */
    public IPackageFragmentRoot[] computePackageFragmentRoots(
            IClasspathEntry[] resolvedClasspath,
            boolean retrieveExportedRoots,
            Map rootToResolvedEntries) throws JavaModelException {

        ObjectVector accumulatedRoots = new ObjectVector();
        computePackageFragmentRoots(
                resolvedClasspath,
                accumulatedRoots,
                new HashSet(5), // rootIDs
                null, // inside original project
                retrieveExportedRoots,
                rootToResolvedEntries);
        IPackageFragmentRoot[] rootArray = new IPackageFragmentRoot[accumulatedRoots.size()];
        accumulatedRoots.copyInto(rootArray);
        return rootArray;
    }

    /**
     * Returns (local/all) the package fragment roots identified by the given project's classpath.
     * Note: this follows project classpath references to find required project contributions,
     * eliminating duplicates silently.
     * Only works with resolved entries
     *
     * @param resolvedClasspath
     *         IClasspathEntry[]
     * @param accumulatedRoots
     *         ObjectVector
     * @param rootIDs
     *         HashSet
     * @param referringEntry
     *         project entry referring to this CP or null if initial project
     * @param retrieveExportedRoots
     *         boolean
     * @throws JavaModelException
     */
    public void computePackageFragmentRoots(
            IClasspathEntry[] resolvedClasspath,
            ObjectVector accumulatedRoots,
            HashSet rootIDs,
            IClasspathEntry referringEntry,
            boolean retrieveExportedRoots,
            Map rootToResolvedEntries) throws JavaModelException {

        if (referringEntry == null) {
            rootIDs.add(rootID());
        }
        for (int i = 0, length = resolvedClasspath.length; i < length; i++) {
            computePackageFragmentRoots(
                    resolvedClasspath[i],
                    accumulatedRoots,
                    rootIDs,
                    referringEntry,
                    retrieveExportedRoots,
                    rootToResolvedEntries);
        }
    }
    /**
    * Reads the classpath file entries of this project's .classpath file.
            * Returns a two-dimensional array, where the number of elements in the row is fixed to 2.
            * The first element is an array of raw classpath entries, which includes the output entry,
    * and the second element is an array of referenced entries that may have been stored
    * by the client earlier.
            * See {@link IJavaProject#getReferencedClasspathEntries()} for more details.
            * As a side effect, unknown elements are stored in the given map (if not null)
    * Throws exceptions if the file cannot be accessed or is malformed.
            */
    public IClasspathEntry[][] readFileEntriesWithException(Map unknownElements) throws CoreException, IOException, ClasspathEntry.AssertionFailedException {
        IFile rscFile = this.project.getFile(JavaProject.CLASSPATH_FILENAME);
        byte[] bytes;
        if (rscFile.exists()) {
            bytes = Util.getResourceContentsAsByteArray(rscFile);
        } else {
            // when a project is imported, we get a first delta for the addition of the .project, but the .classpath is not accessible
            // so default to using java.io.File
            // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=96258
            URI location = rscFile.getLocationURI();
            if (location == null)
                throw new IOException("Cannot obtain a location URI for " + rscFile); //$NON-NLS-1$
            File file = Util.toLocalFile(location, null/*no progress monitor available*/);
            if (file == null)
                throw new IOException("Unable to fetch file from " + location); //$NON-NLS-1$
            try {
                bytes = org.eclipse.jdt.internal.compiler.util.Util.getFileByteContent(file);
            } catch (IOException e) {
                if (!file.exists())
                    return new IClasspathEntry[][]{defaultClasspath(), ClasspathEntry.NO_ENTRIES};
                throw e;
            }
        }
        if (hasUTF8BOM(bytes)) { // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=240034
            int length = bytes.length- IContentDescription.BOM_UTF_8.length;
            System.arraycopy(bytes, IContentDescription.BOM_UTF_8.length, bytes = new byte[length], 0, length);
        }
        String xmlClasspath;
        try {
            xmlClasspath = new String(bytes, org.eclipse.jdt.internal.compiler.util.Util.UTF_8); // .classpath always encoded with UTF-8
        } catch (UnsupportedEncodingException e) {
            Util.log(e, "Could not read .classpath with UTF-8 encoding"); //$NON-NLS-1$
            // fallback to default
            xmlClasspath = new String(bytes);
        }
        return decodeClasspath(xmlClasspath, unknownElements);
    }

    /**
     * Reads and decode an XML classpath string. Returns a two-dimensional array, where the number of elements in the row is fixed to 2.
     * The first element is an array of raw classpath entries and the second element is an array of referenced entries that may have been stored
     * by the client earlier. See {@link IJavaProject#getReferencedClasspathEntries()} for more details.
     *
     */
    public IClasspathEntry[][] decodeClasspath(String xmlClasspath, Map unknownElements) throws IOException, ClasspathEntry.AssertionFailedException {

        ArrayList paths = new ArrayList();
        IClasspathEntry defaultOutput = null;
        StringReader reader = new StringReader(xmlClasspath);
        Element cpElement;
        try {
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            cpElement = parser.parse(new InputSource(reader)).getDocumentElement();
        } catch (SAXException e) {
            throw new IOException(Messages.file_badFormat);
        } catch (ParserConfigurationException e) {
            throw new IOException(Messages.file_badFormat);
        } finally {
            reader.close();
        }

        if (!cpElement.getNodeName().equalsIgnoreCase("classpath")) { //$NON-NLS-1$
            throw new IOException(Messages.file_badFormat);
        }
        NodeList list = cpElement.getElementsByTagName(ClasspathEntry.TAG_CLASSPATHENTRY);
        int length = list.getLength();

        for (int i = 0; i < length; ++i) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                IClasspathEntry entry = ClasspathEntry.elementDecode((Element)node, this, unknownElements);
                if (entry != null){
                    if (entry.getContentKind() == ClasspathEntry.K_OUTPUT) {
                        defaultOutput = entry; // separate output
                    } else {
                        paths.add(entry);
                    }
                }
            }
        }
        int pathSize = paths.size();
        IClasspathEntry[][] entries = new IClasspathEntry[2][];
        entries[0] = new IClasspathEntry[pathSize + (defaultOutput == null ? 0 : 1)];
        paths.toArray(entries[0]);
        if (defaultOutput != null) entries[0][pathSize] = defaultOutput; // ensure output is last item

        paths.clear();
        list = cpElement.getElementsByTagName(ClasspathEntry.TAG_REFERENCED_ENTRY);
        length = list.getLength();

        for (int i = 0; i < length; ++i) {
            Node node = list.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                IClasspathEntry entry = ClasspathEntry.elementDecode((Element)node, this, unknownElements);
                if (entry != null){
                    paths.add(entry);
                }
            }
        }
        entries[1] = new IClasspathEntry[paths.size()];
        paths.toArray(entries[1]);

        return entries;
    }

    public IClasspathEntry decodeClasspathEntry(String encodedEntry) {

        try {
            if (encodedEntry == null) return null;
            StringReader reader = new StringReader(encodedEntry);
            Element node;

            try {
                DocumentBuilder parser =
                        DocumentBuilderFactory.newInstance().newDocumentBuilder();
                node = parser.parse(new InputSource(reader)).getDocumentElement();
            } catch (SAXException e) {
                return null;
            } catch (ParserConfigurationException e) {
                return null;
            } finally {
                reader.close();
            }

            if (!node.getNodeName().equalsIgnoreCase(ClasspathEntry.TAG_CLASSPATHENTRY)
                || node.getNodeType() != Node.ELEMENT_NODE) {
                return null;
            }
            return ClasspathEntry.elementDecode(node, this, null/*not interested in unknown elements*/);
        } catch (IOException e) {
            // bad format
            return null;
        }
    }


    /**
     * Returns a default class path.
     * This is the root of the project
     */
    protected IClasspathEntry[] defaultClasspath() {

        return new IClasspathEntry[] {
                JavaCore.newSourceEntry(this.project.getFullPath())};
    }

    public int hashCode() {
        return this.project.hashCode();
    }


    private boolean hasUTF8BOM(byte[] bytes) {
        if (bytes.length > IContentDescription.BOM_UTF_8.length) {
            for (int i = 0, length = IContentDescription.BOM_UTF_8.length; i < length; i++) {
                if (IContentDescription.BOM_UTF_8[i] != bytes[i])
                    return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the package fragment roots identified by the given entry. In case it refers to
     * a project, it will follow its classpath so as to find exported roots as well.
     * Only works with resolved entry
     *
     * @param resolvedEntry
     *         IClasspathEntry
     * @param accumulatedRoots
     *         ObjectVector
     * @param rootIDs
     *         HashSet
     * @param referringEntry
     *         the CP entry (project) referring to this entry, or null if initial project
     * @param retrieveExportedRoots
     *         boolean
     * @throws JavaModelException
     */
    public void computePackageFragmentRoots(
            IClasspathEntry resolvedEntry,
            ObjectVector accumulatedRoots,
            HashSet rootIDs,
            IClasspathEntry referringEntry,
            boolean retrieveExportedRoots,
            Map rootToResolvedEntries) throws JavaModelException {

        String rootID = ((ClasspathEntry)resolvedEntry).rootID();
        if (rootIDs.contains(rootID)) return;

        IPath projectPath = this.project.getFullPath();
        IPath entryPath = resolvedEntry.getPath();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IPackageFragmentRoot root = null;

        switch (resolvedEntry.getEntryKind()) {

            // source folder
            case IClasspathEntry.CPE_SOURCE:

                if (projectPath.isPrefixOf(entryPath)) {
                Object target = getTarget(entryPath, true/*check existency*/);
                if (target == null) return;

                if (target instanceof IFolder || target instanceof IProject) {
                    root = getPackageFragmentRoot((IResource)target);
                }
                }
                break;

            // internal/external JAR or folder
            case IClasspathEntry.CPE_LIBRARY:
                if (referringEntry != null && !resolvedEntry.isExported())
                    return;
                Object target = getTarget(entryPath, true/*check existency*/);
                if (target == null)
                    return;

                if (target instanceof IResource){
//                    // internal target
                    root = getPackageFragmentRoot((IResource) target, entryPath);
                } else
                if (target instanceof File) {
                    // external target
                    if (isFile(target)) {
                        root = new JarPackageFragmentRoot(entryPath, this);
                    } else if (((File)target).isDirectory()) {
//                        root = getPackageFragmentRoot((File)target, entryPath);
//                        root = new ExternalPackageFragmentRoot(entryPath, this);
                        throw new UnsupportedOperationException();
                    }
                }
                break;

            // recurse into required project
            case IClasspathEntry.CPE_PROJECT:

                if (!retrieveExportedRoots) return;
                if (referringEntry != null && !resolvedEntry.isExported()) return;
                IResource member = workspaceRoot.findMember(entryPath);
                if (member != null && member.getType() == IResource.PROJECT) {// double check if bound to project (23977)
                    IProject requiredProjectRsc = (IProject)member;
                    if (JavaProject.hasJavaNature(requiredProjectRsc)) { // special builder binary output
                        rootIDs.add(rootID);
                        JavaProject requiredProject = (JavaProject)JavaCore.create(requiredProjectRsc);
                        requiredProject.computePackageFragmentRoots(
                                requiredProject.getResolvedClasspath(),
                                accumulatedRoots,
                                rootIDs,
                                rootToResolvedEntries == null ? resolvedEntry
                                                              : ((ClasspathEntry)resolvedEntry)
                                        .combineWith((ClasspathEntry)referringEntry),
                                // only combine if need to build the reverse map
                                retrieveExportedRoots,
                                rootToResolvedEntries);
                    }
                    break;
                }
        }
        if (root != null) {
            accumulatedRoots.add(root);
            rootIDs.add(rootID);
            if (rootToResolvedEntries != null) rootToResolvedEntries
                    .put(root, ((ClasspathEntry)resolvedEntry).combineWith((ClasspathEntry)referringEntry));
        }
    }

    /**
     * @see IJavaProject
     */
    public IPackageFragmentRoot getPackageFragmentRoot(IResource resource) {
        return getPackageFragmentRoot(resource, null/*no entry path*/);
    }

    private IPackageFragmentRoot getPackageFragmentRoot(IResource resource, IPath entryPath) {
        switch (resource.getType()) {
            case IResource.FILE:
                return new JarPackageFragmentRoot(resource, this);
            case IResource.FOLDER:
//                if (ExternalFoldersManager.isInternalPathForExternalFolder(resource.getFullPath()))
//                    return new ExternalPackageFragmentRoot(resource, entryPath, this);
                return new PackageFragmentRoot(resource, this);
            case IResource.PROJECT:
                return new PackageFragmentRoot(resource, this);
            default:
                return null;
        }
    }

    /**
     * Answers an ID which is used to distinguish project/entries during package
     * fragment root computations
     *
     * @return String
     */
    public String rootID() {
        return "[PRJ]" + this.project.getFullPath(); //$NON-NLS-1$
    }

    @Override
    public Object[] getNonJavaResources() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getOption(String optionName, boolean inheritJavaCoreOptions) {
        return options.get(optionName);
    }

    public Map getOptions(boolean b) {
        return options;
    }

    @Override
    public IPath getOutputLocation() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPackageFragmentRoot getPackageFragmentRoot(String externalLibraryPath) {
        return new JarPackageFragmentRoot(new Path(externalLibraryPath), this);
    }

    @Override
    public IPackageFragmentRoot[] getPackageFragmentRoots() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPackageFragmentRoot[] getPackageFragmentRoots(IClasspathEntry entry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPackageFragment[] getPackageFragments() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IProject getProject() {
        return project;
    }

    @Override
    protected boolean buildStructure(OpenableElementInfo info, IProgressMonitor pm, Map newElements, IResource underlyingResource)
            throws JavaModelException {
        // cannot refresh cp markers on opening (emulate cp check on startup) since can create deadlocks (see bug 37274)
        IClasspathEntry[] resolvedClasspath = getResolvedClasspath();

        // compute the pkg fragment roots
        info.setChildren(computePackageFragmentRoots(resolvedClasspath, true, null /*no reverse map*/));
//        JavaModelManager.getIndexManager().indexAll(project);
        return true;
    }

    /*
	 * Returns whether the given resource is accessible through the children or the non-Java resources of this project.
	 * Returns true if the resource is not in the project.
	 * Assumes that the resource is a folder or a file.
	 */
    public boolean contains(IResource resource) {
        return true;
    }

//    @Override
//    public IJavaElement getAncestor(int ancestorType) {
//        return null;
//    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public String getAttachedJavadoc(IProgressMonitor monitor) throws JavaModelException {
        return null;
    }

    @Override
    public IResource getCorrespondingResource() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getElementName() {
        return project.getName();
    }


    /*
     * no path canonicalization
     */
    public IPackageFragmentRoot getPackageFragmentRoot0(IPath externalLibraryPath) {
//        IFolder linkedFolder = JavaModelManager.getExternalManager().getFolder(externalLibraryPath);
//        if (linkedFolder != null)
//            return new ExternalPackageFragmentRoot(linkedFolder, externalLibraryPath, this);
        return new JarPackageFragmentRoot(externalLibraryPath, this);
    }

    /**
     * @param path IPath
     * @return A handle to the package fragment root identified by the given path.
     * This method is handle-only and the element may or may not exist. Returns
     * <code>null</code> if unable to generate a handle from the path (for example,
     * an absolute path that has less than 1 segment. The path may be relative or
     * absolute.
     */
    public IPackageFragmentRoot getPackageFragmentRoot(IPath path) {
        if (!path.isAbsolute()) {
            path = getPath().append(path);
        }
        int segmentCount = path.segmentCount();
        if (segmentCount == 0) {
            return null;
        }
        if (path.getDevice() != null || JavaModel.getExternalTarget(path, true/*check existence*/) != null) {
            // external path
            return getPackageFragmentRoot0(path);
        }
        IWorkspaceRoot workspaceRoot = this.project.getWorkspace().getRoot();
        IResource resource = workspaceRoot.findMember(path);
        if (resource == null) {
            // resource doesn't exist in workspace
            if (path.getFileExtension() != null) {
                if (!workspaceRoot.getProject(path.segment(0)).exists()) {
                    // assume it is an external ZIP archive
                    return getPackageFragmentRoot0(path);
                } else {
                    // assume it is an internal ZIP archive
                    resource = workspaceRoot.getFile(path);
                }
            } else if (segmentCount == 1) {
                // assume it is a project
                String projectName = path.segment(0);
                if (getElementName().equals(projectName)) { // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=75814
                    // default root
                    resource = this.project;
                } else {
                    // lib being another project
                    resource = workspaceRoot.getProject(projectName);
                }
            } else {
                // assume it is an internal folder
                resource = workspaceRoot.getFolder(path);
            }
        }
        return getPackageFragmentRoot(resource);
    }

    @Override
    public IJavaElement getHandleFromMemento(String token, MementoTokenizer memento, WorkingCopyOwner owner) {
        switch (token.charAt(0)) {
            case JEM_PACKAGEFRAGMENTROOT:
                String rootPath = IPackageFragmentRoot.DEFAULT_PACKAGEROOT_PATH;
                token = null;
                while (memento.hasMoreTokens()) {
                    token = memento.nextToken();
                    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=331821
                    if (token == MementoTokenizer.PACKAGEFRAGMENT || token == MementoTokenizer.COUNT) {
                        break;
                    }
                    rootPath += token;
                }
                JavaElement root = (JavaElement)getPackageFragmentRoot(new Path(rootPath));
                if (token != null && token.charAt(0) == JEM_PACKAGEFRAGMENT) {
                    return root.getHandleFromMemento(token, memento, owner);
                } else {
                    return root.getHandleFromMemento(memento, owner);
                }
        }
        return null;
    }

    @Override
    public int getElementType() {
        return IJavaElement.JAVA_PROJECT;
    }

    @Override
    protected char getHandleMementoDelimiter() {
        return JEM_JAVAPROJECT;
    }

    public IPath getPath() {
        return project.getFullPath();
    }

    public String getProjectPath(){
        return projectPath;
    }

    @Override
    public IJavaElement getPrimaryElement() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected IResource resource(PackageFragmentRoot root) {
        return project;
    }

    @Override
    public ISchedulingRule getSchedulingRule() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IResource getUnderlyingResource() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isStructureKnown() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getAdapter(Class aClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws JavaModelException {
        super.close();
    }

    @Override
    public String findRecommendedLineSeparator() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public IBuffer getBuffer() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasUnsavedChanges() throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConsistent() {
        throw new UnsupportedOperationException();
    }

//    @Override
//    public boolean isOpen() {
//        throw new UnsupportedOperationException();
//    }

    @Override
    public void makeConsistent(IProgressMonitor progress) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void open(IProgressMonitor progress) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void save(IProgressMonitor progress, boolean force) throws JavaModelException {
        throw new UnsupportedOperationException();
    }

    /**
     * The path is known to match a source/library folder entry.
     *
     * @param path
     *         IPath
     * @return IPackageFragmentRoot
     */
    public IPackageFragmentRoot getFolderPackageFragmentRoot(IPath path) {
        if (path.segmentCount() == 1) { // default project root
            return getPackageFragmentRoot();
        }
        return getPackageFragmentRoot(this.project.getWorkspace().getRoot().getFolder(path));
    }

    public JavaProjectElementInfo.ProjectCache getProjectCache() throws JavaModelException {
        return ((JavaProjectElementInfo)getElementInfo()).getProjectCache(this);
    }

    /**
     * Returns a new element info for this element.
     */
    protected Object createElementInfo() {
        return new JavaProjectElementInfo();
    }

    @Override
    protected IStatus validateExistence(IResource underlyingResource) {
        return JavaModelStatus.VERIFIED_OK;
    }

    /**
     * @see JavaElement#getHandleMemento(StringBuffer)
     */
    protected void getHandleMemento(StringBuffer buff) {
//        buff.append(getElementName());
    }

    /*
     * Resets this project's caches
     */
    public void resetCaches() {
        JavaProjectElementInfo
                info = (JavaProjectElementInfo) getJavaModelManager().peekAtInfo(this);
        if (info != null){
            info.resetCaches();
        }
    }

    private void addToResult(IClasspathEntry rawEntry, IClasspathEntry resolvedEntry, ResolvedClasspath result,
                             LinkedHashSet resolvedEntries,
                             Map oldChainedEntriesMap, boolean addAsChainedEntry, Map knownDrives) {

        IPath resolvedPath;
        // If it's already been resolved, do not add to resolvedEntries
        if (result.rawReverseMap.get(resolvedPath = resolvedEntry.getPath()) == null) {
            result.rawReverseMap.put(resolvedPath, rawEntry);
            result.rootPathToResolvedEntries.put(resolvedPath, resolvedEntry);
            resolvedEntries.add(resolvedEntry);
            if (addAsChainedEntry) {
                IClasspathEntry chainedEntry = null;
                chainedEntry = (ClasspathEntry) oldChainedEntriesMap.get(resolvedPath);
                if (chainedEntry != null) {
                    // This is required to keep the attributes if any added by the user in
                    // the previous session such as source attachment path etc.
                    copyFromOldChainedEntry((ClasspathEntry) resolvedEntry, (ClasspathEntry) chainedEntry);
                }
            }
        }
//        if (resolvedEntry.getEntryKind() == IClasspathEntry.CPE_LIBRARY &&/* ExternalFoldersManager.isExternalFolderPath(resolvedPath)*/ false) {
//            externalFoldersManager.addFolder(resolvedPath, true/*scheduleForCreation*/); // no-op if not an external folder or if already registered
//        }
        // https://bugs.eclipse.org/bugs/show_bug.cgi?id=336046
//        // The source attachment path could be external too and in which case, must be added.
//        IPath sourcePath = resolvedEntry.getSourceAttachmentPath();
//        if (sourcePath != null && driveExists(sourcePath, knownDrives) && ExternalFoldersManager.isExternalFolderPath(sourcePath)) {
//            externalFoldersManager.addFolder(sourcePath, true);
//        }
    }

    private void copyFromOldChainedEntry(ClasspathEntry resolvedEntry, ClasspathEntry chainedEntry) {
        IPath path = chainedEntry.getSourceAttachmentPath();
        if ( path != null) {
            resolvedEntry.sourceAttachmentPath = path;
        }
        path = chainedEntry.getSourceAttachmentRootPath();
        if (path != null) {
            resolvedEntry.sourceAttachmentRootPath = path;
        }
        IClasspathAttribute[] attributes = chainedEntry.getExtraAttributes();
        if (attributes != null) {
            resolvedEntry.extraAttributes = attributes;
        }
    }


    /**
     * Convenience method that returns the specific type of info for a Java project.
     */
    protected JavaProjectElementInfo getJavaProjectElementInfo()
            throws JavaModelException {

        return (JavaProjectElementInfo)getElementInfo();
    }

    public NameLookup newNameLookup(ICompilationUnit[] workingCopies) throws JavaModelException {
        return getJavaProjectElementInfo().newNameLookup(this, workingCopies);
    }

    public SearchableEnvironment newSearchableNameEnvironment(ICompilationUnit[] workingCopies) throws JavaModelException {
        return new SearchableEnvironment(this, workingCopies);
    }

    /**
     * Returns true if this handle represents the same Java project
     * as the given handle. Two handles represent the same
     * project if they are identical or if they represent a project with
     * the same underlying resource and occurrence counts.
     *
     * @see JavaElement#equals(Object)
     */
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof JavaProject))
            return false;

        JavaProject other = (JavaProject) o;
        return this.project.equals(other.getProject());
    }
    public PerProjectInfo getPerProjectInfo() throws JavaModelException {
        return getJavaModelManager().getPerProjectInfoCheckExistence(this.project);
    }
    /*
     * Returns a new search name environment for this project. This name environment first looks in the working copies
     * of the given owner.
     */
    public SearchableEnvironment newSearchableNameEnvironment(WorkingCopyOwner owner) throws JavaModelException {
        return new SearchableEnvironment(this, owner);
    }

    public String getWsId() {
        return wsId;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    /**
     * This is a helper method returning the expanded classpath for the project, as a list of classpath entries,
     * where all classpath variable entries have been resolved and substituted with their final target entries.
     * All project exports have been appended to project entries.
     * @return IClasspathEntry[]
     * @throws JavaModelException
     */
    public IClasspathEntry[] getExpandedClasspath()	throws JavaModelException {

//        ObjectVector accumulatedEntries = new ObjectVector();
//        computeExpandedClasspath(null, new HashSet(5), accumulatedEntries);
//
//        IClasspathEntry[] expandedPath = new IClasspathEntry[accumulatedEntries.size()];
//        accumulatedEntries.copyInto(expandedPath);
//
//        return expandedPath;
        return getResolvedClasspath();
    }

    public synchronized void creteNewNameEnvironment() {
        try {
            nameEnvironment = new SearchableEnvironment(this, (ICompilationUnit[])null);
        } catch (JavaModelException e) {
            LOG.error("Can't create SearchableEnvironment", e);
        }
    }

    public static class ResolvedClasspath {
        IClasspathEntry[] resolvedClasspath;
        IJavaModelStatus                unresolvedEntryStatus     = JavaModelStatus.VERIFIED_OK;
        HashMap<IPath, IClasspathEntry> rawReverseMap             = new HashMap<>();
        Map<IPath, IClasspathEntry>     rootPathToResolvedEntries = new HashMap<>();
        IClasspathEntry[]               referencedEntries         = null;
    }
}

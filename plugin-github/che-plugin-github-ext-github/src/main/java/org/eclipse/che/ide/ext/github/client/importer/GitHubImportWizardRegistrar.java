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
package org.eclipse.che.ide.ext.github.client.importer;

import org.eclipse.che.api.project.shared.dto.ImportProject;
import org.eclipse.che.ide.api.project.wizard.ImportWizardRegistrar;
import org.eclipse.che.ide.api.wizard.WizardPage;
import org.eclipse.che.ide.ext.github.client.importer.page.GithubImporterPagePresenter;
import com.google.inject.Inject;
import com.google.inject.Provider;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides information for registering GitHub importer into import wizard.
 *
 * @author Artem Zatsarynnyy
 */
public class GitHubImportWizardRegistrar implements ImportWizardRegistrar {
    private final static String ID = "github";
    private final List<Provider<? extends WizardPage<ImportProject>>> wizardPages;

    @Inject
    public GitHubImportWizardRegistrar(Provider<GithubImporterPagePresenter> provider) {
        wizardPages = new ArrayList<>();
        wizardPages.add(provider);
    }

    @Nonnull
    public String getImporterId() {
        return ID;
    }

    @Nonnull
    public List<Provider<? extends WizardPage<ImportProject>>> getWizardPages() {
        return wizardPages;
    }
}

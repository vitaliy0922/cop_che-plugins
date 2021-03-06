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
package org.eclipse.che.ide.ext.ssh.client;

import org.eclipse.che.ide.ext.ssh.dto.KeyItem;
import org.eclipse.che.ide.ext.ssh.dto.PublicKey;
import org.eclipse.che.ide.rest.AsyncRequestCallback;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

/**
 * The client service for working with ssh key.
 *
 * @author <a href="mailto:tnemov@gmail.com">Evgen Vidolob</a>
 */
public interface SshKeyService {

    /**
     * Get the list of SSH keys tokenProvider.
     *
     * @return {@link org.eclipse.che.ide.collections.StringMap}
     */
    Map<String, SshKeyProvider> getSshKeyProviders();

    /**
     * Register SSH key provider
     *
     * @param host
     *         host, for which to provide keys
     * @param sshKeyProvider
     *         keys provider
     */
    void registerSshKeyProvider(@Nonnull String host, @Nonnull SshKeyProvider sshKeyProvider);

    /**
     * Receive all ssh key, stored on server
     *
     * @param callback
     */
    void getAllKeys(@Nonnull AsyncRequestCallback<List<KeyItem>> callback);

    /**
     * Generate new ssh key pare
     *
     * @param host
     *         for ssh key
     * @param callback
     */
    void generateKey(@Nonnull String host, @Nonnull AsyncRequestCallback<Void> callback);

    /**
     * Get public ssh key
     *
     * @param keyItem
     *         to get public key
     * @param callback
     */
    void getPublicKey(@Nonnull KeyItem keyItem, @Nonnull AsyncRequestCallback<PublicKey> callback);

    /**
     * Delete ssh key
     *
     * @param keyItem
     *         to delete
     * @param callback
     */
    void deleteKey(@Nonnull KeyItem keyItem, @Nonnull AsyncRequestCallback<Void> callback);
}
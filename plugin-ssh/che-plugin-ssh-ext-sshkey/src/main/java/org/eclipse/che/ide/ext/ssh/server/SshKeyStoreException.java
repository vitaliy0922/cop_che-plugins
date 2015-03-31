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
package org.eclipse.che.ide.ext.ssh.server;

/** @author andrew00x */
@SuppressWarnings("serial")
public final class SshKeyStoreException extends Exception {
    /**
     * @param message
     *         the detail message
     * @param cause
     *         the cause
     */
    public SshKeyStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message
     *         the detail message
     */
    public SshKeyStoreException(String message) {
        super(message);
    }
}

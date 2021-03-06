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
package org.eclipse.che.ide.ext.java.jdi.server;

/**
 * Element of array in debuggee JVM.
 *
 * @author andrew00x
 */
public interface JdiArrayElement extends JdiVariable {
    /**
     * Get index of this element of array.
     *
     * @return index of this array element
     * @throws DebuggerException
     *         if an error occurs
     */
    int getIndex() throws DebuggerException;
}

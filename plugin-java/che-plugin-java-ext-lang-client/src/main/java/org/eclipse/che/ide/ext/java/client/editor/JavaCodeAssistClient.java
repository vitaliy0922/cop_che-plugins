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
package org.eclipse.che.ide.ext.java.client.editor;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.che.ide.collections.Array;
import org.eclipse.che.ide.collections.Collections;
import org.eclipse.che.ide.ext.java.shared.dto.Problem;
import org.eclipse.che.ide.ext.java.shared.dto.ProposalApplyResult;
import org.eclipse.che.ide.ext.java.shared.dto.Proposals;
import org.eclipse.che.ide.extension.machine.client.machine.MachineManager;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.rest.AsyncRequestFactory;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.rest.Unmarshallable;

import java.util.List;

/**
 * @author Evgen Vidolob
 */
@Singleton
public class JavaCodeAssistClient {

    private final String                 machineExtPath;
    private       DtoUnmarshallerFactory unmarshallerFactory;
    private final MachineManager         machineManager;
    private final AsyncRequestFactory    asyncRequestFactory;

    @Inject
    public JavaCodeAssistClient(@Named("cheExtensionPath") String machineExtPath,
                                DtoUnmarshallerFactory unmarshallerFactory,
                                MachineManager machineManager,
                                AsyncRequestFactory asyncRequestFactory) {
        this.machineExtPath = machineExtPath;
        this.unmarshallerFactory = unmarshallerFactory;
        this.machineManager = machineManager;
        this.asyncRequestFactory = asyncRequestFactory;
    }

    public void computeProposals(String projectPath, String fqn, int offset, String contents, AsyncRequestCallback<Proposals> callback) {
        String url =
                machineExtPath + "/jdt/code-assist/compute/completion" + "/?projectpath=" +
                projectPath + "&fqn=" + fqn + "&offset=" + offset + "&machineId=" + machineManager.getDeveloperMachineId();
        asyncRequestFactory.createPostRequest(url, null).data(contents).send(callback);
    }

    public void computeAssistProposals(String projectPath, String fqn, int offset, List<Problem> problems,
                                       AsyncRequestCallback<Proposals> callback) {
        String url = machineExtPath + "/jdt/code-assist/compute/assist" + "/?projectpath=" +
                     projectPath + "&fqn=" + fqn + "&offset=" + offset + "&machineId=" + machineManager.getDeveloperMachineId();
        Array<Problem> prob = Collections.createArray(problems);
        asyncRequestFactory.createPostRequest(url, prob).send(callback);
    }


    public void applyProposal(String sessionId, int index, boolean insert, final AsyncCallback<ProposalApplyResult> callback) {
        String url =
                machineExtPath + "/jdt/code-assist/apply/completion/?sessionid=" + sessionId + "&index=" + index + "&insert=" + insert +
                "&machineId=" + machineManager.getDeveloperMachineId();
        Unmarshallable<ProposalApplyResult> unmarshaller =
                unmarshallerFactory.newUnmarshaller(ProposalApplyResult.class);
        asyncRequestFactory.createGetRequest(url).send(new AsyncRequestCallback<ProposalApplyResult>(unmarshaller) {
            @Override
            protected void onSuccess(ProposalApplyResult proposalApplyResult) {
                callback.onSuccess(proposalApplyResult);
            }

            @Override
            protected void onFailure(Throwable throwable) {
                callback.onFailure(throwable);
            }
        });
    }

    public String getProposalDocUrl(int id, String sessionId) {
        return machineExtPath + "/jdt/code-assist/compute/info?sessionid=" + sessionId + "&index=" + id + "&machineId=" +
               machineManager.getDeveloperMachineId();
    }
}

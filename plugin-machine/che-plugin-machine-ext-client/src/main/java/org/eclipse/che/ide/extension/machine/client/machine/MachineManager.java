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
package org.eclipse.che.ide.extension.machine.client.machine;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.machine.gwt.client.MachineServiceClient;
import org.eclipse.che.api.machine.shared.dto.MachineDescriptor;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.api.promises.client.callback.AsyncPromiseHelper;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.extension.machine.client.MachineLocalizationConstant;
import org.eclipse.che.ide.extension.machine.client.OutputMessageUnmarshaller;
import org.eclipse.che.ide.extension.machine.client.inject.factories.EntityFactory;
import org.eclipse.che.ide.extension.machine.client.machine.MachineStatusNotifier.RunningListener;
import org.eclipse.che.ide.extension.machine.client.machine.console.MachineConsolePresenter;
import org.eclipse.che.ide.extension.machine.client.machine.extserver.ExtServerStateController;
import org.eclipse.che.ide.extension.machine.client.util.RecipeProvider;
import org.eclipse.che.ide.loader.OperationInfo;
import org.eclipse.che.ide.loader.OperationInfo.Status;
import org.eclipse.che.ide.loader.LoaderPresenter;
import org.eclipse.che.ide.ui.dialogs.DialogFactory;
import org.eclipse.che.ide.util.UUID;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;

import javax.annotation.Nonnull;

import static com.google.gwt.http.client.RequestBuilder.GET;
import static org.eclipse.che.ide.extension.machine.client.machine.MachineManager.MachineOperationType.DESTROY;
import static org.eclipse.che.ide.extension.machine.client.machine.MachineManager.MachineOperationType.RESTART;
import static org.eclipse.che.ide.extension.machine.client.machine.MachineManager.MachineOperationType.START;

/**
 * Manager for machine operations.
 *
 * @author Artem Zatsarynnyy
 */
@Singleton
public class MachineManager {

    private final LoaderPresenter             loader;
    private       MachineLocalizationConstant localizedConstants;
    private final ExtServerStateController extServerStateController;
    private final MachineServiceClient    machineServiceClient;
    private final MessageBus              messageBus;
    private final MachineConsolePresenter machineConsolePresenter;
    private final NotificationManager     notificationManager;
    private final MachineStatusNotifier   machineStatusNotifier;
    private final DialogFactory           dialogFactory;
    private final RecipeProvider          recipeProvider;
    private final EntityFactory           entityFactory;
    private final AppContext              appContext;

    private Machine devMachine;

    @Inject
    public MachineManager(LoaderPresenter loaderPresenter,
                          MachineLocalizationConstant localizedConstants,
                          ExtServerStateController extServerStateController,
                          MachineServiceClient machineServiceClient,
                          MessageBus messageBus,
                          MachineConsolePresenter machineConsolePresenter,
                          NotificationManager notificationManager,
                          MachineStatusNotifier machineStatusNotifier,
                          DialogFactory dialogFactory,
                          RecipeProvider recipeProvider,
                          EntityFactory entityFactory,
                          AppContext appContext) {
        this.loader = loaderPresenter;
        this.localizedConstants = localizedConstants;
        this.extServerStateController = extServerStateController;
        this.machineServiceClient = machineServiceClient;
        this.messageBus = messageBus;
        this.machineConsolePresenter = machineConsolePresenter;
        this.notificationManager = notificationManager;
        this.machineStatusNotifier = machineStatusNotifier;
        this.dialogFactory = dialogFactory;
        this.recipeProvider = recipeProvider;
        this.entityFactory = entityFactory;
        this.appContext = appContext;
    }

    public void restartMachine(@Nonnull final Machine machine) {
        machineServiceClient.destroyMachine(machine.getId()).then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                final String recipeUrl = recipeProvider.getRecipeUrl();
                final String displayName = machine.getDisplayName();
                final boolean isWSBound = machine.isWorkspaceBound();

                startMachine(recipeUrl, displayName, isWSBound, RESTART);
            }
        });
    }

    /** Start new machine. */
    public void startMachine(String recipeURL, @Nonnull String displayName) {
        startMachine(recipeURL, displayName, false, START);
    }

    /** Start new machine as dev-machine (bind workspace to running machine). */
    public void startDevMachine(String recipeURL, @Nonnull String displayName) {
        startMachine(recipeURL, displayName, true, START);
    }

    private void startMachine(@Nonnull final String recipeURL,
                              @Nonnull final String displayName,
                              final boolean bindWorkspace,
                              @Nonnull final MachineOperationType operationType) {
        final OperationInfo operationInfo = new OperationInfo(localizedConstants.startingMachine(displayName), Status.IN_PROGRESS, loader);
        loader.show(operationInfo);
        downloadRecipe(recipeURL).thenPromise(new Function<String, Promise<MachineDescriptor>>() {
            @Override
            public Promise<MachineDescriptor> apply(String recipeScript) throws FunctionException {
                final String outputChannel = "machine:output:" + UUID.uuid();
                subscribeToOutput(outputChannel);

                return machineServiceClient.createMachineFromRecipe("docker",
                                                                    "Dockerfile",
                                                                    recipeScript,
                                                                    displayName,
                                                                    bindWorkspace,
                                                                    outputChannel);
            }
        }).then(new Operation<MachineDescriptor>() {
            @Override
            public void apply(final MachineDescriptor machineDescriptor) throws OperationException {
                RunningListener runningListener = null;

                if (bindWorkspace) {
                    runningListener = new RunningListener() {
                        @Override
                        public void onRunning() {
                            operationInfo.setStatus(Status.FINISHED);
                            machineRunning(machineDescriptor.getId());
                        }
                    };
                }

                final Machine machine = entityFactory.createMachine(machineDescriptor);
                machineStatusNotifier.trackMachine(machine, runningListener, operationType);
            }
        });
    }

    private void machineRunning(final String machineId) {
        machineServiceClient.getMachine(machineId).then(new Operation<MachineDescriptor>() {
            @Override
            public void apply(MachineDescriptor machineDescriptor) throws OperationException {
                appContext.setDevMachineId(machineId);
                devMachine = entityFactory.createMachine(machineDescriptor);
                extServerStateController
                        .initialize(devMachine.getWsServerExtensionsUrl() + "/" + appContext.getWorkspace().getId());
            }
        });
    }

    private Promise<String> downloadRecipe(String recipeURL) {
        final RequestBuilder builder = new RequestBuilder(GET, recipeURL);
        return AsyncPromiseHelper.createFromAsyncRequest(new AsyncPromiseHelper.RequestCall<String>() {
            @Override
            public void makeCall(final AsyncCallback<String> callback) {
                try {
                    builder.sendRequest("", new RequestCallback() {
                        public void onResponseReceived(Request request, Response response) {
                            final String text = response.getText();
                            if (text.isEmpty()) {
                                callback.onFailure(new Exception("Unable to fetch recipe"));
                            } else {
                                callback.onSuccess(text);
                            }
                        }

                        public void onError(Request request, Throwable exception) {
                            callback.onFailure(exception);
                        }
                    });
                } catch (RequestException e) {
                    callback.onFailure(e);
                }
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                dialogFactory.createMessageDialog("", arg.toString(), null).show();
            }
        });
    }

    public void destroyMachine(final Machine machine) {
        machineServiceClient.destroyMachine(machine.getId()).then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                machineStatusNotifier.trackMachine(machine, DESTROY);

                final String devMachineId = appContext.getDevMachineId();
                if (devMachineId != null && machine.getId().equals(devMachineId)) {
                    appContext.setDevMachineId(null);
                }
            }
        });
    }

    private void subscribeToOutput(final String channel) {
        try {
            messageBus.subscribe(
                    channel,
                    new SubscriptionHandler<String>(new OutputMessageUnmarshaller()) {
                        @Override
                        protected void onMessageReceived(String result) {
                            machineConsolePresenter.print(result);
                            loader.printToDetails(new OperationInfo(result, Status.FINISHED));
                        }

                        @Override
                        protected void onErrorReceived(Throwable exception) {
                            notificationManager.showError(exception.getMessage());
                        }
                    });
        } catch (WebSocketException e) {
            Log.error(MachineManager.class, e);
            notificationManager.showError(e.getMessage());
        }
    }

    enum MachineOperationType {
        START, RESTART, DESTROY
    }
}

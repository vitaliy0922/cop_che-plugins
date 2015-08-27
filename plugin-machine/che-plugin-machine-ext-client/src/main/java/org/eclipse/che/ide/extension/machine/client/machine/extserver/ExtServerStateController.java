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
package org.eclipse.che.ide.extension.machine.client.machine.extserver;

import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.extension.machine.client.MachineLocalizationConstant;
import org.eclipse.che.ide.extension.machine.client.machine.events.ExtServerStateEvent;
import org.eclipse.che.ide.loader.OperationInfo;
import org.eclipse.che.ide.loader.OperationInfo.Status;
import org.eclipse.che.ide.loader.LoaderPresenter;
import org.eclipse.che.ide.websocket.WebSocket;
import org.eclipse.che.ide.websocket.events.ConnectionClosedHandler;
import org.eclipse.che.ide.websocket.events.ConnectionErrorHandler;
import org.eclipse.che.ide.websocket.events.ConnectionOpenedHandler;
import org.eclipse.che.ide.websocket.events.WebSocketClosedEvent;

/**
 * @author Roman Nikitenko
 */
public class ExtServerStateController implements ConnectionOpenedHandler, ConnectionClosedHandler, ConnectionErrorHandler {

    private final Timer                       retryConnectionTimer;
    private final EventBus                    eventBus;
    private       MachineLocalizationConstant localizedConstants;
    private final NotificationManager notificationManager;
    private LoaderPresenter loader;

    private ExtServerState state;
    private String         wsUrl;
    private int            countRetry;
    private OperationInfo  loadInfo;


    @Inject
    public ExtServerStateController(EventBus eventBus,
                                    MachineLocalizationConstant localizedConstants,
                                    NotificationManager notificationManager,
                                    LoaderPresenter loader) {
        this.eventBus = eventBus;
        this.localizedConstants = localizedConstants;
        this.notificationManager = notificationManager;
        this.loader = loader;

        retryConnectionTimer = new Timer() {
            @Override
            public void run() {
                connect();
                countRetry--;
            }
        };
    }

    public void initialize(String wsUrl) {
        this.wsUrl = wsUrl;
        this.countRetry = 5;
        this.state = ExtServerState.STOPPED;
        loadInfo = new OperationInfo(localizedConstants.startingOperation("extension server"), Status.IN_PROGRESS, loader);
        loader.print(loadInfo);
        connect();
    }

    @Override
    public void onClose(WebSocketClosedEvent event) {
        if (state == ExtServerState.STARTED) {
            state = ExtServerState.STOPPED;
            notificationManager.showInfo("Extension server stopped");
            eventBus.fireEvent(ExtServerStateEvent.createExtServerStoppedEvent());
        }
    }

    @Override
    public void onError() {
        if (countRetry > 0) {
            retryConnectionTimer.schedule(1000);
        } else {
            loadInfo.setStatus(Status.ERROR);
            loader.hide();
            state = ExtServerState.STOPPED;
            notificationManager.showInfo("Extension server stopped due to an error");
            eventBus.fireEvent(ExtServerStateEvent.createExtServerStoppedEvent());
        }
    }

    @Override
    public void onOpen() {
        state = ExtServerState.STARTED;
        notificationManager.showInfo("Extension server started.");
        loadInfo.setStatus(Status.FINISHED);
        loader.hide();
        eventBus.fireEvent(ExtServerStateEvent.createExtServerStartedEvent());
    }

    public ExtServerState getState() {
        return state;
    }

    private void connect() {
        WebSocket socket = WebSocket.create(wsUrl);
        socket.setOnOpenHandler(this);
        socket.setOnCloseHandler(this);
        socket.setOnErrorHandler(this);
    }
}

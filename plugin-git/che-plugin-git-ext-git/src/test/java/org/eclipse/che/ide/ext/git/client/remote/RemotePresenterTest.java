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
package org.eclipse.che.ide.ext.git.client.remote;

import org.eclipse.che.api.git.shared.Remote;
import org.eclipse.che.api.project.shared.dto.ProjectDescriptor;
import org.eclipse.che.ide.api.notification.Notification;
import org.eclipse.che.ide.ext.git.client.BaseTest;
import org.eclipse.che.ide.ext.git.client.remote.add.AddRemoteRepositoryPresenter;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.googlecode.gwt.test.utils.GwtReflectionUtils;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link RemotePresenter} functionality.
 *
 * @author <a href="mailto:aplotnikov@codenvy.com">Andrey Plotnikov</a>
 */
public class RemotePresenterTest extends BaseTest {
    public static final boolean SHOW_ALL_INFORMATION = true;
    public static final boolean IS_SHOWN             = true;
    @Mock
    private RemoteView                   view;
    @Mock
    private Remote                       selectedRemote;
    @Mock
    private AddRemoteRepositoryPresenter addRemoteRepositoryPresenter;
    private RemotePresenter              presenter;

    @Override
    public void disarm() {
        super.disarm();

        presenter = new RemotePresenter(view, service, appContext, constant, addRemoteRepositoryPresenter, notificationManager,
                                        dtoUnmarshallerFactory);

        when(selectedRemote.getName()).thenReturn(REPOSITORY_NAME);
    }

    @Test
    public void testShowDialogWhenRemoteListRequestIsSuccessful() throws Exception {
        final List<Remote> remotes = new ArrayList<>();
        remotes.add(selectedRemote);
        when(view.isShown()).thenReturn(!IS_SHOWN);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                AsyncRequestCallback<List<Remote>> callback = (AsyncRequestCallback<List<Remote>>)arguments[3];
                Method onSuccess = GwtReflectionUtils.getMethod(callback.getClass(), "onSuccess");
                onSuccess.invoke(callback, remotes);
                return callback;
            }
        }).when(service).remoteList((ProjectDescriptor)anyObject(), anyString(), anyBoolean(),
                                    (AsyncRequestCallback<List<Remote>>)anyObject());

        presenter.showDialog();

        verify(appContext).getCurrentProject();
        verify(service).remoteList(eq(rootProjectDescriptor), anyString(), eq(SHOW_ALL_INFORMATION),
                                   (AsyncRequestCallback<List<Remote>>)anyObject());
        verify(view).setEnableDeleteButton(eq(DISABLE_BUTTON));
        verify(view).setRemotes((List<Remote>)anyObject());
        verify(view).showDialog();
    }

    @Test
    public void testShowDialogWhenRemoteListRequestIsFailed() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                AsyncRequestCallback<List<Remote>> callback = (AsyncRequestCallback<List<Remote>>)arguments[3];
                Method onFailure = GwtReflectionUtils.getMethod(callback.getClass(), "onFailure");
                onFailure.invoke(callback, mock(Throwable.class));
                return callback;
            }
        }).when(service).remoteList((ProjectDescriptor)anyObject(), anyString(), anyBoolean(),
                                    (AsyncRequestCallback<List<Remote>>)anyObject());

        presenter.showDialog();

        verify(appContext).getCurrentProject();
        verify(service).remoteList(eq(rootProjectDescriptor), anyString(), eq(SHOW_ALL_INFORMATION),
                                   (AsyncRequestCallback<List<Remote>>)anyObject());
        verify(constant).remoteListFailed();
    }

    @Test
    public void testOnCloseClicked() throws Exception {
        presenter.onCloseClicked();

        verify(view).close();
    }

    @Test
    public void testOnAddClicked() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                AsyncCallback<Void> callback = (AsyncCallback<Void>)arguments[0];
                callback.onSuccess(null);
                return callback;
            }
        }).when(addRemoteRepositoryPresenter).showDialog((AsyncCallback<Void>)anyObject());

        presenter.onAddClicked();

        verify(service).remoteList((ProjectDescriptor)anyObject(), anyString(), eq(SHOW_ALL_INFORMATION),
                                   (AsyncRequestCallback<List<Remote>>)anyObject());
        verify(notificationManager, never()).showNotification((Notification)anyObject());
    }

    @Test
    public void testOnAddClickedWhenExceptionHappened() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                AsyncCallback<Void> callback = (AsyncCallback<Void>)arguments[0];
                callback.onFailure(mock(Throwable.class));
                return callback;
            }
        }).when(addRemoteRepositoryPresenter).showDialog((AsyncCallback<Void>)anyObject());

        presenter.onAddClicked();

        verify(service, never()).remoteList((ProjectDescriptor)anyObject(), anyString(), eq(SHOW_ALL_INFORMATION),
                                            (AsyncRequestCallback<List<Remote>>)anyObject());
        verify(notificationManager).showNotification((Notification)anyObject());
        verify(constant).remoteAddFailed();
    }

    @Test
    public void testOnDeleteClickedWhenRemoteDeleteRequestIsSuccessful() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                AsyncRequestCallback<String> callback = (AsyncRequestCallback<String>)arguments[2];
                Method onSuccess = GwtReflectionUtils.getMethod(callback.getClass(), "onSuccess");
                onSuccess.invoke(callback, EMPTY_TEXT);
                return callback;
            }
        }).when(service).remoteDelete((ProjectDescriptor)anyObject(), anyString(), (AsyncRequestCallback<String>)anyObject());

        presenter.showDialog();
        presenter.onRemoteSelected(selectedRemote);
        presenter.onDeleteClicked();

        verify(service).remoteDelete(eq(rootProjectDescriptor), eq(REPOSITORY_NAME), (AsyncRequestCallback<String>)anyObject());
        verify(service, times(2)).remoteList((ProjectDescriptor)anyObject(), anyString(), eq(SHOW_ALL_INFORMATION),
                                             (AsyncRequestCallback<List<Remote>>)anyObject());
    }

    @Test
    public void testOnDeleteClickedWhenRemoteDeleteRequestIsFailed() throws Exception {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                AsyncRequestCallback<String> callback = (AsyncRequestCallback<String>)arguments[2];
                Method onFailure = GwtReflectionUtils.getMethod(callback.getClass(), "onFailure");
                onFailure.invoke(callback, mock(Throwable.class));
                return callback;
            }
        }).when(service).remoteDelete((ProjectDescriptor)anyObject(), anyString(), (AsyncRequestCallback<String>)anyObject());

        presenter.showDialog();
        presenter.onRemoteSelected(selectedRemote);
        presenter.onDeleteClicked();

        verify(service).remoteDelete(eq(rootProjectDescriptor), eq(REPOSITORY_NAME), (AsyncRequestCallback<String>)anyObject());
        verify(constant).remoteDeleteFailed();
        verify(notificationManager).showNotification((Notification)anyObject());
    }

    @Test
    public void testOnRemoteSelected() throws Exception {
        presenter.onRemoteSelected(selectedRemote);

        verify(view).setEnableDeleteButton(eq(ENABLE_BUTTON));
    }
}
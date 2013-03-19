package com.pusher.client.endtoend;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.*;

import java.net.URI;

import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.pusher.client.Authorizer;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.impl.ChannelManager;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.connection.impl.InternalConnection;
import com.pusher.client.connection.websocket.WebSocketClientWrapper;
import com.pusher.client.connection.websocket.WebSocketConnection;
import com.pusher.client.connection.websocket.WebSocketListener;
import com.pusher.client.util.Factory;
import com.pusher.client.util.InstantExecutor;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Factory.class})
public class EndToEndTest {

	private static final String API_KEY = "123456";
	private static final String AUTH_KEY = "123456";
	private static final String PUBLIC_CHANNEL_NAME = "my-channel";
  private static final String PRIVATE_CHANNEL_NAME = "private-my-channel";
	private static final String OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE = "{\"event\":\"pusher:subscribe\",\"data\":{\"channel\":\"" + PRIVATE_CHANNEL_NAME + "\",\"auth\":\"" + AUTH_KEY + "\"}}";
	
	private @Mock Authorizer mockAuthorizer;
	private @Mock ConnectionEventListener mockConnectionEventListener;
	private @Mock ServerHandshake mockServerHandshake;
	private Pusher pusher;
	private PusherOptions pusherOptions;
	private InternalConnection connection;
	private TestWebSocketClientWrapper testWebsocket;
	
	@Before
	public void setUp() throws Exception {
		
		PowerMockito.mockStatic(Factory.class);
		
		connection = new WebSocketConnection(API_KEY, false);
		
		when(Factory.getEventQueue()).thenReturn(new InstantExecutor());
		when(Factory.newWebSocketClientWrapper(any(URI.class), any(WebSocketListener.class))).thenAnswer(new Answer<WebSocketClientWrapper>() {

			@Override
			public WebSocketClientWrapper answer(InvocationOnMock invocation) throws Throwable {
				URI uri = (URI) invocation.getArguments()[0];
				WebSocketListener proxy = (WebSocketListener) invocation.getArguments()[1];
				testWebsocket = new TestWebSocketClientWrapper(uri, proxy);
				return testWebsocket;
			}
		});
		
		when(Factory.getConnection(API_KEY, false)).thenReturn(connection);
		
		when(Factory.getChannelManager()).thenAnswer(new Answer<ChannelManager>() {
			public ChannelManager answer(InvocationOnMock invocation) throws Throwable {
				return new ChannelManager();
			}
		});
		
		when(Factory.newPresenceChannel(any(InternalConnection.class), anyString(), any(Authorizer.class))).thenCallRealMethod();
		when(Factory.newPrivateChannel(any(InternalConnection.class), anyString(), any(Authorizer.class))).thenCallRealMethod();
		when(Factory.newPublicChannel(anyString())).thenCallRealMethod();
		when(Factory.newURL(anyString())).thenCallRealMethod();
	
		when(mockAuthorizer.authorize(anyString(), anyString())).thenReturn("{\"auth\":\"" + AUTH_KEY + "\"}");
		
		pusherOptions = new PusherOptions().setAuthorizer(mockAuthorizer);
		pusher = new Pusher(API_KEY, pusherOptions);
	}
	
	@After
	public void tearDown() {
		
		pusher.disconnect();
		testWebsocket.onClose(1, "Close", true);
	}
	
	@Test
	public void testSubscribeToPublicChannelSendsSubscribeMessage() {
		
		establishConnection();
		pusher.subscribe(PUBLIC_CHANNEL_NAME);
		
		testWebsocket.assertLatestMessageWas("{\"event\":\"pusher:subscribe\",\"data\":{\"channel\":\"" + PUBLIC_CHANNEL_NAME + "\"}}");
	}
	
	@Test
	public void testSubscribeToPrivateChannelSendsSubscribeMessage() {
		
		establishConnection();
		pusher.subscribePrivate(PRIVATE_CHANNEL_NAME);
		
		testWebsocket.assertLatestMessageWas(OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE);
	}
	
	@Test
	public void testForQueuedSubscriptionsAuthorizerIsNotCalledUntilTimeToSubscribe() {
		
		pusher.subscribePrivate(PRIVATE_CHANNEL_NAME);
		verify(mockAuthorizer, never()).authorize(anyString(), anyString());
		
		establishConnection();
		verify(mockAuthorizer).authorize(eq(PRIVATE_CHANNEL_NAME), anyString());
	}
	
	@Test
	public void testSubscriptionsAreResubscribedWithFreshAuthTokensEveryTimeTheConnectionComesUp() {
		
		pusher.subscribePrivate(PRIVATE_CHANNEL_NAME);
		verify(mockAuthorizer, never()).authorize(anyString(), anyString());
		
		establishConnection();
		verify(mockAuthorizer).authorize(eq(PRIVATE_CHANNEL_NAME), anyString());
		testWebsocket.assertLatestMessageWas(OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE);
		testWebsocket.assertNumberOfMessagesSentIs(1);
		
		testWebsocket.onClose(0, "No reason", true);
		testWebsocket.onOpen(mockServerHandshake);
		testWebsocket.onMessage("{\"event\":\"pusher:connection_established\",\"data\":\"{\\\"socket_id\\\":\\\"23048.689386\\\"}\"}");
		
		verify(mockAuthorizer, times(2)).authorize(eq(PRIVATE_CHANNEL_NAME), anyString());
		testWebsocket.assertLatestMessageWas(OUTGOING_SUBSCRIBE_PRIVATE_MESSAGE);
		testWebsocket.assertNumberOfMessagesSentIs(2);
	}
	
	/** end of tests **/
	
	private void establishConnection() {
		
		pusher.connect(mockConnectionEventListener);
		
		testWebsocket.assertConnectCalled();
		verify(mockConnectionEventListener).onConnectionStateChange(new ConnectionStateChange(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING));
		
		testWebsocket.onOpen(mockServerHandshake);
		testWebsocket.onMessage("{\"event\":\"pusher:connection_established\",\"data\":\"{\\\"socket_id\\\":\\\"23048.689386\\\"}\"}");
		
		verify(mockConnectionEventListener).onConnectionStateChange(new ConnectionStateChange(ConnectionState.CONNECTING, ConnectionState.CONNECTED));
	}
}
# Pusher Java Client

Pusher client library for Java targeting **Android** and general Java.

This README covers the following topics:

* Installation
* API Overview
* The Pusher constructor
* Connecting
* Disconnecting
* Listening to connection events
* Subscribing to channels
  * Public
  * Private
  * Presence
* Binding and handling events
  * Unbinding events
* Triggering client events
* Accessing the connection socket ID  
* JavaDocs
* Library development environment

## Installation

You can get the library in three ways.

### Maven

Define the `pusher-java-client` dependency:

    <dependency>
      <groupId>com.pusher</groupId>
      <artifactId>pusher-java-client</artifactId>
      <version>0.0.1-SNAPSHOT</version>
      <scope>provided</scope>
    </dependency>
    
Define the repository location:

    <repository>
      <id>pusher-java-client-mvn-repo</id>
      <name>Pusher Java Client Library</name>
      <url>https://raw.github.com/leggetter/pusher-java-client/mvn-repo/</url>
      <!-- Update the following as required -->
      <releases>
        <enabled>true</enabled>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>

### Download

You can download a version of the `.jar` directly via <https://github.com/leggetter/pusher-java-client/tree/mvn-repo/com/pusher/pusher-java-client>

### Source

You can build the project from source. For more information see **Library development environment**.

## API Overview

Here's the API in a nutshell.

```java
// Create a new Pusher instance
Pusher pusher = new Pusher( "YOUR_APP_KEY" );

// Connect
pusher.connect(new ConnectionEventListener() {

  @Override
  public void onConnectionStateChange(ConnectionStateChange change) {
    // handle connection state change
  }

  @Override
  public void onError(String message, String code, Exception e) {
    // handle connection error
  }
  
}, ConnectionState.ALL);

// Subscribe to a channel
Channel channel = pusher.subscribe( "my-channel" );

// Bind to an event and listen for events
channel.bind( "my-event", new SubscriptionEventListener() {

  @Override
  public void onEvent(String channel, String event, String data) {
    // do something with the event data
  }
  
});
```

More information in reference format can be found below.

## The Pusher constructor

The standard constructor take an application key which you can get from the app's API Access section in the Pusher dashboard.

```java
Pusher pusher = new Pusher( YOUR_APP_KEY );
```

If you are going to use [private](http://pusher.com/docs/private_channels) or [presence](http://pusher.com/docs/presence_channels) channels then you will need to provide an `Authorizer` to be used when authenticating subscriptions. In order to do this you need to pass in a `PusherOptions` object which has had an `Authorizer` set.

```java
HttpAuthorizer authorizer = new HttpAuthorizer("http://example.com/some_auth_endpoint");
PusherOptions options = new PusherOptions().setAuthorizer(authorizer);
Pusher pusher = new Pusher( YOUR_APP_KEY, options );
```

See the documentation on [Authenticating Users](http://pusher.com/docs/authenticating_users) for more information.

## Connecting

In order to send and receive messages you need to connect to Pusher.

```java
Pusher pusher = new Pusher( YOUR_APP_KEY );
pusher.connect();
```

## Disconnecting

```java
pusher.disconnect();
```

## Listening to connection events

It is possible to receive connection state change events by implementing the `ConnectionEventListener` interface.

```java

Pusher pusher = new Pusher( YOUR_APP_KEY );
pusher.connect( new ConnectionListener() {

  @Override
  public void onConnectionStateChange(ConnectionStateChange change) {
    System.out.println(
      String.format("Connection state changed from [%s] to [%s]", change.getPreviousState(), change.getCurrentState())
    );
  }

  @Override
  public void onError(String message, String code, Exception e) {
    System.out.println( "Error: " + message );
  }

} );
```

For more information see [connection states](http://pusher.com/docs/connection_states).

## Subscribing to channels

Pusher uses the concept of [channels](http://pusher.com/docs/channels) as a way of subscribing to data. They are identified and subscribed to by a simple name. Events are bound to on a channels and are also identified by name. To listen to an event you need to implemented the `ChannelEventListener` interface (see **Binding and handling events**).

### Public channels

```java
Channel channel = pusher.subscribe( "my-channel" );
```

Sometimes you may want to be informed when the subscription succeeds. You can do this by implementing the `ChannelEventListener` interface:

```java
Channel channel = pusher.subscribe( "my-channel", new ChannelEventListener() {
  
  @Override
  public void onSubscriptionSucceeded(String channelName) {
    System.out.println( "Subscribed!" );
  }

  @Override
  public void onEvent(String channelName, String eventName, String data){
  }

} );
```

### Private channels

It's possible to subscribe to [private channels](http://pusher.com/docs/private_channels) that provide a mechanism for [authenticating channel subscriptions](http://pusher.com/docs/authenticating_users). In order to do this you need to provide an `Authorizer` when creating the `Pusher` instance (see **The Pusher constructor** above).

The library provides a `HttpAuthorizer` implementation of `Authorizer` which makes an HTTP `POST` request to an authenticating endpoint. However, you an implement your own authentication mechanism if required.

Private channels are subscribed to as follows:

```java
PrivateChannel privateChannel = pusher.subscribePrivate( "private-channel" );
```

In addition to the events that are possible on public channels a private channel exposes an `onAuthenticationFailure`. This is called if the `Authorizer` does not successfully authenticate the subscription:

```java
PrivateChannel channel = pusher.subscribePrivate( "private-channel",
  new PrivateChannelEventListener() {

    @Override
    public void onAuthenticationFailure(String message, Exception e) {
	  System.out.println(
        String.format("Authentication failure due to [%s], exception was [%s]", message, e)
      );
    }
    
    // Other ChannelEventListener methods
  } );
```

### Presence channels

[Presence channels](http://pusher.com/docs/presence_channels) are private channels which provide additional events exposing who is currently subscribed to the channel. Since they extend private channels they also need to be authenticated (see [authenticating channel subscriptions](http://pusher.com/docs/authenticating_users)).

Presence channels can be subscribed to as follows:

```java
PresenceChannel presenceChannel = pusher.subscribePresence( "presence-channel" );
```

Presence channels provide additional events relating to users joining (subscribing) and leaving (unsubscribing) the presence channel. It is possible to listen to these events by implementing the `PresenceChannelEventListener`.

```java
PresenceChannel channel = pusher.subscribePresence( "presence-channel",
  new PresenceChannelEventListener() {

    @Override
    public void onUserInformationReceived(String channelName, Set<User> users) {
	  for(User user : users) {
	    userSubscribed(channelName, user);	    
	  }
    }

    @Override
    public void userSubscribed(String channelName, User user) {
	  System.out.println(
        String.format( "A new user has joined channel [%s]: %s, %s", channelName, 
          user.getId(), user.getInfo() )
      );

	  if(user.equals(channel.getMe())) {
	    System.out.println("me");
	  }
    }

    @Override
    public void userUnsubscribed(String channelName, User user) {
      System.out.println(
        String.format( "A user has left channel [%s]: %s %s", channelName,
          user.getId(), user.getInfo() )
      );
    }
    
    // Other ChannelEventListener methods    

  } );
```

#### The User object

*Note: In the [Pusher documentation](http://pusher.com/docs) a User may be referred to as a `Member`.*

The `User` object has two main methods.

`getId` fetches a unique identifier for the user on the presence channel.

```java
String id = user.getId();
```

`getInfo` fetches a string representing arbitrary additional information about the user. The contents of this is entirely up to your application. However, it's recommended that the data is in JSON format so that it can easily be deserialized.

The following example using the [Gson library](https://sites.google.com/site/gson/gson-user-guide) to handle deserialization:

```java
String jsonInfo = user.getInfo();
Gson gson = new Gson();
UserInfo info = gson.fromJson(jsonInfo, UserInfo.class);  
```

For more information on defining the user id and user info on the server see [Implementing the auth endpoint for a presence channel](http://pusher.com/docs/authenticating_users#implementing_presence_endpoints) documentation.

## Binding and handling events

There are two types of events that occur on channel subscriptions.

1. Protocol related events such as those triggered when a subscription succeeds
2. Application events that have been triggered by code within your application

### ChannelEventListener

The `ChannelEventListener` is an interface that is informed of both protocol related events and application data events. A `ChannelEventListener` can be used when initially subscribing to a channel.

```java
Channel channel = pusher.subscribe( "my-channel", new ChannelEventListener() {
  
  @Override
  public void onSubscriptionSucceeded(String channelName) {
    System.out.println( "Subscribed!" );
  }

  @Override
  public void onEvent(String channelName, String eventName, String data){
  }

} );
```

The `ChannelEventListener` interface extends the `SubscriptionEventListener` interface.

### SubscriptionEventListener

Events triggered by your application are received by the `onEvent` method on the `SubscriptionEventListener` interface implementation. If you are only related to application events you can bind to events on `Channel` objects.

```java
Channel channel = pusher.subscribe( "my-channel" );
channel.bind( 'my-event', new ChannelEventListener() {

  @Override
  public void onEvent(String channelName, String eventName, String data){
  }

} );
```

The event data will be passed as the third parameter to the `onEvent` method. From there you can handle the data as you like. Since we encourage data to be in JSON here's an example that uses [Gson object deserialization](https://sites.google.com/site/gson/gson-user-guide#TOC-Object-Examples):

```java
public class Example implements ChannelEventListener {
  
  public Example() {
    Pusher pusher = new Pusher( YOUR_APP_KEY );
    pusher.connect( this );
    
    pusher.subscribe( "my-channel", this );
  }

  @Override
  public void onEvent(String channelName, String eventName, String data){
    Gson gson = new Gson();
    EventExample exampleEvent = gson.fromJson(data, EventExample.class); 
  }

}

class EventExample {
  private int value1 = 1;
  private String value2 = "abc";
  private transient int value3 = 3;
  EventExample() {
  }
}
```

### Unbinding event listeners

You can unbind from an event:

```java
channel.unbind( "my_event", listener );
```

### Example

```java
public class Example implements ChannelEventListener {
  
  private final Pusher pusher;
  private final Channel channel;

  public Example() {
    pusher = new Pusher( YOUR_APP_KEY );
    pusher.connect( this );
    
    channel = pusher.subscribe( "my-channel", this, "my_event" );    
  }

  public void listenToOtherEvent() {
    channel.bind( "my_other_event", this );
  }

  public void stopListeningToOtherEvent() {
    channel.unbind( "my_other_event", this );
  }

}
```

## Triggering events

Once a [private](http://pusher.com/docs/private_channels) or [presence](http://pusher.com/docs/presence_channels) subscription has been authorized (see [authenticating users](http://pusher.com/docs/authenticating_users)) and the subscription has succeeded, it is possible to trigger events on those channels.

```java
channel.trigger("client-myEvent", "{\"myName\":\"Bob\"}");
```

Events triggered by clients are called [client events](http://pusher.com/docs/client_events). Because they are being triggered from a client which may not be trusted there are a number of enforced rules when using them. Some of these rules include:

* Event names must have a `client-` prefix
* Rate limits
* You can only trigger an event when the subscription has succeeded

For full details see the [client events documentation](http://pusher.com/docs/client_events).

```java
PrivateChannel channel;
channel = pusher.subscribePrivate( "private-channel",
  new PrivateChannelEventListener() {

    @Override
    public void onSubscriptionSucceeded(String channelName) {
	  channel.trigger("client-myEvent", "{\"myName\":\"Bob\"}");
    }

    // Other PrivateChannelEventListener methods
  } );
```

## Accessing the connection socket ID

Once connected you can access a unique identifier for the current client's connection. This is known as the `socket_id`.

You can access the value **once the connection has been established** as follows:

```java
Pusher pusher = new Pusher( YOUR_APP_KEY );
String socketId = pusher.getConnection().getSocketId();
```

For more information on how and why there is a `socket_id` see the documentation on [authenticating users](http://pusher.com/docs/authenticating_users) and [excluding recipients](http://pusher.com/docs/server_api_guide/server_excluding_recipients).

## JavaDocs

The JavaDocs can be found here: <http://leggetter.github.com/pusher-java-client/>

## Library Development Environment

### Cloning the project

* If you don't have it already, [download Maven](http://maven.apache.org/download.cgi) and add the `mvn` executable to your path.
* Clone the project: `git clone https://github.com/leggetter/pusher-java-client.git`
* Change to the top level directory for the project: `cd pusher-java-client`
* Retrieve the Java-WebSocket library: `git submodule update --init`

### Eclipse Project

Assuming you are using Eclipse, follow these steps:

* Make a note of your Eclipse workspace location, for example `C:\Users\Phil\workspace`
* In the root of the project execute `mvn -Declipse.workspace="<your workspace location>" eclipse:configure-workspace`, for example `mvn -Declipse.workspace="C:\Users\Phil\workspace" eclipse:configure-workspace`. This will add an M2_REPO environment variable to your Eclipse workspace, which is required for the next step.
* Still in the root of the project execute `mvn eclipse:clean eclipse:eclipse`. This will read the pom file to determine the dependencies and generate `.project` and `.classpath` files that point to those dependencies in your local Maven cache.

You can now load the project in Eclipse by navigating to `Import project` and pointing it to the root directory of the project.

### Build

From the top level directory execute `mvn clean test` to compile and run the unit tests or `mvn clean package` to build the jar. The jar will be output to the `target` directory.

### Run the Example Application

After running `mvn clean install` change to the `target` directory and run `java -jar pusher-java-client-<version>-jar-with-dependencies.jar`. This will run the example application. 

By default the example will connect to a sample application and subscribe to the channel `my-channel`, listening to events on `my-event`. If you want to change these defaults, they can be specified on the command line:

`java -jar pusher-java-client-<version>-jar-with-dependencies.jar [appKey] [channelName] [eventName]` 

### Deploy

    mvn deploy
    
This command does two things:

1. The library is deployed to a Maven repository in a github branch. For more information see: <http://stackoverflow.com/a/14013645/39904>
2. Generated JavaDocs are deployed to <http://leggetter.github.com/pusher-java-client/>. For more information see: <https://github.com/github/maven-plugins>

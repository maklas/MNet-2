## Fast UDP Networking for games using LibGDX PC/Android.

### Features:
* Fully over UDP. Optimized for maximum speed. Basically as fast as your UDP connection.
0.15 ms round trip on localhost pc. 3 ms round trip for Phone -> Wi-Fi -> PC -> Wi-Fi -> Phone.
* Supports: Reliable ordered sending, unreliable unordered sending, sending multiple objects in batches, automated ping checking.
* Supports Login-Authentication out of the box. No need to worry about 5th guy connecting to a max of 4 game lobby.
You can decline users before establishing connection with them. Can be used to ping server for current status as well.
* Written with Java 6. Done with java 8 in mind. Suitable for Libgdx.
* Default serialization - Kryo.
* Testable. Simulate packet loss, simulate high ping, test sending and receiving speed in java code!
* Used in real working projects and currently developing projects for PC and Android. 
* Local network broadcaster and receiver included for fast and easy way to find servers in local network.
* You can use JitPack to download this library into your project right now! 

### Cons:
* No reliable unordered sending (yet)
* Only single-threaded usage.
* No protection from dos or any kind of attack really

## Example:

1.  Create your data model which includes classes for connecting, accepting/rejecting connection and all data-classes for in-game mechanics, also provide Serializer
In this example I'll use 1 object for connecting to server, 1 object for accepting or rejecting connection and 1 in-game object. Serialization will be done
with KryoSerializer. You can use it as well or make your own serializer.

```java
public class ConnectionRequest {
    String name;
    String password;
}

public class ConnectionResponse {
    String message;
}

public class EntityUpdate {
    int id;
    float x;
    float y;
}

public static Serializer createSerializer(){
    Kryo kryo = new Kryo();

    kryo.register(ConnectionRequest.class, 1);
    kryo.register(ConnectionResponse.class, 2);
    kryo.register(EntityUpdate.class, 3);
    
    return new KryoSerializer(kryo, 512);
}
```

2.  Make ServerAuthenticator. It will be used to authorize new connections. 
In this example I will have up to 4 players connected at the same time and they also need a correct password to connect.
Here you can implement any kind of logic you want pretty easily. 
White-listing, black-listing, bans, password protection, game version compatibility check, virtually anything!

* `conn.accept()` will respond successfully to a client and return a socket. Never forget to register socket and remove when it's closed. 
At this stage you can also add PingListener to listen for pings.
* `conn.reject()` will respond to a client with rejection. In this case socket is not created and connection is not established.
* If you don't accept nor reject new connection, it will be automatically rejected with `null` object as a response, 
but it's always better to respond yourself.

```java
public class MyServerAuthenticator implements ServerAuthenticator {
    Array<Player> players = new Array<Player>();

    @Override
    public void acceptConnection(Connection conn) {
        if (players.size >= 4){ //Check if server is busy
            conn.reject(new ConnectionResponse("Server is full"));
        } else if (!(conn.getRequest() instanceof ConnectionRequest)) { //request was wrong
            conn.reject(new ConnectionResponse("Wrong type of request"));
        } else { 
            
            ConnectionRequest req = (ConnectionRequest) conn.getRequest();
            if ("123".equals(req.getPassword())){ //validate password
                Socket socket = conn.accept(new ConnectionResponse("Welcome, " + req.getName() + "!")); //obtain Socket
                final Player player = new Player(req.getName(), socket);
                socket.setUserData(player); //Save Player in socket, so that we can know who send us data
                players.add(player);
                socket.addDcListener((sock, msg) -> { //Add dc listener. We need to remove Player from Array after he disconnects
                    players.removeValue(player, true);
                });
            } else {
                conn.reject(new ConnectionResponse("Wrong password"));
            }
        }
    }
}
```

3.  Server socket and Client socket
```java

ServerSocket serverSocket = new ServerSocket(port, new MyServerAuthenticator(), () -> createSerializer());

/* Server socket must be updated every frame. 
During that frame, any new connections will be processed by ServerAuthenticator 
and all non-responsive clients will be disconnected */
serverSocket.update(); 

/* All of the sub-sockets of Server socket also have to be separately updated every frame */
for (Socket socket : serverAuthenticator.getSockets()) {
       socket.update(socketProcessor);
}


```

```java
Socket clientSocket = new SocketImpl(InetAddress.getByName(address), port, bufferSize,
        /* Disconnect on inactivity timeout in millis */ 7_000,
        /* PingFrequency. How often to send pings and check for inactivity. Must be lower than Inactivity timeout. In millis */ 2_000,
        /* ResendFrequency. How much time we wait until lost packet must be resent. Millis */ 100,
        createSerializer());

/* Socket must be updated every frame. During this time socketPorcessor will receive events from connected socket. Also, during this call,
Ping listener and disconnection listeners are called */
socket.update(socketProcessor);
```

4.  Implement `SocketProcessor.class` interface by one of your game-flow classes. 
If you're using ECS, it can be one of your systems that calls `socket.update(this)` or if you're a man of inheritance culture,
`player.update()` might be a good place for that.

```java
@Override
public void process(Socket socket, Object o) {
    System.out.println("Event received by " + ((Player) socket.getUserData()).getName() + ":" + o);
    if (o instanceof UpdateObject){
        ...
    }
}
```

5.  Now when we're finally all set, it's time to connect to Server and start sending and receiving data!
```java
//Blocks for 5 seconds. You can also connect asynchroniously by calling socket.connectAsync()  
ServerResponse response = socket.connect(new ConnectionRequest("maklas", "123"), 5_000); 

//Here is our response object that Server replied with. Check it for being NULL just in case.  
ConnectionResponse connResp = (ConnectionResponse) response.getResponse();

//There is 4 types of possible outcomes during connection. 
//The only time we can be sure to be connected is when ResponseType == ACCEPTED.
//In any other case, socket is not connected.
switch (response.getType()){
    case ACCEPTED:
        System.out.println("Successfully connected with message " + connResp.getMessage());
        break;
    case REJECTED:
        System.out.println("Server rejected our request with message " + connResp.getMessage());
        break;
    case NO_RESPONSE:
        System.out.println("Server doesn't respond");
        break;
    case WRONG_STATE:
        System.out.println("Socket was closed or was already connected");
        break;
}

socket.update(socketProcessor); //Now call this every frame to receive data from server.

socket.send(new EntityUpdate(id, x, y)); // sends data reliably and in order of sending.
socket.sendUnreliable(new EntityUpdate(id, x, y)) // sends data unreliably and unordered.
socket.sendBig(new EntityUpdate(id, x, y)) // sends data reliably and ordered up to 30 MB of size with buffersize = 512.
socket.*() //Also many other methods for sending and controlling data. JavaDocs are provided.
```

6.  Disconnecting

Disconnecting is simple. Just call `socket.close()`. DisconnectionListeners will be called and connected socket will be notified (unreliably).
After socket was closed, it cannot be reused. Use `socket.close(msg)` to send disconnection message. Usually it specifies reason for disconnection.
Default disconnection types can be found at `DCType.class`. `DCListener` will receive this message as a parameter. If you need to shutdown server, disconnecting individual sub-sockets won't be enough. 
Use `serverSocket.close()`. By closing serverSocket all sub-sockets will also be closed and DCListeners notified.

## Adding to your project
1. Add JitPack repo if you haven't already `maven { url "https://jitpack.io" }`
2. Add as a dependency `compile "com.github.maklas:MNet-2:0.3"` (check current version in GitHub releases)

## FAQ

* **What's with bufferSize and why is it recommended to be 512?** 

Buffer size is a size of a byte[] buffer that's used by java's DatagramSocket implementation.
It's recommended to be lower than 576 bytes. If it's above that, then there is no guarantee that
data will arrive intact. Note that some bytes are used for UDP header and some (from 1 to 9) are used by MNet-2.
So 512 is a safe bet. It's 128 integers/floats! Good enough for basic game stuff. If you need to send a bigger object (for an in-game chat for example),
send it via `socket.sendBig()`. This object will be divided in parts and reassembled on another end. 
BufferSize can also be lower than 512, but there is no benefits in it.

* **I can successfully connect locally, but not to my friend over Internet** 

Make sure you have the port opened.

* **How to interrupt `socket.update(socketProcessor)` so that I can change states in my game**

`socket.stop()` will do. Let's say you received a command from server to \[**go from a castle to a dungeon**\] and an \[**info about dungeon**\] right after and they
they arrive in the same frame. Now you haven't managed to load dungeon yet, but you receive dungeon info and your game crashes or doesn't respond, 
because you can only change from one location to another inbetween frames or even after loading phase, not in a single method call, 
so by the time you loaded dungeon, there is no **dungeon info**. It stayed in a **castle state**.
So what you gotta do is call `socket.stop()` when you receive a _state important event_, finish loading new state and only then start updating socket again.

* **What Address should I use for BroadcastServlet and BroadcastSocket?**

For `BroadcastServlet` you generally use `0.0.0.0`
For `BroadcastSocket` you can use `255.255.255.255` (full [broadcast address](https://en.wikipedia.org/wiki/Broadcast_address "Wikipedia")) or your subnet-directed broadcast address like `192.168.255.255`
which is better because there is no guarantee that `255.255.255.255` will be redirected by all routers. But you can't always know subnet-directed broadcast address.
I personally tested this at my house with unconfigured router and some public Wi-Fi spots in malls and subway. Worked every time.

* **What is batching? When and How should I use it?**

Underlying implementation of UDP in Android and PC doesn't care about how much data you send. 
1 byte or  512 bytes in a single call. It'll take equal amount of time.
Now, imagine it takes 1 ms to serialize data and 1 ms for `socket.send()` to actually pack and send your data over Internet, your **buffer size** is 512 and 
you want to send 5 objects of size 100 bytes in a single frame.
If you call `socket.send()` 5 times. it will take 10 ms to complete. But if you pack them all in a single batch (which your buffer size allows),
It will only take 6 ms to serialize and send.
Achieving this is easy. You just carry around the `NetBatch`, collecting all your data in it since the start of the frame 
and at the end of it you call `socket.send(NetBatch batch)`.
All you have to care about is that all individual object's sizes are less than **buffer size**.
If all objects don't fit an a single buffer, they will be split among multiple buffers and sent independently.

## Testing
When you need to test your game for high ping or packet loss sustainability, you can use
different implementations of `UDPSocket`.

`JavaUDPSocket` - Is java's implementation for udp. You need this as a base for all your connections.

`HighPingUDPSocket` - Allows testing increased ping

`PacketLossUDPSocket` - Allows testing additional packet loss

`TraficCounterUDPSocket` - Allows profiling network data usage.

## LAN discovery

**MNet-2** includes utils for LAN discovery. They allow you to communicate over LAN UDP broadcast. 
Most commonly used for finding servers in local network. 
Note that this method of communication is not optimized for anything more than server finding in local network.
**It can't be used for gaming.**

`BroadcastServlet.class` - Used by server. It listens to specific port on local UDP broadcast and able to only respond.
`BroadcastSocket.class` - Used by clients. It's able to send broadcast messages to local network and listen to multiple responses coming from servers.



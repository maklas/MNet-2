## Network protocol for games based on UDP using Kryo serialization.
### Features:
* Completely written over UDP. Better performance, customizable packet resending time
* Testable. Simulate packet loss, simulate high ping, test sending and receiving speed!
* Supports Login-Authentication out of the box. No need to worry about 5th guy connecting to a max of 4 game lobby. 
Decline new users on your conditions before establishing connection.
* Supports: Reliable ordered sending, unreliable unordered sending, sending in batches, automated ping checking.
* Local network broadcaster and receiver included for fast and easy way to find servers over local network.
* Very convenient way of receiving data on the other end in a game loop. 


## Example:

1.  Create your data model which includes classes for connecting, accepting/rejecting connection and all data-classes for in-game mechanics. Also Serializer
In this example I'll use 1 object for connecting to server, 1 object for accepting or rejecting connection and 1 in-game object. Also, serialization will be done
with KryoSerializer. You can use default Kryo serializer as well or make your own.

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
In this example I will have up to 4 players connected at the same time and they also need a correct password to connect
```java
public class MyServerAuthenticator implements ServerAuthenticator {
    Array<Player> players = new Array<Player>();

    @Override
    public void acceptConnection(Connection conn) {
        if (players.size >= 4){ //Check if server is busy
            conn.reject(new ConnectionResponse("Server is full"));
        } else if (!(conn.getRequest() instanceof ConnectionRequest)) { //request was wrong
            conn.reject("Wrong type of request");
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

ServerSocket serverSocket = new ServerSocket(6565, new MyServerAuthenticator(), () -> createSerializer());

serverSocket.update(); 
/* Server socket must be updated every frame. 
During that frame, any new connections will be processed by ServerAuthenticator 
and all non-responsive clients will be disconnected */

for (Socket socket : serverSocket.getSockets()) {
       socket.update(socketProcessor);
}
/*All of the sub-sockets of Server socket also have to be separately updated every frame */


Socket clientSocket = new SocketImpl(InetAddress.getByName(address), port, bufferSize,
        /*Inactivity timeout*/ 7_000,
        /*PingFrequency. How often to send pings and check for concurrent connection*/ 2_000,
        /*ResendFrequency. How much time we wait until resend packet*/ 100,
        createSerializer());

```

4.  Implement SocketProcessor.class interface by one of your game-flow classes
```java
@Override
public void process(Socket socket, Object o) {
    System.out.println("Event received by " + ((Player) socket.getUserData()).getName() + ":" + o);
}
```

5.  Now when you're all set, it's time to connect to Server and start sending and receiving data!
```java
ServerResponse response = socket.connect(new ConnectionRequest("maklas", "123", 22), 5_000); //Blocks for 5 seconds. You can also connect asynchroniously by calling socket.connectAsync()
        
ConnectionResponse connResp = (ConnectionResponse) response.getResponse();

switch (response.getType()){
    case ACCEPTED:
        System.out.println("Successfully connected with message " + connResp.getMessage());
        break;
    case REJECTED:
        System.out.println("Servrer rejected our request with message " + connResp.getMessage());
        break;
    case NO_RESPONSE:
        System.out.println("Server doesn't respond");
        break;
    case WRONG_STATE:
        System.out.println("Socket was closed or was already connected");
        break;
}

socket.update(this); //Now call this every frame to receive data from server.

socket.send(new EntityUpdate(id, x, y)); // sends data in order and reliably
socket.sendUnreliable(new EntityUpdate(id, x, y)) // sends data unreliably and unordered.
socket.sendBig(new EntityUpdate(id, x, y)) // sends data reliably and ordered up to 30 MB of size with buffersize = 512.
socket.*() //Also many other methods for sending and controlling data. JavaDocs are provided.
```

## Testing
When you need to test your game for high ping or packet loss sustainability, you can use
different implementations of `UDPSocket`.

`JavaUDPSocket` - Is java's implementation for udp. You need this as a base for all your connections.

`HighPingUDPSocket` - Allows testing increased ping

`PacketLossUDPSocket` - Allows testing additional packet loss

`TraficCounterUDPSocket` - Allows profiling network data usage.
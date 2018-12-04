## Network protocol for games based on UDP using Kryo serialization.
### Features:
* Completely written over UDP. Better performance, customizable packet resending time
* Testable. Simulate packet loss, simulate high ping, test sending and receiving speed!
* Supports Login-Authentication out of the box. No need to worry about 5th guy connecting to a max of 4 game lobby. 
Decline new connections on your conditions in just single line.
* Supports: Reliable ordered sending, unreliable unordered sending, sending in batches, automated ping checking.
* Local network broadcaster and receiver included for fast and easy way to find servers over local network.
* Very convenient way of receiving data on the other end in a game loop. 

### Cons:
* Uses my fork of Libgdx version 1.9.8
* max size of single packet is about 512 bytes. If you want to send a bigger packet, split it in small sized byte[] and send in a batch. (Will be fixed in future)

## Example:

1.  Create ConnectionRequest object
```java
public class ConnectionRequest {
    String name;
    String password;
}
```

2.  Create ConnectionResponse object

```java
public class ConnectionResponse {
    String message;
}
```

3.  Another object for ingame mechanics:

```java
public class EntityUpdate {
    int id;
    float x;
    float y;
}
```

4.  Make Serializer provider. You can use default Kryo serializer or make your own.

```java
public static Serializer createSerializer(){
    Kryo kryo = new Kryo();

    kryo.register(ConnectionRequest.class, 1);
    kryo.register(ConnectionResponse.class, 2);
    kryo.register(EntityUpdate.class, 3);
    
    return new KryoSerializer(kryo, 512);
}
```

5.  Make ServerAuthenticator. It will be used to authorize new connections. 
In our example I will have up to 4 players connected at the same time and they also need a password to connect
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
                final Player player = new Player(req.getName(), req.getAge(), socket);
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

6.  Create Server and Client sockets
```java

ServerSocket serverSocket = new ServerSocket(6565, new MyServerAuthenticator(), () -> createSerializer());

Socket clientSocket = new SocketImpl(InetAddress.getByName(address), port, bufferSize,
        /*Inactivity timeout*/ 7_000,
        /*PingFrequency. How often to send pings and check for concurrent connection*/ 2_000,
        /*ResendFrequency. How much time we wait until resend packet*/ 100,
        createSerializer());

```
**After Server socket is created it must be updated every frame!**
```java
serverSocket.update();
```

7.  Implement SocketProcessor.class interface by one of your game-flow classes
```java
@Override
public void process(Socket socket, Object o) {
    System.out.println("Event received by " + ((Player) socket.getUserData()).getName() + ":" + o);
}
```

8.  Connect to Server socket and start sending data!
```java
ServerResponse response = socket.connect(new ConnectionRequest("maklas", "123", 22), 5_000); //Blocks for 5 seconds. You can also connect asynchroniously by calling socket.connectAsync()
        
ConnectionResponse connResp = (ConnectionResponse) response.getResponse();

switch (response.getType()){
    case ACCEPTED:
        System.out.println("Successfully connected with message " + ((ConnectionResponse) connResp).getMessage());
        break;
    case REJECTED:
        System.out.println("Servrer rejected our request with message " + ((ConnectionResponse) connResp).getMessage());
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
```
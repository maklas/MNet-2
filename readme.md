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


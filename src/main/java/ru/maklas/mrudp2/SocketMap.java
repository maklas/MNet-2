package ru.maklas.mrudp2;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Iterator;

class SocketMap {

    Array<SocketWrap> sockets = new Array<SocketWrap>();

    public synchronized void put(InetAddress address, int port, Socket socket){
        sockets.add(new SocketWrap(address, port, socket));
    }

    public Socket get(DatagramPacket packet){
        return get(packet.getAddress(), packet.getPort());
    }

    public synchronized Socket get(InetAddress address, int port){
        for (SocketWrap socket : sockets) {
            if (socket.address.equals(address) && socket.port == port){
                return socket.socket;
            }
        }
        return null;
    }

    public synchronized void remove(Socket socket){
        for (Iterator<SocketWrap> iter = sockets.iterator(); iter.hasNext();) {
            SocketWrap wrap = iter.next();
            if (wrap.socket == socket){
                iter.remove();
                return;
            }
        }
    }

    static class SocketWrap {
        InetAddress address;
        int port;
        Socket socket;

        public SocketWrap(InetAddress address, int port, Socket socket) {
            this.address = address;
            this.port = port;
            this.socket = socket;
        }
    }

}

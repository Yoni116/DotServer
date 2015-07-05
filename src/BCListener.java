import java.io.IOException;
import java.net.*;

/**
 * the BCListener class is a support service class for the main server
 * its main job is to listen to broadcasts from clients that wants to connect to the server
 * and sends them a replay with the server info
 *
 * @author Yoni Maymon
 * @version 1.0
 * @since 02/07/2015
 * <p/>
 * P.S. : to @hen this is how you write code comments !!!!
 */

public class BCListener implements Runnable {

    private DatagramSocket socket;
    private final int bcPort = 56378;
    private int connectionPort;
    private boolean serverRunning;


    /**
     * main constructor for class
     * it receives the connection port from the main server
     *
     * @param port the port to sent to the client for connection
     */
    public BCListener(int port) {
        this.connectionPort = port;
        this.serverRunning = true;
    }

    @Override
    public void run() {
        try {
            // port 56378 will always be used for bc reason
            socket = new DatagramSocket(bcPort, InetAddress.getByName("0.0.0.0"));
            socket.setBroadcast(true);

            while (serverRunning) {
                System.out.println(getClass().getName() + ">>>Ready to receive broadcast packets on port: " + bcPort + " !");

                //Receive a packet
                byte[] recvBuf = new byte[15000];
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(packet);

                //Packet received
                System.out.println(getClass().getName() + ">>>Discovery packet received from: " + packet.getAddress().getHostAddress());
                System.out.println(getClass().getName() + ">>>Packet received; data: " + new String(packet.getData()));

                //See if the packet holds the right command (message)

                String message = new String(packet.getData()).trim();
                if (message.equals("CONTROLY DISCOVER REQUEST")) {
                    String reply = "DISCOVER CONTROLY RESPONSE PORT: " + connectionPort;
                    byte[] sendData = reply.getBytes();

                    //Send a response
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());
                    socket.send(sendPacket);

                    System.out.println(getClass().getName() + ">>>Sent packet to: " + sendPacket.getAddress().getHostAddress());

                }
            }

        } catch (SocketException e) {
            System.out.println("BCListener closed");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * let the service know that the server have been closed
     * so it cant stop listning and die
     */
    public void closeBC() {
        serverRunning = false;
        socket.close();
    }
}
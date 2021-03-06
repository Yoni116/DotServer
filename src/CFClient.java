import java.awt.im.InputContext;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

public class CFClient extends Thread {

    public static native boolean GetCapsLockState();

    private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static boolean macroBusy;

    private String clientName;
    private String ip;
    private int keyPort;
    private int mousePort;
    private Socket clientSocket;
    private boolean isRunning;
    private boolean ready = false;

    private byte[] recvBuf;
    private byte[] msgBuffer;
    private BufferedInputStream is;
    private BufferedOutputStream os;
    private String receivedMsg;
    private String returnMsg;


    private boolean isSuspended;
    private boolean capsState;


    private MacroRecorder mr;
    private CFService server;

    private Timer timer;


    public CFClient(Socket socket, String clientIP, CFService server, int keyPort, int mousePort) {
        this.isRunning = true;
        this.clientSocket = socket;
        this.ip = clientIP;
        this.server = server;
        this.isSuspended = false;
        this.keyPort = keyPort;
        this.mousePort = mousePort;
        this.clientName = "";
    }




    public String getClientName() {
        return clientName;
    }

    public String getIp() { return  ip; }



    public boolean isSuspended() {
        return isSuspended;
    }

    @Override
    public String toString() {
        return "{ ClientName: " + clientName + "\tClientIP: " + ip + " Suspended: " + isSuspended + " }";
    }

    @Override
    public int hashCode() {
        return clientSocket.hashCode() + ip.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this.ip.equals(((CFClient) obj).ip);

    }

    public void run() {

        try {
            is = new BufferedInputStream(clientSocket.getInputStream(), 512);
            os = new BufferedOutputStream(clientSocket.getOutputStream(), 512);


            while (isRunning) {
                this.recvBuf = new byte[256];
                if (is.read(recvBuf) > 0 && isRunning) {
                    receivedMsg = new String(recvBuf);
                    recvBuf = null;
                    receivedMsg = receivedMsg.trim();
                    LOGGER.info("The message: " + receivedMsg);
                    if(receivedMsg != "") {

                        String[] splitMsg = receivedMsg.split(":");

                        LOGGER.info(splitMsg[0]);


                        switch (splitMsg[0]) {

                            case "ControlyClient":
                                LOGGER.info("Checking server Channels");
                                server.checkChannels();
                                this.clientName = splitMsg[1];
                                //System.out.println(server.getHasPassword() + " "+ server.passwordProperty().getValue());
                                if (server.getHasPassword()) {
                                    System.out.println(server.getPassword());
                                    System.out.println(splitMsg[2]);
                                    if (splitMsg.length < 3 || !splitMsg[2].equals(server.getPassword().toString())) {
                                        returnMsg = "5000-ERROR:Wrong Password";
                                        msgBuffer = returnMsg.getBytes();
                                        os.write(msgBuffer);
                                        os.flush();
                                        msgBuffer = null;
                                        LOGGER.info("Connection was rejected from: " + this.clientName + " address: " + this.ip + " Reason: Wrong Password");
                                        closeClient();
                                        break;
                                    }
                                }
                                new Thread(new NotificationPopup("New Client Connected", this.clientName)).start();
                                server.addClientName(this.clientName);
                                returnMsg = "1000-OK:" + keyPort + ":" + mousePort + ":" + ControlyUtility.OSName;
                                msgBuffer = returnMsg.getBytes();
                                os.write(msgBuffer);
                                os.flush();
                                msgBuffer = null;
                                LOGGER.info("Received connection request from client: " + this.clientName + " address: " + this.ip);
                                server.printClients();
                                ready = true;
                                break;

                            case "MacroStart":
                                LOGGER.info(macroBusy ? "macro busy" : " macro free");
                                if (!macroBusy) {
                                    macroBusy = true;
                                    returnMsg = "2000-macro record started";
                                    msgBuffer = returnMsg.getBytes();
                                    os.write(msgBuffer);
                                    os.flush();
                                    msgBuffer = null;
                                    LOGGER.info("Received Macro Start Msg");

                                    if (Integer.parseInt(splitMsg[1]) == 0)
                                        mr = new MacroRecorder(false, this.ip);
                                    else
                                        mr = new MacroRecorder(true, this.ip);
                                    mr.start();
                                    //new Thread(new NotificationPopup("Recording Started", "")).start();
                                } else {
                                    returnMsg = "2002-cannot record more then one macro at a time";
                                    msgBuffer = returnMsg.getBytes();
                                    os.write(msgBuffer);
                                    os.flush();
                                }
                                break;

                            case "MacroStop":
                                LOGGER.info("Received Macro Stop Msg");
                                if(macroBusy) {
                                    mr.stopRecord();
                                    new Thread(new NotificationPopup("Recording Finished", "")).start();
                                    returnMsg = mr.buildMacro();
                                    if (returnMsg == "")
                                        returnMsg = "2001-Empty";
                                    else
                                        returnMsg = "2001-" + returnMsg;
                                    mr.finishMacro();
                                    msgBuffer = returnMsg.getBytes();
                                    os.write(msgBuffer);
                                    os.flush();
                                    msgBuffer = null;
                                    LOGGER.info(returnMsg);
                                    macroBusy = false;
                                }
                                break;

                            case "Disconnect":
                                closeClient();
                                LOGGER.info("Client Disconnected  " + this);
                                break;

                            case "Suspend":
                                if (timer != null)
                                    timer.cancel();
                                isSuspended = true;
                                LOGGER.info("Client Suspended " + this);
                                break;

                            case "UnSuspend":
                                isSuspended = false;
                                LOGGER.info("Client UnSuspend " + this);
                                break;

                            case "Pong":
                                if (timer != null)
                                    timer.cancel();
                                LOGGER.info("Received ping back from client: " + this);
                                break;

                            case "ActivateKeyboard":
                                returnMsg = "SystemInfo:CapsLockState:" + GetCapsLockState() + ":SystemLang:" + Locale.getDefault() + ":CurrentInput:" + InputContext.getInstance().getLocale().toLanguageTag();
                                returnMsg = returnMsg.trim();
                                msgBuffer = returnMsg.getBytes();
                                os.write(msgBuffer);
                                os.flush();
                                msgBuffer = null;
                                LOGGER.info(returnMsg);
                                break;


                            default:
                                LOGGER.warning("Received Wrong Message from client: " + this);
                                break;

                        }
                    } else
                        LOGGER.warning("received empty msg");
                }

            }


        } catch (IOException e) {
            LOGGER.info("IO Exception occurred - Ignore this if it happened after ping TimeOut - " + e.getMessage() + " From Client: " + this.toString());

        } finally {
            try {
                if (!clientSocket.isClosed())
                    this.clientSocket.close();
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
            server.removeClient(this);
            server.removeClientName(this.clientName);
            server.printClients();
        }
    }

    public void sentFinishedMacro(String macroID){

        returnMsg = "2003-"+macroID;
        msgBuffer = returnMsg.getBytes();
        try {
            os.write(msgBuffer);
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        msgBuffer = null;
        LOGGER.info(returnMsg);

    }

    public void closeClient() {
        isRunning = false;
        try {
            is.close();
            os.close();
        } catch (IOException e) {
            LOGGER.warning("Closing Client: " + clientName + " expect exceptions");
            LOGGER.warning(e.getMessage());
        }

    }

    public void pingClient() {
        if (ready && !isSuspended) {
            returnMsg = "Ping";
            msgBuffer = returnMsg.getBytes();
            try {
                os.write(msgBuffer);
                os.flush();
                msgBuffer = null;
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
            LOGGER.info("Pinging client: " + clientName + " ip: " + ip);

            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    LOGGER.info("TimeOut - not received ping back from client: " + clientName + " ip: " + ip);
                    closeClient();
                    try {
                        clientSocket.close();
                    } catch (IOException e) {
                        LOGGER.warning(e.getMessage());
                    }
                }
            }, 60000);
        }

    }

}

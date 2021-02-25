import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TFTPServer {
    public static final int TFTPPORT = 69;
    public static final int BUFSIZE = 516;
    public static final int MAXPACKSIZE = 512;
    public static final String READDIR = "D:\\TFTPAssighnment\\src\\Read\\"; //custom address at your PC
    public static final String WRITEDIR = "D:\\TFTPAssighnment\\src\\Write\\"; //custom address at your PC
    // OP codes
    public static final int OP_RRQ = 1;
    public static final int OP_WRQ = 2;
    public static final int OP_DAT = 3;
    public static final int OP_ACK = 4;
    public static final int OP_ERR = 5;

    // Error Codes
    public static  final int ERR_CODE0=0;
    public static  final int ERR_CODE1=1;
    public static  final int ERR_CODE2=2;
    public static  final int ERR_CODE3=3;
    public static  final int ERR_CODE5=5;
    public static  final int ERR_CODE6=6;



    public static void main(String[] args) {
        if (args.length > 0) {
            System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
            System.exit(1);
        }
        //Starting the server
        try {
            TFTPServer server = new TFTPServer();
            server.start();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void start() throws IOException {
        byte[] buf = new byte[BUFSIZE];

        // Create socket
        DatagramSocket socket = new DatagramSocket(null);

        // Create local bind point
        SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
        socket.bind(localBindPoint);

        System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

        // Loop to handle client requests
        while (true) {

            final InetSocketAddress clientAddress = receiveFrom(socket, buf);

            // If clientAddress is null, an error occurred in receiveFrom()
            if (clientAddress == null)
                continue;

            final StringBuffer requestedFile = new StringBuffer();
            final int reqtype = ParseRQ(buf, requestedFile);

            new Thread() {
                public void run() {
                    try {
                        DatagramSocket sendSocket = new DatagramSocket(0);

                        // Connect to client
                        sendSocket.connect(clientAddress);

                        System.out.printf("%s request for %s from %s using port %d\n",
                                (reqtype == OP_RRQ) ? "Read" : "Write", requestedFile,
                                clientAddress.getHostName(), clientAddress.getPort());

                        // Read request
                        if (reqtype == OP_RRQ) {
                            requestedFile.insert(0, READDIR);
                            HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
                        }
                        // Write request
                        else {
                            requestedFile.insert(0, WRITEDIR);
                            HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
                        }
                        sendSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    /**
     * Reads the first block of data, i.e., the request for an action (read or write).
     *
     * @param socket (socket to read from)
     * @param buf    (where to store the read data)
     * @return socketAddress (the socket address of the client)
     */
    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws IOException {
        DatagramPacket receivedPacket = new DatagramPacket(buf, buf.length);
        socket.receive(receivedPacket);
        InetSocketAddress socketAddress = new InetSocketAddress(receivedPacket.getAddress(), receivedPacket.getPort());
        // Create datagram packet

        // Receive packet

        // Get client address and port from the packet

        return socketAddress;

    }

    /**
     * Parses the request in buf to retrieve the type of request and requestedFile
     *
     * @param buf           (received request)
     * @param requestedFile (name of file to read/write)
     * @return opcode (request type: RRQ or WRQ)
     */
    private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
        ByteBuffer recieved = ByteBuffer.wrap(buf);
        byte[] toRead = new byte[600];
        int bytesRead = 0;
        for (int i = 2; i < buf.length; i++) {
            if (buf[i] != 0) {
                toRead[bytesRead] = buf[i];
                bytesRead++;
            } else {
                break;
            }
        }
        String filename = new String(toRead, 0, bytesRead);
        requestedFile.append(filename);
        int opcode = recieved.getShort();
        // See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents

        return opcode;
    }

    /**
     * Handles RRQ and WRQ requests
     *
     * @param sendSocket    (socket used to send/receive packets)
     * @param requestedFile (name of file to read/write)
     * @param opcode        (RRQ or WRQ)
     */
    private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) throws IOException {
        if (opcode == OP_RRQ) {
            // See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
            boolean result = send_DATA_receive_ACK(sendSocket, requestedFile);
        } else if (opcode == OP_WRQ) {
            boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
        } else {
            System.err.println("Invalid request. Sending an error packet.");
            // See "TFTP Formats" in TFTP specification for the ERROR packet contents
            byte [] err=create_ERR_Package(4,"Illegal TFTP operation");
            DatagramPacket errPack=new DatagramPacket(err,err.length);
            sendSocket.send(errPack);
            return;
        }
    }

    /**
     * To be implemented
     */
    private boolean send_DATA_receive_ACK(DatagramSocket interSocket, String fileName) throws IOException {
        int block = 1;
        File requestedFile = new File(fileName);

        try {

        if (!requestedFile.exists()) {  // Check if the requested file exists
            byte [] err=create_ERR_Package(ERR_CODE1,"File Not Found");
            DatagramPacket errPack=new DatagramPacket(err,err.length);
            interSocket.send(errPack);
            System.err.println("File not found");
            return false;
            // End connection?
        }
        FileInputStream readFile = new FileInputStream(requestedFile);
        boolean countinueSending = true;

        while (countinueSending) {                                         // Package Creating and Sending Loop
            byte[] dataPart = new byte[MAXPACKSIZE];
            int amountRead = readFile.read(dataPart);
            if (amountRead == 512) {  // Check if it is the last package to be sent
                byte[] fin_DATA = created_DATA_Package(amountRead, dataPart, block);
                DatagramPacket sendPacket = new DatagramPacket(fin_DATA, fin_DATA.length, interSocket.getInetAddress(), interSocket.getPort());


                int repeats = 0;

                byte[] ackRecieved = new byte[BUFSIZE];
                short opcodeRecieved;
                short ACK_BlockRecived;

                interSocket.setSoTimeout(600);

                while (true) {
                    interSocket.send(sendPacket);
                    try {
                        DatagramPacket ACK_Res = new DatagramPacket(ackRecieved, BUFSIZE);
                        interSocket.receive(ACK_Res);
                        ByteBuffer recievedBuff = ByteBuffer.wrap(ackRecieved);
                        opcodeRecieved = recievedBuff.getShort();


                        if (ACK_Res.getSocketAddress().equals(interSocket.getRemoteSocketAddress())) {  // Check if it is the right computer sending package
                            if (opcodeRecieved == (short) OP_ACK) { // Check if it is the correct opcode
                                ACK_BlockRecived = recievedBuff.getShort(2);
                                if (ACK_BlockRecived == (short) block) { // check if it is the right block of data being ACK
                                    break;
                                } else {
                                    byte [] err=create_ERR_Package(ERR_CODE0,"Wrong Acknowledgement");
                                    DatagramPacket errPack=new DatagramPacket(err,err.length,interSocket.getInetAddress(), interSocket.getPort());
                                    interSocket.send(errPack);

                                    countinueSending = false;
                                    break;
                                }

                            } else {
                                byte [] err=create_ERR_Package(ERR_CODE0,"Wrong Type of Package");
                                DatagramPacket errPack=new DatagramPacket(err,err.length,interSocket.getInetAddress(), interSocket.getPort());
                                interSocket.send(errPack);

                                countinueSending = false;
                                break;
                            }
                        } else {
                            byte [] err=create_ERR_Package(ERR_CODE5,"Unknown Transfer ID");
                            DatagramPacket errPack=new DatagramPacket(err,err.length,ACK_Res.getAddress(),ACK_Res.getPort());
                            interSocket.send(errPack);
                            repeats++;
                            if (repeats == 7) {
                                break;
                            } else {
                                continue;
                            }
                        }

                    } catch (SocketException e) {
                        repeats++;
                        if (repeats == 7) {
                            countinueSending = false;
                            break;
                        }
                    }
                }
                block++;
            } else {
                byte[] fin_DATA = created_DATA_Package(amountRead, dataPart, block);
                DatagramPacket sendPacket = new DatagramPacket(fin_DATA, fin_DATA.length, interSocket.getInetAddress(), interSocket.getPort());
                interSocket.send(sendPacket);
                countinueSending = false;

            }

        }
            readFile.close();
        }catch (IOException e){
            byte [] err=create_ERR_Package(ERR_CODE2,"Access violation");
            DatagramPacket errPack=new DatagramPacket(err,err.length);
            interSocket.send(errPack);

        }
        return false;
    }

    private boolean receive_DATA_send_ACK(DatagramSocket interSocket, String fileName) throws IOException {
        File WR_REQ = new File(fileName);
        try {
            if (WR_REQ.exists()) {          // Check if the file already exist
                byte[] err = create_ERR_Package(ERR_CODE6, "File already exists");
                DatagramPacket errPack = new DatagramPacket(err, err.length);
                interSocket.send(errPack);
                return false;
            } else {
                FileOutputStream writeToFile = new FileOutputStream(WR_REQ);
                boolean countinueRecieving = true;
                int block = 0;

                while (countinueRecieving) {

                    DatagramPacket ackPack = new DatagramPacket(create_ACK_Package(block), 4, interSocket.getInetAddress(), interSocket.getPort());
                    interSocket.send(ackPack);


                    int repeats = 0;
                    interSocket.setSoTimeout(600);
                    while (true) {
                        byte[] recieved = new byte[516];
                        DatagramPacket recievedPack = new DatagramPacket(recieved, BUFSIZE);

                        try {
                            interSocket.receive(recievedPack);
                            ByteBuffer buff = ByteBuffer.wrap(recieved);
                            short opcode = buff.getShort();
                            if (recievedPack.getSocketAddress().equals(interSocket.getRemoteSocketAddress())) {    // Check if the package is from the right computer
                                if (opcode == (short) OP_DAT) {     // Check if it is the right OP code

                                    short block_ACK = buff.getShort(2);
                                    if (block_ACK == (short) block + 1) { // Check if the right block is recieved

                                        byte[] whole_Package = recievedPack.getData();
                                        byte[] data_Array = Arrays.copyOfRange(whole_Package, 4, recievedPack.getLength());

                                        File directory = new File(WRITEDIR);
                                        long usableSpace = directory.getUsableSpace();
                                        if (usableSpace >= data_Array.length) {  // Check if there is enough space to save the data
                                            writeToFile.write(data_Array);
                                            writeToFile.flush();
                                            if (data_Array.length < 512) {    // Check if it is the last package of data
                                                System.out.println(data_Array.length);
                                                countinueRecieving = false;
                                                break;
                                            } else {
                                                block++;
                                                break;
                                            }
                                        } else {
                                            // send error no space
                                            byte[] errorPackage = create_ERR_Package(ERR_CODE3, "Disk full or allocation exceeded");
                                            DatagramPacket noSpaceError = new DatagramPacket(errorPackage, errorPackage.length);
                                            interSocket.send(noSpaceError);
                                            countinueRecieving = false;
                                            break;
                                        }

                                    } else {
                                        byte[] errorPackage = create_ERR_Package(ERR_CODE0, "Wrong Block Acknowledged");
                                        DatagramPacket noSpaceError = new DatagramPacket(errorPackage, errorPackage.length);
                                        interSocket.send(noSpaceError);
                                        countinueRecieving = false;
                                        break;

                                    }

                                } else {
                                    byte[] errorPackage = create_ERR_Package(ERR_CODE0, "Wrong Package Type");
                                    DatagramPacket noSpaceError = new DatagramPacket(errorPackage, errorPackage.length);
                                    interSocket.send(noSpaceError);
                                    countinueRecieving = false;
                                    break;
                                }

                            } else {
                                byte[] err = create_ERR_Package(ERR_CODE5, "Unknown Transfer ID");
                                DatagramPacket errPack = new DatagramPacket(err, err.length, recievedPack.getAddress(), recievedPack.getPort());
                                interSocket.send(errPack);
                                // send error massesge to that server and countinue
                                repeats++;
                                if (repeats == 7) {      // check if it is the 7th time sent
                                    break;
                                } else {
                                    continue;
                                }

                            }

                        } catch (SocketException e) {
                            repeats++;
                            if (repeats == 7) {
                                countinueRecieving = false;
                                break;
                            }

                        }

                    }


                }
                writeToFile.close();

            }
        }catch (IOException e){
            byte[] errorPackage = create_ERR_Package(ERR_CODE2, "Access violation");
            DatagramPacket noSpaceError = new DatagramPacket(errorPackage, errorPackage.length);
            interSocket.send(noSpaceError);
        }


        return false;
    }


    private byte[] created_DATA_Package(int amountRead, byte[] dataPartInit, int block) throws IOException { // Method to create DATA array

        byte[] dataPart = new byte[amountRead];
        for (int i = 0; i < amountRead; i++) {
            dataPart[i] = dataPartInit[i];
        }

        ByteBuffer thepackage = ByteBuffer.allocate(amountRead + 4);
        thepackage.putShort((short) OP_DAT);
        thepackage.putShort((short) block);
        thepackage.put(dataPart);
        byte[] arrayToSend = thepackage.array();
        return arrayToSend;
    }

    private byte[] create_ACK_Package(int block) {      // Method to create ACK array

        ByteBuffer buff = ByteBuffer.allocate(4);
        short opcode = (short) OP_ACK;
        short blockNumber = (short) block;
        buff.putShort(opcode);
        buff.putShort(blockNumber);
        byte[] ACK_Array = buff.array();
        return ACK_Array;
    }

    private byte[] create_ERR_Package(int errorCode, String errorMessage) {      // Method to create error arrays
        ByteBuffer buff = ByteBuffer.allocate(errorMessage.length()+5);
        short opcode = (short) OP_ERR;
        short shorError = (short) errorCode;
        byte[] stringErr = errorMessage.getBytes();
        byte endString = 0x00;
        buff.putShort(opcode);
        buff.putShort(shorError);
        buff.put(stringErr);
        buff.put(endString);
        return buff.array();
    }

}

package lesson5_channel.client;

import lesson5_channel.client.bean.ServerInfo;
import lesson5_channel.constants.UDPConstants;
import lesson5_channel.uitls.ByteUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @Created by jdchi
 * @Date 2019/5/22
 * @Description
 **/
public class UDPSearcher {

    private static final int LISTEN_PORT = UDPConstants.PORT_CLIENT_RESPONSE;

    public static ServerInfo searchServer(int timeout) throws InterruptedException, IOException {

        System.out.println("UDPSearcher Started");

        CountDownLatch receiveLatch = new CountDownLatch(1);

        Listener listener = listen(receiveLatch);
        sendBroadcast();
        receiveLatch.await(timeout , TimeUnit.MILLISECONDS);

        System.out.println("UDPSearcher Finished");

        if (listener == null) {
            return null;
        }

        List<ServerInfo> devices = listener.getServerAndClose();
        if (devices.size() > 0) {
            return devices.get(0);
        }

        return null;
    }

    private static void sendBroadcast() throws IOException {
        System.out.println("UDPSearcher sendBroadcast started");

        DatagramSocket datagramSocket = new DatagramSocket();

        ByteBuffer byteBuffer = ByteBuffer.allocate(128);

        byteBuffer.put(UDPConstants.HEADER);
        byteBuffer.putShort((short) 1);
        byteBuffer.putInt(LISTEN_PORT);

        DatagramPacket requestPacket = new DatagramPacket(byteBuffer.array() , byteBuffer.position() + 1);
        requestPacket.setAddress(InetAddress.getByName("255.255.255.255"));

        requestPacket.setPort(UDPConstants.PORT_SERVER);

        datagramSocket.send(requestPacket);

        datagramSocket.close();
        System.out.println("UDPSearcher sendBroadcast finished");

    }

    private static Listener listen(CountDownLatch receiveLatch) throws InterruptedException {
        System.out.println("UDPSearcher start listen.");
        CountDownLatch startDownLatch = new CountDownLatch(1);
        Listener listener = new Listener(LISTEN_PORT , startDownLatch , receiveLatch);
        listener.start();
        startDownLatch.await();
        return listener;
    }


    private static class Listener extends Thread{
        private final int listenPort;
        private final CountDownLatch startDownLatch;
        private final CountDownLatch receiveDownLatch;
        private final List<ServerInfo> serverInfoList = new ArrayList<>();
        private final byte[] buffer = new byte[128];
        private final int minLen = UDPConstants.HEADER.length + 2 + 4;
        private boolean done = false;
        private DatagramSocket ds = null;

        private Listener(int listenPort , CountDownLatch startDownLatch , CountDownLatch receiveDownLatch){
            this.listenPort = listenPort;
            this.startDownLatch = startDownLatch;
            this.receiveDownLatch = receiveDownLatch;
        }

        @Override
        public void run() {
            super.run();

            startDownLatch.countDown();

            try {
                ds = new DatagramSocket(listenPort);

                DatagramPacket receivePack = new DatagramPacket(buffer , buffer.length);

                while (!done) {
                    ds.receive(receivePack);

                    String ip = receivePack.getAddress().getHostAddress();
                    int port = receivePack.getPort();
                    int dataLen = receivePack.getLength();
                    byte[] data = receivePack.getData();
                    boolean isValid = dataLen >= minLen && ByteUtils.startsWith(data , UDPConstants.HEADER);

                    System.out.println("UDPSearcher receive from ip: " + ip + "\nport: " + port + "\n dataValid: " + isValid);

                    if (!isValid) {
                        continue;
                    }

                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer , UDPConstants.HEADER.length , dataLen);
                    final short cmd = byteBuffer.getShort();
                    final int serverPort = byteBuffer.getInt();

                    if (cmd != 2 || serverPort <= 0) {
                        System.out.println("UDPSearcher receive cmd:" + cmd + "\nserverPort:" + serverPort);
                        continue;
                    }

                    String sn = new String(buffer , minLen , dataLen - minLen);
                    ServerInfo serverInfo = new ServerInfo(serverPort , ip , sn);
                    serverInfoList.add(serverInfo);

                    receiveDownLatch.countDown();

                }


            } catch (IOException e) {
            }finally {
                close();
            }

            System.out.println("UDPSearcher listener finished");
        }

        private void close(){
            if (ds != null) {
                ds.close();
                ds = null;
            }
        }

        public List<ServerInfo> getServerAndClose() {
            done = true;
            close();
            return serverInfoList;
        }
    }


}

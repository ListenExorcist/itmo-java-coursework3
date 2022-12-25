package ru.itmo.coursework3;

import ru.itmo.coursework3.common.Connection;
import ru.itmo.coursework3.common.Message;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Server {
    private final int port;
    private final BlockingQueue<Message> messages;
    private final Map<String, Connection<Message>> connections;

    public Server(int port) {
        this.port = port;
        messages = new ArrayBlockingQueue<>(50);
        connections = new HashMap<>();
    }

    public static void main(String[] args) {
        Properties properties = new Properties();
        try (InputStream iS = Client.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {
            properties.load(iS);
        } catch (IOException e) {
            System.out.println("Проблемы с чтением config.properties");
        }
        int port = Integer.parseInt(properties.getProperty("port").trim());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
        Server server = new Server(port);
        server.run();
    }

    private void run() {
        System.out.println("Сервер запущен");
        Thread messageSender = new Thread(new MessageSender());
        messageSender.start();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Connection<Message> connection = new Connection<>(socket);
                Thread messageReceiver = new Thread(new MessageReceiver(connection));
                messageReceiver.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MessageSender implements Runnable {
        @Override
        public void run() {
            while (true) {
                for (Message message : messages) {
                    for (Map.Entry<String, Connection<Message>> entry : connections.entrySet()) {
                        if (!message.getSender().equals(entry.getKey())) {
                            try {
                                entry.getValue().sendMessage(message);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    messages.remove(message);
                }
            }
        }
    }

    private class MessageReceiver implements Runnable {
        private final Connection<Message> connection;
        private String name = null;

        public MessageReceiver(Connection<Message> connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            while (true) {
                Message fromClient = null;
                try {
                    fromClient = connection.readMessage();
                    name = fromClient.getSender();
                    if (!connections.containsKey(name)) {
                        connections.put(name, connection);
                    }
                } catch (SocketException e) {
                    connections.remove(name);
                    System.out.println("Пользователь " + name + " отключился от сервера");
                    for (Connection<Message> connection : connections.values()) {
                        try {
                            connection.sendMessage(new Message("Сервер", "Пользователь " + name + " покинул чат"));
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                    return;
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
                System.out.println("От клиента: " + fromClient);
                messages.add(fromClient);
            }
        }
    }
}

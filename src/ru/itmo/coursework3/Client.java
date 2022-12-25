package ru.itmo.coursework3;

import ru.itmo.coursework3.common.Connection;
import ru.itmo.coursework3.common.Message;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Scanner;

public class Client {
    private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy");
    private final String NAME;
    private Connection<Message> connection;

    public Client(String ip, int port, String name) {
        NAME = name;
        try {
            connection = new Connection<>(new Socket(ip, port));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        String ip = properties.getProperty("ip").trim();
        String[] ipParts = ip.split("\\.");
        for (String s : ipParts) {
            int ipPart = Integer.parseInt(s);
            if (ipPart < 0 || ipPart > 255) {
                throw new IllegalArgumentException("Invalid ip");
            }
        }
        int port = Integer.parseInt(properties.getProperty("port").trim());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
        Scanner scanner = new Scanner(System.in);
        System.out.println("Введите имя");
        String name = scanner.nextLine();
        Client client = new Client(ip, port, name);
        client.run();
    }

    private void run() {
        Thread messageReceiver = new Thread(new MessageReceiver());
        messageReceiver.start();
        Thread messageSender = new Thread(new MessageSender());
        messageSender.start();
    }

    private class MessageSender implements Runnable {
        @Override
        public void run() {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String text = scanner.nextLine();
                if (text.equals("/exit")) {
                    System.exit(0);
                }
                Message message = new Message(NAME, text);
                try {
                    connection.sendMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MessageReceiver implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Message fromServer = connection.readMessage();
                    if (fromServer.getSender().equals("Сервер")) {
                        System.out.println(fromServer.getDateTime().format(FORMATTER) +
                                " сообщение от Сервера: " +
                                fromServer.getText());
                    } else {
                        System.out.println(fromServer.getDateTime().format(FORMATTER) +
                                " сообщение от пользователя " +
                                fromServer.getSender() + ": " +
                                fromServer.getText());
                    }
                } catch (SocketException e) {
                    System.out.println("Сервер не отвечает. Приложение будет закрыто");
                    System.exit(1);
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

package com.baifendian.udp.example.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.receiver.Receiver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.Socket;

public class JavaCustomReceiver extends Receiver<String> {
  public static void main(String[] args) throws InterruptedException {
    if (args.length < 2) {
      System.err.println("Usage: JavaCustomReceiver <hostname> <port>");
      System.exit(1);
    }

    // StreamingExamples.setStreamingLogLevels();
    // Create the context with a 1 second batch size
    SparkConf sparkConf = new SparkConf().setAppName("JavaCustomReceiver");
    JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, new Duration(1000));
    // Create a input stream with the custom receiver on target ip:port and count the
    // words in input stream of \n delimited text (eg. generated by 'nc')
    JavaReceiverInputDStream<String> lines = ssc.receiverStream(
        new JavaCustomReceiver(args[0], Integer.parseInt(args[1])));

    lines.print();
    ssc.start();
    ssc.awaitTermination();
  }

  String host = null;
  int port = -1;

  public JavaCustomReceiver(String host_ , int port_) {
    super(StorageLevel.MEMORY_AND_DISK_2());
    host = host_;
    port = port_;
  }

  public void onStart() {
    // Start the thread that receives data over a connection
    new Thread(this::receive).start();
  }

  public void onStop() {
    // There is nothing much to do as the thread calling receive()
    // is designed to stop by itself if isStopped() returns false
  }

  /** Create a socket connection and receive data until receiver is stopped */
  private void receive() {
    try {
      Socket socket = null;
      BufferedReader reader = null;
      String userInput = null;
      try {
        // connect to the server
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // Until stopped or connection broken continue reading
        while (!isStopped() && (userInput = reader.readLine()) != null) {
          //System.out.println("Received data '" + userInput + "'");
          store(userInput);
        }
      } finally {
        reader.close();
        socket.close();
      }
      // Restart in an attempt to connect again when server is active again
      restart("Trying to connect again");
    } catch(ConnectException ce) {
      // restart if could not connect to server
      restart("Could not connect", ce);
    } catch(Throwable t) {
      restart("Error receiving data", t);
    }
  }
}

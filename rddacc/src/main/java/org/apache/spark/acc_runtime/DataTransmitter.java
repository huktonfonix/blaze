package org.apache.spark.acc_runtime;

import java.lang.InterruptedException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.io.*;
import java.util.*;
import java.net.Socket;
import java.net.SocketException;
import java.lang.System;

/**
* The class is in charge of maintaining the connection between Spark and Accelerator manager.
* DataTransmitter is used for building the connection, send and receive the data between Spark
* and Accelerator manager.
**/
public class DataTransmitter {
	Socket acc_socket = null;

	public DataTransmitter() {
		init("127.0.0.1", 1027);
	}

	/**
	* Initialize connection.
	* Initialize the connection. This method should be called only once to avoid 
	* duplicated connections.
	*
	* @param hostname 
	*		The hostname of the Accelerator manager.
	* @param port
	*		The port of the Accelerator manager.
	**/
	public void init(String hostname, int port) {
		try {
	  	acc_socket = new Socket(hostname, port); 
		}
		catch (Exception e) {
			; // do nothing
		}
		finally {
			; // do nothing
		}
	}

	/**
	* Check connection between Manager.
	* Check if the socket is built with Node Manager.
	*
	* @return 
	*		True if the connection is built successfully.
	**/
	public boolean isConnect() {
		if (acc_socket == null)
			return false;
		else
			return true;
	}

	/**
	* Send message to Accelerator manager.
	* This method also builds the connection when it is called first time by launching 
	* method init.
	*
	* @param msg
	*		The message to be sent.
	*	@see init(String hostname, int port)
	**/
	public void send(AccMessage.TaskMsg.Builder msgBuilder)
    throws IOException {

		AccMessage.TaskMsg msg = msgBuilder.build();
    int msg_size = msg.getSerializedSize();

    // send byte size
    DataOutputStream sout = new DataOutputStream(acc_socket.getOutputStream());
    sout.write(ByteBuffer.allocate(4)
        .order(ByteOrder.nativeOrder())
        .putInt(msg_size).array(),0,4);
    sout.flush();

    // send message data
    msg.writeTo(sout);
    sout.flush();
  }

	/**
	* Receive the message from Accelerator manager.
	**/
  public AccMessage.TaskMsg receive()
    throws IOException {

    DataInputStream sin = new DataInputStream(acc_socket.getInputStream());

    int msg_size = ByteBuffer.allocate(4)
      .putInt(sin.readInt())
      .order(ByteOrder.nativeOrder())
      .getInt(0);

    byte[] msg_data = ByteBuffer.allocate(msg_size).array();

    sin.read(msg_data, 0, msg_size);

    return AccMessage.TaskMsg.parseFrom(msg_data);
  }

	/**
	* Create a task message for requesting.
	*
	* @param acc_id
	*		The accelerator ID. Should be provided by accelerator library.
	* @param blockId
	*		The unique block ID.
	* @return The message which hasn't been built.
	**/
	public AccMessage.TaskMsg.Builder buildRequest(String acc_id, int[] blockId) {
		AccMessage.TaskMsg.Builder msg = AccMessage.TaskMsg.newBuilder()
			.setType(AccMessage.MsgType.ACCREQUEST)
			.setAccId(acc_id);

			for (int id: blockId) {
				AccMessage.DataMsg.Builder data = AccMessage.DataMsg.newBuilder()
					.setPartitionId(id);
				msg.addData(data);
			}
		
		return msg;
	}

	/**
	* Create an empty message with specific type.
	*
	* @param type
	*		The message type.
	* @see AccMessage.MsgType
	**/
	public AccMessage.TaskMsg.Builder buildMessage(AccMessage.MsgType type) {
		AccMessage.TaskMsg.Builder msg = AccMessage.TaskMsg.newBuilder()
			.setType(type);

		return msg;
	}

	/**
	* Add a data block.
	* Create and add a data block to assigned message with full information. 
	*
	*	@param msg The message that wanted to be added.
	* @param id The unique ID of the data block.
	* @param length The number of element of the data.
	* @param size The file size of either memory mapped file or HDFS file.
	* @param offset The start position of this block in the file.
	* @param path The file path.
	**/
	public void addData(AccMessage.TaskMsg.Builder msg, int id, int length, int size, int offset, String path) {
		AccMessage.DataMsg.Builder data = AccMessage.DataMsg.newBuilder()
			.setPartitionId(id)
			.setLength(length)
			.setSize(size)
			.setOffset(offset)
			.setPath(path);

		msg.addData(data);
		return ;
	}

	/**
	* Add a scalar data block.
	* Create and add a data block with a scalar data. This is only used for 
	* broadcasting scalar variables.
	*
	*	@param msg The message that wanted to be added.
	* @param id The unique ID of the data block.
	* @param value The value of the scalar variable.
	**/
	public void addScalarData(AccMessage.TaskMsg.Builder msg, int id, long value) {
		AccMessage.DataMsg.Builder data = AccMessage.DataMsg.newBuilder()
			.setPartitionId(id)
			.setBval(value);

		msg.addData(data);
		return ;
	}

	/**
	* Add a broadcast block.
	* Create and add a broadcast block with broadcast ID. This is only used for 
	* indicating the Manager about which broadcast data block should be used.
	*
	* '''Note''' The broadcast data block must be transmitted to the Manager in advance.
	*
	*	@param msg The message that wanted to be added.
	* @param id The unique ID of the data block.
	* @param value The value of the scalar variable.
	**/
	public void addBroadcastData(AccMessage.TaskMsg.Builder msg, int id) {
		AccMessage.DataMsg.Builder data = AccMessage.DataMsg.newBuilder()
			.setPartitionId(id);

		msg.addData(data);
		return ;
	}
}

/**************************************************************
 * This file is part of WEX Software.
 * Copyright (c) Farakh JAVID 2016-2017, All Rights Reserved
 * Author : Farakh JAVID
 * E-mail : farakh.javid@gmail.com
 **************************************************************/

package com.app.wex;

import android.os.Handler;

import android.util.Log;

import android.content.Context;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import android.graphics.Bitmap;

// Wifi
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;

// Sockets
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import android.telephony.TelephonyManager;


// This class manages the Wex P2P data exchange protocol.
public class WexP2PProtocol
{
    private static final String TAG = "wex-WexP2PProtocol";
    
    /****************************************************************************************************************/    
    // This byte is written at the beginning of every data sent through socket, in order to identify the data type (pseudo, picture...) :

    // NEW prefixes (1-256) :
    public static final int PREFIX__MAC_ADDRESS    = 1;   // used to send this device MAC address to the GO.
    public static final int PREFIX__MAC_IP_ADDRESS = 2;   // used by the Group Owner to broadcast the peer IP to all connected peers.
    public static final int PREFIX__PSEUDO         = 3;   // used to send this device pseudo to the target peer.
    public static final int PREFIX__PICTURE        = 4;   // used to send this device picture to the target peer.
    public static final int PREFIX__MAIL           = 5;   // 
    public static final int PREFIX__GENDER         = 6;   // 
    public static final int PREFIX__AGE            = 7;   // 
    public static final int PREFIX__STATUS         = 8;   // 
    public static final int PREFIX__JOB            = 9;   // 
    public static final int PREFIX__STORY          = 10;  // 
    public static final int PREFIX__QUESTION       = 11;  // 

    public static final int PREFIX__REQUEST_PROFILE = 20;  // used by this device to request the Group Owner profile.
    public static final int PREFIX__ANSWER          = 21;  // used by this device to send an "answer" (to the "question") to a remote peer.

    public static final int PREFIX__TEST1        = 30;  // used for test.
    
    /****************************************************************************************************************/
    
    public static final int EXECUTE_SENDMYMACTOGO              = 1;   // 
    public static final int EXECUTE_BROADCASTPEERIP            = 2;   // 
    public static final int EXECUTE_CONNECTANDWRITEBEACONDATA  = 3;   // 
    public static final int EXECUTE_TESTCONNECTANDSENDDATA     = 4;   // 
    public static final int EXECUTE_SENDMYPROFILETOGO          = 5;   //
    //public static final int EXECUTE_REQUESTPROFILEFROMGO       = 6;   //
    public static final int EXECUTE_SENDGOPROFILETOPEER        = 7;   // 
    public static final int EXECUTE_ONCONNECTIONINFOAVAILABLE  = 8;   // 
    public static final int EXECUTE_BROADCASTPEERIP_TEST       = 9;   // 
    public static final int EXECUTE_CREATEINETADDRESS          = 10;  // 
    public static final int EXECUTE_SENDMYANSWERTOPEER         = 11;  //
    
    /****************************************************************************************************************/
    
    
    
    
    // Returns a unique ID for this device.
    public static String getMyUniqueID(Context context) {
	// Generate a constant unique ID (the ID is always the same) :
	TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
	
	String tmDevice, tmSerial, androidId;
	tmDevice = "" + tm.getDeviceId();
	tmSerial = "" + tm.getSimSerialNumber();
	androidId = "" + android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
	
	UUID uniquePeerID = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
	Log.d(TAG, "getMyUniqueID(...) :  CONSTANT uniquePeerID.toString() = " + uniquePeerID.toString());
	return uniquePeerID.toString();
    }
    
    
    // Only called by the client peers (i.e. not called by the Group Owner).
    // Send the device MAC address to the Group Owner.
    // The GO then broadcasts this IP to the other connected peers in the group (see in ConnectionManager).
    public static void sendMyMACtoGO(Context context, InetAddress groupOwnerIPAddress) {
	Log.d(TAG, "sendMyMACtoGO(...) :");
	String macAddress = getMyUniqueID(context);  // get my MAC address.
	connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__MAC_ADDRESS, macAddress.getBytes());
    }
    
    // The Group Owner sends his MAC address to the given remote peer.
    public static void sendGOMACtoPeer(Context context, InetAddress remotePeerIPAddress) {
	sendMyMACtoGO(context, remotePeerIPAddress);
    }
    
    
    // Send the profile of this device to the Group Owner.
    // This function is also used to send the GO profile to a remote peer.
    public static void sendMyProfileToGO(InetAddress groupOwnerIPAddress, Context context) {
	WexDBModel myProfileData = wexGUI.myProfileData;  // get my profile data.

	if(myProfileData != null) {
	    // send the pseudo, the story... :
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__PSEUDO, myProfileData.getPseudo().getBytes());
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__STORY, myProfileData.getStory().getBytes());
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__GENDER, myProfileData.getGender().getBytes());	    
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__AGE, myProfileData.getAge().getBytes());
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__STATUS, myProfileData.getStatus().getBytes());
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__JOB, myProfileData.getJob().getBytes());
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__QUESTION, myProfileData.getQuestion().getBytes());

	    // send the picture :
	    Bitmap profilePic = PictureManager.getScaledPicture(myProfileData.getPicPath());
	    byte[] picByteArray = IOManager.convertBitmapToByteArray(profilePic);
	    Log.d(TAG, "picByteArray toString = " + picByteArray.toString() + "::endOfByteArrayToString");
	    connectAndWriteBeaconData(groupOwnerIPAddress, WexP2PProtocol.PREFIX__PICTURE, picByteArray);
	}
    }
    
    // Send the profile of the Group Owner to the peer with the given IP address.
    public static void sendGOProfileToPeer(InetAddress remotePeerIPAddress, Context context) {
	sendMyProfileToGO(remotePeerIPAddress, context);
    }
    
    
    // Only called by peers other than the Group Owner.
    // The device asks the Group Owner to send him its profile.
    public static void requestProfileFromGO(InetAddress groupOwnerIPAddress, Context context) {
	connectAndWriteBeacon(groupOwnerIPAddress, WexP2PProtocol.PREFIX__REQUEST_PROFILE);
    }
    

    // Send the given answer to the target peer.
    public static void sendMyAnswerToPeer(InetAddress remotePeerIPAddress, String answerText) {
	Log.d(TAG, "sendMyAnswerToPeer(...) :");
	connectAndWriteBeaconData(remotePeerIPAddress, WexP2PProtocol.PREFIX__ANSWER, answerText.getBytes());
	Log.d(TAG, "sendMyAnswerToPeer(...) : DONE.");
    }
    
    
    // NOT YET USEFUL because : the Group Owner already broadcasts the new peers MAC/IP addresses.
    /*
    // Called by client peers (i.e. not called by Group Owner).
    // The device asks the Group Owner to send him the list of the connected peers IP addresses.
    public static void requestDevicesIPs(InetAddress groupOwnerIPAddress) {
	
	
    }
    */
    
    
    // The Group Owner broadcasts the new connected peer MAC/IP to each connected peer.
    // macAddress, ipAddress are the MAC/IP of the new peer, these data are to be broadcasted.
    public static void goBroadcastNewPeerIPtoConnectedPeers(String macAddress, InetAddress ipAddress) {
	Log.d(TAG, "goBroadcastNewPeerIPtoConnectedPeers(...) :");
	WexPeersData connectedPeers = null;
	Map peersList = null;
	
	connectedPeers = WexTabCommunity.connectedPeers;  // get the connected peers.
	
	if(connectedPeers != null) {
	    peersList = connectedPeers.getPeersList();
	}
	
	Set keysList = null;
	if(peersList != null) {
	    keysList = peersList.keySet();  // get the keys (i.e. the MAC addresses) from peersList.
	}
	Iterator it = null;
	if(keysList != null) {
	    it = keysList.iterator();  // defines an iterator on the keys.
	}
	while(it.hasNext()) {  // traverse the keys (i.e. traverse the connected peers) :
	    String keyMACaddress = (String)it.next();  // get the current key.
	    if(macAddress != keyMACaddress) {  // this test prevents to send the new connected peer data to itself.
		WexDBModel wDBModel = (WexDBModel)peersList.get(keyMACaddress);  // get the current peer data.
		broadcastPeerIP(wDBModel, macAddress, ipAddress);  // broadcast the IP.
	    }
	}
	
	Log.d(TAG, "goBroadcastNewPeerIPtoConnectedPeers(...) : DONE.");
    }
    
    
    // Broadcasts the MAC/IP to the given peer wDBModel.
    // macAddress, ipAddress are the MAC/IP of the new peer, these data are to be broadcasted.
    public static void broadcastPeerIP(WexDBModel wDBModel, String macAddress, InetAddress ipAddress) {
	Log.d(TAG, "aqsd broadcastPeerIP(...) :");
	String macIpAddresses = "";
	InetAddress targetPeerIP = null;
	if(wDBModel != null) {
	    targetPeerIP = wDBModel.getP2PDeviceIPAddress();  // get the target peer IP address, i.e. the peer to which the data are sent.
	    Log.d(TAG, "broadcastPeerIP(...) :  deb0,  targetPeerIP = " + targetPeerIP.getHostAddress());
	}
	
	if(ipAddress != null) {
	    macIpAddresses = macAddress + "__" + ipAddress.getHostAddress();  // builds a string : MACaddress__IPaddress, ex : stringData = "f0:1f:af:09:74:49__192.168.0.10".
	    Log.d(TAG, "broadcastPeerIP(...) :  deb1,  macAddress                 = " + macAddress);
	    Log.d(TAG, "broadcastPeerIP(...) :  deb1,  ipAddress                  = " + ipAddress.getHostAddress());
	    Log.d(TAG, "broadcastPeerIP(...) :  deb1,  macIpAddresses             = " + macIpAddresses);
	}
	
	connectAndWriteBeaconData(targetPeerIP, WexP2PProtocol.PREFIX__MAC_IP_ADDRESS, macIpAddresses.getBytes());  // send the MAC/IP to the WexP2PProtocol::targetPeerIP.
	Log.d(TAG, "broadcastPeerIP(...) : DONE.");
    }
    
    
    // Close the given socket.
    // First close the socket output stream, then close the socket itself.
    private static void closeSocket(Socket socket) {
	OutputStream oStream = null;
	try {
	    if(socket != null) {
		oStream = socket.getOutputStream();
		if(oStream != null)
		    oStream.close();  // first close the socket output stream.
		socket.close();       // close the socket.
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }
    
    
    // This function :
    //     1) connects to a remote peer (targetPeerIPAddress)
    //     2) sends a beacon (beacon) to the remote peer.
    //     3) sends the corresponding data (dataBuffer).
    //     4) close the socket to end the transmission.
    public static void connectAndWriteBeaconData(InetAddress targetPeerIPAddress, int beacon, byte[] dataBuffer) {
	Log.d(TAG, "connectAndWriteBeaconData(...) :");
	Socket socket = null;
	OutputStream oStream = null;
	
	try {
	    socket = new Socket();  // the socket used to connect to the target peer.
	    
	    if(socket!=null && targetPeerIPAddress!=null) {
		socket.bind(null);
		socket.connect(new InetSocketAddress(targetPeerIPAddress.getHostAddress(), WexTabCommunity.WEX_SERVER_PORT), 5000);  // the socket connects to the target peer.
		oStream = socket.getOutputStream();  // get the output stream, only once the socket is connected.
	    }
	    
	    IOManager.write(beacon, oStream);      // send the beacon to the target peer.
	    IOManager.write(dataBuffer, oStream);  // send the data to the target peer.

	    closeSocket(socket);  // close the outputstream and socket to end the transmission.
	} catch (IOException e) {
	    e.printStackTrace();
	    try {
		if(oStream != null)
		    oStream.close();
		if(socket != null)
		    socket.close();
	    } catch (IOException e2) {
		e2.printStackTrace();
	    }
	}
	
    }


    // This function :
    //     1) connects to a remote peer (targetPeerIPAddress)
    //     2) sends a beacon (beacon) to the remote peer.
    //     3) close the socket to end the transmission.
    public static void connectAndWriteBeacon(InetAddress targetPeerIPAddress, int beacon) {
	Socket socket = null;
	OutputStream oStream = null;
	
	try {
	    socket = new Socket();  // the socket used to connect to the target peer.
	    
	    if(socket!=null && targetPeerIPAddress!=null) {
		socket.bind(null);
		socket.connect(new InetSocketAddress(targetPeerIPAddress.getHostAddress(), WexTabCommunity.WEX_SERVER_PORT), 5000);  // the socket connects to the target peer.
		oStream = socket.getOutputStream();  // get the output stream.
	    }
	    
	    IOManager.write(beacon, oStream);      // send the beacon to the target peer.
	    
	    closeSocket(socket);  // close the outputstream and socket to end the transmission.
	} catch (IOException e) {
	    e.printStackTrace();
	    try {
		if(oStream != null)
		    oStream.close();
		if(socket != null)
		    socket.close();
	    } catch (IOException e2) {
		e2.printStackTrace();
	    }
	}
	
    }


    // This function converts an InputStream to a String.
    public static String convertInputStreamToString(InputStream iStream) throws Exception {
	String str = "";
	
	if(iStream == null)
	    throw new Exception("convertInputStreamToString() : iStream is null");
	
	byte buf[] = new byte[1024];
	int len = 0;
	int totalSize = 0;
	try {
	    while((len = iStream.read(buf)) != -1) {  // just read from the given input stream until the connection is closed.
		totalSize += len;
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}

	str = bytesToString(buf);
	
	return str;
    }

    // Converts a byte array to a String.
    public static String bytesToString(byte[] bytes) {
	char[] buffer = new char[bytes.length];
	int bpos = 0;
	StringBuilder charConcat = new StringBuilder("");

	for(int i=0; i<buffer.length; i++) {
	    if(bytes[bpos] == 0) {
		break;
	    }
	    char c = (char)(bytes[bpos]);
	    buffer[i] = c;
	    charConcat.append(c);
	    bpos++;
	}

	return charConcat.toString();
    }


    // Creates and return an InetAddress from the given string.
    // ex : IPaddress = "192.168.0.10"
    public static InetAddress createInetAddress(String IPaddress) {
	InetAddress inetIPAddress = null;
	
	try {
	    inetIPAddress = InetAddress.getByName(IPaddress);  // creates a new InetAddress using the string representation of the IP.
	    Log.d(TAG, "createInetAddress(...) : inetIPAddress.getHostAddress() = " + inetIPAddress.getHostAddress());
	}
	catch(UnknownHostException e) {
	    Log.d(TAG, "createInetAddress(...) : UnknownHostException, EXCEPTION message : " + e);
	}
	
	return inetIPAddress;
    }



    
    /**************************************************************************************************************/
    /* TEST AREA **************************************************************************************************/
    /**************************************************************************************************************/
    
    // Connect to the given InetAddress and send a string to it.
    public static void testConnectAndSendData(Context context, InetAddress peerIPAddress) {
	Log.d(TAG, "testConnectAndSend(...) :");
	
	String dataToSend = "Hi it's me ! Just testing...end";  // the string to be sent.
	connectAndWriteBeaconData(peerIPAddress, WexP2PProtocol.PREFIX__TEST1, dataToSend.getBytes());
	
	Log.d(TAG, "testConnectAndSend(...) : DONE.");
    }
    
    /**************************************************************************************************************/
    /* END OF TEST AREA *******************************************************************************************/
    /**************************************************************************************************************/

}  // end of class.

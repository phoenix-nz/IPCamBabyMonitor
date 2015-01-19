package de.rosent.ipcambabymonitor.cam.easyn.foscam;

import java.nio.ByteBuffer;

public class EasyNUtil {
	public final static byte ReqLogin = 0;
	public final static byte RespReqLogin = 1;
	public final static byte AuthLogin = 2;
	public final static byte RespAuthLogin = 3;
	public final static byte StartVideo = 4;
	public final static byte RespStartVideo = 5;
	public final static byte StopVideo = 6;
	public final static byte UNKNOWN_1 = 7;
	public final static byte StartAudio = 8;
	public final static byte RespStartAudio = 9;
	public final static byte StopAudio = (byte) 0x0a;
	public final static byte StartTalk = (byte) 0x0b;
	public final static byte RespStartTalk = (byte) 0x0c;
	public final static byte StopTalk = (byte) 0x0d;
	public final static byte IRSwitch = (byte) 0x0e;
	public final static byte IROn = (byte) 0x5e;
	public final static byte IROff = (byte) 0x5f;

	public final static byte UNKNOWN_2 = (byte) 0x10;
	public final static byte RespUNKNOWN_2 = (byte) 0x11;
	

	public final static byte NotifyAlarm = (byte) 0x19;
	public final static byte EndAlarm = (byte) 0x1a;
	public final static int  NoAlarm = 0;
	public final static int  MoveAlarm = 1;
	public final static int  AudioAlarm  = 2;
	
	public final static byte KeepAlive = (byte) 0xff;
	
	public final static byte[] MO_O = { (byte) 'M', (byte) 'O', (byte) '_', (byte) 'O' };
	public final static byte[] MO_V = { (byte) 'M', (byte) 'O', (byte) '_', (byte) 'V' };
	
	public final static int LengthPos = 16;
	public final static int HeaderSize = 32;
	public final static int CommandPos = 4;
	
    public static int parseContentLength(byte[] headerBytes) {
        byte[] temp = new byte[4];
        for (int i = 0; i < 4; i++ ) {
        	temp[3-i] = headerBytes[EasyNUtil.LengthPos + i];
        }
        ByteBuffer tBuffer = ByteBuffer.wrap(temp);
        return tBuffer.getInt();
    }
	
	public static byte[] getCommand( byte id ){
		byte[] command = { 0 };
		switch (id) {
		case EasyNUtil.ReqLogin:
		case EasyNUtil.StopVideo:
		case EasyNUtil.StopAudio:
		case EasyNUtil.StopTalk:
		case EasyNUtil.KeepAlive:
			command = new byte[23];
			break;
		case EasyNUtil.AuthLogin:
			command = new byte[49];
			command[15] = command[19] = 0x1a;        //length
			//No data - as the util doesn't know username/password
			break;
		case EasyNUtil.StartVideo:
		case EasyNUtil.StartAudio:
			command = new byte[24];
			command[15] = command[19] = 0x01;         //length
			command[23] = 0x01;                       //data
			break;
		case EasyNUtil.StartTalk:
			//separate - as we can adjust the audio buffer here
			command = new byte[24];
			command[15] = command[19] = 0x01;         //length
			command[23] = 0x01;                       //data (buffer length)
			break;
		case EasyNUtil.UNKNOWN_1:
			command = new byte[27];
			command[15] = command[19] = 0x04;         //length
			break;
		case EasyNUtil.UNKNOWN_2:
			command = new byte[23];
			break;
		case EasyNUtil.IRSwitch:
			command = new byte[24];
			command[15] = command[19] = 0x01;         //length
			//data is set by caller
			break;
		default:
			command = new byte[23];
		}
		
		for (int i = 0; i < 4; i ++)                 //preamble
			command[i] = EasyNUtil.MO_O[i];
		command[EasyNUtil.CommandPos] = id;          //command
		
		return command;
	}
	
	public static byte[] getCommence( byte[] magicBytes ) {
		byte[] command = new byte[27];
		for (int i = 0; i < 4; i++)                 //preamble
			command[i] = EasyNUtil.MO_V[i];
		
		command[15] = command[19] = 0x04;         //length
		
		for (int i = 0; i < 4; i++)
			command[EasyNUtil.HeaderSize+i] = magicBytes[i];
		
		return command;
	}
	
	
}

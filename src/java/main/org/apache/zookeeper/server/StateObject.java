package org.apache.zookeeper.server;

public class StateObject{
		public int size;
		public int[] list;
		public StateObject(){}
		public StateObject(int size1, int[] list1){
			size = size1;
			list = list1;
		}	   

  
  public static StateObject ByteToObj(byte[] oldByteValue){   
		String stateString = new String(oldByteValue);    		
		String[] stateTokens = stateString.split(":");
		StateObject currObject = new StateObject();
		currObject.size= Integer.parseInt(stateTokens[0]);
		currObject.list=new int[currObject.size];
		for(int i=1;i<=currObject.size;i++){
			currObject.list[i-1] = Integer.parseInt(stateTokens[i]);
		}
		return currObject;
	} 
	
	public static byte[] ObjToByte(StateObject stateObj){
		String stateString="";
		stateString+=stateObj.size;
		for(int i=0;i<stateObj.size;i++){
			stateString+=":"+stateObj.list[i];	
		}
		return stateString.getBytes();
  }
	
	public static byte[] JoinValue(byte[] oldByteValue, byte[] proposedByteValue){
  	StateObject newValue = new StateObject();   	
  	StateObject oldValue = ByteToObj(oldByteValue);
  	StateObject proposedValue = ByteToObj(proposedByteValue);
  	
  	if(oldValue!=null && proposedValue!=null){
	    	newValue.size = oldValue.size;
	    	newValue.list = new int[newValue.size];
	    	for(int i=0;i<newValue.size;i++){
	    		if(oldValue.list[i] < proposedValue.list[i]) newValue.list[i] = proposedValue.list[i];
	    		else newValue.list[i] = oldValue.list[i];
	    	}
	    	return ObjToByte(newValue);
  	}
  	else return null;
  }  
	
	public static boolean isContained (byte[] value1, byte[] value2){
		StateObject objValue1 = ByteToObj(value1);
  	StateObject objValue2 = ByteToObj(value2);
  	if(objValue1!=null && objValue2!=null){
	    	for(int i=0;i<objValue1.size;i++){
	    		if(objValue1.list[i] > objValue2.list[i]) 
	    			return false;	    		
	    	}
	    	return true;
  	}
  	return false;
	}
	
	public static String printByteValue(byte[] value){
		if(value == null) return "Value not set\n";
		String result = "(";
		StateObject obj = ByteToObj(value);
		for(int i=0;i<obj.size-1;i++){
			result += (obj.list[i]);
			result+=", ";
		}
		result+=obj.list[obj.size-1];
		result+=")";
		return result;
	}
};